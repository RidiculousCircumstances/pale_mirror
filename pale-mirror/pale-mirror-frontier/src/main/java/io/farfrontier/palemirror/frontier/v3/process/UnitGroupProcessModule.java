package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupNavigationReady;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import java.util.Set;

final class UnitGroupProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.unit_group_advanced");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("unit-groups", Set.of("frontier.unit_group_navigation_ready"),
            Set.of(UnitGroupProcess.PROGRESS), TYPES, Set.of("frontier.unit_group_advanced", "frontier.actor_movement_started",
                    "kernel.schedule_created", "kernel.schedule_rescheduled", "kernel.schedule_cancelled"),
            Set.of("frontier.unit_group_advanced", "frontier.unit_group_navigation_ready"));
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (!(command.payload() instanceof UnitGroupNavigationReady value))
            throw new IllegalArgumentException("group owner rejects foreign readiness command");
        var group = state.unitGroups().groups().get(value.groupId());
        if (group == null || group.revision() != value.expectedRevision())
            return FrontierWorldCommandPlanner.rejected("navigation notification has a stale group revision");
        var port = UnitGroupMissionPorts.require(group); port.validate(state, group);
        var events = new java.util.ArrayList<>(port.reconsider(state, group, command.submittedAt().ticks()));
        events.add(UnitGroupProcess.wake(group.id(), command.submittedAt().ticks()));
        return new CommandPlan.Accepted(java.util.List.copyOf(events));
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (!(event.payload() instanceof UnitGroupAdvanced value)) throw new IllegalArgumentException("group owner rejects a foreign payload");
        return UnitGroupProcess.reduce(state, event.subject(), value);
    }
}
