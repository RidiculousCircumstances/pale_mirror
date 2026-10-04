package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Current family progress requires independent physical evidence, never a pose-writing receipt. */
class FieldHotObservationAuthorityTest {
    @Test void commonInspectionAloneAdmitsAnUnblockedWorkStationAndKeepsCropAndResourcesUntouched() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var goal = ResourceSiteHarvestGoal.current(hot.state(), hot.job());
        var state = ModeledActorBodyFacts.inspected(hot.state(), hot.job().workerId(),
                goal.representative().standingBody());
        assertTrue(ResourceSiteHarvestGoal.actorAtWorkCell(state, hot.job()));
        assertEquals(hot.state().resourceSites(), state.resourceSites());
        assertEquals(hot.state().inventory(), state.inventory());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceHotGoalArrived(
                state, hot.site(), receipt(hot, observation(hot), hot.job().target().generation())));
    }

    @Test void blockedGoalCanClearOnlyAfterCommonInspectionWithCapturedCurrentAuthority() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var goal = ResourceSiteHarvestGoal.current(hot.state(), hot.job());
        var blocked = hot.state().withResourceSites(hot.state().resourceSites().replace(
                hot.state().resourceSites().site(hot.site()).blockHarvestRoute(hot.job(),
                        new ResourceSiteHarvestNavigationBlock(goal.representative(), goal.layoutRevision(),
                                ResourceSiteHarvestNavigationBlock.Reason.PATH_STALLED))));
        var witness = observation(hot);
        var arrived = receipt(hot, witness, hot.job().target().generation());
        assertEquals(arrived, FrontierWorldRuntimeDefinition.payloadCodecs().decode(arrived.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(arrived)));
        assertThrows(IllegalArgumentException.class, () ->
                ResourceSiteHarvestProcess.reduceHotGoalArrived(blocked, hot.site(), arrived));
        var inspected = ModeledActorBodyFacts.inspected(blocked, hot.job().workerId(), arrived.observedWorker());
        var retained = ResourceSiteHarvestProcess.reduceHotGoalArrived(inspected, hot.site(), arrived);
        assertEquals(inspected.actorLocations(), retained.actorLocations());
        assertEquals(inspected.inventory(), retained.inventory());
        assertEquals(inspected.actorExecutions(), retained.actorExecutions());
        assertTrue(retained.resourceSites().site(hot.site()).harvestJob(hot.job().id()).orElseThrow().navigationBlock().isEmpty());
        assertEquals(retained, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(retained)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceHotGoalArrived(retained, hot.site(), arrived));

        var body = witness.actuation().body();
        var execution = witness.actuation().execution();
        for (var stale : new ActorHotObservation[] {
                new ActorHotObservation(witness.actuation(), witness.scopeRevision() + 1),
                new ActorHotObservation(new ActorActuationId(new ActorBodyId(body.actorId(), body.physicalEpoch() + 1),
                        execution), witness.scopeRevision()),
                new ActorHotObservation(new ActorActuationId(body, new ActorExecutionId(execution.actorId(),
                        execution.activityKind(), execution.activityOwnerId(), execution.generation() + 1)), witness.scopeRevision()) }) {
            assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceHotGoalArrived(
                    inspected, hot.site(), receipt(hot, stale, hot.job().target().generation())));
        }
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceHotGoalArrived(
                inspected, hot.site(), receipt(hot, witness, hot.job().target().generation() + 1)));
    }

    private static ActorHotObservation observation(ResourceSiteHarvestProcessTest.HotHarvest hot) {
        return new ActorHotObservation(new ActorActuationId(ActorBodyAuthority.current(hot.state(), hot.job().workerId()),
                hot.state().actorExecutions().current(ActorActivityKind.FIELD_HARVEST).get(hot.job().workerId())),
                hot.lease().revision());
    }
    private static ResourceSiteHarvestHotGoalArrived receipt(ResourceSiteHarvestProcessTest.HotHarvest hot,
                                                             ActorHotObservation witness, long generation) {
        var goal = ResourceSiteHarvestGoal.current(hot.state(), hot.job());
        return new ResourceSiteHarvestHotGoalArrived(hot.job().id(), hot.lease().id(), hot.job().workerId(),
                goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(), goal.representative().standingBody(),
                generation, witness);
    }
}
