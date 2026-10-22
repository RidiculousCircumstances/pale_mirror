package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Shared bounded block projection. Never re-evaluates loot, force-loads or overwrites foreign facts. */
final class FrontierV3WorksiteProjection {
    private static final Map<ServerLevel, FrontierV3PendingProjection> QUEUES = new WeakHashMap<>();
    private static final Map<ServerLevel, List<WorksiteBlock>> PREPARING = new WeakHashMap<>();
    private record Availability(net.minecraft.world.level.chunk.LevelChunk chunk, boolean ticking) { }
    private FrontierV3WorksiteProjection() { }
    static void forget(ServerLevel level) { QUEUES.remove(level); PREPARING.remove(level); }
    private static FrontierV3PendingProjection queue(ServerLevel level, FrontierWorldState state) {
        var queue = QUEUES.computeIfAbsent(level, ignored -> new FrontierV3PendingProjection());
        queue.update(FrontierV3WorksiteRegistry.currentChunks(state));
        return queue;
    }
    static int pending(ServerLevel level, FrontierWorldState state, net.minecraft.world.level.ChunkPos chunk) {
        return queue(level, state).pending(chunk.toLong());
    }
    private static long chunk(WorksiteBlock block) {
        return net.minecraft.world.level.ChunkPos.asLong(block.position().x() >> 4, block.position().z() >> 4);
    }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || !state.bootstrap().ruleset().extraction().enabled()) return;
        for (var owner : FrontierV3WorksiteRegistry.OWNERS) owner.beforeTurn(level, runtime);
        if (!FrontierV3PhysicalExecutors.registry().admitPreparation(level, "worksite-projection")) return;
        state = runtime.decodedState().orElseThrow();
        var ledger = FrontierV3GrayboxLedger.get(level);
        if (!ledger.prepareDurable(level)) return;
        var writes = new ArrayList<FrontierV3WorksiteBlockWitness>();
        int budget = state.bootstrap().ruleset().extraction().projectionWritesPerTurn();
        int readBudget = io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_WEIGHT_BUDGET.get();
        var queue = queue(level, state);
        for (long address : ledger.takeWorksiteChanges()) {
            var position = BlockPos.of(address);
            queue.invalidate(net.minecraft.world.level.ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4),
                    new BlockPosition(position.getX(), position.getY(), position.getZ()));
        }
        var viewers = level.players().stream().map(net.minecraft.server.level.ServerPlayer::chunkPosition).toList();
        var probes = queue.batch(key -> {
            var chunk = new net.minecraft.world.level.ChunkPos(key);
            var loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
            return loaded == null ? null : new Availability(loaded, level.shouldTickBlocksAt(
                    new BlockPos(chunk.x * 16, level.getMinBuildHeight(), chunk.z * 16)));
        }, key -> {
            var chunk = new net.minecraft.world.level.ChunkPos(key);
            // An already-presented chunk can still need a renewed HOT custody proof.
            // Current viewer proximity, not historical first-presentation status,
            // prioritizes both first entry and loaded/ticking re-entry.
            return viewers.stream().mapToLong(viewer -> Math.abs((long) viewer.x - chunk.x) + Math.abs((long) viewer.z - chunk.z))
                    .min().orElse(Long.MAX_VALUE);
        }, readBudget);
        var preparation = new FrontierV3FirstVisibilityBudget(readBudget, budget,
                (long) (io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000D),
                System::nanoTime);
        var iterator = PREPARING.getOrDefault(level, probes).iterator();
        while (iterator.hasNext() && preparation.takeRead()) {
            var declared = iterator.next();
            var owner = FrontierV3WorksiteRegistry.owner(declared.key().family());
            if (declared.key().family() != owner.family()) throw new IllegalStateException("worksite owner returned foreign declaration");
            var position = position(declared);
            if (!level.hasChunkAt(position)) continue;
            var current = owner.current(state, declared); var prior = alignDeclaration(ledger, state, current);
            if (prior != null && !prior.declaration().key().equals(current.key())) continue;
            if (prior != null && prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED) {
                var committed = owner.committedEffect(state, prior);
                if (committed.isEmpty()) {
                    // The effect owner, not projection, resumes this split. Handoff still
                    // checks its exact operation/postimage before allowing that owner back.
                    queue.complete(chunk(current), current); continue;
                }
                if (!owner.beforeProjection(level, runtime, state, current)) continue;
                prior = committed.orElseThrow(); ledger.worksite(prior);
            }
            if (prior != null && (prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL
                    || prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING
                    || prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED)) {
                queue.complete(chunk(current), current); continue;
            }
            if (!owner.beforeProjection(level, runtime, state, current)) {
                state = runtime.decodedState().orElseThrow(); continue;
            }
            if (prior != null && prior.phase() == FrontierV3WorksiteBlockWitness.Phase.SETTLED && prior.declaration().equals(current)) {
                queue.complete(chunk(current), current); continue;
            }
            var actual = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position));
            if (prior != null && (prior.phase() == FrontierV3WorksiteBlockWitness.Phase.PREPARED
                    || prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_COMMITTED)) {
                // Only a retained preimage or its exact postimage can finish that intent.
                if (!prior.declaration().equals(current)) throw new IllegalStateException("canonical worksite advanced through an unresolved physical write");
                if (!actual.equals(prior.before()) && !actual.equals(current.block())) {
                    ledger.worksite(new FrontierV3WorksiteBlockWitness(current, actual, FrontierV3WorksiteBlockWitness.Phase.EXTERNAL));
                    continue;
                }
                writes.add(prior); continue;
            }
            var preimage = prior == null ? generatedBase(level, position) : prior.declaration().block();
            if (ledger.claim(position) != null) continue; // Another declared owner is never overwritten or adopted.
            if (level.getBlockEntity(position) != null || !actual.equals(preimage) && (prior == null || !actual.equals(current.block()))) {
                ledger.worksite(new FrontierV3WorksiteBlockWitness(current, actual, FrontierV3WorksiteBlockWitness.Phase.EXTERNAL)); continue;
            }
            var prepared = new FrontierV3WorksiteBlockWitness(current, preimage, FrontierV3WorksiteBlockWitness.Phase.PREPARED);
            ledger.worksite(prepared); writes.add(prepared);
        }
        // Retain the exact prepared window while storage advances on later turns.
        // Re-entry still rechecks owner/current declaration and the actual preimage.
        if (!ledger.prepareDurable(level)) {
            PREPARING.put(level, writes.stream().map(FrontierV3WorksiteBlockWitness::declaration).toList());
            return;
        }
        PREPARING.remove(level);
        var planned = writes.stream().filter(write -> level.hasChunkAt(position(write.declaration()))
                        && !FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position(write.declaration())))
                            .equals(write.declaration().block()))
                .limit(Math.min(budget, FrontierV3ManagedBlockWrites.MAX_PLANNED_WRITES))
                .map(write -> new FrontierV3ManagedBlockWrites.PlannedWrite(position(write.declaration()),
                        FrontierV3MinecraftBlockExtraction.block(write.declaration().block()))).toList();
        if (!planned.isEmpty()) FrontierV3ManagedBlockWrites.preparePlannedWrites(level, runtime, planned);
        for (var write : writes) {
            var position = position(write.declaration());
            if (!level.hasChunkAt(position)) continue;
            var actual = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position));
            if (!actual.equals(write.declaration().block())) {
                if (!actual.equals(write.before())) {
                    ledger.worksite(new FrontierV3WorksiteBlockWitness(write.declaration(), actual, FrontierV3WorksiteBlockWitness.Phase.EXTERNAL));
                    continue;
                }
                // Current preimages need a durable witness, not a block-write permit.
                // Unused prepared intents remain pending if actual mutations exhaust
                // this turn's allowance; their owner/recovery proof is unchanged.
                if (!preparation.takeWrite()) continue;
                if (!FrontierV3WorksiteWrites.apply(write.declaration().key(), () ->
                        level.setBlock(position, FrontierV3MinecraftBlockExtraction.block(write.declaration().block()), 3))) continue;
            }
            if (FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)).equals(write.declaration().block())) {
                ledger.worksite(new FrontierV3WorksiteBlockWitness(write.declaration(), write.before(), FrontierV3WorksiteBlockWitness.Phase.SETTLED));
                queue.complete(chunk(write.declaration()), write.declaration());
            }
        }
        if (ledger.isDirty()) ledger.persistAsync(level);
        for (var owner : FrontierV3WorksiteRegistry.OWNERS) owner.afterProjection(level, runtime);
    }
    /** Same metadata-only boundary for projection and an unloaded region's proven withdrawal. */
    static FrontierV3WorksiteBlockWitness alignDeclaration(FrontierV3GrayboxLedger ledger,
            FrontierWorldState state, WorksiteBlock current) {
        var prior = ledger.worksite(position(current));
        if (prior == null || prior.declaration().key().equals(current.key())) return prior;
        var owner = FrontierV3WorksiteRegistry.owner(current.key().family());
        var transition = owner.reclassification(state, prior.declaration(), current).orElseThrow(
                () -> new IllegalStateException("worksite projection crosses an undeclared physical owner at " + current.position()
                        + "; retained=" + prior.declaration().key() + "; proposed=" + current.key()));
        if (!transition.predecessor().equals(prior.declaration()) || !transition.successor().equals(current))
            throw new IllegalStateException("worksite owner returned a foreign reclassification proof");
        if (prior.phase() != FrontierV3WorksiteBlockWitness.Phase.SETTLED) return prior;
        // No block write or resource receipt: the following physical action still needs custody.
        return ledger.reclassify(prior, transition);
    }
    private static io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block generatedBase(ServerLevel level, BlockPos position) {
        // The native generator supplies the initial terrain preimage. A foreign observed
        // block is not a permission to carve it just because a plan wants an empty pit.
        return FrontierV3MinecraftBlockExtraction.describe(
                io.farfrontier.palemirror.internal.world.GeneratedTerrainBaseline.block(level, position));
    }
    static BlockPos position(WorksiteBlock cell) { return new BlockPos(cell.position().x(), cell.position().y(), cell.position().z()); }
}
