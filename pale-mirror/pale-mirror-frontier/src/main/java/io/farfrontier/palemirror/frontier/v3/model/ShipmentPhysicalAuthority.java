package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Prepared transport effects retain endpoint authority independently of a later body handoff. */
public final class ShipmentPhysicalAuthority {
    public static boolean pendingForContainer(FrontierWorldState state, SubjectId container) {
        return state.shipments().shipments().values().stream().anyMatch(shipment -> shipment.pendingPhysicalStep().isPresent()
                && shipment.itemOrder().containerEndpoint().containerId().equals(container));
    }
    public static Set<Integer> reservedSlots(FrontierWorldState state, SubjectId container) {
        var result = new HashSet<Integer>();
        for (Shipment shipment : state.shipments().shipments().values()) {
            var step = shipment.pendingPhysicalStep().orElse(null);
            if (step != null && shipment.status() == Shipment.Status.CARRYING && shipment.receiver().containerId().equals(container))
                result.add(step.destinationSlot());
        }
        return Set.copyOf(result);
    }
    private ShipmentPhysicalAuthority() { }
}
