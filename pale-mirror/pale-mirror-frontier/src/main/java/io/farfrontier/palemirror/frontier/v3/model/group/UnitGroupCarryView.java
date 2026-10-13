package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Optional;

/** Presentation dispatches to the declared mission; generic group consumers do not inspect cargo. */
public final class UnitGroupCarryView {
    private UnitGroupCarryView() { }
    public static Optional<ActorCarriedResources.Presentation> read(FrontierWorldState state, HumanAssignment assignment) {
        var group = state.unitGroups().groups().get(assignment.ownerId().orElseThrow());
        if (assignment.kind() != HumanAssignmentKind.GROUP_MEMBER || group == null || group.phase() == UnitGroup.Phase.CLOSED)
            throw new IllegalArgumentException("group carry view lacks its exact retained assignment");
        var member = group.member(assignment.residentId());
        return UnitGroupMissionPorts.require(group).workCargo(state, group, member);
    }
}
