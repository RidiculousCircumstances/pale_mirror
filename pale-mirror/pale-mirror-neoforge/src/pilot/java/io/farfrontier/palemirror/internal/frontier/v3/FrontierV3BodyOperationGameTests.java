package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.function.Consumer;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3BodyOperationGameTests {
    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void nativeJoinRefusalClosesOnlyItsOwnAdmissionAndNextAttemptConfirms(GameTestHelper helper) {
        var level = helper.getLevel(); var actor = new SubjectId("resident:1-1");
        var config = FrontierV3AmbientActorGameTests.configurationAt(helper, new WorldId("frontier:body-operation-refusal"), 91, actor);
        var store = new FrontierV3AmbientActorGameTests.EphemeralStore();
        var runtime = FrontierV3ServerRuntime.start(config, store, 10_000);
        FrontierV3AmbientActorGameTests.initializeAdmission(level, config, store);
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); FrontierV3AmbientActorGameTests.prepareFloor(level, feet);
        var lease = AmbientActorProcess.nextLease(runtime.decodedState().orElseThrow(), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "body-refusal-prepare", actor.value(), new AmbientLeasePrepared(lease));
        FrontierV3AmbientActorGameTests.publishProjectionBeforeManagedJoin(helper, level, runtime);
        var state = runtime.decodedState().orElseThrow(); var position = new BodyPosition(feet.getX(), feet.getY(), feet.getZ());
        var id = FrontierV3AmbientActorExecutor.entityId(state, actor);
        var carriers = FrontierV3AmbientCarrierLedger.get(level, config.worldId()); var operations = FrontierV3OperationLedger.get(level);
        Consumer<EntityJoinLevelEvent> refusal = event -> { if (event.getLevel() == level && event.getEntity().getUUID().equals(id)) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(refusal);
        try {
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state, actor, position),
                    FrontierV3AmbientActorExecutor.Result.DEFERRED, "native attempt waits for both durable declarations");
            var link = carriers.bodyOperation(actor).orElseThrow();
            carriers.persist(level, config.worldId()); operations.persist(level);
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state, actor, position),
                    FrontierV3AmbientActorExecutor.Result.DEFERRED, "the actual NeoForge join event refused this private object");
            helper.assertTrue(level.getEntity(id) == null && carriers.bodyOperation(actor).isEmpty()
                    && carriers.firstAdmission(actor).orElseThrow().phase() == FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED,
                    "refusal restores the exact unused permission without creating a body or unload receipt");
            helper.assertTrue(operations.prunable(link.operation().nativeReceipt(link.participant())),
                    "only the rolled-back refusal is durably retired");
        } finally { NeoForge.EVENT_BUS.unregister(refusal); }
        try {
            helper.assertValueEqual(FrontierV3BodyAdmissionGameTestFixture.ambient(level, runtime, state, actor, position),
                    FrontierV3AmbientActorExecutor.Result.APPLIED, "a new exact attempt remains admissible after positive refusal");
            var body = (Mob) level.getEntity(id); var link = carriers.bodyOperation(actor).orElseThrow();
            helper.assertTrue(FrontierV3NativeOperationMarkers.contains(body.getPersistentData(), link.operation().nativeReceipt(link.participant())),
                    "actual indexed body carries the same operation and native lifetime");
            FrontierV3ActorBodyController.confirmPresent(level, runtime, body);
            var next = runtime.decodedState().orElseThrow();
            helper.assertValueEqual(ActorBodyAuthority.require(next, ActorBodyAuthority.current(next, actor)).phase(), FencedRecoveryPhase.RUNNING,
                    "common owner confirmation grants execution only after supported physical insertion");
            helper.assertTrue(carriers.bodyOperation(actor).isEmpty() && operations.require(link.operation().ticket()).ownerClosed()
                    && !operations.require(link.operation().ticket()).retirable(), "owner closure is not native disk-save acknowledgement");
            body.discard();
        } finally { FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); }
        helper.succeed();
    }
}
