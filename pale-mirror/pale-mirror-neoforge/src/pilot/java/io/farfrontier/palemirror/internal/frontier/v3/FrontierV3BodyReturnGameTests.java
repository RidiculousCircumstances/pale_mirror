package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.internal.world.SourceGrayboxEntityAdmission;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real saved entity return through the common departure and join owners; not a region crash test. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3BodyReturnGameTests {
    private FrontierV3BodyReturnGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void savedDepartureReturnsThroughOrdinaryJoinFirewall(GameTestHelper helper) {
        savedDepartureReturns(helper, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void savedPreparedBodyReturnsAfterInsertionAcknowledgement(GameTestHelper helper) {
        savedDepartureReturns(helper, true);
    }

    private static void savedDepartureReturns(GameTestHelper helper, boolean preparedSave) {
        var level = helper.getLevel();
        var actor = new SubjectId("resident:1-1");
        var feet = helper.absolutePos(new BlockPos(0, 1, 0));
        FrontierV3AmbientActorGameTests.prepareFloor(level, feet);
        var config = FrontierV3AmbientActorGameTests.configurationAt(helper,
                new WorldId(preparedSave ? "frontier:saved-prepared-body-return" : "frontier:saved-body-return"), 91L, actor);
        var store = new FrontierV3AmbientActorGameTests.EphemeralStore();
        var runtime = FrontierV3ServerRuntime.start(config, store, 10_000);
        FrontierV3AmbientActorGameTests.initializeAdmission(level, config, store);
        var lease = AmbientActorProcess.nextLease(runtime.decodedState().orElseThrow(), actor,
                runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "saved-return-prepare", actor.value(), new AmbientLeasePrepared(lease));
        FrontierV3AmbientActorGameTests.publishProjectionBeforeManagedJoin(helper, level, runtime);
        FrontierV3AmbientActorExecutor.materialize(level, runtime, runtime.decodedState().orElseThrow(), actor, lease.handoffBody());
        helper.runAfterDelay(1L, () -> {
            var state = runtime.decodedState().orElseThrow();
            var body = (Mob) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, actor));
            helper.assertTrue(body != null, "original body must be indexed");
            if (!preparedSave) FrontierV3ActorBodyController.confirmPresent(level, runtime, body);
            var saved = new CompoundTag();
            helper.assertTrue(body.save(saved), "real body must serialize before final unload");
            var ledger = FrontierV3AmbientCarrierLedger.get(level, config.worldId());
            if (preparedSave) {
                helper.assertValueEqual(io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.require(state,
                        io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actor)).phase(),
                        io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase.PREPARED,
                        "this boundary exercises an inserted body before supported admission");
                // Modeled acknowledgement ordering, not a claimed entity-region durable save.
                // The returned object below is genuinely serialized/deserialized and indexed.
                var binding = FrontierV3ActorOwnerBinding.from(body).orElseThrow();
                helper.assertTrue(binding.matchesSaved(saved), "serialized physical identity matches the pending insertion");
                helper.assertTrue(ledger.acknowledgeFirstAdmission(ledger.firstAdmission(actor).orElseThrow(), binding),
                        "successful insertion history can precede the common supported observation");
            }
            body.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(FrontierV3ActorBodyController.observeLeave(level, runtime, body), "final unload must retain exact receipt");
            var receipt = ledger.bodyDeparture(actor).orElseThrow();
            helper.assertTrue(ledger.markBodyReturnRead(receipt), "positive return read fences old absence");
            var returned = EntityType.loadEntityRecursive(saved, level, entity -> entity);
            var proof = FrontierV3ServerLifecycle.observeSourceJoin(level, runtime, returned);
            helper.assertTrue(proof.verifiedV3Carrier()
                    && !SourceGrayboxEntityAdmission.rejectsSourceMob(proof, true, false),
                    "exact saved departure must be accepted before indexing, not silently canceled");
            helper.assertFalse(ledger.hasBodyDeparture(actor), "accepted return consumes only its exact departure");
            helper.assertTrue(level.addFreshEntity(returned), "returned body must enter native index");
            helper.assertTrue(level.getEntity(body.getUUID()) == returned, "one exact returned body remains indexed");
            FrontierV3ActorBodyController.confirmPresent(level, runtime, returned);
            helper.assertValueEqual(io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.require(
                    runtime.decodedState().orElseThrow(), io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(
                        runtime.decodedState().orElseThrow(), actor)).phase(),
                    io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase.RUNNING,
                    "only the indexed supported physical observation grants execution readiness");
            returned.discard();
            FrontierV3AmbientActorExecutor.forget(runtime);
            runtime.shutdown();
            helper.succeed();
        });
    }
}
