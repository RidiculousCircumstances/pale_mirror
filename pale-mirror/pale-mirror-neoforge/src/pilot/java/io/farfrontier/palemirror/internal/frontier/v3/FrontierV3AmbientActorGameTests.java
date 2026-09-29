package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.world.SourceGrayboxEntityAdmission;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseRestartAbsenceObserved;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticRuntimeIdentity;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjective;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjectiveKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicObjectiveStatus;
import io.farfrontier.palemirror.frontier.v3.model.StrategicPlanState;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTask;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskKind;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskRequirement;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskStatus;
import io.farfrontier.palemirror.frontier.v3.process.HiveGrowthProcess;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Materialized recovery evidence for the ambient before-effect actor lease boundary. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3AmbientActorGameTests {
    private FrontierV3AmbientActorGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void finalAmbientUnloadRetainsDamageAndReleasesOnlyWithExactWitness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(12, 8, 0)); prepareFloor(level, origin);
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:ambient-unload-game-test"), 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        var lease = AmbientActorProcess.nextLease(state(runtime), resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "ambient-unload-prepare", resident.value(), new AmbientLeasePrepared(lease));
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident, bodyAt(origin)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "actual managed fixture body must enter Minecraft");
        helper.runAfterDelay(1L, () -> {
            var body = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
            helper.assertTrue(body != null, "body must be indexed");
            FrontierV3CommandSubmission.submit(runtime, "ambient-unload-hot", resident.value(), new AmbientLeaseTransition(resident, AmbientLeaseStatus.HOT));
            body.setPos(lease.handoffBody().x() + 0.5D, lease.handoffBody().y(), lease.handoffBody().z() + 0.5D);
            body.setHealth(9.0F);
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state(runtime).bootstrap().worldId());
            helper.assertFalse(FrontierV3AmbientDepartureObserver.observeLeave(level, runtime, body),
                    "a live/tracking-end observation must not become final departure evidence");
            helper.assertFalse(FrontierV3AmbientActorExecutor.releaseUnloadedReservedColdContinuation(level, runtime,
                    state(runtime), resident, state(runtime).ambientLeases().get(resident)), "missing witness must not release a physical owner");
            body.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(FrontierV3AmbientDepartureObserver.observeLeave(level, runtime, body),
                    "actual final removal reason and exact stamped authority must admit the observation");
            helper.assertValueEqual(ledger.ambientDeparture(resident).orElseThrow().observed().health(),
                    io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(9L), "final damage must be retained");
            helper.assertFalse(ledger.hasCarrier(resident), "observation itself must not transfer custody");
            helper.runAfterDelay(1L, () -> {
                try {
                    helper.assertTrue(FrontierV3AmbientActorExecutor.releaseUnloadedReservedColdContinuation(level, runtime,
                            state(runtime), resident, state(runtime).ambientLeases().get(resident)),
                            "unloaded exact witness must permit ordinary canonical release");
                    helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.CLOSED, "lease must close");
                    helper.assertValueEqual(state(runtime).actorLocations().get(resident).condition().health(),
                            io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(9L), "release must not restore old health");
                    helper.assertTrue(ledger.hasCarrier(resident), "release must preserve next-admission evidence");
                    helper.succeed();
                } finally { FrontierV3AmbientActorExecutor.forget(runtime); }
            });
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void reservationReleaseRetainsCarrierAndObservedHealthForNextColdAdmission(GameTestHelper helper) {
        verifyReservationRelease(helper, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void interruptedDrainingReleaseResumesFromItsExactCarrier(GameTestHelper helper) {
        verifyReservationRelease(helper, true);
    }

    private static void verifyReservationRelease(GameTestHelper helper, boolean interrupted) {
        verifyReservationRelease(helper, interrupted, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void preparedBodyReleaseRetainsObservedHealthAndDurableCarrier(GameTestHelper helper) {
        verifyReservationRelease(helper, false, true);
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void restartUnknownBodyCompletesOnlyItsExactRetainedRelease(GameTestHelper helper) {
        verifyReservationRelease(helper, false, false, true);
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void cancelledNeverCreatedLeaseStillAdmitsOneBodyOnNextRevision(GameTestHelper helper) {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:first-cancel-retry"), 91L);
        var runtime = FrontierV3ServerRuntime.start(config, new EphemeralStore(), 10_000);
        var level = helper.getLevel(); var actor = new SubjectId("resident:1-1");
        var ledger = FrontierV3AmbientCarrierLedger.get(level, config.worldId());
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, config.initialState(),
                new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(config.worldId(), java.util.Optional.empty(), List.of()),
                () -> ledger.persist(level, config.worldId()));
        var first = AmbientActorProcess.nextLease(state(runtime), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "first-cancel-prepare", actor.value(), new AmbientLeasePrepared(first));
        helper.assertTrue(FrontierV3AmbientActorExecutor.abandonPreparedForReservation(level, runtime, state(runtime), actor, first).isPresent(),
                "unconsumed initial permit permits ordinary preparation cancellation");
        helper.assertValueEqual(ledger.firstAdmission(actor).orElseThrow().phase(), FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED,
                "cancellation must retain unused permission instead of inventing an inactive body");
        var next = AmbientActorProcess.nextLease(state(runtime), actor, runtime.checkpointImage().orElseThrow().instant());
        helper.assertValueEqual(next.revision(), 2L, "fixture must exercise the formerly stranded second lease");
        FrontierV3CommandSubmission.submit(runtime, "first-cancel-retry", actor.value(), new AmbientLeasePrepared(next));
        helper.assertFalse(FrontierV3AmbientActorExecutor.admissionDiagnostic(level, runtime, state(runtime), actor)
                .status().equals("CARRIER_MISSING"), "unused permit must not be diagnosed as missing solely from revision2");
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, feet);
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        // Only the physical fixture position is local to GameTest; canonical transitions above are ordinary commands.
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "second lease must create the first body through the runtime-aware boundary");
        helper.assertValueEqual(ledger.firstAdmission(actor).orElseThrow().phase(), FrontierV3ActorFirstAdmission.Phase.PENDING,
                "insertion is not an entity-save acknowledgement");
        helper.assertFalse(FrontierV3AmbientActorExecutor.abandonPreparedForReservation(level, runtime, state(runtime), actor, next).isPresent(),
                "in-flight body cannot be cancelled as never created");
        helper.runAfterDelay(1L, () -> {
            var body = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), actor));
            helper.assertTrue(body instanceof Villager, "exact first body must become indexed");
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                    FrontierV3AmbientActorExecutor.Result.CURRENT, "ordinary retry must reuse the existing body");
            helper.assertTrue(level.getEntity(body.getUUID()) == body, "no replacement body on retry");
            body.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void scheduledResidentBirthAdmitsItsOwnFirstMinecraftBody(GameTestHelper helper) {
        var world = new WorldId("frontier:scheduled-resident-first-body");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var initial = base.initialState();
        var settlement = initial.bootstrap().settlements().getFirst().id();
        var food = new SubjectId("item:scheduled-resident-first-body-bread");
        var stocked = initial.withInventory(initial.inventory()
                .store(new io.farfrontier.palemirror.frontier.v3.model.ExactItemStack(
                        food, settlement, io.farfrontier.palemirror.frontier.v3.process.PopulationBirthProcess.BREAD, 64,
                        new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot(
                                FrontierWorldState.depotId(settlement), 1)))
                .store(new io.farfrontier.palemirror.frontier.v3.model.ExactItemStack(
                        new SubjectId("item:scheduled-resident-first-body-reserve"), settlement,
                        io.farfrontier.palemirror.frontier.v3.process.PopulationBirthProcess.BREAD, 64,
                        new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot(
                                FrontierWorldState.depotId(settlement), 2))));
        var config = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(world, stocked,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(
                        io.farfrontier.palemirror.frontier.v3.process.PopulationBirthProcess.review(settlement, 1, 100L)),
                base.transactionCommitter(), io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateTransitionValidator.INSTANCE);
        var level = helper.getLevel();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        var store = new EphemeralStore();
        var runtime = FrontierV3ServerRuntime.startRecovered(config, store, store.recover(world), 10_000,
                DiagnosticRuntimeIdentity.unavailable(), new FrontierV3ActorBirthCommitter(world, ledger,
                        () -> ledger.persist(level, world), new FrontierStoreTransactionCommitter(store)));
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, stocked, store.recover(world),
                () -> ledger.persist(level, world));
        helper.assertTrue(runtime.advance(100, new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(16, 64)).isPresent(),
                "the ordinary scheduled conception must commit");
        var job = state(runtime).humanPopulation().birthJobs().values().iterator().next();
        helper.assertTrue(ledger.firstAdmission(job.resident().id()).isEmpty(), "conception cannot issue a body");
        helper.assertTrue(runtime.advance(200, new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(16, 64)).isPresent(),
                "the exact scheduled birth must commit");
        var actor = job.resident().id();
        helper.assertValueEqual(state(runtime).humanPopulation().resident(actor), job.resident(),
                "the retained job must create its own resident");
        helper.assertValueEqual(ledger.firstAdmission(actor).orElseThrow().phase(),
                FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED, "birth must issue one unused first-body permission");
        var lease = AmbientActorProcess.nextLease(state(runtime), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "born-resident-ambient-prepare", actor.value(), new AmbientLeasePrepared(lease));
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, feet);
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "the newly born resident must enter Minecraft once");
        helper.assertValueEqual(ledger.firstAdmission(actor).orElseThrow().phase(), FrontierV3ActorFirstAdmission.Phase.PENDING,
                "insertion must keep the save acknowledgement pending");
        helper.runAfterDelay(1L, () -> {
            try {
                var body = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), actor));
                helper.assertTrue(body instanceof Villager && body.isAlive(), "the job's exact new body must be indexed");
                helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                        FrontierV3AmbientActorExecutor.Result.CURRENT, "repeated admission must reuse that body");
                helper.assertTrue(level.getEntity(body.getUUID()) == body, "no duplicate resident body may be created");
                body.discard(); helper.succeed();
            } finally { FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void scheduledHiveBirthAdmitsItsOwnFirstMinecraftBody(GameTestHelper helper) {
        var world = new WorldId("frontier:scheduled-bioform-first-body");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 93L);
        var initial = base.initialState(); var hive = initial.bootstrap().hive().id();
        var objective = new StrategicObjective(new SubjectId("objective:scheduled-bioform-first-body"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        var task = new StrategicTask(new SubjectId("task:scheduled-bioform-first-body"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, Optional.empty(),
                List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), List.of(), StrategicTaskStatus.PENDING);
        var lot = new ResourceLot(new SubjectId("lot:scheduled-bioform-first-body"), hive,
                "minecraft:rotten_flesh", 64, "bootstrap", List.of());
        var account = new CustodyAccount(new SubjectId("custody:scheduled-bioform-first-body"),
                new ResourceCustody.Container(new SubjectId("container:hive-west-store")),
                java.util.Map.of(lot.id(), 64), java.util.Map.of());
        var stocked = initial.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task))
                .withInventory(initial.inventory().withFungibleResources(FungibleResourceLedger.empty().issue(lot, account)));
        var config = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(world, stocked,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(HiveGrowthProcess.start(task, 100L)),
                base.transactionCommitter(), io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateTransitionValidator.INSTANCE);
        var level = helper.getLevel(); var ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        var store = new EphemeralStore();
        var runtime = FrontierV3ServerRuntime.startRecovered(config, store, store.recover(world), 10_000,
                DiagnosticRuntimeIdentity.unavailable(), new FrontierV3ActorBirthCommitter(world, ledger,
                        () -> ledger.persist(level, world), new FrontierStoreTransactionCommitter(store)));
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, stocked, store.recover(world),
                () -> ledger.persist(level, world));
        helper.assertTrue(runtime.advance(100, new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(16, 64)).isPresent(),
                "ordinary scheduled hive growth must commit its retained job");
        var job = state(runtime).hiveColony().growthJobs().values().iterator().next();
        helper.assertTrue(ledger.firstAdmission(job.bioform().id()).isEmpty(), "growth work cannot issue a body before birth");
        helper.assertTrue(runtime.advance(200, new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(16, 64)).isPresent(),
                "the exact scheduled hive completion must commit");
        var actor = job.bioform().id();
        helper.assertValueEqual(state(runtime).hiveColony().spawnedBioforms().get(actor), job.bioform(),
                "the retired growth job must retain its exact bioform output");
        helper.assertValueEqual(ledger.firstAdmission(actor).orElseThrow().phase(),
                FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED, "birth must issue one unused bioform permission");
        var lease = AmbientActorProcess.nextLease(state(runtime), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "born-bioform-ambient-prepare", actor.value(), new AmbientLeasePrepared(lease));
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, feet);
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "the newly grown bioform must enter Minecraft once");
        helper.assertValueEqual(ledger.firstAdmission(actor).orElseThrow().phase(), FrontierV3ActorFirstAdmission.Phase.PENDING,
                "insertion must wait for actual entity-save acknowledgement");
        helper.runAfterDelay(1L, () -> {
            try {
                var body = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), actor));
                helper.assertTrue(body instanceof Zombie && body.isAlive(), "the job's exact new Zombie must be indexed");
                helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                        FrontierV3AmbientActorExecutor.Result.CURRENT, "repeated admission must reuse the same bioform body");
                helper.assertTrue(level.getEntity(body.getUUID()) == body, "no duplicate bioform body may be created");
                body.discard(); helper.succeed();
            } finally { FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void pendingFirstBodyRejoinsRecoveredRuntimeWithoutAnotherCreation(GameTestHelper helper) {
        verifyPendingFirstBodyRecovery(helper, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void proofBackedFirstResidentAdmissionCreatesOnlyItsOriginalUuid(GameTestHelper helper) {
        var world = new WorldId("frontier:proof-backed-first-resident-native");
        var config = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var store = new EphemeralStore();
        var runtime = FrontierV3ServerRuntime.start(config, store, 10_000);
        var level = helper.getLevel();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, config.initialState(), store.recover(world),
                () -> ledger.persist(level, world));
        var actor = new SubjectId("resident:1-1");
        var lease = AmbientActorProcess.nextLease(state(runtime), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "proof-backed-prepare", actor.value(), new AmbientLeasePrepared(lease));
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state(runtime), actor,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3AmbientActorExecutor.entityId(state(runtime), actor),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), 1L);
        var binding = FrontierV3ActorOwnerBinding.ambient(declaration);
        helper.assertTrue(ledger.beginFirstAdmission(binding), "fixture must durably retain its original first attempt");
        ledger.persist(level, world);
        helper.assertTrue(ledger.rearmFirstAdmissionAfterAbsence(ledger.firstAdmission(actor).orElseThrow(), "a".repeat(64)),
                "fixture must retain the typed proof-backed permission");
        ledger.persist(level, world);
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, feet);
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "ordinary ambient owner must consume the proof-backed permission");
        var pending = ledger.firstAdmission(actor).orElseThrow();
        helper.assertValueEqual(pending.phase(), FrontierV3ActorFirstAdmission.Phase.PENDING,
                "real entity insertion does not acknowledge an unsaved body");
        helper.assertValueEqual(pending.absenceReceipt().orElseThrow(), "a".repeat(64),
                "the exact offline proof reference survives admission");
        helper.runAfterDelay(1L, () -> {
            try {
                var body = level.getEntity(declaration.entityId());
                helper.assertTrue(body instanceof Villager && body.isAlive(), "original resident UUID must be indexed once");
                helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                        FrontierV3AmbientActorExecutor.Result.CURRENT, "repeat must reuse the same body");
                helper.assertTrue(level.getEntity(declaration.entityId()) == body, "proof-backed retry cannot duplicate the actor");
                body.discard(); helper.succeed();
            } finally { FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void pendingFirstBioformRejoinsRecoveredRuntimeWithoutAnotherCreation(GameTestHelper helper) {
        verifyPendingFirstBodyRecovery(helper, true);
    }

    private static void verifyPendingFirstBodyRecovery(GameTestHelper helper, boolean bioform) {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId(bioform
                ? "frontier:first-bioform-recovery" : "frontier:first-body-recovery"), 91L);
        // west-0 is still cocoon-retained; the awake scout exercises lawful ambient admission.
        var level = helper.getLevel(); var actor = new SubjectId(bioform ? "bioform:west-1" : "resident:1-1");
        final java.nio.file.Path storePath;
        try { storePath = java.nio.file.Files.createTempDirectory("pm-first-body-recovery-"); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        var store = new FrontierFileStore(storePath, FrontierWorldRuntimeDefinition.payloadCodecs());
        var runtime = FrontierV3ServerRuntime.start(config, store, 10000);
        var ledger = FrontierV3AmbientCarrierLedger.get(level, config.worldId());
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, config.initialState(), store.recover(config.worldId()),
                () -> ledger.persist(level, config.worldId()));
        var lease = AmbientActorProcess.nextLease(state(runtime), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "first-recovery-prepare", actor.value(), new AmbientLeasePrepared(lease));
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, feet);
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "first body must be created once");
        helper.runAfterDelay(1L, () -> {
            var original = (net.minecraft.world.entity.Mob) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), actor));
            helper.assertTrue(original != null, "first entity must be indexed before component recovery");
            helper.assertTrue(bioform ? original instanceof Zombie : original instanceof Villager,
                    "first admission must preserve the explicitly declared actor kind");
            original.setHealth(7.0F);
            var pose = original.position(); var id = original.getUUID();
            var nbt = original.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            var pending = ledger.firstAdmission(actor).orElseThrow();
            helper.assertValueEqual(pending.phase(), FrontierV3ActorFirstAdmission.Phase.PENDING, "fixture retains missing save acknowledgement");
            // Component recovery: reload real canonical WAL/SavedData and serialized entity NBT.
            // This does not claim process termination or an entity-region crash window.
            FrontierV3AmbientActorExecutor.forget(runtime);
            var recovered = FrontierV3ServerRuntime.start(config,
                    new FrontierFileStore(storePath, FrontierWorldRuntimeDefinition.payloadCodecs()), 10000);
            helper.assertValueEqual(recovered.status().kind(), FrontierV3RuntimeStatus.Kind.ACTIVE, "canonical WAL recovery must succeed");
            final FrontierV3AmbientCarrierLedger restored;
            try {
                var file = FrontierV3AmbientCarrierLedger.storageFile(level, config.worldId());
                restored = FrontierV3AmbientCarrierLedger.load(net.minecraft.nbt.NbtIo.readCompressed(file,
                        net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("data"), level.registryAccess());
                var name = file.getFileName().toString();
                level.getDataStorage().set(name.substring(0, name.length() - ".dat".length()), restored);
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            original.discard();
            helper.runAfterDelay(1L, () -> {
                helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, recovered, state(recovered), actor, bodyAt(feet)),
                        FrontierV3AmbientActorExecutor.Result.CONFLICT, "loaded absence cannot duplicate a pending first body");
                net.minecraft.world.entity.Mob returned = bioform
                        ? new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level)
                        : new Villager(net.minecraft.world.entity.EntityType.VILLAGER, level);
                returned.load(nbt);
                helper.assertTrue(FrontierV3ActorHandoffRecovery.retainsRecordedBody(level, state(recovered), returned),
                        "late exact first body must retain its pending evidence before indexing");
                publishProjectionBeforeManagedJoin(helper, level, recovered);
                helper.assertTrue(level.addFreshEntity(returned), "serialized first body must rejoin");
                helper.runAfterDelay(1L, () -> {
                    helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, recovered, state(recovered), actor, bodyAt(feet)),
                            FrontierV3AmbientActorExecutor.Result.CURRENT, "recovered first body must be reused");
                    helper.assertTrue(level.getEntity(id) == returned, "exact rejoined body stays indexed");
                    helper.assertValueEqual(returned.position(), pose, "recovery must not teleport");
                    helper.assertValueEqual(returned.getHealth(), 7.0F, "recovery must not heal");
                    helper.assertValueEqual(restored.firstAdmission(actor).orElseThrow(), pending, "recognition is not a save acknowledgement");
                    returned.discard(); FrontierV3AmbientActorExecutor.forget(recovered); helper.succeed();
                });
            });
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void oldAmbientBodyReplaysExactRecordedSceneWithoutPromotingIt(GameTestHelper helper) {
        var config = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(new WorldId("frontier:scene-handoff-replay"), 41L);
        var initial = config.initialState(); var operation = initial.operations().values().iterator().next();
        var travel = operation.activeTravel().orElseThrow();
        var lease = io.farfrontier.palemirror.frontier.v3.model.SceneLease.atExactPositions(
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:scene-handoff-replay"), initial.bootstrap().worldId(),
                operation.id(), operation.cargoId(), operation.currentPosition(), travel.cargoAnchor().surface().support(),
                config.initialInstant(), 17L, io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.PREPARED, java.util.Optional.empty(),
                operation.participantIds().stream().map(actor -> new io.farfrontier.palemirror.frontier.v3.model.SceneMember(actor,
                        FrontierV3AmbientActorExecutor.entityId(initial, actor))).toList(), travel.formation());
        var unknown = initial.prepareSceneLease(lease)
                .transitionSceneLease(lease.id(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.HOT)
                .transitionSceneLease(lease.id(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        var member = lease.members().getFirst(); var level = helper.getLevel();
        var target = FrontierV3AmbientActorExecutor.carrierDeclaration(unknown, member.actorId(),
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), 4L);
        var source = target.liveBody(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, 3L, 4L);
        var ledger = FrontierV3AmbientCarrierLedger.get(level, initial.bootstrap().worldId());
        helper.assertTrue(ledger.prepareHandoff(FrontierV3ActorOwnerBinding.ambient(source),
                FrontierV3ActorOwnerBinding.scene(target, lease.id())), "fixture must retain the exact pending transfer");
        ledger.persist(level, initial.bootstrap().worldId());
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, feet);
        var body = FrontierV3ActorCarrierFactory.create(FrontierV3ActorCarrierComposition.InventoryEntry.AMBIENT_BODY, level, source,
                unknown.actorLocations().get(member.actorId()).condition());
        body.setPos(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D); body.setNoAi(true); body.setHealth(7.0F);
        body.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, member.actorId().value());
        body.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, source.kind().name());
        body.getPersistentData().putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, source.epoch());
        var firewallRuntime = FrontierV3ServerRuntime.start(config, new EphemeralStore(), 10_000);
        var blockedProof = FrontierV3ServerLifecycle.observeSourceJoin(firewallRuntime, body);
        helper.assertFalse(SourceGrayboxEntityAdmission.rejectsSourceMob(blockedProof, true, false),
                "recorded old body must survive source firewall even without admissible target");
        helper.assertTrue(FrontierV3ActorCarrierComposition.owns(body, source), "retention must not invent a current owner");
        helper.assertTrue(ledger.pendingHandoff(member.actorId()).isPresent(), "retention must not acknowledge transfer");
        body.getPersistentData().putLong(FrontierV3ActorCarrierComposition.REVISION_KEY, 999L);
        helper.assertTrue(SourceGrayboxEntityAdmission.rejectsSourceMob(
                FrontierV3ServerLifecycle.observeSourceJoin(firewallRuntime, body), true, false),
                "unrecorded tuple must still be rejected at the same firewall");
        FrontierV3ActorCarrierComposition.stamp(body, source);
        helper.assertTrue(level.addFreshEntity(body), "old physical image must be indexed");
        helper.runAfterDelay(1L, () -> {
            var pose = body.position();
            helper.assertFalse(FrontierV3ActorHandoffRecovery.resume(level, initial, body), "missing canonical target cannot borrow journal authority");
            helper.assertTrue(FrontierV3ActorHandoffRecovery.resume(level, unknown, body), "exact scene transfer must replay");
            helper.assertTrue(FrontierV3SceneExecutor.owned(body, unknown, unknown.sceneLeases().get(lease.id()), member),
                    "ordinary scene recognizer must accept recovered body");
            helper.assertValueEqual(unknown.sceneLeases().get(lease.id()).status(),
                    io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.UNKNOWN_AFTER_RESTART, "replay must not promote canonical scene");
            helper.assertFalse(FrontierV3ActorHandoffRecovery.resume(level, unknown, body), "current body is a no-op");
            helper.assertTrue(level.getEntity(member.entityId()) == body, "no replacement entity");
            helper.assertValueEqual(body.position(), pose, "no recovery teleport");
            helper.assertValueEqual(body.getHealth(), 7.0F, "no recovery healing");
            helper.assertTrue(ledger.pendingHandoff(member.actorId()).isPresent(), "retag is not a save acknowledgement");
            var duplicate = FrontierV3ActorCarrierFactory.create(FrontierV3ActorCarrierComposition.InventoryEntry.AMBIENT_BODY, level, source,
                    unknown.actorLocations().get(member.actorId()).condition());
            helper.assertFalse(FrontierV3ActorHandoffRecovery.retainsRecordedBody(level, unknown, duplicate),
                    "an indexed body excludes another Java object with the same recorded UUID");
            FrontierV3AmbientActorExecutor.forget(firewallRuntime);
            body.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void closedHarvestReturnsThroughOrdinaryAmbientMaterialization(GameTestHelper helper) {
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:closed-harvest-ambient-composition"), 91L), new EphemeralStore(), 10_000);
        var initial = state(runtime); var actor = new SubjectId("resident:1-1");
        var member = new io.farfrontier.palemirror.frontier.v3.model.SceneMember(actor,
                FrontierV3AmbientActorExecutor.entityId(initial, actor));
        var canonicalBody = initial.actorLocations().get(actor).body();
        // Component precondition: canonical harvest is already closed. The test must
        // execute real ambient materialization, never stamp its target owner itself.
        var closed = io.farfrontier.palemirror.frontier.v3.model.SceneLease.forCause(
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:harvest-return-composition"),
                initial.bootstrap().worldId(), new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause(
                        new SubjectId("site:1-wheat-field"), new SubjectId("job:site-harvest-composition")), canonicalBody.supportingSurface().support(),
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 17L,
                io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED,
                List.of(member), java.util.Map.of(actor, canonicalBody), java.util.Set.of(), java.util.Optional.empty());
        var ambient = new AmbientActorLease(actor, canonicalBody,
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 3L,
                AmbientLeaseStatus.PREPARED, AmbientGoalKind.WORK, canonicalBody);
        var prepared = initial.withChanges(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin()
                .sceneLeases(java.util.Map.of(closed.id(), closed)).ambientLeases(java.util.Map.of(actor, ambient)));
        var level = helper.getLevel();
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, feet);
        var body = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("missing fixture body");
        body.setUUID(member.entityId()); body.setNoAi(true); body.setPersistenceRequired(); body.setHealth(7.0F);
        body.setPos(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        FrontierV3ActorCarrierComposition.stamp(body, FrontierV3AmbientActorExecutor.carrierDeclaration(prepared, actor,
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, closed.revision(), 4L));
        var tag = body.getPersistentData(); tag.putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, 4L);
        tag.putString(FrontierV3SceneExecutor.LEASE_KEY, closed.id().value());
        tag.putString(FrontierV3SceneExecutor.ACTOR_KEY, actor.value());
        tag.putLong(FrontierV3SceneExecutor.REVISION_KEY, closed.revision());
        helper.assertTrue(level.addFreshEntity(body), "closed body must enter the isolated cell");
        helper.runAfterDelay(1L, () -> {
            var before = body.position();
            helper.assertValueEqual(FrontierV3SceneExecutor.inspectClosedHarvestReturn(level, prepared, actor),
                    FrontierV3SceneExecutor.ClosedSceneReturnRecovery.LIVE_BODY, "closed owner still requires its real handoff");
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, prepared, actor, canonicalBody),
                    FrontierV3AmbientActorExecutor.Result.CURRENT, "ordinary ambient path must adopt the retained farmer");
            helper.assertTrue(level.getEntity(member.entityId()) == body, "handoff must retain the indexed Java entity");
            helper.assertValueEqual(body.position(), before, "canonical admission anchor must not teleport the physical body");
            helper.assertValueEqual(body.getHealth(), 7.0F, "handoff preserves physical health");
            helper.assertTrue(FrontierV3AmbientCarrierRecognition.recoverableOwnership(prepared,
                    FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(body),
                    FrontierV3AmbientCarrierLedger.get(level, prepared.bootstrap().worldId())), "returned farmer must be recognized by the next owner");
            helper.assertValueEqual(FrontierV3SceneExecutor.inspectClosedHarvestReturn(level, prepared, actor),
                    FrontierV3SceneExecutor.ClosedSceneReturnRecovery.NOT_RETAINED, "historical harvest must not suppress the new owner's execution");
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, prepared, actor, canonicalBody),
                    FrontierV3AmbientActorExecutor.Result.CURRENT, "repeated ordinary admission must be idempotent");
            body.getPersistentData().putLong(FrontierV3ActorCarrierComposition.REVISION_KEY, ambient.revision() + 1L);
            helper.assertValueEqual(FrontierV3SceneExecutor.inspectClosedHarvestReturn(level, prepared, actor),
                    FrontierV3SceneExecutor.ClosedSceneReturnRecovery.CONFLICT, "same UUID with a foreign owner revision is not a lawful return");
            helper.assertFalse(FrontierV3ActorHandoffRecovery.resumeAmbient(level, prepared, body),
                    "a tuple not retained by the transfer cannot be repaired heuristically");
            // Model an older entity-region image with a newer durable transfer receipt.
            // This is a recovery component fixture, not a simulated process-crash claim.
            FrontierV3ActorCarrierComposition.stamp(body, FrontierV3AmbientActorExecutor.carrierDeclaration(prepared, actor,
                    FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                    FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, closed.revision(), 4L));
            tag.putString(FrontierV3SceneExecutor.LEASE_KEY, closed.id().value());
            tag.putString(FrontierV3SceneExecutor.ACTOR_KEY, actor.value());
            tag.putLong(FrontierV3SceneExecutor.REVISION_KEY, closed.revision());
            tag.remove(FrontierV3AmbientActorExecutor.ACTOR_KEY); tag.remove(FrontierV3AmbientActorExecutor.KIND_KEY);
            var recovered = prepared.withChanges(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin()
                    .sceneLeases(java.util.Map.of()).ambientLeases(java.util.Map.of(actor,
                            ambient.withStatus(AmbientLeaseStatus.UNKNOWN_AFTER_RESTART))));
            helper.assertTrue(FrontierV3ActorHandoffRecovery.resumeAmbient(level, recovered, body),
                    "durable transfer must recover old saved body even after closed-scene history compaction");
            helper.assertTrue(FrontierV3AmbientCarrierRecognition.recoverableOwnership(recovered,
                    FrontierV3AmbientCarrierRecognition.ManagedCarrier.from(body),
                    FrontierV3AmbientCarrierLedger.get(level, recovered.bootstrap().worldId())), "exact unknown owner must recognize recovered body");
            helper.assertFalse(FrontierV3ActorHandoffRecovery.resumeAmbient(level, recovered, body),
                    "already recovered body must not replay or persist on every ordinary tick");
            helper.assertValueEqual(body.position(), before, "recovery preserves position");
            helper.assertValueEqual(body.getHealth(), 7.0F, "recovery preserves health");
            body.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void closedSceneBodyRequiresBothExactRevisionDeclarations(GameTestHelper helper) {
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:closed-scene-revision-test"), 91L), new EphemeralStore(), 10_000);
        var state = state(runtime); var actor = new SubjectId("resident:1-1");
        var member = new io.farfrontier.palemirror.frontier.v3.model.SceneMember(actor,
                FrontierV3AmbientActorExecutor.entityId(state, actor));
        var position = bodyAt(helper.absolutePos(new BlockPos(0, 1, 0)));
        var lease = io.farfrontier.palemirror.frontier.v3.model.SceneLease.forCause(
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:closed-revision-test"),
                state.bootstrap().worldId(), new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause(
                        new SubjectId("site:1-wheat-field"), new SubjectId("job:site-harvest-test")), position.supportingSurface().support(),
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 17L,
                io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED,
                List.of(member), java.util.Map.of(actor, position), java.util.Set.of(), java.util.Optional.empty());
        var body = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        if (body == null) throw new IllegalStateException("missing fixture body");
        body.setUUID(member.entityId());
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor,
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 17L, 4L);
        FrontierV3ActorCarrierComposition.stamp(body, declaration);
        var tag = body.getPersistentData();
        tag.putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, 4L);
        tag.putString(FrontierV3SceneExecutor.LEASE_KEY, lease.id().value());
        tag.putString(FrontierV3SceneExecutor.ACTOR_KEY, actor.value());
        tag.putLong(FrontierV3SceneExecutor.REVISION_KEY, 17L);
        helper.assertTrue(FrontierV3SceneExecutor.ownedByClosedLease(body, state, lease, member), "exact closed body remains recognizable");
        tag.putLong(FrontierV3SceneExecutor.REVISION_KEY, 16L);
        helper.assertFalse(FrontierV3SceneExecutor.ownedByClosedLease(body, state, lease, member), "old scene metadata cannot authorize transfer or deletion");
        tag.putLong(FrontierV3SceneExecutor.REVISION_KEY, 17L);
        FrontierV3ActorCarrierComposition.stamp(body, declaration.liveBody(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, 16L, 4L));
        helper.assertFalse(FrontierV3SceneExecutor.ownedByClosedLease(body, state, lease, member), "old common declaration cannot authorize transfer or deletion");
        FrontierV3ActorCarrierComposition.stamp(body, declaration);
        helper.assertFalse(FrontierV3SceneExecutor.ownedByClosedLease(body, state,
                lease.withStatus(io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.HOT), member), "live lease is not a historical owner");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void liveHandoffPersistsBeforeStampAndDoesNotMoveOrReplaceBody(GameTestHelper helper) {
        var level = helper.getLevel(); var world = new WorldId("frontier:live-handoff-game-test");
        var actor = new SubjectId("resident:1-1");
        var position = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, position);
        var body = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("missing fixture body");
        body.setUUID(io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(world, actor));
        body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        body.setNoAi(true); body.setHealth(7.0F);
        var source = new FrontierV3ActorCarrierComposition.Declaration(actor,
                    FrontierV3ActorCarrierComposition.ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE,
                    body.getUUID(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 17L, 4L);
        FrontierV3ActorCarrierComposition.stamp(body, source);
        body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, "lease:handoff-source");
        body.getPersistentData().putLong(FrontierV3SceneExecutor.REVISION_KEY, source.authorityRevision());
        var sourceBinding = FrontierV3ActorOwnerBinding.scene(source,
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:handoff-source"));
        var ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        helper.assertTrue(ledger.fence(source.liveBody(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, 16L, 3L)
                .inactiveCarrier(), 16L, 2L), "fixture retains exact inactive predecessor");
        helper.assertTrue(FrontierV3ActorAdoptionAdmission.admit(ledger, sourceBinding, () -> ledger.persist(level, world), () -> {
            try {
                var saved = net.minecraft.nbt.NbtIo.readCompressed(FrontierV3AmbientCarrierLedger.storageFile(level, world),
                        net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                var receipt = FrontierV3AmbientCarrierLedger.load(saved.getCompound("data"), level.registryAccess())
                        .pendingAdoption(actor).orElseThrow();
                helper.assertValueEqual(receipt.admittedBinding(), sourceBinding, "exact scene adoption must precede insertion");
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            return level.addFreshEntity(body);
        }), "physical fixture must use the ordinary durable admission boundary");
        helper.runAfterDelay(1L, () -> {
            var target = source.liveBody(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, 3L, 4L);
            var evidenceState = io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState.initial(
                    io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper.create(world, 91L));
            helper.assertTrue(FrontierV3ActorHandoffRecovery.retainsRecordedBody(level, evidenceState, body),
                    "pending adoption must retain exact body even without a current canonical scene");
            body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, "lease:foreign");
            helper.assertFalse(FrontierV3ActorHandoffRecovery.retainsRecordedBody(level, evidenceState, body),
                    "another scene at the same UUID/revision has no retained adoption evidence");
            body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, "lease:handoff-source");
            var pose = body.position(); var uuid = body.getUUID();
            helper.assertTrue(FrontierV3ActorHandoffAdmission.transfer(ledger, body, FrontierV3ActorOwnerBinding.ambient(target), () -> {
                ledger.persist(level, world);
                try {
                    var saved = net.minecraft.nbt.NbtIo.readCompressed(FrontierV3AmbientCarrierLedger.storageFile(level, world),
                            net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    var receipt = FrontierV3AmbientCarrierLedger.load(saved.getCompound("data"), level.registryAccess())
                            .pendingHandoff(actor).orElseThrow();
                    helper.assertValueEqual(receipt.current(), target, "target must be on disk before metadata mutation");
                    helper.assertTrue(FrontierV3ActorCarrierComposition.owns(body, source), "source stamp still present before effect");
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }), "same body must transfer to exact ambient target");
            helper.assertTrue(FrontierV3AmbientActorExecutor.owned(body, actor, false), "ambient recognizer must accept the new owner");
            helper.assertFalse(body.getPersistentData().contains(FrontierV3SceneExecutor.LEASE_KEY)
                    || body.getPersistentData().contains(FrontierV3SceneExecutor.ACTOR_KEY), "ambient owner removes all old scene tags");
            var next = source.liveBody(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, 18L, 4L);
            var nextBinding = FrontierV3ActorOwnerBinding.scene(next, new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:handoff-next"));
            helper.assertTrue(FrontierV3ActorHandoffAdmission.transfer(level, world, body, nextBinding), "same body must transfer again before save");
            helper.assertTrue(FrontierV3ActorOwnerBinding.from(body).orElseThrow().equals(nextBinding)
                    && !body.getPersistentData().contains(FrontierV3AmbientActorExecutor.ACTOR_KEY)
                    && !body.getPersistentData().contains(FrontierV3AmbientActorExecutor.KIND_KEY), "scene owner installs exact binding without ambient residue");
            // Component recovery fixture: present the earliest possibly serialized tuple.
            FrontierV3ActorCarrierComposition.stamp(body, source);
            body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, "lease:handoff-source");
            body.getPersistentData().putLong(FrontierV3SceneExecutor.REVISION_KEY, source.authorityRevision());
            helper.assertTrue(FrontierV3ActorHandoffAdmission.transfer(level, world, body, nextBinding), "old declaration must resume exact durable target");
            helper.assertTrue(FrontierV3ActorCarrierComposition.owns(body, next), "latest target must be restored");
            helper.assertValueEqual(body.position(), pose, "handoff must not teleport");
            helper.assertValueEqual(body.getUUID(), uuid, "handoff must not replace identity");
            helper.assertValueEqual(body.getHealth(), 7.0F, "handoff must preserve observed health");
            helper.assertValueEqual(FrontierV3AmbientCarrierLedger.get(level, world).pendingHandoff(actor).orElseThrow()
                    .declarations().size(), 3, "retry must not append duplicate history");
            body.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void directPreparedCancellationCannotBypassMissingHistory(GameTestHelper helper) {
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:prepared-cancel-missing-history"), 91L), new EphemeralStore(), 10_000);
        SubjectId actor = new SubjectId("resident:1-1");
        var first = AmbientActorProcess.nextLease(state(runtime), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "missing-history-first", actor.value(), new AmbientLeasePrepared(first));
        FrontierV3CommandSubmission.submit(runtime, "missing-history-drain", actor.value(), new AmbientLeaseTransition(actor, AmbientLeaseStatus.DRAINING));
        FrontierV3CommandSubmission.submit(runtime, "missing-history-close", actor.value(),
                new io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseReleased(actor, first.handoffBody(), state(runtime).actorLocations().get(actor).condition().health()));
        var next = AmbientActorProcess.nextLease(state(runtime), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "missing-history-next", actor.value(), new AmbientLeasePrepared(next));
        var before = runtime.checkpointImage().orElseThrow();
        helper.assertTrue(next.revision() > 1L, "fixture must be a historical generation");
        helper.assertTrue(FrontierV3AmbientActorExecutor.abandonPreparedForReservation(helper.getLevel(), runtime,
                state(runtime), actor, next).isEmpty(), "direct reserved caller must not bypass custody evidence");
        helper.assertValueEqual(runtime.checkpointImage().orElseThrow(), before, "rejection must leave canonical head unchanged");
        FrontierV3AmbientActorExecutor.forget(runtime);
        helper.succeed();
    }

    private static void verifyReservationRelease(GameTestHelper helper, boolean interrupted, boolean prepared) {
        verifyReservationRelease(helper, interrupted, prepared, false);
    }
    private static void verifyReservationRelease(GameTestHelper helper, boolean interrupted, boolean prepared, boolean restart) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, origin);
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(
                new WorldId(restart ? "frontier:restart-fenced-carrier-game-test" : prepared ? "frontier:prepared-carrier-game-test" : interrupted ? "frontier:interrupted-carrier-game-test" :
                        "frontier:reservation-carrier-game-test"), 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        var lease = AmbientActorProcess.nextLease(state(runtime), resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "reservation-carrier-prepare", resident.value(), new AmbientLeasePrepared(lease));
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident, bodyAt(origin)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "the test must create its actual managed body");
        helper.runAfterDelay(1L, () -> {
            var body = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
            helper.assertTrue(body != null, "the body must be indexed before release");
            if (!prepared) FrontierV3CommandSubmission.submit(runtime, "reservation-carrier-hot", resident.value(),
                    new AmbientLeaseTransition(resident, AmbientLeaseStatus.HOT));
            // GameTest templates live millions of blocks outside the canonical world.
            // This component fixture supplies the retained in-world observation; it makes
            // no navigation or player-ingress claim.
            body.setPos(lease.handoffBody().x() + 0.5D, lease.handoffBody().y(), lease.handoffBody().z() + 0.5D);
            body.setHealth(7.0F);
            if (restart) {
                FrontierV3CommandSubmission.submit(runtime, "restart-unknown", resident.value(),
                        new AmbientLeaseTransition(resident, AmbientLeaseStatus.UNKNOWN_AFTER_RESTART));
                var before = runtime.checkpointImage().orElseThrow();
                helper.assertTrue(FrontierV3AmbientActorExecutor.drainForAdmission(runtime, body).isEmpty(),
                        "a returned body without retained release intent must not be released as a recovery guess");
                helper.assertValueEqual(runtime.checkpointImage().orElseThrow(), before, "missing fence must not mutate canonical state");
            }
            if (interrupted || restart) {
                var retained = FrontierV3AmbientCarrierLedger.get(level, state(runtime).bootstrap().worldId());
                var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state(runtime), resident,
                        FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, body.getUUID(),
                        FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, lease.revision(),
                        body.getPersistentData().getLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY));
                helper.assertTrue(retained.fence(declaration, lease.revision(), lease.revision()), "retain the exact first half of release");
                if (interrupted) FrontierV3CommandSubmission.submit(runtime, "interrupted-draining", resident.value(),
                        new AmbientLeaseTransition(resident, AmbientLeaseStatus.DRAINING));
                if (restart) {
                    retained.persist(level, state(runtime).bootstrap().worldId());
                    long epoch = declaration.epoch();
                    body.getPersistentData().putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, epoch + 1L);
                    body.getPersistentData().putLong(FrontierV3ActorCarrierComposition.EPOCH_KEY, epoch + 1L);
                    var before = runtime.checkpointImage().orElseThrow();
                    helper.assertTrue(FrontierV3AmbientActorExecutor.drainForAdmission(runtime, body).isEmpty(),
                            "a different physical generation must not consume a retained release");
                    helper.assertValueEqual(runtime.checkpointImage().orElseThrow(), before, "wrong generation must not mutate canonical state");
                    body.getPersistentData().putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, epoch);
                    body.getPersistentData().putLong(FrontierV3ActorCarrierComposition.EPOCH_KEY, epoch);
                }
            }
            if (prepared) {
                helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.PREPARED,
                        "test must exercise a physical body before HOT confirmation");
                // This component fixture's move to canonical coordinates exits the
                // loaded GameTest section. Vanilla removes it from the UUID index;
                // test the shared body-release boundary, not an indexed lookup we
                // intentionally invalidated. The direct absence caller is tested separately.
                helper.assertTrue(level.getEntity(body.getUUID()) == null, "fixture move must be classified as outside loaded index");
            }
            helper.assertTrue(FrontierV3AmbientActorExecutor.drainForAdmission(runtime, body).isPresent(),
                    "reservation release must use the common physical release");
            helper.assertValueEqual(state(runtime).actorLocations().get(resident).condition().health(),
                    io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(7L), "release must retain observed damage");
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state(runtime).bootstrap().worldId());
            helper.assertTrue(ledger.hasCarrier(resident), "release must not discard the only reconstruction evidence");
            try {
                var saved = net.minecraft.nbt.NbtIo.readCompressed(FrontierV3AmbientCarrierLedger.storageFile(level,
                        state(runtime).bootstrap().worldId()), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                helper.assertTrue(FrontierV3AmbientCarrierLedger.load(saved.getCompound("data"), level.registryAccess())
                        .hasCarrier(resident), "release must persist the carrier before returning, not wait for shutdown");
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            helper.assertTrue(body.isRemoved(), "the inactive carrier must replace the physical body");
            var next = AmbientActorProcess.nextLease(state(runtime), resident, runtime.checkpointImage().orElseThrow().instant());
            FrontierV3CommandSubmission.submit(runtime, "reservation-carrier-next", resident.value(), new AmbientLeasePrepared(next));
            helper.runAfterDelay(1L, () -> {
                try {
                    helper.assertTrue(FrontierV3AmbientActorExecutor.abandonUndemandedPrepared(level, runtime, state(runtime), resident, next),
                            "an unneeded next admission must not indefinitely reserve this COLD actor");
                    helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.CLOSED,
                            "the next canonical owner must be able to acquire the worker");
                    helper.assertTrue(ledger.hasCarrier(resident), "cancelling preparation must preserve the inactive carrier");
                    helper.succeed();
                } finally { FrontierV3AmbientActorExecutor.forget(runtime); }
            });
        });
    }


    static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
    }
    /** Mirrors the registered production order: projection publishes before ambient Entity admission. */
    static void publishProjectionBeforeManagedJoin(GameTestHelper helper, ServerLevel level,
                                                           FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3AftermathOwnerComposition.projection(level, runtime);
        helper.assertTrue(FrontierV3GrayboxExecutor.admissionProvider(runtime, runtime.decodedState().orElseThrow()).isPresent(),
                "the projection owner must publish provider truth before a restored ambient Entity joins");
    }
    static BodyPosition bodyAt(BlockPos feet) { return new BodyPosition(feet.getX(), feet.getY(), feet.getZ()); }
    static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }
    static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }
}
