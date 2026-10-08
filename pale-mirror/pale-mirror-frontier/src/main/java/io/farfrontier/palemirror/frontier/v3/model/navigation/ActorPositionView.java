package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** Read-only as-of positions. An observation cannot award arrival, custody or execution authority. */
@FunctionalInterface
public interface ActorPositionView {
    BodyPosition bodyAt(SubjectId actorId);

    /** Ephemeral feet observation for continuous steering; never a second durable location. */
    record TravelPoint(double x, double y, double z) {
        public TravelPoint {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("non-finite travel observation");
        }
        public static TravelPoint at(BodyPosition body) {
            return new TravelPoint(body.x() + 0.5, body.y(), body.z() + 0.5);
        }
    }
    default TravelPoint pointAt(SubjectId actorId) { return TravelPoint.at(bodyAt(actorId)); }

    /** Empty means a physically held body has no current observation, not a distant pedestrian. */
    default java.util.Optional<TravelPoint> currentPointAt(SubjectId actorId) {
        return java.util.Optional.of(pointAt(actorId));
    }

    static ActorPositionView canonical(FrontierWorldState state, long tick) {
        return new ActorPositionView() {
            @Override public BodyPosition bodyAt(SubjectId actorId) {
                return ActorMovementProjection.bodyAt(state, actorId, tick);
            }
            @Override public java.util.Optional<TravelPoint> currentPointAt(SubjectId actorId) {
                return io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.retainsPhysicalCustody(state, actorId)
                        ? java.util.Optional.empty() : java.util.Optional.of(pointAt(actorId));
            }
        };
    }
}
