package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaObserved;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reconciles ordinary Minecraft explosion aftermath without ownership filtering or forced loads. */
final class FrontierV3PhysicalObservationExecutor {
    private static final int MAX_CELLS_PER_TICK = 64;

    private FrontierV3PhysicalObservationExecutor() { }

    static boolean captureExternalExplosion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                            List<BlockPos> affected) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        Set<Long> resourceSiteCells = FrontierV3ResourceSiteExplosionExecutor.activeOwnedCells(level, state);
        return FrontierV3PhysicalObservationLedger.get(level).captureExternalExplosion(level, level.getGameTime(), affected,
                FrontierV3GrayboxLedger.get(level), position -> state.bootstrap().bounds().contains(new BlockPosition(position.getX(), position.getY(), position.getZ()))
                        && !resourceSiteCells.contains(position.asLong()));
    }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3PhysicalObservationLedger ledger = FrontierV3PhysicalObservationLedger.get(level);
        for (int index = 0; index < MAX_CELLS_PER_TICK; index++) {
            Optional<FrontierV3PhysicalObservationLedger.Ready> ready = ledger.nextReady(level.getGameTime());
            if (ready.isEmpty()) return;
            FrontierV3PhysicalObservationLedger.Ready value = ready.orElseThrow(); BlockPos position = value.candidate().blockPos();
            if (!level.hasChunkAt(position)) return; // retained for an ordinary later loaded-chunk inspection
            if (level.getBlockState(position).equals(value.candidate().baseline(level.registryAccess()))) {
                ledger.resolve(value); continue;
            }
            io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            FrontierWorldState state = runtime.decodedState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            BlockPosition canonicalPosition = new BlockPosition(position.getX(), position.getY(), position.getZ());
            if (state.physicalDeltas().containsKey(canonicalPosition)) {
                ledger.resolve(value); continue; // crash/retry after a durable command append
            }
            PhysicalDelta delta = delta(value, canonicalPosition);
            CommandId id = FrontierV3CommandIds.externalExplosionObservation(value.effectId(), position.asLong());
            CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalDeltaObserved(delta)))
                    .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("v3 physical observation command was rejected: " + result);
            ledger.resolve(value);
        }
    }

    static void recordManagedExplosionDelta(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                           io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId intentId,
                                           FrontierV3ManagedExplosionLedger.BlockCandidate candidate) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        BlockPos position = candidate.blockPos(); BlockPosition canonicalPosition = new BlockPosition(position.getX(), position.getY(), position.getZ());
        FrontierWorldState state = runtime.decodedState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (state.physicalDeltas().containsKey(canonicalPosition)) return;
        Optional<FrontierV3PhysicalObservationLedger.Semantic> semantic = candidate.semantic();
        PhysicalDelta delta = semantic.isEmpty() ? new PhysicalDelta(canonicalPosition, PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "explosion:" + intentId.value())
                : new PhysicalDelta(canonicalPosition, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(new SubjectId(semantic.orElseThrow().owner())),
                Optional.of(GrayboxSemanticPart.valueOf(semantic.orElseThrow().semanticPart())), "explosion:" + intentId.value());
        CommandId id = FrontierV3CommandIds.managedExplosionObservation(intentId, position.asLong());
        CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalDeltaObserved(delta)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("managed explosion observation command was rejected: " + result);
    }

    private static PhysicalDelta delta(FrontierV3PhysicalObservationLedger.Ready ready, BlockPosition position) {
        String cause = "explosion:" + ready.effectId();
        Optional<FrontierV3PhysicalObservationLedger.Semantic> semantic = ready.candidate().semantic();
        if (semantic.isEmpty()) return new PhysicalDelta(position, PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), cause);
        FrontierV3PhysicalObservationLedger.Semantic known = semantic.orElseThrow();
        return new PhysicalDelta(position, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(new SubjectId(known.owner())),
                Optional.of(GrayboxSemanticPart.valueOf(known.semanticPart())), cause);
    }
}
