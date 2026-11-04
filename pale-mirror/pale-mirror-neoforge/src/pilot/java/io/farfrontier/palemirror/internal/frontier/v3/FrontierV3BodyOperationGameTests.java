package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
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
    public static void bodyFatalityConfirmsActualNativeDeathWithoutAcknowledgingAnUnsavedCorpse(GameTestHelper helper) {
        var level = helper.getLevel(); var actor = new SubjectId("resident:1-1");
        var config = FrontierV3AmbientActorGameTests.configurationAt(helper, new WorldId("frontier:body-operation-fatality"), 91, actor);
        var store = new FrontierV3AmbientActorGameTests.EphemeralStore();
        var runtime = FrontierV3ServerRuntime.start(config, store, 10_000);
        FrontierV3AmbientActorGameTests.initializeAdmission(level, config, store);
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); FrontierV3AmbientActorGameTests.prepareFloor(level, feet);
        var lease = AmbientActorProcess.nextLease(runtime.decodedState().orElseThrow(), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "body-fatality-prepare", actor.value(), new AmbientLeasePrepared(lease));
        FrontierV3AmbientActorGameTests.publishProjectionBeforeManagedJoin(helper, level, runtime);
        try {
            var state = runtime.decodedState().orElseThrow();
            helper.assertValueEqual(FrontierV3BodyAdmissionGameTestFixture.ambient(level, runtime, state, actor,
                    new BodyPosition(feet.getX(), feet.getY(), feet.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                    "real body is present before the fatality operation");
            var body = (Mob) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, actor));
            FrontierV3ActorBodyController.confirmPresent(level, runtime, body);
            var id = ActorBodyAuthority.current(runtime.decodedState().orElseThrow(), actor);
            body.setHealth(0);
            // Component fixture in the template dimension: invoke the same body producer
            // around real vanilla die, not a fabricated accepted domain event. The production
            // mixin sits later, at the dead-field write, before loot; this fixture does not
            // claim coverage of the physical-dimension callback or cancellation ordering.
            helper.assertTrue(FrontierV3ActorBodyController.commitDeath(level, runtime, body, null,
                    () -> body.die(level.damageSources().generic()), () -> body.getPose() == net.minecraft.world.entity.Pose.DYING),
                    "actual fatality is confirmed by the common producer");
            helper.assertValueEqual(runtime.decodedState().orElseThrow().actorLocations().get(actor).condition().status(),
                    ActorLifeStatus.DEAD, "canonical lifetime terminates, not merely the presentation");
            var ledger = FrontierV3OperationLedger.get(level);
            var operation = ledger.find(FrontierV3OperationReceipt.Protocol.BODY_FATALITY, actor,
                    config.worldId().value() + "/epoch:" + id.physicalEpoch() + "/residence:"
                            + body.getPersistentData().getLong(FrontierV3ActorBodyController.RESIDENCE_KEY)).orElseThrow();
            helper.assertTrue(operation.ownerClosed() && operation.saved().isEmpty() && !operation.retirable(),
                    "canonical death cannot manufacture a native entity-file save");
            var participant = operation.declaration().participants().iterator().next();
            helper.assertTrue(FrontierV3NativeOperationMarkers.contains(body.getPersistentData(), operation.nativeReceipt(participant)),
                    "same native corpse retains its exact fatality receipt");
            helper.assertTrue(FrontierV3AmbientCarrierLedger.get(level, config.worldId()).firstAdmission(actor).isEmpty(),
                    "dead lifetime cannot retain a replacement permission");
            body.discard();
        } finally { FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 120)
    public static void bodyDeathRetainsOnePocketDropWhileItsRealCanonicalJournalIsDelayed(GameTestHelper helper) throws Exception {
        var level=helper.getLevel();var actor=new SubjectId("resident:1-1");
        var base=FrontierV3AmbientActorGameTests.configurationAt(helper,new WorldId("frontier:body-pocket-delayed"),91,actor);
        var initial=base.initialState();var account=new SubjectId("custody:delayed-death-pocket");
        var lot=new ResourceLot(new SubjectId("lot:delayed-death-pocket"),initial.humanPopulation().resident(actor).settlementId(),
                "minecraft:cobblestone",7,"fixture:death-pocket",java.util.List.of());
        var resources=initial.inventory().fungibleResources().issue(lot,new CustodyAccount(account,new ResourceCustody.Actor(actor),
                java.util.Map.of(lot.id(),7),java.util.Map.of(),java.util.Optional.of(new ActorItemSlot.Pocket(3))));
        initial=initial.withInventory(initial.inventory().withFungibleResources(resources));
        var config=new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(),initial,base.initialInstant(),
                base.commandPlanner(),base.scheduledPlanner(),base.reducer(),base.stateCodec(),base.projectionMapper(),base.limits(),java.util.List.of(),
                base.transactionCommitter(),base.stateValidator(),base.executionMetrics());
        var tasks=new java.util.ArrayDeque<Runnable>();var delayed=new java.util.concurrent.atomic.AtomicBoolean();
        var disk=new FrontierFileStore(java.nio.file.Files.createTempDirectory("pm-death-pocket-"),
                io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs(),
                action->{if(delayed.get())tasks.add(action);else action.run();});
        var delegate=new FrontierStoreTransactionCommitter(disk);
        TransactionCommitter committer=new TransactionCommitter(){
            public void commit(TransactionRecord record,Durability ignored){delegate.commit(record,Durability.DURABLE_BEFORE_EFFECT);}
            public Confirmation prepare(TransactionRecord record,Durability ignored){return delegate.prepare(record,Durability.DURABLE_BEFORE_EFFECT);}
        };
        var runtime=FrontierV3ServerRuntime.startRecovered(config,disk,disk.recover(config.worldId()),10_000,DiagnosticRuntimeIdentity.unavailable(),committer);
        FrontierV3AmbientActorGameTests.initializeAdmission(level,config,disk);
        var feet=helper.absolutePos(new BlockPos(0,1,0));FrontierV3AmbientActorGameTests.prepareFloor(level,feet);
        var lease=AmbientActorProcess.nextLease(runtime.decodedState().orElseThrow(),actor,runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime,"death-pocket-prepare",actor.value(),new AmbientLeasePrepared(lease));
        FrontierV3AmbientActorGameTests.publishProjectionBeforeManagedJoin(helper,level,runtime);
        helper.assertValueEqual(FrontierV3BodyAdmissionGameTestFixture.ambient(level,runtime,runtime.decodedState().orElseThrow(),actor,
                new BodyPosition(feet.getX(),feet.getY(),feet.getZ())),FrontierV3AmbientActorExecutor.Result.APPLIED,"actual managed body admitted");
        var body=(net.minecraft.world.entity.npc.Villager)level.getEntity(FrontierV3AmbientActorExecutor.entityId(initial,actor));
        FrontierV3ActorBodyController.confirmPresent(level,runtime,body);var id=ActorBodyAuthority.current(runtime.decodedState().orElseThrow(),actor);
        helper.assertValueEqual(body.getInventory().getItem(3).getCount(),7,"actual birth projects the retained pocket stock");
        body.setHealth(0);delayed.set(true);
        runtime.runPersistenceTurn(()->helper.assertTrue(FrontierV3ActorBodyController.commitDeath(level,runtime,body,null,
                ()->body.die(level.damageSources().generic()),()->body.getPose()==net.minecraft.world.entity.Pose.DYING),
                "native death is handled even though its resource owner still has aftermath"));
        helper.assertTrue(FrontierV3BodyFatalityOperation.holdsBody(level,runtime.decodedState().orElseThrow(),body),"unsettled inventory retains exact fatality custody");
        var outbox=FrontierV3ResourceHandoffLedger.get(level);
        var transfer=new java.util.concurrent.atomic.AtomicReference<FrontierV3EntityResourceDropTransaction>();
        var finished=new java.util.concurrent.atomic.AtomicBoolean();
        var rejoined=new java.util.concurrent.atomic.AtomicReference<Mob>();
        helper.onEachTick(()->{
            if(finished.get())return;
            try{
                if(helper.getTick()<5)return;
                delayed.set(false);while(!tasks.isEmpty())tasks.remove().run();
                runtime.runPersistenceTurn(()->{
                    delayed.set(true);
                    FrontierV3EntityResourceDropOperation.reconcileOne(level,runtime);
                    FrontierV3BodyFatalityOperation.reconcile(level,runtime);
                    if(transfer.get()==null)outbox.entityDrops().stream().filter(t->t.source().identity().equals(body.getUUID().toString())).findFirst().ifPresent(pending->{
                        helper.assertTrue(level.getEntity(pending.entity()) instanceof net.minecraft.world.entity.item.ItemEntity
                                && body.getInventory().getItem(3).isEmpty(),"one actual native transfer precedes canonical acknowledgement");
                        helper.assertTrue(!FrontierV3UnitInventoryDeathResources.settled(runtime.decodedState().orElseThrow(),id),"pending acknowledgement cannot release source accounting early");
                        helper.assertValueEqual(FrontierV3EntityResourceDropTransaction.read(pending.save()),pending,"pending consequence survives outbox hydration");
                        var current=runtime.decodedState().orElseThrow();
                        FrontierV3ActorBodyController.cleanRetired(level,current,actor);
                        helper.assertTrue(FrontierV3AmbientCarrierLedger.get(level,current.bootstrap().worldId()).knownBodyResidence(actor,
                                body.getPersistentData().getLong(FrontierV3ActorBodyController.RESIDENCE_KEY)),"pending fatality keeps native residency through ordinary cleanup");
                        var saved=body.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                        var restored=net.minecraft.world.entity.EntityType.VILLAGER.create(level);restored.load(saved);restored.setUUID(body.getUUID());
                        helper.assertFalse(FrontierV3BodyFatalityOperation.recognizesCorpse(level,current,restored),"a duplicate corpse cannot bypass the UUID index");
                        body.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                        long lifetime=restored.getPersistentData().getLong(FrontierV3NativeOperationMarkers.LIFETIME);
                        restored.getPersistentData().putLong(FrontierV3NativeOperationMarkers.LIFETIME,lifetime+1);
                        helper.assertFalse(FrontierV3BodyFatalityOperation.recognizesCorpse(level,current,restored),"another native lifetime cannot borrow fatality custody");
                        restored.getPersistentData().putLong(FrontierV3NativeOperationMarkers.LIFETIME,lifetime);
                        helper.assertTrue(FrontierV3ServerLifecycle.observeSourceJoin(level,runtime,restored).verifiedV3Carrier(),"exact serialized corpse passes the real join composition only for aftermath");
                        helper.assertFalse(FrontierV3ActorBodyController.recognizes(current,restored),"corpse admission is never living execution authority");
                        helper.assertTrue(level.addFreshEntity(restored),"serialized native corpse rejoins for retained receipt recovery");
                        rejoined.set(restored);transfer.set(pending);
                    });
                });
                helper.assertValueEqual(runtime.status().kind(),FrontierV3RuntimeStatus.Kind.ACTIVE,"delayed aftermath is ordinary progress, not quarantine");
                var current=runtime.decodedState().orElseThrow();
                if(!FrontierV3UnitInventoryDeathResources.settled(current,id) || FrontierV3BodyFatalityOperation.holdsBody(level,current,body))return;
                var destination=current.inventory().fungibleResources().accounts().values().stream().filter(a->a.lotQuantities().containsKey(lot.id())).findFirst().orElseThrow();
                helper.assertTrue(destination.custody() instanceof ResourceCustody.Ground ground && transfer.get()!=null && ground.entityId().equals(transfer.get().entity())
                        && destination.lotQuantities().equals(java.util.Map.of(lot.id(),7)),"confirmation preserves the exact single drop, title and quantity");
                helper.assertTrue(outbox.entityDrops().stream().noneMatch(t->t.id().equals(transfer.get().id())),"confirmed outbox entry is retired");
                FrontierV3BodyFatalityOperation.reconcile(level,runtime);
                helper.assertValueEqual(runtime.decodedState().orElseThrow(),current,"repeated recovery cannot replay death or duplicate stock");
                finished.set(true);delayed.set(false);while(!tasks.isEmpty())tasks.remove().run();
                body.discard();if(rejoined.get()!=null)rejoined.get().discard();FrontierV3AmbientActorExecutor.forget(runtime);runtime.shutdown();disk.close();helper.succeed();
            }catch(RuntimeException failure){
                finished.set(true);delayed.set(false);while(!tasks.isEmpty())tasks.remove().run();
                body.discard();if(rejoined.get()!=null)rejoined.get().discard();FrontierV3AmbientActorExecutor.forget(runtime);runtime.shutdown();disk.close();throw failure;
            }
        });
    }
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
