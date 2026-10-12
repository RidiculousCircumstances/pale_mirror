package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.model.DefenderEquipmentReturnVisitChanged;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.Set;

/** Exact selected-visit event owner; no public command, manual tick or second inventory executor. */
final class DefenderEquipmentReturnVisitProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.defender_equipment_return_visit_changed");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("defender-equipment-return-visit",
            Set.of(), Set.of(), TYPES, Set.of(), TYPES);
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (!(event.payload() instanceof DefenderEquipmentReturnVisitChanged changed))
            throw new IllegalArgumentException("equipment visit owner rejects undeclared payload");
        return DefenderEquipmentReturnProcess.reduceVisit(state, event.subject(), changed, event.instant().ticks());
    }
}
