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
        return new ActorPositionView() {
          private Mob observed(io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
            var binding = state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(actorId));
            var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actorId));
            if (binding != null && entity instanceof Mob body && body.isAlive() && !body.isRemoved()
                    && FrontierV3ActorBodyController.readyForExecution(level, state,
                        List.of(ActorBodyAuthority.current(state, actorId))))
                return body;
            return null;
          }
          @Override public io.farfrontier.palemirror.frontier.v3.model.BodyPosition bodyAt(io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
            var body = observed(actorId);
            return body == null ? canonical.bodyAt(actorId) : FrontierV3BodyObservation.position(body);
          }
          @Override public TravelPoint pointAt(io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
            var body = observed(actorId);
            return body == null ? canonical.pointAt(actorId) : new TravelPoint(body.getX(), body.getY(), body.getZ());
          }
          @Override public java.util.Optional<TravelPoint> currentPointAt(io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
            var body = observed(actorId);
            return body == null ? canonical.currentPointAt(actorId)
                    : java.util.Optional.of(new TravelPoint(body.getX(), body.getY(), body.getZ()));
          }
        };
    }

    /** Exact live witnesses only. Missing physical facts remain explicitly unknown. */
    static io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPositionSnapshot snapshot(
            ServerLevel level, FrontierWorldState state, long tick,
            java.util.Collection<io.farfrontier.palemirror.frontier.v3.api.SubjectId> subjects) {
        var positions = observed(level, state, tick);
        var points = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPositionSnapshot.HotPoint>();
        for (var actor : subjects) {
            if (!ActorBodyAuthority.retainsPhysicalCustody(state, actor)) continue;
            positions.currentPointAt(actor).ifPresent(point -> points.add(
                    new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPositionSnapshot.HotPoint(
                            ActorBodyAuthority.current(state, actor), point)));
        }
        return new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPositionSnapshot(tick, points);
    }
}
