package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.*;

/** Bounded physical facts used by one route-start decision. No pose store or arrival authority. */
public record MovementPositionSnapshot(long atTick, List<HotPoint> hotPoints) {
    public static final int MAX_POINTS = 64;
    public record HotPoint(ActorBodyId body, ActorPositionView.TravelPoint point) {
        public HotPoint { Objects.requireNonNull(body); Objects.requireNonNull(point); }
    }
    public MovementPositionSnapshot {
        hotPoints = List.copyOf(hotPoints);
        if (atTick < 0 || hotPoints.size() > MAX_POINTS
                || hotPoints.stream().map(p -> p.body().actorId()).distinct().count() != hotPoints.size())
            throw new IllegalArgumentException("invalid movement observation snapshot");
    }
    public ActorPositionView positions(FrontierWorldState state, long decisionTick, Collection<SubjectId> subjects) {
        if (atTick != decisionTick) throw new IllegalArgumentException("movement observation belongs to another instant");
        var points = new LinkedHashMap<SubjectId, ActorPositionView.TravelPoint>();
        for (var observation : hotPoints) {
            if (!subjects.contains(observation.body().actorId())
                    || ActorBodyAuthority.require(state, observation.body()).phase() != FencedRecoveryPhase.RUNNING
                    || state.actorLocations().get(observation.body().actorId()).condition().status() != ActorLifeStatus.ALIVE)
                throw new IllegalArgumentException("movement observation has foreign or inactive body authority");
            var point = observation.point();
            FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), new BlockPosition(
                    (int) Math.floor(point.x()), (int) Math.ceil(point.y()) - 1, (int) Math.floor(point.z())));
            points.put(observation.body().actorId(), point);
        }
        var cold = ActorPositionView.canonical(state, decisionTick);
        return new ActorPositionView() {
            @Override public BodyPosition bodyAt(SubjectId actor) { return cold.bodyAt(actor); }
            @Override public TravelPoint pointAt(SubjectId actor) {
                return currentPointAt(actor).orElseThrow(() -> new IllegalArgumentException("movement position unavailable"));
            }
            @Override public Optional<TravelPoint> currentPointAt(SubjectId actor) {
                return points.containsKey(actor) ? Optional.of(points.get(actor)) : cold.currentPointAt(actor);
            }
        };
    }
}
