package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

/** One addressed continuation for the retained visit; repeated wakes replace, never multiply, it. */
public final class DefenderEquipmentReturnContinuation {
    public static final String REVIEW = "frontier.population.defender_equipment_return.review";
    private DefenderEquipmentReturnContinuation() { }
    public static ScheduledAction review(SettlementAssault parent, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:defender-equipment-return-" + StableCorrelationIdentity.digest(parent.id().value())),
                new SimInstant(tick), 0, parent.id(), REVIEW, 1);
    }
    public static ProposedEvent wake(SettlementAssault parent, long tick) {
        var next = review(parent, Math.addExact(tick, 1L));
        return new ProposedEvent(parent.id(), new ScheduleEffect.Rescheduled(next.id(), next));
    }
}
