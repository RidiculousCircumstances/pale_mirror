package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Bounded native avoidance within the current execution, never another task/body/cargo owner. */
final class FrontierV3PedestrianCourtesy {
    private record Target(FrontierV3ActorActuation authority, SurfaceAnchor station) { }
    private static final Map<Mob, Target> TARGETS = new WeakHashMap<>();
    private static final Map<Mob, Long> NEXT_QUERY = new WeakHashMap<>();
    private static final Map<Mob, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityCheckpoint.Wait> DEFERRED = new WeakHashMap<>();
    private static final Map<ServerLevel, Integer> CURSORS = new WeakHashMap<>();
    private static final int MAX_ACTIVE = 32, MAX_ADMISSIONS_PER_TICK = 4;
    private static final int MAX_PHYSICAL_CANDIDATES = MovementOrder.MAX_LEGAL_STATIONS;
    private FrontierV3PedestrianCourtesy() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        TARGETS.keySet().stream().filter(body -> !TARGETS.get(body).authority().current(body)).toList()
                .forEach(FrontierV3PedestrianCourtesy::cancel);
        var bodies = new java.util.LinkedHashSet<Mob>();
        TARGETS.keySet().stream().filter(body -> body.level() == level).toList().forEach(bodies::add);
        var candidates = FrontierV3PedestrianYieldRequests.candidates(level);
        if (!candidates.isEmpty()) {
            int cursor = CURSORS.getOrDefault(level, 0);
            for (int n = 0; n < Math.min(MAX_ADMISSIONS_PER_TICK, candidates.size()); n++) {
                var candidate = candidates.get(Math.floorMod(cursor + n, candidates.size()));
                var entity = level.getEntity(candidate.entity());
                if (entity instanceof Mob body) bodies.add(body);
            }
            CURSORS.put(level, Math.floorMod(cursor + MAX_ADMISSIONS_PER_TICK, candidates.size()));
        }
        for (Mob body : bodies) {
            var state = runtime.decodedState().orElse(null);
            if (state == null) return;
            var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
            var retained = declaration == null ? null : state.actorExecutions().actors().get(declaration.actorId());
            var execution = retained == null ? null : retained.current().orElse(null);
            if (execution == null) { cancel(body); continue; }
            // Recovery/handoff cannot be displaced by a stale local courtesy target.
            var lease = state.ambientLeases().get(execution.actorId());
            if (lease != null && lease.status() != AmbientLeaseStatus.HOT
                    || state.sceneLeases().values().stream().anyMatch(scene -> scene.retainsMemberCustody(execution.actorId())
                        && scene.status() != SceneLeaseStatus.HOT)) { cancel(body); continue; }
            FrontierV3ActorActuation authority;
            try { authority = FrontierV3ActorActuation.capture(state, body, execution, runtime::decodedState); }
            catch (IllegalArgumentException unavailable) { cancel(body); continue; }
            var checkpoint = ActorSpatialCourtesy.assess(state, execution);
            if (!checkpoint.ready()) {
                var wait = checkpoint.waiting().orElseThrow();
                if (!wait.equals(DEFERRED.put(body, wait)))
                    io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                            "PMV3_TRAFFIC_YIELD_DEFER actor={} activity={} reason={} dependency={}",
                            execution.actorId().value(), execution.activityKind(), wait.reason(), wait.dependencyOwner().value());
                cancel(body);
                continue;
            }
            DEFERRED.remove(body);
            if (TARGETS.containsKey(body) || TARGETS.size() < MAX_ACTIVE) pursue(level, state, body, authority);
            // The ordinary body observer remains the only canonical position writer.
            state = runtime.decodedState().orElse(null);
            lease = state == null ? null : state.ambientLeases().get(execution.actorId());
            if (lease != null && lease.status() == AmbientLeaseStatus.HOT)
                FrontierV3AmbientServiceOccupancy.observe(level, runtime, state, execution.actorId(), body, lease);
        }
    }

    static boolean active(Mob body) {
        var target = TARGETS.get(body);
        return target != null && target.authority().current(body);
    }

    static boolean active(FrontierWorldState state, SubjectId actor) {
        return TARGETS.entrySet().stream().anyMatch(entry -> entry.getValue().authority().id().execution().actorId().equals(actor)
                && entry.getValue().authority().current(entry.getKey())
                && state.actorExecutions().actors().get(actor) != null
                && state.actorExecutions().actors().get(actor).current().filter(
                    entry.getValue().authority().id().execution()::equals).isPresent()
                && ActorSpatialCourtesy.assess(state, entry.getValue().authority().id().execution()).ready());
    }

    private static void cancel(Mob body) {
        var target = TARGETS.remove(body);
        if (target != null) FrontierV3GoalNavigation.stopCourtesy(body, target.authority());
    }

    private static boolean canYield(FrontierWorldState state, Mob body) {
        if (body == null) return false;
        var declared = FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
        var retained = declared == null ? null : state.actorExecutions().actors().get(declared.actorId());
        var execution = retained == null ? null : retained.current().orElse(null);
        var lease = execution == null ? null : state.ambientLeases().get(execution.actorId());
        return execution != null && (lease == null || lease.status() == AmbientLeaseStatus.HOT)
                && ActorSpatialCourtesy.assess(state, execution).ready();
    }

    private static boolean pursue(ServerLevel level, FrontierWorldState state, Mob body, FrontierV3ActorActuation actuation) {
        SubjectId actor = actuation.id().execution().actorId();
        Target target = TARGETS.get(body);
        if (target != null && !target.authority().id().equals(actuation.id())) {
            cancel(body); target = null;
        }
        var request = FrontierV3PedestrianYieldRequests.forBody(level, body, actuation.id().body(), other -> canYield(state, other));
        if (target == null && request.isEmpty()) return false;
        if (target == null || !FrontierV3SemanticMovement.targetIsNavigable(level, body, target.station())) {
            if (request.isEmpty()) { cancel(body); return false; }
            if (level.getGameTime() < NEXT_QUERY.getOrDefault(body, 0L)) return target != null;
            NEXT_QUERY.put(body, level.getGameTime() + FrontierV3MinecraftGoalNavigation.retryIntervalTicks());
            var observed = FrontierV3BodyObservation.capture(body).supportedBody();
            if (observed.isEmpty()) return false;
            var start = observed.orElseThrow().supportingSurface();
            var points = state.bootstrap().settlements().stream()
                    .flatMap(settlement -> SettlementServiceAccessPoints.forSettlement(state, settlement.id()).stream()).toList();
            var knowledge = PedestrianLocalDeparture.departure(state, start);
            var excluded = new HashSet<>(ServiceDestinationClaims.excludedFor(state, actor));
            TARGETS.entrySet().removeIf(entry -> !entry.getValue().authority().current(entry.getKey()));
            TARGETS.forEach((other, reserved) -> { if (other != body) excluded.add(reserved.station()); });
            var scope = new FrontierV3NavigationScope.ObservedWorld(state.bootstrap().bounds());
            var passage = request.orElseThrow().passage();
            int[] queries = {0};
            var destination = ServiceAreaDestinations.select(points, actor,
                    start, knowledge, excluded,
                    station -> PedestrianLocalDeparture.publicPosition(state, station) && !passage.contains(station.support())
                            && FrontierV3SemanticMovement.targetIsNavigable(level, body, station)
                            && queries[0]++ < MAX_PHYSICAL_CANDIDATES
                            && FrontierV3GoalNavigation.canReach(level, body, FrontierV3GoalNavigation.Goal.station(station, scope)));
            if (destination.isEmpty()) return false; // No invented route and no recursive displacement chain.
            target = new Target(actuation, destination.orElseThrow());
            TARGETS.put(body, target);
            FrontierV3PedestrianYieldRequests.clear(body); // A yielding body cannot start a displacement chain.
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                    "PMV3_TRAFFIC_YIELD actor={} activity={} generation={} bodyEpoch={} requester={} target={}",
                    actor.value(), actuation.id().execution().activityKind(), actuation.id().execution().generation(),
                    actuation.id().body().physicalEpoch(), request.orElseThrow().requester(), target.station());
        }
        MovementOrder order = new MovementOrder(actor, actor, 0,
                actuation.id().execution().generation(), List.of(target.station()), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        var result = FrontierV3GoalNavigation.pursueCourtesy(level, body,
                FrontierV3GoalNavigation.Goal.routed(order, List.of(), state.bootstrap().bounds()), actuation);
        if (result.status() == FrontierV3GoalNavigation.Status.ARRIVED) {
            cancel(body); NEXT_QUERY.remove(body);
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_TRAFFIC_YIELD_COMPLETE actor={} target={}",
                    actor.value(), target.station());
        } else if (result.status() == FrontierV3GoalNavigation.Status.BLOCKED
                || result.status() == FrontierV3GoalNavigation.Status.AMBIGUOUS) {
            cancel(body);
            NEXT_QUERY.put(body, level.getGameTime() + FrontierV3MinecraftGoalNavigation.retryIntervalTicks());
        }
        // Arrival is courtesy completion only, never evidence of ordinary work/meal arrival.
        return true;
    }
}
