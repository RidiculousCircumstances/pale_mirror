package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierIdentity;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Readiness to inspect a body, not proof that an absent body has died or disappeared. */
final class FrontierV3SceneReleaseReadiness {
    private FrontierV3SceneReleaseReadiness() { }

    static boolean awaitingEntityStorage(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        // A stored receipt also permits inspection, not acceptance. The release owner must
        // reject stale/conflicting evidence rather than hide it behind an unloaded-column wait.
        return awaitingEntityStorage(state, lease, id -> level.getEntity(id) != null
                        || lease.members().stream().filter(member -> member.entityId().equals(id))
                            .anyMatch(member -> FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).departure(member.actorId()).isPresent()),
                body -> FrontierV3SceneExecutor.entityStorageReady(level, new BlockPos(body.x(), body.y(), body.z())))
                || awaitingCargoStorage(state, lease, id -> level.getEntity(id) != null
                        || FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId()).observation(id).isPresent(),
                        support -> FrontierV3SceneExecutor.entityStorageReady(level, new BlockPos(support.x(), support.y(), support.z())));
    }

    /** Cargo absence also needs entity-storage readiness, not just loaded actor chunks. */
    static boolean awaitingCargoStorage(FrontierWorldState state, SceneLease lease,
                                       java.util.function.Predicate<java.util.UUID> evidenceAvailable,
                                       java.util.function.Predicate<BlockPosition> storageReady) {
        if (!FrontierSceneBehaviors.isLogistics(lease)) return false;
        var logistics = FrontierSceneBehaviors.logistics(lease);
        var operation = state.operations().get(logistics.operationId());
        if (operation != null && operation.stage() == OperationStage.INTERRUPTED) return false;
        // Contradictory evidence must reach the release validator, never wait behind this gate.
        if (evidenceAvailable.test(CargoCarrierIdentity.id(lease))) return false;
        if (!storageReady.test(logistics.cargoPosition())) return true;
        return operation != null && operation.activeTravel().isPresent()
                && !storageReady.test(operation.activeTravel().orElseThrow().cargoAnchor().surface().support());
    }

    static boolean awaitingEntityStorage(FrontierWorldState state, SceneLease lease,
                                         java.util.function.Predicate<java.util.UUID> evidenceAvailable,
                                         java.util.function.Predicate<io.farfrontier.palemirror.frontier.v3.model.BodyPosition> storageReady) {
        for (var member : lease.members()) {
            var actor = state.actorLocations().get(member.actorId());
            if (actor == null) throw new IllegalStateException("release member lacks canonical actor: " + member.actorId());
            if (actor.condition().status() == ActorLifeStatus.DEAD) continue;
            // Global UUID lookup may find the moving body outside the original handoff chunk.
            // A present body is inspected by its owner even if that old chunk has unloaded.
            if (evidenceAvailable.test(member.entityId())) continue;
            var body = actor.body();
            if (!storageReady.test(body)) return true;
            var retained = lease.memberPosition(member.actorId());
            if (!storageReady.test(retained)) return true;
        }
        return false;
    }
}
