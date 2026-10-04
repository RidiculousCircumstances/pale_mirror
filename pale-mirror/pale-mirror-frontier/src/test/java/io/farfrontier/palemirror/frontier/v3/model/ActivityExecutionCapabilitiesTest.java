package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActivityExecutionCapabilitiesTest {
    @Test void coordinatedAssignmentUsesItsExactRegisteredExecutionCheckpoint() {
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                    new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:assignment-owner-checkpoint"), 91L));
        var state = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                .decode(engine.checkpoint().canonicalState());
        var operation = FrontierDevelopmentScenarios.initialNorthwatchShipment(state).orElseThrow();
        for (var actor : operation.participantIds()) {
            var assignment = HumanAssignmentProjection.compile(state).assignment(actor);
            var execution = state.actorExecutions().actors().get(actor).current().orElseThrow();
            assertEquals(operation.id(), execution.activityOwnerId());
            var ownerCheckpoint = ActorExecutionComposition.CAPABILITIES.require(execution.activityKind())
                    .checkpoint(state, execution);
            ownerCheckpoint.validate(state, execution);
            var assessment = ActivityExecutionCapabilities.assess(state, assignment);
            assertEquals(ownerCheckpoint.ready(), assessment.ready());
            assertEquals(ResidentWorkYield.Status.OWNER_SAFETY_HOLD, assessment.status(),
                    "this coordinated owner deliberately retains the crew until its terminal boundary");
        }
    }

    private static List<ActivityExecutionCapability> declarations() {
        return java.util.Arrays.stream(HumanAssignmentKind.values()).map(kind ->
                ActivityExecutionCapabilities.registration(kind, (state, assignment) ->
                        new ActivityExecutionCheckpoint(state, assignment, ResidentWorkYield.Status.OWNER_SAFETY_HOLD)))
                .toList();
    }

    @Test void missingOrDuplicateOwnerNeverDefaultsToReady() {
        var values = new ArrayList<>(declarations());
        values.removeFirst();
        assertThrows(IllegalArgumentException.class, () -> new ActivityExecutionCapabilities(values));
        values.addAll(declarations());
        assertThrows(IllegalArgumentException.class, () -> new ActivityExecutionCapabilities(values));
    }

    @Test void evidenceCannotCrossStateOrAssignmentBoundary() {
        var state = ResourceSiteHarvestProcessTest.initial();
        SubjectId actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        HumanAssignment assignment = HumanAssignment.idle(actor);
        var checkpoint = new ActivityExecutionCheckpoint(state, assignment, ResidentWorkYield.Status.READY);
        assertEquals(ResidentWorkYield.Status.READY, checkpoint.validate(state, assignment).status());
        var other = ResourceSiteHarvestProcessTest.initial();
        assertThrows(IllegalArgumentException.class, () -> checkpoint.validate(other, assignment));
        SubjectId foreign = state.humanPopulation().residents().keySet().stream().sorted().skip(1).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> checkpoint.validate(state, HumanAssignment.idle(foreign)));
    }

    @Test void coordinatorRejectsAnOwnerReturningForeignEvidence() {
        var state = ResourceSiteHarvestProcessTest.initial();
        SubjectId actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId foreign = state.humanPopulation().residents().keySet().stream().sorted().skip(1).findFirst().orElseThrow();
        var values = new ArrayList<>(declarations());
        values.removeIf(value -> value.kind() == HumanAssignmentKind.IDLE);
        values.add(ActivityExecutionCapabilities.registration(HumanAssignmentKind.IDLE, (current, assignment) ->
                new ActivityExecutionCheckpoint(current, HumanAssignment.idle(foreign), ResidentWorkYield.Status.READY)));
        var registry = new ActivityExecutionCapabilities(values);
        assertThrows(IllegalArgumentException.class, () -> registry.evaluate(state, HumanAssignment.idle(actor)));
        var idle = declarations().stream().filter(value -> value.kind() == HumanAssignmentKind.IDLE).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> idle.checkpoint(state,
                new HumanAssignment(actor, HumanAssignmentKind.PRODUCTION,
                        java.util.Optional.of(new SubjectId("job:forged")))));
    }
}
