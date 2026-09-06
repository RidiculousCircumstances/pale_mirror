package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HumanAssignmentProjectionTest {
    @Test
    void supplyOperationOwnsTheSameExactTransportAndEscortAssignmentsAcrossRecovery() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(
                new WorldId("frontier:human-assignment"), 91L));
        for (long tick = 100L; tick <= 2_750L; tick++) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));

        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        assertFalse(operation.unit().legacyUnderstrength());
        assertEquals(3, operation.participantIds().size());
        assertEquals(HumanAssignmentKind.CARGO_TRANSPORT, assignments.assignment(operation.unit().cargoCrewId()).kind());
        assertEquals(operation.id(), assignments.assignment(operation.unit().cargoCrewId()).ownerId().orElseThrow());
        var escorts = operation.unit().members().stream().filter(member -> member.duty() == RouteUnitDuty.ESCORT).toList();
        assertEquals(2, escorts.size());
        escorts.forEach(member -> assertEquals(HumanAssignmentKind.ESCORT, assignments.assignment(member.residentId()).kind()));
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(assignments, HumanAssignmentProjection.compile(restored));
        assertEquals(operation.unit(), restored.operations().get(operation.id()).unit());
    }

    @Test
    void idleAssignmentCannotClaimAnOwner() {
        assertThrows(IllegalArgumentException.class, () -> new HumanAssignment(new SubjectId("resident:assignment-negative"),
                HumanAssignmentKind.IDLE, Optional.of(new SubjectId("job:foreign"))));
        assertFalse(HumanAssignment.idle(new SubjectId("resident:assignment-idle")).active());
    }
}
