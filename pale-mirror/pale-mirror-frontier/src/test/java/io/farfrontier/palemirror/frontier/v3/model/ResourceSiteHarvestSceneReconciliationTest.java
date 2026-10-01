package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestSceneReconciliation;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceSiteHarvestSceneReconciliationTest {
    @Test void exactInspectedBatchResumesSameWorkerAndEpochWithoutReplayingHarvest() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var complete = ResourceSiteHarvestProcessTest.completeHarvestWorkHot(hot.state(), hot.site(), hot.job(), hot.lease().id());
        var state = complete.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.CONFLICT);
        var job = (ResourceSiteHarvestJob) state.resourceSites().site(hot.site()).activeWork().orElseThrow();
        var lease = state.sceneLeases().get(hot.lease().id());
        var bindingId = FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(job.workerId());
        long epoch = state.fencedRecovery().current().get(bindingId).authorityEpoch();
        var original = state.actorLocations().get(job.workerId()).body();
        var displaced = new BodyPosition(original.x() + 1, original.y(), original.z());
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                lease.members().getFirst().entityId()), "minecraft:wheat", 64);
        var receipt = new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch, displaced, hand);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
        var restored = ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(), receipt);
        assertEquals(SceneLeaseStatus.HOT, restored.sceneLeases().get(lease.id()).status());
        assertEquals(displaced, restored.actorLocations().get(job.workerId()).body());
        assertEquals(epoch, restored.fencedRecovery().current().get(bindingId).authorityEpoch());
        assertEquals(FencedRecoveryPhase.RUNNING, restored.fencedRecovery().current().get(bindingId).phase());
        assertEquals(state.inventory(), restored.inventory());
        assertEquals(state.resourceSites(), restored.resourceSites());
        assertEquals(restored, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(restored)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(restored, hot.site(), receipt));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(),
                new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch + 1, displaced, hand)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(),
                new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision() + 1, epoch, displaced, hand)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(),
                new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch, displaced,
                    new FungiblePhysicalObservation.Stack(hand.address(), "minecraft:wheat", 63))));
    }
}
