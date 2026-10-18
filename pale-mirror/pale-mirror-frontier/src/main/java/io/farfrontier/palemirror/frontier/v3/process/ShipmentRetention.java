package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Transport owns executable-record retirement. Commercial history and physical
 * evidence have independent owners and are never discarded by capacity policy. */
final class ShipmentRetention {
    // Bound one maintenance transaction, not the lifetime of business history.
    static final int MAX_RETIREMENTS = 32;
    record Result(FrontierWorldState state, List<ProposedEvent> events) { }
    static List<ProposedEvent> retire(Shipment shipment, long now) {
        // The kernel's existing exact-subject retirement hook cancels only
        // still-present continuations. Old terminal records may have none;
        // an explicit cancellation here would reject that legitimate cleanup.
        return List.of(new ProposedEvent(shipment.id(), new ShipmentRetired(shipment.id(), shipment.revision())));
    }
    static Result collect(FrontierWorldState state, int maximum, long now) {
        if (maximum < 0 || maximum > MAX_RETIREMENTS) throw new IllegalArgumentException("unbounded transport retirement");
        var closed = state.shipments().shipments().values().stream()
                .filter(shipment -> ShipmentStateSupport.closedForRetirement(state, shipment))
                .sorted(Comparator.comparing(Shipment::id)).limit(maximum).toList();
        var events = new ArrayList<ProposedEvent>(); var following = state;
        for (var shipment : closed) {
            following = ShipmentStateSupport.retire(following, shipment.id(), shipment.id(), shipment.revision());
            events.addAll(retire(shipment, now));
        }
        return new Result(following, List.copyOf(events));
    }
    private ShipmentRetention() { }
}
