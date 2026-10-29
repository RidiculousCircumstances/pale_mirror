package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.BiFunction;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;

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

    private record Destination(SubjectId actor, Set<SurfaceAnchor> surfaces) {
        private Destination { Objects.requireNonNull(actor); surfaces = Set.copyOf(surfaces); }
    }
    /** Shared registry/group machinery updates only changed declarations. Neither
     * actor motion within one support cell nor meal progress renews spatial facts. */
    private static final class Destinations<I> {
        private final ImmutableRegistryView<SubjectId, I, Destination> declarations;
        private final ImmutableGroupView<SubjectId, Destination, SurfaceAnchor> groups = new ImmutableGroupView<>();
        Destinations(int maximumRows) { declarations = new ImmutableRegistryView<>(maximumRows, Objects::equals); }
        Map<SurfaceAnchor, List<Destination>> read(Object epoch, Map<SubjectId, I> source,
                                                  BiFunction<SubjectId, I, Destination> project) {
            return groups.groupedRows(epoch, declarations.project(epoch, source, project), Destination::surfaces);
        }
    }
    private static final Destinations<ActorLocation> BODIES = new Destinations<>(SimulationCapacity.MAX_ACTORS);
    private static final Destinations<ActorMovement> MOVES = new Destinations<>(SimulationCapacity.MAX_ACTORS);
    private static final Destinations<ResidentMeal> MEAL_DESTINATIONS = new Destinations<>(HumanPopulation.MAX_RESIDENTS);
    private static final Destinations<ResidentMeal> MEAL_PREFERRED = new Destinations<>(HumanPopulation.MAX_RESIDENTS);

    private static Set<SurfaceAnchor> exclusions(FrontierWorldState state, SubjectId actorId,
                                                Set<SubjectId> incoming, boolean includePreferred) {
        Objects.requireNonNull(actorId);
        var bodies = BODIES.read(state.bootstrap(), state.actorLocations(), (id, actor) -> new Destination(id,
                actor.condition().status() == ActorLifeStatus.ALIVE ? Set.of(actor.supportingSurface()) : Set.of()));
        var moves = MOVES.read(state.bootstrap(), state.actorMovements(), (id, movement) ->
                new Destination(movement.order().actorId(), Set.copyOf(movement.order().legalStations())));
        var mealDestinations = MEAL_DESTINATIONS.read(state.bootstrap(), state.humanPopulation().meals(), (id, meal) ->
                new Destination(meal.residentId(), meal.coldTravel().map(travel -> Set.copyOf(travel.order().legalStations())).orElse(Set.of())));
        var mealPreferred = MEAL_PREFERRED.read(state.bootstrap(), state.humanPopulation().meals(), (id, meal) ->
                new Destination(meal.residentId(), Set.of(meal.clearingSurface())));
        // Consumers ask about a bounded set of local candidates. Retain exact global
        // claims without rebuilding a world-sized excluded set for each resident/check.
        // Views bind immutable inputs; only changed spatial memberships update their groups.
        List<Map<SurfaceAnchor, List<Destination>>> destinations = includePreferred
                ? List.of(moves, mealDestinations, mealPreferred) : List.of(moves, mealDestinations);
        return Collections.unmodifiableSet(new AbstractSet<>() {
            @Override public boolean contains(Object value) {
                Objects.requireNonNull(value);
                if (!(value instanceof SurfaceAnchor surface)) return false;
                if (bodies.getOrDefault(surface, List.of()).stream()
                        .anyMatch(claim -> !claim.actor().equals(actorId) && !incoming.contains(claim.actor()))) return true;
                return destinations.stream().anyMatch(index -> index.getOrDefault(surface, List.of()).stream()
                        .anyMatch(claim -> !claim.actor().equals(actorId)));
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
}
