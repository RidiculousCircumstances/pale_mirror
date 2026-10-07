package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** Observes indexed current HOT incarnations; COLD uses the sole retained route projection. */
final class FrontierV3ActorPositionView {
    private FrontierV3ActorPositionView() { }

    static ActorPositionView observed(ServerLevel level, FrontierWorldState state, long tick) {
        var canonical = ActorPositionView.canonical(state, tick);
        return actorId -> {
            var binding = state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(actorId));
            var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actorId));
            if (binding != null && entity instanceof Mob body && body.isAlive() && !body.isRemoved()
                    && FrontierV3ActorBodyController.readyForExecution(level, state,
                        List.of(ActorBodyAuthority.current(state, actorId))))
                return FrontierV3BodyObservation.position(body);
            return canonical.bodyAt(actorId);
        };
    }
}
