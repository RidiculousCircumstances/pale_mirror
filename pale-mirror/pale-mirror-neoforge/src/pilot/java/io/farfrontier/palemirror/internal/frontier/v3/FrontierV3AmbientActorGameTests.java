package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.world.SourceGrayboxEntityAdmission;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientBodyConfirmed;
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

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void finalAmbientUnloadRetainsDamageAndWaitsForSavedAbsence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SubjectId resident = new SubjectId("resident:1-1");
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, origin);
        var config = configurationAt(helper, new WorldId("frontier:ambient-unload-game-test"), 91L, resident);
        var store = new EphemeralStore();
        var runtime = FrontierV3ServerRuntime.start(config, store, 10_000);
        initializeAdmission(level, config, store);
        var lease = AmbientActorProcess.nextLease(state(runtime), resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "ambient-unload-prepare", resident.value(), new AmbientLeasePrepared(lease));
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident, bodyAt(origin)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "actual managed fixture body must enter Minecraft");
        helper.runAfterDelay(1L, () -> {
            var body = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
            helper.assertTrue(body != null, "body must be indexed");
            FrontierV3ActorBodyController.confirmPresent(level, runtime, body);
            FrontierV3CommandSubmission.submit(runtime, "ambient-unload-hot", resident.value(),
                    new AmbientBodyConfirmed(resident, lease.revision(), AmbientBodyConfirmed.Boundary.ADMISSION, lease.handoffBody(), lease.handoffBody(), io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state(runtime), resident)));
            body.setHealth(9.0F);
            helper.assertTrue(FrontierV3ActorBodyController.inspectCurrent(level, runtime, body),
                    "the common indexed observer must retain damage before departure");
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state(runtime).bootstrap().worldId());
            helper.assertFalse(FrontierV3ActorBodyController.observeLeave(level, runtime, body),
                    "a live/tracking-end observation must not become final departure evidence");
            helper.assertFalse(FrontierV3AmbientActorExecutor.releaseUnloadedReservedColdContinuation(level, runtime,
                    state(runtime), resident, state(runtime).ambientLeases().get(resident)), "missing witness must not release a physical owner");
            body.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(FrontierV3ActorBodyController.observeLeave(level, runtime, body),
                    "actual final removal reason and exact stamped authority must admit the observation");
            helper.assertValueEqual(ledger.bodyDeparture(resident).orElseThrow().observed().health(),
                    io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(9L), "final damage must be retained");
            helper.assertFalse(ledger.hasCarrier(resident), "observation itself must not transfer custody");
            helper.runAfterDelay(1L, () -> {
                try {
                    helper.assertFalse(FrontierV3AmbientActorExecutor.releaseUnloadedReservedColdContinuation(level, runtime,
                            state(runtime), resident, state(runtime).ambientLeases().get(resident)),
                            "removal callback without entity write and storage sync cannot grant COLD");
                    helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                            "unsaved departure must retain the unresolved scope");
                    helper.assertValueEqual(state(runtime).actorLocations().get(resident).condition().health(),
                            io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(9L), "release must not restore old health");
                    helper.assertFalse(ledger.hasCarrier(resident), "an unsaved callback is not reconstruction permission");
                    helper.assertTrue(ledger.bodyDeparture(resident).isPresent(), "the exact departure evidence must be retained");
                    helper.assertTrue(io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.retainsPhysicalCustody(state(runtime), resident),
                            "physical ambiguity cannot permit competing COLD progress");
                    helper.succeed();
                } finally { FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); }
            });
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void reservationReleaseKeepsLoadedBodyAndObservedHealth(GameTestHelper helper) {
        verifyReservationRelease(helper, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void interruptedScopeClosureResumesWithoutBodyTransfer(GameTestHelper helper) {
        verifyReservationRelease(helper, true);
    }

    private static void verifyReservationRelease(GameTestHelper helper, boolean interrupted) {
        verifyReservationRelease(helper, interrupted, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void preparedScopeClosureKeepsConfirmedBody(GameTestHelper helper) {
        verifyReservationRelease(helper, false, true);
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void restartUnknownScopeClosesOnlyExactLoadedBody(GameTestHelper helper) {
        verifyReservationRelease(helper, false, false, true);
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void cancelledNeverCreatedLeaseStillAdmitsOneBodyOnNextRevision(GameTestHelper helper) {
        var config = configurationAt(helper, new WorldId("frontier:first-cancel-retry"), 91L, new SubjectId("resident:1-1"));
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

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void scheduledResidentBirthAdmitsItsOwnFirstMinecraftBody(GameTestHelper helper) {
        var world = new WorldId("frontier:scheduled-resident-first-body");
        var base = configurationAt(helper, world, 91L, new SubjectId("resident:1-1"));
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

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void scheduledHiveBirthAdmitsItsOwnFirstMinecraftBody(GameTestHelper helper) {
        var world = new WorldId("frontier:scheduled-bioform-first-body");
        var base = configurationAt(helper, world, 93L, new SubjectId("bioform:west-1"));
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

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void pendingFirstBodyRejoinsRecoveredRuntimeWithoutAnotherCreation(GameTestHelper helper) {
        verifyPendingFirstBodyRecovery(helper, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void proofBackedFirstResidentAdmissionCreatesOnlyItsOriginalUuid(GameTestHelper helper) {
        var world = new WorldId("frontier:proof-backed-first-resident-native");
        var config = configurationAt(helper, world, 91L, new SubjectId("resident:1-1"));
        var store = new EphemeralStore();
        var runtime = FrontierV3ServerRuntime.start(config, store, 10_000);
        var level = helper.getLevel();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, config.initialState(), store.recover(world),
                () -> ledger.persist(level, world));
        var actor = new SubjectId("resident:1-1");
        var lease = AmbientActorProcess.nextLease(state(runtime), actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "proof-backed-prepare", actor.value(), new AmbientLeasePrepared(lease));
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state(runtime), actor, FrontierV3AmbientActorExecutor.entityId(state(runtime), actor), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L);
        var binding = FrontierV3ActorOwnerBinding.body(declaration);
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

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void pendingFirstBioformRejoinsRecoveredRuntimeWithoutAnotherCreation(GameTestHelper helper) {
        verifyPendingFirstBodyRecovery(helper, true);
    }

    private static void verifyPendingFirstBodyRecovery(GameTestHelper helper, boolean bioform) {
        // west-0 is still cocoon-retained; the awake scout exercises lawful ambient admission.
        var level = helper.getLevel(); var actor = new SubjectId(bioform ? "bioform:west-1" : "resident:1-1");
        var feet = helper.absolutePos(new BlockPos(0, 1, 0));
        var config = configurationAt(helper, new WorldId(bioform
                ? "frontier:first-bioform-recovery" : "frontier:first-body-recovery"), 91L, actor);
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
        prepareFloor(level, feet);
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), actor, bodyAt(feet)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "first body must be created once");
        helper.runAfterDelay(1L, () -> {
            var firstBody = (net.minecraft.world.entity.Mob) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), actor));
            helper.assertTrue(firstBody != null, "first entity must be indexed before component recovery");
            helper.assertTrue(bioform ? firstBody instanceof Zombie : firstBody instanceof Villager,
                    "first admission must preserve the explicitly declared actor kind");
            firstBody.setHealth(7.0F);
            var pose = firstBody.position(); var id = firstBody.getUUID();
            var nbt = firstBody.saveWithoutId(new net.minecraft.nbt.CompoundTag());
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
            firstBody.discard();
            helper.runAfterDelay(1L, () -> {
                helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, recovered, state(recovered), actor, bodyAt(feet)),
                        FrontierV3AmbientActorExecutor.Result.CONFLICT, "loaded absence cannot duplicate a pending first body");
                net.minecraft.world.entity.Mob returned = bioform
                        ? new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level)
                        : new Villager(net.minecraft.world.entity.EntityType.VILLAGER, level);
                returned.load(nbt);
                helper.assertTrue(FrontierV3ActorBodyController.retainsRecordedBody(level, state(recovered), returned),
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

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void commonBirthAndActivityReplacementKeepOneUnmodifiedPhysicalBody(GameTestHelper helper) {
        var level = helper.getLevel();
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:single-body-lifetime"), 91L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var presence = state.actorExecutions().next(actor,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, actor);
        state = io.farfrontier.palemirror.frontier.v3.model.ActorExecutionComposition.LIFECYCLE
                .prepareVacant(state, presence).commit(state, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin());
        state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.demand(state, actor);
        var bodyId = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actor);
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor,
                FrontierV3AmbientActorExecutor.entityId(state, actor),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, bodyId.physicalEpoch());
        var binding = FrontierV3ActorOwnerBinding.body(declaration);
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        helper.assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(actor, declaration.kind(), declaration.entityId()))),
                "fixture must explicitly issue first-admission permission");
        var feet = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, feet);
        var support = bodyAt(feet).supportingSurface();
        var request = new FrontierV3ActorBodyController.BirthRequest(
                FrontierV3ActorCarrierComposition.InventoryEntry.AMBIENT_BODY, binding, List.of(support),
                new FrontierV3NavigationScope.Restricted(io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope.around(
                        support.standingBody(), support.standingBody())),
                FrontierV3StandingPosition::aboveExactFloor, body -> { body.setHealth(7.0F); return true; });
        helper.assertValueEqual(FrontierV3ActorBodyController.materialize(level, state, request),
                FrontierV3ActorBodyController.Result.APPLIED, "only the common boundary creates the body");
        var body = (net.minecraft.world.entity.Mob) level.getEntity(declaration.entityId());
        helper.assertTrue(body != null, "the common body is indexed");
        state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.running(state, bodyId);
        var successor = state.actorExecutions().next(actor,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, actor);
        final var next = io.farfrontier.palemirror.frontier.v3.model.ActorExecutionComposition.LIFECYCLE
                .prepareBegin(state, successor, presence.generation())
                .commit(state, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin());
        final var baseline = state;
        var pose = body.position(); var metadata = body.getPersistentData().copy();
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, next, actor,
                        next.actorLocations().get(actor).body()), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "a changed activity adopts no new physical incarnation");
        helper.assertTrue(level.getEntity(declaration.entityId()) == body, "same Java object and UUID");
        helper.assertValueEqual(body.position(), pose, "activity replacement cannot teleport");
        helper.assertValueEqual(body.getHealth(), 7.0F, "activity replacement cannot heal");
        helper.assertValueEqual(body.getPersistentData(), metadata, "activity replacement cannot restamp body metadata");
        helper.assertFalse(metadata.contains(FrontierV3SceneExecutor.LEASE_KEY)
                || metadata.contains(FrontierV3SceneExecutor.REVISION_KEY), "birth has no scene ownership metadata");
        var duplicate = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (duplicate == null) throw new IllegalStateException("missing duplicate fixture body");
        duplicate.setUUID(declaration.entityId()); binding.stamp(duplicate);
        helper.assertFalse(FrontierV3ActorBodyController.retainsRecordedBody(level, next, duplicate),
                "pending admission cannot retain another Java object at the indexed UUID");
        helper.assertFalse(FrontierV3ActorBodyController.recognizesRecordedBody(level, next, duplicate),
                "common lifetime recognition cannot confirm another object at the same UUID");
        helper.assertTrue(FrontierV3ActorBodyController.recognizesRecordedBody(level, next, body),
                "the exact body remains recognized without an ambient or scene scope");
        body.getPersistentData().putLong(FrontierV3ActorCarrierComposition.EPOCH_KEY, declaration.epoch() + 1L);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, baseline, actor,
                        baseline.actorLocations().get(actor).body()), FrontierV3AmbientActorExecutor.Result.CONFLICT,
                "a similar physical tuple cannot acquire the canonical incarnation");
        helper.assertTrue(level.getEntity(declaration.entityId()) == body, "rejection cannot remove or replace the body");
        binding.stamp(body); body.discard(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
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
        SubjectId resident = new SubjectId("resident:1-1");
        var config = configurationAt(helper,
                new WorldId(restart ? "frontier:restart-scope-game-test" : prepared ? "frontier:prepared-scope-game-test"
                        : interrupted ? "frontier:interrupted-scope-game-test" : "frontier:reservation-scope-game-test"),
                91L, resident);
        var store = new EphemeralStore();
        var runtime = FrontierV3ServerRuntime.start(config, store, 10_000);
        initializeAdmission(level, config, store);
        var lease = AmbientActorProcess.nextLease(state(runtime), resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "reservation-carrier-prepare", resident.value(), new AmbientLeasePrepared(lease));
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident, bodyAt(origin)),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "the test must create its actual managed body");
        helper.runAfterDelay(1L, () -> {
            var body = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
            helper.assertTrue(body != null, "the body must be indexed before release");
            FrontierV3ActorBodyController.confirmPresent(level, runtime, body);
            if (!prepared) FrontierV3CommandSubmission.submit(runtime, "reservation-carrier-hot", resident.value(),
                    new AmbientBodyConfirmed(resident, lease.revision(), AmbientBodyConfirmed.Boundary.ADMISSION, lease.handoffBody(), lease.handoffBody(), io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state(runtime), resident)));
            body.setHealth(7.0F);
            var pose = body.position();
            var metadata = body.getPersistentData().copy();
            var physical = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state(runtime), resident);
            if (restart) {
                FrontierV3CommandSubmission.submit(runtime, "restart-unknown", resident.value(),
                        new AmbientLeaseTransition(resident, AmbientLeaseStatus.UNKNOWN_AFTER_RESTART));
                long epoch = physical.physicalEpoch();
                body.getPersistentData().putLong(FrontierV3ActorCarrierComposition.EPOCH_KEY, epoch + 1L);
                var before = runtime.checkpointImage().orElseThrow();
                helper.assertTrue(FrontierV3AmbientActorExecutor.drainForAdmission(runtime, body).isEmpty(),
                        "a different physical generation cannot close the unknown scope");
                helper.assertValueEqual(runtime.checkpointImage().orElseThrow(), before, "wrong generation must not mutate canonical state");
                body.getPersistentData().putLong(FrontierV3ActorCarrierComposition.EPOCH_KEY, epoch);
            }
            if (interrupted) FrontierV3CommandSubmission.submit(runtime, "interrupted-draining", resident.value(),
                    new AmbientLeaseTransition(resident, AmbientLeaseStatus.DRAINING));
            if (prepared) {
                helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.PREPARED,
                        "physical presence confirmation is independent of ambient HOT confirmation");
            }
            helper.assertTrue(FrontierV3AmbientActorExecutor.drainForAdmission(runtime, body).isPresent(),
                    "scope closure must use the common indexed observation");
            helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.CLOSED,
                    "only the activity projection closes");
            helper.assertValueEqual(state(runtime).actorLocations().get(resident).condition().health(),
                    io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(7L), "release must retain observed damage");
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state(runtime).bootstrap().worldId());
            helper.assertFalse(ledger.hasCarrier(resident), "a loaded body cannot be replaced by an inactive carrier");
            try {
                var saved = net.minecraft.nbt.NbtIo.readCompressed(FrontierV3AmbientCarrierLedger.storageFile(level,
                        state(runtime).bootstrap().worldId()), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                helper.assertTrue(FrontierV3AmbientCarrierLedger.load(saved.getCompound("data"), level.registryAccess())
                        .firstAdmission(resident).isPresent(), "the common body history must remain durable");
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            helper.assertTrue(level.getEntity(body.getUUID()) == body && !body.isRemoved(), "scope closure retains the exact loaded object");
            helper.assertValueEqual(body.position(), pose, "scope closure cannot teleport");
            helper.assertValueEqual(body.getPersistentData(), metadata, "scope closure cannot retag the body");
            helper.assertValueEqual(io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state(runtime), resident),
                    physical, "scope closure cannot allocate another body epoch");
            helper.assertTrue(io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.retainsPhysicalCustody(state(runtime), resident),
                    "closing the scope does not grant COLD while the body remains loaded");
            var next = AmbientActorProcess.nextLease(state(runtime), resident, runtime.checkpointImage().orElseThrow().instant());
            FrontierV3CommandSubmission.submit(runtime, "reservation-carrier-next", resident.value(), new AmbientLeasePrepared(next));
            helper.runAfterDelay(1L, () -> {
                try {
                    helper.assertFalse(FrontierV3AmbientActorExecutor.abandonUndemandedPrepared(level, runtime, state(runtime), resident, next),
                            "a loaded body cannot be cancelled as never inserted");
                    helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), resident, bodyAt(origin)),
                            FrontierV3AmbientActorExecutor.Result.CURRENT, "the next scope must reuse the exact same body");
                    helper.assertTrue(FrontierV3AmbientActorExecutor.drainForAdmission(runtime, body).isPresent(),
                            "the next loaded scope can close without body transfer");
                    helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.CLOSED,
                            "the next canonical owner must be able to acquire the worker");
                    helper.assertTrue(level.getEntity(body.getUUID()) == body && !body.isRemoved(), "successive scopes cannot remove the body");
                    body.discard();
                    helper.succeed();
                } finally { FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); }
            });
        });
    }

    private static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState,
            io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> configurationAt(
            GameTestHelper helper, WorldId world, long seed, SubjectId actor) {
        var original = FrontierWorldRuntimeDefinition.configuration(world, seed);
        var canonical = original.initialState().actorLocations().get(actor).body();
        var feet = helper.absolutePos(new BlockPos(0, 1, 0));
        // Translate immutable initial geometry into the randomly positioned GameTest
        // cell. Do not relocate a runtime actor or loosen production bounds.
        return FrontierWorldRuntimeDefinition.configuration(FrontierV3CargoLoadingGameTests.translatedBootstrap(
                original.initialState().bootstrap(), feet.getX() - canonical.x(), feet.getY() - canonical.y(), feet.getZ() - canonical.z()));
    }

    private static void initializeAdmission(ServerLevel level,
            io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState,
                    io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> config, FrontierStore store) {
        var ledger = FrontierV3AmbientCarrierLedger.get(level, config.worldId());
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, config.initialState(), store.recover(config.worldId()),
                () -> ledger.persist(level, config.worldId()));
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
        return runtime.decodedState().orElseThrow();
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
