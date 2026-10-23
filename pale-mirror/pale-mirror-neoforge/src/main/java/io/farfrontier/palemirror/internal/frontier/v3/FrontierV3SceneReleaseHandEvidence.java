package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.Optional;
import java.util.Set;

/** Physical evidence only: reads an exact present body or its already-validated saved departure. */
final class FrontierV3SceneReleaseHandEvidence {
    private FrontierV3SceneReleaseHandEvidence() { }
    static Optional<FrontierV3ActorBodyDeparture.HandStack> read(ServerLevel level, FrontierWorldState state,
            SceneLease lease, SceneMember member, Set<SubjectId> departed, ActorContainerItemOrder.Hand hand) {
        var carrier = level.getEntity(member.entityId());
        if (carrier instanceof Mob body && FrontierV3SceneExecutor.owned(body, state, lease, member)) {
            var held = hand == ActorContainerItemOrder.Hand.MAIN ? body.getMainHandItem() : body.getOffhandItem();
            return FrontierV3ActorHandEvidence.capturable(held)
                    ? FrontierV3ActorHandEvidence.observed(held) : Optional.empty();
        }
        if (carrier != null || !departed.contains(member.actorId())) return Optional.empty();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        return FrontierV3SceneDepartureObserver.validDeparture(state, lease, member, ledger)
                .flatMap(receipt -> hand == ActorContainerItemOrder.Hand.MAIN ? receipt.mainhand() : receipt.offhand());
    }
}
