package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ShipmentState;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransportGroupResponsibilityTest {
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");
    private static final SubjectId OLD = new SubjectId("unit-group:old");
    private static final SubjectId NEXT = new SubjectId("unit-group:next");
    private static UnitGroup group(UnitGroup.Phase phase) {
        return new UnitGroup(OLD, new UnitGroup.Mission(UnitGroup.MissionKind.TRANSPORT,
                new SubjectId("transport-mission:old")), List.of(new UnitGroup.Member(ACTOR, UnitGroup.Role.GUIDE,
                ActorActivityKind.GROUP_MEMBER, OLD)), UnitGroup.Formation.COLUMN, phase, 4, 2, Optional.empty());
    }
    private static ActorExecutionState execution(SubjectId owner, boolean suspended) {
        var work = new ActorExecutionId(ACTOR, ActorActivityKind.GROUP_MEMBER, owner, 1);
        var record = suspended ? new ActorExecution(ACTOR, 2, Optional.of(new ActorExecutionId(ACTOR,
                ActorActivityKind.MEAL, ACTOR, 2)), Optional.of(work))
                : new ActorExecution(ACTOR, 1, Optional.of(work), Optional.empty());
        return new ActorExecutionState(Map.of(ACTOR, record));
    }
    @Test void closedHistoricalRosterDoesNotRejectNextCurrentOrSuspendedAssignment() {
        for (boolean suspended : List.of(false, true)) assertDoesNotThrow(() ->
                TransportGroupMissionPort.validateExecutions(execution(NEXT, suspended), ShipmentState.empty(), group(UnitGroup.Phase.CLOSED)));
    }
    @Test void closedRosterStillRejectsItsOwnRetainedAuthority() {
        for (boolean suspended : List.of(false, true)) assertThrows(IllegalArgumentException.class, () ->
                TransportGroupMissionPort.validateExecutions(execution(OLD, suspended), ShipmentState.empty(), group(UnitGroup.Phase.CLOSED)));
    }
    @Test void activeRosterStillRejectsForeignAssignmentAndAcceptsItsOwnGuide() {
        assertThrows(IllegalArgumentException.class, () -> TransportGroupMissionPort.validateExecutions(
                execution(NEXT, false), ShipmentState.empty(), group(UnitGroup.Phase.AT_GOAL)));
        assertDoesNotThrow(() -> TransportGroupMissionPort.validateExecutions(
                execution(OLD, false), ShipmentState.empty(), group(UnitGroup.Phase.AT_GOAL)));
    }
}
