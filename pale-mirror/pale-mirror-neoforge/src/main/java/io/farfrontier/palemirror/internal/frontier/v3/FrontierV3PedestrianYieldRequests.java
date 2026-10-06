package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Bounded HOT courtesy requests, never a body actuator, service permit or canonical activity. */
final class FrontierV3PedestrianYieldRequests {
    private static final int MAX_REQUESTS = 256;
    private static final int MAX_RETAINED_TICKS = 100; // Stale-evidence safety bound, not a retry cadence.
    record Blocker(ActorBodyId body, java.util.UUID entity, AABB observedBounds) { }
    record Request(java.util.UUID requester, WeakReference<Mob> source,
                   FrontierV3GoalNavigation.ProviderPermission permission, List<Blocker> blockers,
                   List<BlockPosition> passage, long refreshedAt) {
        Request { blockers = List.copyOf(blockers); passage = List.copyOf(passage); }
        boolean current(ServerLevel level) {
            Mob actor = source.get();
            return actor != null && actor.level() == level && permission.current(actor)
                    && level.getGameTime() - refreshedAt <= MAX_RETAINED_TICKS;
        }
    }
    private static final Map<Mob, Request> REQUESTS = new WeakHashMap<>();
    private FrontierV3PedestrianYieldRequests() { }

    static void request(ServerLevel level, Mob requester, FrontierV3GoalNavigation.ProviderPermission permission,
                        FrontierV3PedestrianTraffic.Query evidence) {
        if (!permission.current(requester) || FrontierV3PedestrianCourtesy.active(requester)) return;
        prune();
        List<Blocker> blockers = evidence.blockers().stream().map(observed -> {
            var entity = level.getEntity(observed.id());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) return Optional.<Blocker>empty();
            return FrontierV3ActorCarrierComposition.declaredBy(mob).map(declaration -> new Blocker(
                    new ActorBodyId(declaration.actorId(), declaration.epoch()), observed.id(), observed.bounds()));
        }).flatMap(Optional::stream).toList();
        if (blockers.isEmpty()) { REQUESTS.remove(requester); return; }
        if (!REQUESTS.containsKey(requester) && REQUESTS.size() >= MAX_REQUESTS) return;
        REQUESTS.put(requester, new Request(requester.getUUID(), new WeakReference<>(requester), permission,
                blockers, evidence.passage(), level.getGameTime()));
    }

    /** The participant's own activity decides whether it can yield under its own exact permission. */
    static Optional<Request> forBody(ServerLevel level, Mob body, ActorBodyId identity) {
        prune();
        return REQUESTS.values().stream().filter(request -> request.current(level))
                .filter(request -> request.blockers().stream().anyMatch(blocker ->
                        matches(blocker, body.getUUID(), identity, body.getBoundingBox())))
                .filter(request -> winsReciprocal(request.requester(), body.getUUID(), reciprocal(body, request.requester())))
                .min(java.util.Comparator.comparing(Request::requester));
    }

    /** Only one participant yields when requests are reciprocal; no collision-order coin flip. */
    static boolean winsReciprocal(java.util.UUID requester, java.util.UUID blocker, boolean reciprocal) {
        return !reciprocal || requester.compareTo(blocker) < 0;
    }

    private static boolean reciprocal(Mob body, java.util.UUID requester) {
        var own = REQUESTS.get(body);
        return own != null && own.blockers().stream().anyMatch(blocker -> blocker.entity().equals(requester));
    }

    static List<Blocker> candidates(ServerLevel level) {
        prune();
        return REQUESTS.values().stream().filter(request -> request.current(level))
                .flatMap(request -> request.blockers().stream()).distinct()
                .sorted(java.util.Comparator.comparing(Blocker::entity)).toList();
    }

    static boolean matches(Blocker blocker, java.util.UUID entity, ActorBodyId identity, AABB currentBounds) {
        return blocker.entity().equals(entity) && blocker.body().equals(identity)
                && blocker.observedBounds().intersects(currentBounds);
    }

    static void clear(Mob requester) { REQUESTS.remove(requester); }

    static List<ActorBodyId> observedBlockers(Mob requester) {
        var request = REQUESTS.get(requester);
        return request != null && requester.level() instanceof ServerLevel level && request.current(level)
                ? request.blockers().stream().map(Blocker::body).toList() : List.of();
    }

    static String explanation(ServerLevel level, FrontierV3PedestrianTraffic.Query evidence) {
        return evidence.blockers().stream().map(observed -> {
            var entity = level.getEntity(observed.id());
            return entity instanceof Mob mob ? FrontierV3ActorCarrierComposition.declaredBy(mob)
                    .map(declaration -> declaration.actorId().value() + "@" + declaration.epoch())
                    .orElse("foreign:" + observed.id()) : "foreign:" + observed.id();
        }).toList() + ";passage=" + evidence.passage();
    }

    private static void prune() {
        REQUESTS.entrySet().removeIf(entry -> !(entry.getKey().level() instanceof ServerLevel level)
                || !entry.getValue().current(level));
    }
}
