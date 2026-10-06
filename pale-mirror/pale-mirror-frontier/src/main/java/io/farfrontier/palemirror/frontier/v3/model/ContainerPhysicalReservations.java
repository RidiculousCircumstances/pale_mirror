package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.BiFunction;

/** Registered physical effect owners contribute exact held slots; storage does not inspect jobs. */
final class ContainerPhysicalReservations {
    private static final List<BiFunction<FrontierWorldState, SubjectId, Set<Integer>>> PORTS = List.of(
            (state, container) -> HarvestContainerReservations.slots(state.resourceSites(), container), ShipmentPhysicalAuthority::reservedSlots);
    static Set<Integer> slots(FrontierWorldState state, SubjectId container) {
        var result = new HashSet<Integer>();
        PORTS.forEach(port -> result.addAll(port.apply(state, container)));
        return Set.copyOf(result);
    }
    private ContainerPhysicalReservations() { }
}
