package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityBodyCheckpoint;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.LinkedHashMap;

/** Owner-local formation continuation; body departure alone commits actual position and absence. */
public final class ExpeditionBodyCheckpoint {
    private ExpeditionBodyCheckpoint() { }
    static ActorActivityBodyCheckpoint.Acknowledgement acknowledge(ActorActivityBodyCheckpoint.Request request, SettlementAssault owner) {
        var state = request.expectedState();
        var march = owner.march();
        if (!march.memberIds().contains(request.execution().actorId()) || march.complete() || march.issue().isPresent()
                || owner.tacticalPlan().phase() != TacticalPlanPhase.TRAVEL)
            return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
        boolean divergent = march.memberIds().stream().anyMatch(actor -> !march.bodies().get(actor).equals(
                actor.equals(request.execution().actorId()) ? request.observedPosition() : state.actorLocations().get(actor).body()));
        if (!divergent) return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
        var approaches = new LinkedHashMap<>(march.rejoins());
        for (var actor : march.memberIds().stream().sorted().toList()) {
            var observed = actor.equals(request.execution().actorId()) ? request.observedPosition().supportingSurface()
                    : state.actorLocations().get(actor).supportingSurface();
            if (approaches.containsKey(actor) && approaches.get(actor).current().equals(observed)) continue;
            var path = HiveGroundNavigation.route(state, actor, observed, march.checkpoint(actor), java.util.Set.of());
            if (path.isEmpty()) throw new IllegalArgumentException("expedition departure lacks a bounded known rejoin");
            approaches.put(actor, new TraversalRejoin(path, 0));
        }
        return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin().strategicPlans(
                state.strategicPlans().replaceSettlementAssault(owner.withSpatialContinuation(march.withRejoins(approaches)))));
    }
    public static boolean openFormation(FrontierWorldState state, SettlementAssault owner, SettlementAssault next) {
        return owner.march().memberIds().stream().allMatch(actor -> HiveGroundNavigation.stepClear(state, actor,
                owner.formationBodies().get(actor).supportingSurface(), next.formationBodies().get(actor).supportingSurface()));
    }
}
