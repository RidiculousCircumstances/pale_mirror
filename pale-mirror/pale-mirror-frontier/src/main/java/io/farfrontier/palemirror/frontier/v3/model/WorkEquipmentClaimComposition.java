package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Function;
import java.util.function.BiFunction;

/** Read-only composition of owner-retained reservations, not another mutable equipment registry. */
public final class WorkEquipmentClaimComposition {
    private static final List<Function<FrontierWorldState, Set<SubjectId>>> OWNERS = List.of(
            producer(state -> state.forest().work(), (state, job) -> Set.of(job.toolId())),
            producer(state -> state.extractionSites().work(), (state, job) -> job.terminal() ? Set.of() : Set.of(job.toolId())),
            producer(state -> state.resourceSites().harvestJobs(), (state, job) -> job.equipment().settled() ? Set.of() : Set.of(job.equipment().itemId())),
            producer(FrontierWorldState::productionJobs, StationWorkItemDeparture::reserved),
            producer(state -> state.shipments().shipments(), (state, shipment) ->
                    (!shipment.terminal() || shipment.reception().isPresent()) && shipment.cargo() instanceof ShipmentCargo.Individual item ? Set.of(item.itemId()) : Set.of()),
            producer(state -> state.companies().goodsTrade().individualAllocations(), (state, allocation) -> Set.of(allocation.itemId())));
    private static <I> Function<FrontierWorldState, Set<SubjectId>> producer(Function<FrontierWorldState, Map<SubjectId, I>> rows,
            BiFunction<FrontierWorldState, I, Set<SubjectId>> claims) {
        var projection = new ImmutableRegistryView<SubjectId, I, Set<SubjectId>>(Integer.MAX_VALUE, Objects::equals);
        var inputs = new ImmutableInputView<Map<SubjectId, Set<SubjectId>>>();
        var publication = new ImmutableInputView<Set<SubjectId>>();
        return state -> {
            var source = rows.apply(state);
            var projected = inputs.get(List.of(state.bootstrap(), source), () -> projection.project(state.bootstrap(), source,
                    (id, row) -> claims.apply(state, row)));
            return publication.get(List.of(projected), () -> projected.values().stream().flatMap(Set::stream)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        };
    }
    private static final ImmutableInputView<Set<SubjectId>> CLAIMS = new ImmutableInputView<>();
    private static final ImmutableInputView<ImmutableSetContributions<Boolean, SubjectId>> IMAGES = new ImmutableInputView<>();
    private static final ImmutableInputView<Map<SubjectId, Boolean>> RESERVED_ITEMS = new ImmutableInputView<>();
    static Map<SubjectId, Boolean> reservedItems(FrontierWorldState state) {
        var reserved = reserved(state);
        return RESERVED_ITEMS.get(List.of(reserved), () -> {
            var result = new HashMap<SubjectId, Boolean>(); reserved.forEach(id -> result.put(id, Boolean.TRUE));
            return ImmutableLookupMap.copyOf(result);
        });
    }
    public static Set<SubjectId> reserved(FrontierWorldState state) {
        var parts = new ArrayList<Object>(OWNERS.size() + 1); parts.add(state.bootstrap());
        var contributions = OWNERS.stream().map(owner -> owner.apply(state)).toList(); parts.addAll(contributions);
        return CLAIMS.get(parts, () -> {
            var declared = contributions.stream().flatMap(Set::stream)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            // The complete declared union is one contribution. Common immutable
            // set images preserve equality across progress-only changes without
            // retaining world snapshots or inventing reservation authority.
            return IMAGES.get(List.of(state.bootstrap()), () -> new ImmutableSetContributions<>(1))
                    .replaceAll(Map.of(Boolean.TRUE, declared));
        });
    }
    private WorkEquipmentClaimComposition() { }
}
