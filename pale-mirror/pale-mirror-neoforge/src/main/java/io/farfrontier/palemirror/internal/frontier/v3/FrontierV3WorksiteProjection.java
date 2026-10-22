package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Shared bounded block projection. Never re-evaluates loot, force-loads or overwrites foreign facts. */
final class FrontierV3WorksiteProjection {
    private static final Map<ServerLevel, FrontierV3PendingProjection> QUEUES = new WeakHashMap<>();
    private record PreparedWindow(List<FrontierV3WorksiteBlockWitness> writes,
                                  FrontierV3JournaledSavedData.DurabilityFence fence) { }
    private static final Map<ServerLevel, PreparedWindow> PREPARING = new WeakHashMap<>();
    private static final Map<ServerLevel, Progress> PROGRESS = new WeakHashMap<>();
    private static final class Progress {
        long turns, admissionWaits, durabilityWaits, probes, prepared, applied, settled;
        String json() {
            return "{\"turns\":" + turns + ",\"admissionWaits\":" + admissionWaits
                    + ",\"durabilityWaits\":" + durabilityWaits + ",\"probes\":" + probes
                    + ",\"prepared\":" + prepared + ",\"applied\":" + applied + ",\"settled\":" + settled + "}";
        }
    }
    static String progressDiagnostic(ServerLevel level) {
        var progress = PROGRESS.get(level); return progress == null ? "null" : progress.json();
    }
    private record Availability(net.minecraft.world.level.chunk.LevelChunk chunk, boolean ticking) { }
    private FrontierV3WorksiteProjection() { }
    static void forget(ServerLevel level) { QUEUES.remove(level); PREPARING.remove(level); PROGRESS.remove(level); }
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
        var progress = PROGRESS.computeIfAbsent(level, ignored -> new Progress()); progress.turns++;
        for (var owner : FrontierV3WorksiteRegistry.OWNERS) owner.beforeTurn(level, runtime);
        var pending = PREPARING.get(level);
        // Shared admission limits NEW preparation, not completion of an already
        // fenced window. Its physical write ceiling still applies on every turn.
        if (pending == null && !FrontierV3PhysicalExecutors.registry().admitPreparation(level, "worksite-projection")) {
            progress.admissionWaits++; return;
        }
        state = runtime.decodedState().orElseThrow();
        var ledger = FrontierV3GrayboxLedger.get(level);
        var writes = new ArrayList<FrontierV3WorksiteBlockWitness>();
        if (pending != null && !ledger.durable(pending.fence())) { progress.durabilityWaits++; return; }
        int budget = state.bootstrap().ruleset().extraction().projectionWritesPerTurn();
        int readBudget = io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_WEIGHT_BUDGET.get();
        var queue = queue(level, state);
        for (long address : ledger.takeWorksiteChanges()) {
            var position = BlockPos.of(address);
            queue.invalidate(net.minecraft.world.level.ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4),
                    new BlockPosition(position.getX(), position.getY(), position.getZ()));
        }
        var viewers = level.players().stream().map(net.minecraft.server.level.ServerPlayer::chunkPosition).toList();
        var probes = pending == null ? queue.batch(key -> {
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
        }, readBudget) : List.<WorksiteBlock>of();
        var preparation = new FrontierV3FirstVisibilityBudget(readBudget, budget,
                (long) (io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000D),
                System::nanoTime);
        var iterator = probes.iterator();
        if (pending != null) writes.addAll(pending.writes());
        while (pending == null && iterator.hasNext() && preparation.takeRead()) {
            progress.probes++;
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
        // Wait for THIS immutable window, not for all other owners to stop writing.
        // The fence is only durability evidence; current authority/preimages are
        // revalidated below even when later unrelated rows remain dirty.
        if (pending == null) {
            progress.prepared += writes.size();
            pending = new PreparedWindow(List.copyOf(writes), ledger.worksiteFence(writes));
            PREPARING.put(level, pending);
            if (!ledger.durable(pending.fence())) { progress.durabilityWaits++; return; }
        }
        PREPARING.remove(level);
        writes.removeIf(write -> !currentPreparation(level, runtime.decodedState().orElseThrow(), ledger, write)
                || !FrontierV3WorksiteRegistry.owner(write.declaration().key().family())
                    .beforeProjection(level, runtime, runtime.decodedState().orElseThrow(), write.declaration()));
        var planned = writes.stream().filter(write -> level.hasChunkAt(position(write.declaration()))
                        && !FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position(write.declaration())))
                            .equals(write.declaration().block()))
                .limit(Math.min(budget, FrontierV3ManagedBlockWrites.MAX_PLANNED_WRITES))
                .map(write -> new FrontierV3ManagedBlockWrites.PlannedWrite(position(write.declaration()),
                        FrontierV3MinecraftBlockExtraction.block(write.declaration().block()))).toList();
        if (!planned.isEmpty()) FrontierV3ManagedBlockWrites.preparePlannedWrites(level, runtime, planned);
        var remaining = new ArrayList<FrontierV3WorksiteBlockWitness>();
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
                if (!preparation.takeWrite()) { remaining.add(write); continue; }
                if (!FrontierV3WorksiteWrites.apply(write.declaration().key(), () ->
                        level.setBlock(position, FrontierV3MinecraftBlockExtraction.block(write.declaration().block()), 3))) {
                    remaining.add(write); continue;
                }
                progress.applied++;
            }
            if (FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)).equals(write.declaration().block())) {
                ledger.worksite(new FrontierV3WorksiteBlockWitness(write.declaration(), write.before(), FrontierV3WorksiteBlockWitness.Phase.SETTLED));
                queue.complete(chunk(write.declaration()), write.declaration());
                progress.settled++;
            }
        }
        if (!remaining.isEmpty()) PREPARING.put(level, new PreparedWindow(List.copyOf(remaining), pending.fence()));
        if (ledger.isDirty()) ledger.persistAsync(level);
        for (var owner : FrontierV3WorksiteRegistry.OWNERS) owner.afterProjection(level, runtime);
    }
    private static boolean currentPreparation(ServerLevel level, FrontierWorldState state,
            FrontierV3GrayboxLedger ledger, FrontierV3WorksiteBlockWitness write) {
        var position = position(write.declaration());
        return level.hasChunkAt(position) && ledger.claim(position) == null
                && write.equals(ledger.worksite(position))
                && FrontierV3WorksiteRegistry.point(state, position).filter(write.declaration()::equals).isPresent();
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
