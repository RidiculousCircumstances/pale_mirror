package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3PendingAdmissionGameTests {
    private FrontierV3PendingAdmissionGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void pendingDeliveryStopsGenericReleaseBeforeContinuationAndBodyInspection(GameTestHelper helper) {
        var base = FrontierV3FixtureCatalog.resourceSiteHarvestConfiguration(new WorldId("frontier:pending-delivery-barrier"), 421L);
        var site = new SubjectId("site:1-wheat-field");
        var initial = base.initialState();
        var job = initial.resourceSites().site(site).harvestJobs().values().iterator().next();
        var body = initial.actorLocations().get(job.workerId()).body();
        var lease = SceneLease.forCause(new SceneLeaseId("lease:pending-delivery-barrier"), base.worldId(),
                new ResourceSiteHarvestSceneCause(site, job.id()), body.supportingSurface().support(),
                base.initialInstant(), 17L, SceneLeaseStatus.DRAINING,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(base.worldId(), job.workerId()))), java.util.Set.of(), Optional.empty());
        var state = initial.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(Map.of(lease.id(), lease)));
        // No continuation is fabricated: an unresolved effect must prevent release before
        // the generic executor even asks for that next action or inspects the absent hand.
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(), base.transactionCommitter(), FrontierWorldStateTransitionValidator.INSTANCE);
        var runtime = FrontierV3ServerRuntime.start(configuration, new FrontierV3SceneGameTests.EphemeralStore(), 20_000);
        var ledger = FrontierV3ResourceSiteLedger.get(helper.getLevel());
        var witness = new FrontierV3ResourceSiteDeliveryWitness(site, job.id(), job.intentId(), job.workerId(),
                lease.members().getFirst().entityId(), lease.id(), lease.revision(), job.outputSlot().containerId(),
                job.outputSlot().slot(), 64, 3L, "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64),
                "witness:pending-delivery-barrier", 0, true, job.outputSlot().slot() + 1);
        try {
            ledger.beginFieldDelivery(witness);
            helper.assertTrue(!FrontierV3SceneBehaviorRegistry.releaseReady(helper.getLevel(), state, lease),
                    "closed behavior registration must expose the harvest-owned pending effect");
            FrontierV3SceneReleaseExecutor.release(helper.getLevel(), runtime, lease);
            helper.assertTrue(runtime.decodedState().orElseThrow().equals(state),
                    "generic release cannot change custody, conflict the field or close the scene");
            helper.assertTrue(ledger.fieldDelivery(site).equals(witness), "generic release cannot consume the delivery permission");
        } finally {
            ledger.retireFieldDelivery(witness);
            runtime.shutdown();
        }
        helper.succeed();
    }

}
