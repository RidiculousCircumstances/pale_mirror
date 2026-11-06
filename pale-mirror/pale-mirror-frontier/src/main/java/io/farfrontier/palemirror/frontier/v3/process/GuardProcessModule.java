package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** One explicit owner for peaceful appointments and their common equipment effect capability. */
final class GuardProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of(GuardDutyEvent.Appointed.TYPE, GuardDutyEvent.Changed.TYPE, GuardMissionSelected.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("guard", Set.of(),
            Set.of(GuardContinuation.REVIEW, GuardContinuation.PROGRESS), TYPES,
            Set.of(io.farfrontier.palemirror.frontier.v3.model.combat.CombatEvent.ManeuverClosed.TYPE,io.farfrontier.palemirror.frontier.v3.model.combat.CombatEvent.PositionPlanned.TYPE,
                    GuardAmmunitionEvent.Selected.TYPE,GuardAmmunitionEvent.Loaded.TYPE,GuardAmmunitionEvent.Cancelled.TYPE,
                    GuardMissionSelected.TYPE, GuardDutyEvent.Appointed.TYPE, GuardDutyEvent.Changed.TYPE, "frontier.actor_movement_started", "frontier.actor_movement_interrupted", "frontier.physical_intent_prepared",
                    "kernel.schedule_created", "kernel.schedule_rescheduled", "kernel.schedule_cancelled", "kernel.reconsideration_requested"), TYPES);
    public List<ScheduledAction> initialSchedules(FrontierBootstrap bootstrap) {
        if (!bootstrap.ruleset().labour().entries().containsKey(ResidentWorkKind.GUARD)) return List.of();
        return bootstrap.settlements().stream().map(home -> GuardContinuation.review(home.id(), bootstrap.ruleset().cadence().strategicReviewInterval())).toList();
    }
    public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() { return List.of(GuardEquipmentLifecycle.capability()); }
    public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof GuardMissionSelected mission) return mission.apply(state,event.subject(),event.instant().ticks());
        if (!(event.payload() instanceof GuardDutyEvent guard)) throw new IllegalArgumentException("guard rejects an undeclared payload");
        return GuardDutyTransitions.apply(state, event.subject(), guard, event.instant().ticks());
    }
}
