package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Addressed one-shot owner acceptance, not a periodic global receipt scan. */
final class InternalShipmentReceipts {
    static final String RECEIVE = "frontier.internal_shipment_receive";
    static ScheduledAction at(Shipment shipment, long tick) {
        var receipt = shipment.reception().orElseThrow();
        return new ScheduledAction(new ScheduleId("schedule:internal-shipment/" + receipt.id().value().replace(':', '/')),
                new SimInstant(tick), 15, shipment.id(), RECEIVE, 1);
    }
    static List<ProposedEvent> wake(Shipment shipment, long tick) {
        return List.of(new ProposedEvent(shipment.id(), new ScheduleEffect.Created(at(shipment, Math.addExact(tick, 1)))));
    }
    static List<ProposedEvent> receive(FrontierWorldState state, ScheduledAction action, SimInstant now) {
        var shipment = Objects.requireNonNull(state.shipments().shipments().get(action.subject()), "internal receipt shipment");
        if (shipment.reception().isEmpty()) return List.of(new ProposedEvent(shipment.id(), new ScheduleEffect.Cancelled(action.id())));
        if (!action.kind().equals(RECEIVE) || !at(shipment, action.dueAt().ticks()).equals(action))
            throw new IllegalArgumentException("internal reception lost its exact addressed schedule");
        var event = new InternalShipmentReceived(shipment.id(), shipment.revision(), shipment.reception().orElseThrow().id());
        InternalShipmentStateSupport.receive(state, shipment.id(), event);
        return List.of(new ProposedEvent(shipment.id(), event), new ProposedEvent(shipment.id(), new ScheduleEffect.Cancelled(action.id())),
                ShipmentProcess.wake(shipment.id(), now.ticks()));
    }
    private InternalShipmentReceipts() { }
}
