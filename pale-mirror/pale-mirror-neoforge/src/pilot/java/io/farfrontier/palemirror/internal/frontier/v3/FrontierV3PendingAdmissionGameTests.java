package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(base.worldId(), job.workerId()))),
                Map.of(job.workerId(), body), java.util.Set.of(), Optional.empty());
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

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void retainedSceneBodyReleasesOldAmbientJoinBridgeWithoutMovingOrLosingCargo(GameTestHelper helper) {
        var base = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(new WorldId("frontier:pending-scene-bridge"), 41L);
        var initial = base.initialState();
        var operation = initial.operations().values().iterator().next();
        var travel = operation.activeTravel().orElseThrow();
        var actor = operation.participantIds().getFirst();
        var id = new SceneLeaseId("lease:pending-scene-bridge");
        var lease = SceneLease.atExactPositions(id, initial.bootstrap().worldId(), operation.id(), operation.cargoId(),
                operation.currentPosition(), travel.cargoAnchor().surface().support(), base.initialInstant(), 17L,
                SceneLeaseStatus.CLOSED, Optional.empty(), operation.participantIds().stream().map(value ->
                        new SceneMember(value, SceneLease.deterministicEntityId(initial.bootstrap().worldId(), value))).toList(), travel.formation());
        var state = initial.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(Map.of(id, lease)));
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(), base.transactionCommitter(), FrontierWorldStateTransitionValidator.INSTANCE);
        var runtime = FrontierV3ServerRuntime.start(configuration, new FrontierV3SceneGameTests.EphemeralStore(), 20_000);
        var level = helper.getLevel();
        var body = EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("test body unavailable");
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor,
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, SceneLease.deterministicEntityId(base.worldId(), actor),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), 2L);
        body.setUUID(declaration.entityId());
        FrontierV3ActorOwnerBinding.scene(declaration, id).stamp(body);
        BlockPos position = helper.absolutePos(new BlockPos(2, 3, 2));
        body.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
        body.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.WHEAT, 17));
        var pose = body.position();
        try {
            helper.assertTrue(level.addFreshEntity(body), "exact body must be indexed");
            FrontierV3AmbientPendingAdmissions.retain(runtime, body, actor);
            helper.assertTrue(FrontierV3AmbientPendingAdmissions.get(runtime, body.getUUID()) == body, "reproduce the stale ambient join entry");
            FrontierV3AmbientPendingAdmissions.reclaimProjected(runtime, state);
            helper.assertTrue(FrontierV3AmbientPendingAdmissions.get(runtime, body.getUUID()) == null,
                    "recorded closed-scene ownership must release the bridge that otherwise blocks return admission");
            helper.assertTrue(level.getEntity(body.getUUID()) == body && body.position().equals(pose), "cleanup must retain the exact body and pose");
            helper.assertTrue(body.getOffhandItem().is(Items.WHEAT) && body.getOffhandItem().getCount() == 17, "cleanup cannot alter cargo");
        } finally {
            body.discard(); FrontierV3AmbientPendingAdmissions.forget(runtime); runtime.shutdown();
        }
        helper.succeed();
    }
}
