package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProcessTest.HotHarvest;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProcessTest.*;

class SceneHandContinuityTest {
    @Test void hotFieldReceiptsCoverEveryCellWithoutMovingTheWorkGoalByWaypoints() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob start = hot.job();
        FrontierWorldState worked = completeHarvestWorkHot(hot.state(), hot.site(), start, hot.lease().id());
        ResourceSiteHarvestJob completed = (ResourceSiteHarvestJob) worked.resourceSites().site(hot.site())
                .harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertTrue(completed.progress().complete());
        assertEquals(completed.progress().totalCropSlots(), worked.resourceSites().cycle(hot.site()).accountedPrefixCount());
        assertEquals(ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE,
                ResourceSiteHarvestGoal.current(worked, completed).kind());
        assertEquals(start.workerId(), completed.workerId());
        assertEquals(worked, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(worked)));
        // The actual reported boundary: all cells collected, wheat retained, FREE starts before delivery.
        worked = worked.withHumanPopulation(worked.humanPopulation().withSchedule(new SubjectId("settlement:1"),
                SettlementDailySchedule.initial()));
        assertEquals(SettlementDailySchedule.Window.FREE,
                worked.humanPopulation().schedule(new SubjectId("settlement:1")).windowAt(22_301L));
        assertFalse(ResidentActivityCoordinator.requestsYield(worked, completed.workerId(), 22_301L));
        assertTrue(ResidentActivityCoordinator.ordinaryWorkPermitted(worked, completed.workerId(), 22_301L));
        worked = HarvestFixtureOwners.withBoundPersonalFood(worked, completed.workerId(), hot.lease().members().getFirst().entityId());
        var pocketBefore = worked.inventory().fungibleResources().bindings().values().stream()
                .filter(value -> value.address() instanceof PhysicalStackAddress.ActorPocket).toList();
        var body = worked.actorLocations().get(completed.workerId());
        var draining = worked.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING);
        assertThrows(IllegalArgumentException.class, () -> draining.releaseSceneLease(hot.lease().id(),
                List.of(new SceneMemberPosition(completed.workerId(), body.body(), body.condition().health()))),
                "personal pockets do not waive typed release of the actual wheat hand");
        var released = ResourceSiteHarvestProcess.reduceHandRelease(draining, worked.resourceSite(hot.site()).settlementId(),
                new ResourceSiteHarvestHandRelease(hot.site(), completed.id(), completed.actorAccountId(),
                        hot.lease().revision(), new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ActorHand(completed.workerId(), hot.lease().members().getFirst().entityId()),
                        "minecraft:wheat", completed.progress().totalCropSlots()),
                        new SceneLeaseReleased(hot.lease().id(), List.of(new SceneMemberPosition(
                                completed.workerId(), body.body(), body.condition().health())))));
        assertEquals(draining.inventory().fungibleResources().bindings(), released.inventory().fungibleResources().bindings(),
                "closing a work scope retains the actual body and all of its inventory bindings");
        assertEquals(released, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released)));
        assertEquals(pocketBefore, released.inventory().fungibleResources().bindings().values().stream()
                .filter(value -> value.address() instanceof PhysicalStackAddress.ActorPocket).toList(),
                "scene closure cannot discard or rebind personal pocket food");
        assertFalse(ActorExecutionCoordinator.coldAvailable(released, completed.workerId()),
                "process release alone is not physical body absence");
        released = ActorBodyAuthority.released(released, ActorBodyAuthority.current(released, completed.workerId()));
        assertTrue(released.inventory().fungibleResources().bindings().values().stream()
                .noneMatch(value -> value.accountId().equals(completed.actorAccountId())),
                "only the common body departure retires the physical crop binding");
        var delivery = ResourceSiteHarvestProcess.planColdProgress(released,
                ResourceSiteHarvestProcess.coldProgress(completed, 22_301L));
        assertTrue(delivery.stream().map(ProposedEvent::payload)
                .anyMatch(ResourceSiteHarvestColdGoalAdvanced.class::isInstance),
                "FREE must admit actual delivery travel, not defer the batch until next WORK");
    }
}
