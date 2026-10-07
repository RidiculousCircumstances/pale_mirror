package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupAdvanced;
import java.util.Set;

final class UnitGroupProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.unit_group_advanced");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("unit-groups", Set.of(),
            Set.of(UnitGroupProcess.PROGRESS), TYPES, Set.of("frontier.unit_group_advanced", "frontier.actor_movement_started", "frontier.actor_movement_interrupted",
                    "kernel.schedule_created", "kernel.schedule_rescheduled", "kernel.schedule_cancelled"),
            Set.of("frontier.unit_group_advanced"));
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (!(event.payload() instanceof UnitGroupAdvanced value)) throw new IllegalArgumentException("group owner rejects a foreign payload");
        return UnitGroupProcess.reduce(state, event.subject(), value);
    }
}
