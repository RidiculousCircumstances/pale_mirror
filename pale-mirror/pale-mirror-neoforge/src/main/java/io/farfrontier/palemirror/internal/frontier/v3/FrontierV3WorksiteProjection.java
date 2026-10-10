package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Shared bounded block projection. Never re-evaluates loot, force-loads or overwrites foreign facts. */
final class FrontierV3WorksiteProjection {
    private static final int PROBES_PER_TURN = 64;
    private static final Map<ServerLevel, FrontierV3IndexedProbeCursor<WorksiteBlock>> CURSORS = new WeakHashMap<>();
    private FrontierV3WorksiteProjection() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || !state.bootstrap().ruleset().extraction().enabled()) return;
        for (var owner : FrontierV3WorksiteRegistry.OWNERS) owner.beforeTurn(level, runtime);
        state = runtime.decodedState().orElseThrow();
        var ledger = FrontierV3GrayboxLedger.get(level);
        var writes = new ArrayList<FrontierV3WorksiteBlockWitness>();
        int budget = state.bootstrap().ruleset().extraction().projectionWritesPerTurn();
        var cursor = CURSORS.computeIfAbsent(level, ignored -> new FrontierV3IndexedProbeCursor<>());
        var probes = cursor.probes(FrontierV3WorksiteRegistry.chunks(state), key -> {
            var chunk = new net.minecraft.world.level.ChunkPos(key);
            return level.getChunkSource().hasChunk(chunk.x, chunk.z);
        }, PROBES_PER_TURN);
        var iterator = probes.iterator();
        while (writes.size() < budget && iterator.hasNext()) {
            var declared = iterator.next();
            var owner = FrontierV3WorksiteRegistry.owner(declared.key().family());
            if (declared.key().family() != owner.family()) throw new IllegalStateException("worksite owner returned foreign declaration");
            var position = position(declared);
            if (!level.hasChunkAt(position)) continue;
            var current = owner.current(state, declared); var prior = ledger.worksite(position);
            if (prior != null && !prior.declaration().key().equals(current.key()))
                throw new IllegalStateException("worksite projection crosses a foreign physical owner");
            if (prior != null && prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED) {
                var committed = owner.committedEffect(state, prior);
                if (committed.isEmpty()) continue;
                if (!owner.beforeProjection(level, runtime, state, current)) continue;
                prior = committed.orElseThrow(); ledger.worksite(prior);
            }
            if (prior != null && (prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL
                    || prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING
                    || prior.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED)) continue;
            if (!owner.beforeProjection(level, runtime, state, current)) {
                state = runtime.decodedState().orElseThrow(); continue;
            }
            if (prior != null && prior.phase() == FrontierV3WorksiteBlockWitness.Phase.SETTLED && prior.declaration().equals(current)) continue;
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
        // One bounded journal force group BEFORE all writes, not a full-registry save per block.
        if (ledger.isDirty()) ledger.persist(level);
        for (var write : writes) {
            var position = position(write.declaration());
            if (!level.hasChunkAt(position)) continue;
            var actual = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position));
            if (!actual.equals(write.declaration().block())) {
                if (!actual.equals(write.before())) {
                    ledger.worksite(new FrontierV3WorksiteBlockWitness(write.declaration(), actual, FrontierV3WorksiteBlockWitness.Phase.EXTERNAL));
                    continue;
                }
                if (!FrontierV3WorksiteWrites.apply(write.declaration().key(), () ->
                        level.setBlock(position, FrontierV3MinecraftBlockExtraction.block(write.declaration().block()), 3))) continue;
            }
            if (FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)).equals(write.declaration().block()))
                ledger.worksite(new FrontierV3WorksiteBlockWitness(write.declaration(), write.before(), FrontierV3WorksiteBlockWitness.Phase.SETTLED));
        }
        if (ledger.isDirty()) ledger.persist(level);
        for (var owner : FrontierV3WorksiteRegistry.OWNERS) owner.afterProjection(level, runtime);
    }
    private static io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block generatedBase(ServerLevel level, BlockPos position) {
        // The native generator supplies the initial terrain preimage. A foreign observed
        // block is not a permission to carve it just because a plan wants an empty pit.
        var column = level.getChunkSource().getGenerator().getBaseColumn(position.getX(), position.getZ(), level, level.getChunkSource().randomState());
        return FrontierV3MinecraftBlockExtraction.describe(column.getBlock(position.getY()));
    }
    static BlockPos position(WorksiteBlock cell) { return new BlockPos(cell.position().x(), cell.position().y(), cell.position().z()); }
}
