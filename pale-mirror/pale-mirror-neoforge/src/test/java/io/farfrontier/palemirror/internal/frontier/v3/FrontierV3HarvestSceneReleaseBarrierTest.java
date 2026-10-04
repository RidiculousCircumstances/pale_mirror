package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3HarvestSceneReleaseBarrierTest {
    @Test void savedUnconfirmedDeliveryBlocksOnlyItsSceneUntilTheOwnerRetiresItsWitness() {
        var base = FrontierV3FixtureCatalog.resourceSiteHarvestConfiguration(new WorldId("frontier:delivery-release"), 421L);
        var site = new SubjectId("site:1-wheat-field");
        var job = base.initialState().resourceSites().site(site).harvestJobs().values().iterator().next();
        var body = base.initialState().actorLocations().get(job.workerId()).body();
        var lease = SceneLease.forCause(new SceneLeaseId("lease:delivery-release"), base.worldId(),
                new ResourceSiteHarvestSceneCause(site, job.id()), body.supportingSurface().support(),
                base.initialInstant(), 17L, SceneLeaseStatus.UNKNOWN_AFTER_RESTART,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(base.worldId(), job.workerId()))), Set.of(), Optional.empty());
        var witness = new FrontierV3ResourceSiteDeliveryWitness(site, job.id(), job.intentId(), job.workerId(),
                lease.members().getFirst().entityId(), lease.id(), lease.revision(), job.outputSlot().containerId(),
                job.outputSlot().slot(), 64, 3L, "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64),
                "witness:delivery-release", 0, true, job.outputSlot().slot() + 1);
        var ledger = FrontierV3ResourceSiteLedger.fixture();
        assertTrue(FrontierV3HarvestSceneReleaseBarrier.ready(ledger, lease));
        ledger.beginFieldDelivery(witness);
        assertFalse(FrontierV3HarvestSceneReleaseBarrier.ready(ledger, lease));
        assertFalse(FrontierV3HarvestSceneReleaseBarrier.ready(ledger, lease.withStatus(SceneLeaseStatus.DRAINING)));
        var restored = FrontierV3ResourceSiteLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertFalse(FrontierV3HarvestSceneReleaseBarrier.ready(restored, lease),
                "restart must retain the effect barrier even when the saved farmer hand is already empty");
        var other = SceneLease.forCause(new SceneLeaseId("lease:another-farmer"), base.worldId(), lease.cause(),
                lease.handoffPosition(), base.initialInstant(), 18L, SceneLeaseStatus.UNKNOWN_AFTER_RESTART,
                lease.members(), Set.of(), Optional.empty());
        assertTrue(FrontierV3HarvestSceneReleaseBarrier.ready(restored, other),
                "an independent scene on the same field must not wait for this delivery");
        restored.retireFieldDelivery(witness);
        assertTrue(FrontierV3HarvestSceneReleaseBarrier.ready(restored, lease));
    }
}
