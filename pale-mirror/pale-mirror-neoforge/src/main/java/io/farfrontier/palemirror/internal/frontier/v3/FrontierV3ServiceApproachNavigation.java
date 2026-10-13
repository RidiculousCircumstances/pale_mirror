package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Ephemeral waiting leg; never changes the retained owner goal or awards its arrival. */
final class FrontierV3ServiceApproachNavigation {
    // One rebuildable local target per live body, never a canonical queue or a movement writer.
    private record Waiting(FrontierV3ActorActuation authority, SubjectId point, int phase,
                           long generation, SurfaceAnchor target) { }
    private static final Map<Mob, Waiting> WAITING = new WeakHashMap<>();
    private record Search(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId authority,
                          SubjectId point, int phase, long generation, int next) { }
    private static final Map<Mob, Search> SEARCHES = new WeakHashMap<>();
    private static final int MAX_CANDIDATE_PROBES = 8;
    private FrontierV3ServiceApproachNavigation() { }

    static void forget(Mob body) { WAITING.remove(body); SEARCHES.remove(body); }

    /** Travel does not acquire service: reserve a local waiting target, never the shared egress. */
    static FrontierV3GoalNavigation.Result waitOutside(ServerLevel level, Mob body, FrontierWorldState state,
            SubjectId pointId, SubjectId actorId, int phase, long generation,
            FrontierV3ActorActuation actuation) {
        requireWaitingAuthority(state, actorId, actuation.id());
        var declared = ServiceBoundaryComposition.declaration(state, pointId);
        var point = ServiceBoundaryComposition.geometry(state, declared.identity());
        var excluded = new HashSet<>(ServiceDestinationClaims.excludedFor(state, actorId));
        WAITING.entrySet().removeIf(entry -> !entry.getValue().authority().current(entry.getKey()));
        WAITING.forEach((other, waiting) -> { if (other != body) excluded.add(waiting.target()); });
        var prior = WAITING.get(body);
        if (prior == null || !prior.authority().id().equals(actuation.id()) || !prior.point().equals(pointId)
                || prior.phase() != phase || prior.generation() != generation
                || !point.waitingSurfaces().contains(prior.target()) || excluded.contains(prior.target())
                || !FrontierV3SemanticMovement.targetGeometryIsNavigable(level, body, prior.target())) {
            WAITING.remove(body);
            var origin = FrontierV3SurfaceObservation.observedBody(body).supportingSurface();
            var candidates = ServiceClearanceTargets.waitingTargets(point, origin, excluded);
            // Different visitors prefer different pockets, but an existing target remains stable.
            var search = SEARCHES.get(body);
            int cursor = search != null && search.authority().equals(actuation.id()) && search.point().equals(pointId)
                    && search.phase() == phase && search.generation() == generation ? search.next() : actorId.value().hashCode();
            int first = candidates.isEmpty() ? 0 : Math.floorMod(cursor, candidates.size());
            for (int offset = 0; offset < Math.min(candidates.size(), MAX_CANDIDATE_PROBES); offset++) {
                var candidate = candidates.get((first + offset) % candidates.size());
                if (!level.hasChunkAt(new net.minecraft.core.BlockPos(candidate.x(), candidate.y(), candidate.z()))
                        || !FrontierV3SemanticMovement.targetGeometryIsNavigable(level, body, candidate)) continue;
                prior = new Waiting(actuation, pointId, phase, generation, candidate);
                WAITING.put(body, prior);
                SEARCHES.put(body, new Search(actuation.id(), pointId, phase, generation,
                        (first + offset + 1) % candidates.size()));
                break;
            }
            if (!WAITING.containsKey(body)) SEARCHES.put(body, new Search(actuation.id(), pointId, phase, generation,
                    first + Math.min(candidates.size(), MAX_CANDIDATE_PROBES)));
        }
        if (prior == null || !WAITING.containsKey(body)) {
            FrontierV3GoalNavigation.stop(body, actuation);
            return new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.BLOCKED,
                    "service-access:no-reachable-free-waiting-target",
                    Optional.of(FrontierV3GoalNavigation.BlockReason.SEARCH_BUDGET_EXHAUSTED), Optional.empty());
        }
        var result = FrontierV3GoalNavigation.pursue(level, body,
                waitingGoal(point, prior.target(), state.bootstrap().bounds()), actuation);
        // Do not preflight with canReach: a pedestrian obstruction must reach the
        // navigator's courtesy protocol, not become a permanent no-path decision.
        // A real geometry failure advances the bounded candidate search next turn.
        if (result.status() == FrontierV3GoalNavigation.Status.BLOCKED
                && result.blockReason().filter(reason -> reason == FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE
                    || reason == FrontierV3GoalNavigation.BlockReason.OFF_CONTRACT
                    || reason == FrontierV3GoalNavigation.BlockReason.TARGET_CHUNK_UNLOADED).isPresent()) WAITING.remove(body);
        return result.status() == FrontierV3GoalNavigation.Status.ARRIVED
                ? new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.IN_PROGRESS,
                    "service-access:waiting-outside target=" + prior.target(), Optional.empty(), Optional.empty())
                : result; // Never return semantic ARRIVED to a work owner.
    }

    /**
     * The owning provider validates its service/order relation before calling us.
     * A parcel can legitimately be serviced by a GROUP_MEMBER execution whose
     * owner is the group, not the parcel. Waiting only exercises that actor's
     * exact captured body/execution; it cannot acquire or complete service.
     */
    static void requireWaitingAuthority(FrontierWorldState state, SubjectId actorId,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId authority) {
        if (!authority.execution().actorId().equals(actorId))
            throw new IllegalArgumentException("service waiting addresses a foreign actor: actor="
                    + actorId + "; authority=" + authority);
        ActorBodyAuthority.requireActuation(state, authority);
    }

    static FrontierV3GoalNavigation.Goal waitingGoal(ServiceAccessPoint point, SurfaceAnchor target, WorldBounds bounds) {
        if (!point.waitingSurfaces().contains(target) || point.egressSurfaces().contains(target)
                || !point.boundary().cleared(target.standingBody()))
            throw new IllegalArgumentException("waiting target must belong to the declared non-egress buffer");
        return new FrontierV3GoalNavigation.Goal(List.of(target), TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.OutsideService(new FrontierV3NavigationScope.ObservedWorld(bounds), point.boundary()),
                Optional.empty(), List.of());
    }
}
