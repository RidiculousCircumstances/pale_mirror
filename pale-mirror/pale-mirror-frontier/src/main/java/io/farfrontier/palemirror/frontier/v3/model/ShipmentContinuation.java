package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;

/** One durable shipment continuation identity for its process and execution capability. */
public final class ShipmentContinuation {
    public static final String PROGRESS = "frontier.shipment.progress";
    private ShipmentContinuation() { }
    public static ScheduledAction at(SubjectId shipment, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:shipment/" + shipment.value().replace(':', '/')),
                new SimInstant(tick), 12, shipment, PROGRESS, 1);
    }
    public static ProposedEvent wake(SubjectId shipment, long tick) {
        var action = at(shipment, Math.addExact(tick, 1L));
        return new ProposedEvent(shipment, new ScheduleEffect.Rescheduled(action.id(), action));
    }
}
