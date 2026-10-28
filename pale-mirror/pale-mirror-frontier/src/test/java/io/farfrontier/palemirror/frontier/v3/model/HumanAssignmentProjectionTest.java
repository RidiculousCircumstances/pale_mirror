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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HumanAssignmentProjectionTest {
    @Test void fieldProgressDoesNotChangeAssignmentsButRetirementCannotRetainTheWorker() {
        var fixture = ResourceSiteHarvestProcessTest.awaitingEquipment(125L);
        var state = fixture.state(); var job = fixture.job();
        var before = HumanAssignmentProjection.compile(state);
        var refreshed = state.resourceSites().replace(state.resourceSites().site(job.siteId())
                .withHarvestEquipment(job, job.equipment().transferred()));
        // Read-only precursor fixture: no physical tool transfer or publication is claimed.
        var progress = FrontierWorldState.duringUnpublishedTransition(() -> state.withChanges(
                FrontierWorldStateUpdate.begin().resourceSites(refreshed)));
        assertEquals(before, HumanAssignmentProjection.compile(progress));
        var retired = FrontierWorldState.duringUnpublishedTransition(() -> state.withChanges(
                FrontierWorldStateUpdate.begin().resourceSites(state.resourceSites().replace(state.resourceSites().site(job.siteId()).destroyed()))));
        org.junit.jupiter.api.Assertions.assertTrue(HumanAssignmentProjection.compile(retired).idle(job.workerId()));
        assertFalse(HumanAssignmentProjection.compile(state).idle(job.workerId()), "a later read cannot change the predecessor's worker claim");
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(state, retired),
                "an assignment read cannot bless a removed job whose execution still exists");
        var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(before, HumanAssignmentProjection.compile(restored));
    }

    @Test
    void idleAssignmentCannotClaimAnOwner() {
        assertThrows(IllegalArgumentException.class, () -> new HumanAssignment(new SubjectId("resident:assignment-negative"),
                HumanAssignmentKind.IDLE, Optional.of(new SubjectId("job:foreign"))));
        assertFalse(HumanAssignment.idle(new SubjectId("resident:assignment-idle")).active());
    }

    @Test
    void sameDecodedStateReusesItsImmutableAssignmentProjection() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:assignment-cache"), 91L));
        assertSame(HumanAssignmentProjection.compile(state), HumanAssignmentProjection.compile(state),
                "a physical tick may reuse only the exact immutable state projection, never a stale later state");
        var moved = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(new java.util.LinkedHashMap<>(state.actorLocations())));
        assertSame(HumanAssignmentProjection.compile(state), HumanAssignmentProjection.compile(moved),
                "a pose-only state change must not rebuild unrelated work assignments");
    }
}
