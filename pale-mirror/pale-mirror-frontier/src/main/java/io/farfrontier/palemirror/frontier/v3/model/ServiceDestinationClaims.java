package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only final-position claims, not terrain obstacles or another movement owner. */
public final class ServiceDestinationClaims {
    private ServiceDestinationClaims() { }

    public static Set<SurfaceAnchor> excludedFor(FrontierWorldState state, SubjectId actorId) {
        return exclusions(state, actorId, Set.of(), true);
    }

    /**
     * Actual occupants and committed movement destinations remain exclusive.
     * A future preferred eating position is not a permit to block an active
     * service exit; clearance itself accepts any supported exit, not that spot.
     */
    public static Set<SurfaceAnchor> forImmediateExit(FrontierWorldState state, SubjectId actorId) {
        return exclusions(state, actorId, Set.of(), false);
    }

    /** COLD arrivals may share transit space, but may not turn an incumbent's only
     * exit into a permanent waiting permit. HOT/unresolved bodies, idle occupants
     * and committed final destinations remain exclusive. The point is supplied
     * by the caller; the registered owners declare their incoming access demand. */
    public static Set<SurfaceAnchor> forImmediateExit(FrontierWorldState state, SubjectId actorId, SubjectId pointId) {
        var incoming = ServiceAccessCapabilities.current(state, pointId).stream()
                .filter(demand -> demand.presence() != ServiceAccessDemand.Presence.OCCUPIED && !demand.physicallyAdmitted())
                .map(demand -> demand.identity().actorId())
                .filter(id -> ActorExecutionCoordinator.coldAvailable(state, id))
                .collect(java.util.stream.Collectors.toSet());
        return exclusions(state, actorId, incoming, false);
    }

    private record MealClaims(Map<SurfaceAnchor, List<SubjectId>> destinations,
                              Map<SurfaceAnchor, List<SubjectId>> preferred) { }
    private static final ImmutableInputView<Map<SurfaceAnchor, List<SubjectId>>> BODIES = new ImmutableInputView<>();
    private static final ImmutableInputView<Map<SurfaceAnchor, List<SubjectId>>> MOVES = new ImmutableInputView<>();
    private static final ImmutableInputView<MealClaims> MEALS = new ImmutableInputView<>();

    private static Set<SurfaceAnchor> exclusions(FrontierWorldState state, SubjectId actorId,
                                                Set<SubjectId> incoming, boolean includePreferred) {
        Objects.requireNonNull(actorId);
        var bodies = BODIES.get(List.of(state.actorLocations()), () -> {
            Map<SurfaceAnchor, List<SubjectId>> index = new HashMap<>();
            state.actorLocations().forEach((id, actor) -> {
                if (actor.condition().status() == ActorLifeStatus.ALIVE) add(index, actor.supportingSurface(), id);
            });
            return freeze(index);
        });
        var moves = MOVES.get(List.of(state.actorMovements()), () -> {
            Map<SurfaceAnchor, List<SubjectId>> index = new HashMap<>();
            state.actorMovements().values().forEach(movement -> movement.order().legalStations()
                    .forEach(surface -> add(index, surface, movement.order().actorId())));
            return freeze(index);
        });
        var meals = MEALS.get(List.of(state.humanPopulation().meals()), () -> {
            Map<SurfaceAnchor, List<SubjectId>> destinations = new HashMap<>(), preferred = new HashMap<>();
            state.humanPopulation().meals().values().forEach(meal -> {
                add(preferred, meal.clearingSurface(), meal.residentId());
                meal.coldTravel().ifPresent(travel -> travel.order().legalStations()
                        .forEach(surface -> add(destinations, surface, meal.residentId())));
            });
            return new MealClaims(freeze(destinations), freeze(preferred));
        });
        // Consumers ask about a bounded set of local candidates. Retain exact global
        // claims without rebuilding a world-sized excluded set for each resident/check.
        // Views bind immutable inputs; a changed body, meal or movement rebuilds its own index.
        List<Map<SurfaceAnchor, List<SubjectId>>> destinations = includePreferred
                ? List.of(moves, meals.destinations(), meals.preferred()) : List.of(moves, meals.destinations());
        return Collections.unmodifiableSet(new AbstractSet<>() {
            @Override public boolean contains(Object value) {
                Objects.requireNonNull(value);
                if (!(value instanceof SurfaceAnchor surface)) return false;
                if (bodies.getOrDefault(surface, List.of()).stream()
                        .anyMatch(id -> !id.equals(actorId) && !incoming.contains(id))) return true;
                return destinations.stream().anyMatch(index -> index.getOrDefault(surface, List.of()).stream()
                        .anyMatch(id -> !id.equals(actorId)));
            }
            @Override public Iterator<SurfaceAnchor> iterator() {
                Set<SurfaceAnchor> keys = new HashSet<>(bodies.keySet());
                destinations.forEach(index -> keys.addAll(index.keySet()));
                keys.removeIf(surface -> !contains(surface));
                return Collections.unmodifiableSet(keys).iterator();
            }
            @Override public int size() {
                int count = 0; for (var iterator = iterator(); iterator.hasNext(); ) { iterator.next(); count++; }
                return count;
            }
        });
    }
    private static void add(Map<SurfaceAnchor, List<SubjectId>> index, SurfaceAnchor surface, SubjectId actor) {
        index.computeIfAbsent(surface, ignored -> new ArrayList<>()).add(actor);
    }
    private static Map<SurfaceAnchor, List<SubjectId>> freeze(Map<SurfaceAnchor, List<SubjectId>> index) {
        index.replaceAll((surface, actors) -> List.copyOf(actors));
        return ImmutableLookupMap.copyOf(index);
    }
}
