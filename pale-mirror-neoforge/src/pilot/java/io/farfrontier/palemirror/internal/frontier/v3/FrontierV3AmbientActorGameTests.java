package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;

import io.farfrontier.palemirror.PaleMirrorMod;
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
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
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

/** Materialized recovery evidence for the ambient before-effect actor lease boundary. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3AmbientActorGameTests {
    private FrontierV3AmbientActorGameTests() { }

    @GameTest(batch = "pm-frontier-v3-ambient-prepared-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedAmbientLeaseSurvivesRecoveryAndMaterializesOneInertBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(12, 8, 0)); prepareFloor(level, origin);
        WorldId world = new WorldId("frontier:ambient-prepared-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState initial = state(runtime);
        AmbientActorLease lease = AmbientActorProcess.nextLease(initial, resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "ambient-prepared-game-test", resident.value(), new AmbientLeasePrepared(lease));

        helper.assertValueEqual(FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime), 0,
                "restart must retain a before-effect PREPARED lease for loaded-chunk inspection");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.PREPARED,
                "prepared recovery must not turn an unacknowledged body into UNKNOWN");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                        bodyAt(origin)), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "a retained PREPARED lease may create exactly its expected loaded-world body");
        Villager body = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
        helper.assertTrue(body != null && body.isNoAi(), "the before-HOT body must stay inert across the acknowledgement window");
        helper.assertTrue(FrontierV3AmbientActorExecutor.recognizes(runtime, body),
                "the shared graybox boundary must accept only the exact prepared V3 carrier");
        Villager forged = new Villager(net.minecraft.world.entity.EntityType.VILLAGER, level);
        forged.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, resident.value());
        forged.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        helper.assertFalse(FrontierV3AmbientActorExecutor.recognizes(runtime, forged),
                "a copied V3 tag without the canonical UUID is never an admissible carrier");
        FrontierV3CommandSubmission.submit(runtime, "ambient-prepared-game-test-hot", resident.value(),
                new AmbientLeaseTransition(resident, AmbientLeaseStatus.HOT));
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                        bodyAt(origin)), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "the durable HOT acknowledgement retains the one existing UUID rather than duplicating it");
        SubjectId carpetResident = new SubjectId("resident:1-2"); BlockPos carpet = origin.offset(4, 0, 0);
        level.setBlock(carpet.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(carpet, Blocks.RED_CARPET.defaultBlockState(), 3);
        level.setBlock(carpet.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(carpet.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), carpetResident,
                        bodyAt(carpet.above())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an owned infection carpet is a physical surface, not a reason to strand the canonical actor");
        Villager carpetBody = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), carpetResident));
        helper.assertTrue(carpetBody != null && carpetBody.getY() >= carpet.getY() + 1.0D
                        && level.getBlockState(carpet).is(Blocks.RED_CARPET),
                "the exact body must stand above the carpet without replacing it: body="
                        + (carpetBody == null ? "missing" : carpetBody.position()) + " carpet=" + carpet);
        helper.assertFalse(FrontierV3AmbientActorExecutor.observeLeave(runtime, body, false),
                "an ordinary EntityLeave callback is too late to close a serialized HOT body into COLD");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                "unexpected departure retains the exact saved UUID for later recovery instead of permitting a duplicate");
        helper.assertFalse(FrontierV3AmbientActorExecutor.observeLeave(runtime, body, true),
                "server teardown must not release a saved HOT body into COLD");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                "a graceful shutdown retains the HOT lease for exact UUID recovery after restart");
        body.discard(); carpetBody.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-prepared-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void freshAmbientBodyRehydratesExactEngineeringToolFromActorCustody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(20, 8, 0)); prepareFloor(level, origin);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ambient-engineering-tool-hydration"), 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1"), tool = new SubjectId("item:bootstrap-1-engineering-tool-1");
        FrontierWorldState initial = state(runtime);
        var exactTool = initial.inventory().items().get(tool);
        FrontierWorldState equipped = initial.withInventory(initial.inventory().moveObservedItem(tool, exactTool.custody(),
                new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.Actor(resident)));

        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, equipped, resident, bodyAt(origin)),
                FrontierV3AmbientActorExecutor.Result.APPLIED,
                "a fresh actor body must materialize only its one canonical identity");
        Villager body = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(equipped, resident));
        helper.assertTrue(body != null && FrontierV3CargoHandoffExecutor.exactMatch(
                        body.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND), exactTool),
                "canonical engineering-tool custody must survive fresh COLD/restart materialization as the same tagged physical stack");
        body.discard(); FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-prepared-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void unindexedManagedJoinDefersAdmissionUntilItsExactUuidIsPublished(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ambient-unindexed-join-game-test"), 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState before = state(runtime);
        FrontierV3CommandSubmission.submit(runtime, "ambient-unindexed-prepare", resident.value(),
                new AmbientLeasePrepared(AmbientActorProcess.nextLease(before, resident, runtime.checkpointImage().orElseThrow().instant())));
        FrontierWorldState prepared = state(runtime);
        Villager joining = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (joining == null) throw new IllegalStateException("game test could not create resident body");
        joining.setUUID(FrontierV3AmbientActorExecutor.entityId(prepared, resident));
        BlockPos observed = helper.absolutePos(new BlockPos(2, 8, 0)); joining.setPos(observed.getX() + 0.5D, observed.getY(), observed.getZ() + 0.5D);
        joining.setNoAi(true); joining.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, resident.value());
        joining.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.observeJoin(runtime, joining), FrontierV3AmbientActorExecutor.JoinDisposition.RETAINED,
                "an exact PREPARED managed body must be retained while its UUID is not yet indexed");
        FrontierV3AmbientActorExecutor.AdmissionDiagnostic diagnostic = FrontierV3AmbientActorExecutor.admissionDiagnostic(level, runtime, prepared, resident);
        helper.assertValueEqual(diagnostic.status(), "PENDING_UNINDEXED",
                "the diagnostic must expose the pre-index bridge instead of claiming the actor is ready");
        helper.assertTrue(diagnostic.pending(), "the exact body must retain the bounded pending identity bridge");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, prepared, resident, prepared.actorLocations().get(resident).body()),
                FrontierV3AmbientActorExecutor.Result.PENDING,
                "a pending exact UUID must defer admission rather than create a second managed body");
        Villager duplicate = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (duplicate == null) throw new IllegalStateException("game test could not create duplicate resident body");
        duplicate.setUUID(joining.getUUID()); duplicate.setPos(joining.position()); duplicate.setNoAi(true);
        duplicate.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, resident.value());
        duplicate.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.observeJoin(runtime, duplicate),
                FrontierV3AmbientActorExecutor.JoinDisposition.DUPLICATE_UNINDEXED,
                "a second unindexed body with the same exact UUID must be rejected before Minecraft admits it");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, prepared, resident, prepared.actorLocations().get(resident).body()),
                FrontierV3AmbientActorExecutor.Result.PENDING,
                "rejecting the later duplicate must retain the first exact body as the only pending admission");
        duplicate.discard();
        joining.discard(); FrontierV3AmbientActorExecutor.tick(level, runtime);
        helper.assertFalse(FrontierV3AmbientActorExecutor.admissionDiagnostic(level, runtime, prepared, resident).pending(),
                "a discarded candidate must release only its volatile bridge and allow normal later admission");
        FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-restart-absence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void loadedAbsentHotBodyBecomesAVisibleFreshAdmissionAfterRestartRecovery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ambient-restart-absence-game-test"), 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState initial = state(runtime);
        BodyPosition anchor = initial.actorLocations().get(resident).body();
        prepareFloor(level, new BlockPos(anchor.x(), anchor.y(), anchor.z()));
        AmbientActorLease lease = new AmbientActorLease(resident, anchor, runtime.checkpointImage().orElseThrow().instant(), 1L,
                AmbientLeaseStatus.PREPARED, AmbientGoalKind.WORK, anchor);
        FrontierV3CommandSubmission.submit(runtime, "ambient-restart-absence-prepare", resident.value(), new AmbientLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "ambient-restart-absence-hot", resident.value(), new AmbientLeaseTransition(resident, AmbientLeaseStatus.HOT));
        helper.assertValueEqual(FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                "an active body becomes explicitly unknown at restart");
        FrontierWorldState unknown = state(runtime);
        helper.assertTrue(FrontierV3AmbientActorExecutor.restartAbsenceIsObserved(level, unknown, resident, unknown.ambientLeases().get(resident)),
                "only the loaded exact hand-off column may prove the pre-restart body absent");
        FrontierV3CommandSubmission.submit(runtime, "ambient-restart-absence-observed", resident.value(),
                new AmbientLeaseRestartAbsenceObserved(resident, anchor));
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.CLOSED,
                "absence closes the failed HOT hand-off without inferring a death");
        AmbientActorLease fresh = AmbientActorProcess.nextLease(state(runtime), resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "ambient-restart-absence-fresh", resident.value(), new AmbientLeasePrepared(fresh));
        helper.assertFalse(FrontierV3AmbientActorExecutor.mayCreateFreshBody(true, false, true),
                "production admission must defer while Minecraft has loaded blocks but is still restoring entity storage");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident, anchor), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the isolated fixture may admit its known-empty test chunk exactly once");
        helper.runAfterDelay(1L, () -> {
            try {
                Entity recovered = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
                helper.assertTrue(recovered instanceof Villager && FrontierV3AmbientActorExecutor.recognizes(runtime, recovered),
                        "re-materialization retains the one exact UUID and normal ownership proof");
                recovered.discard(); helper.succeed();
            } finally { FrontierV3AmbientActorExecutor.forget(runtime); }
        });
    }

    @GameTest(batch = "pm-frontier-v3-ambient-local-brain", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 120)
    public static void hotAmbientBodiesUseRoleAwareControlledMotionWithoutVanillaAi(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0)); prepareSquareFloor(level, origin, 16);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ambient-local-brain"), 91L), new EphemeralStore(), 10_000);
        FrontierWorldState state = state(runtime);
        SubjectId farmer = new SubjectId("resident:1-1"), scout = new SubjectId("bioform:west-1");
        BodyPosition anchor = bodyAt(origin);
        AmbientActorLease farmerLease = new AmbientActorLease(farmer, anchor, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, new BodyPosition(anchor.x() + 12, anchor.y(), anchor.z()));
        AmbientActorLease scoutLease = new AmbientActorLease(scout, new BodyPosition(anchor.x() + 2, anchor.y(), anchor.z()),
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L, AmbientLeaseStatus.HOT, AmbientGoalKind.PATROL, anchor);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, farmer, anchor), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the farmer fixture must materialize as one exact body");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, scout,
                        new BodyPosition(anchor.x() + 2, anchor.y(), anchor.z())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the scout fixture must materialize as one exact body");
        Villager farmerBody = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, farmer));
        Zombie scoutBody = (Zombie) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, scout));
        double farmerBefore = farmerBody.distanceToSqr(origin.getX() + 0.5D, farmerBody.getY(), origin.getZ() + 0.5D);
        helper.runAfterDelay(1L, () -> driveLocalGoals(helper, level, runtime, state, farmer, farmerBody, farmerLease,
                scout, scoutBody, scoutLease, 80, () -> {
        helper.assertTrue(farmerBody.isNoAi() && scoutBody.isNoAi(),
                "HOT ambient bodies must stay outside uncontrolled vanilla target/combat AI");
        helper.assertTrue(farmerBody.distanceToSqr(origin.getX() + 0.5D, farmerBody.getY(), origin.getZ() + 0.5D) > farmerBefore + 0.1D,
                "the farmer must visibly work around its assigned facility rather than freeze at its hand-off point");
        helper.assertTrue(FrontierV3AmbientActorExecutor.localTarget(state, scout, scoutLease, 0L)
                        .distanceToSqr(scoutLease.handoffBody().x() + 0.5D, scoutLease.handoffBody().y(), scoutLease.handoffBody().z() + 0.5D)
                    > FrontierV3AmbientActorExecutor.localTarget(state, farmer, farmerLease, 0L)
                        .distanceToSqr(farmerLease.handoffBody().x() + 0.5D, farmerLease.handoffBody().y(), farmerLease.handoffBody().z() + 0.5D),
                "a scout patrol must use a wider role-specific perimeter than a farmer work cycle");
        helper.assertTrue(FrontierV3AmbientActorExecutor.localTarget(state, farmer, farmerLease, 0L)
                        .distanceToSqr(farmerLease.goalBody().x() + 0.5D, farmerLease.goalBody().y(), farmerLease.goalBody().z() + 0.5D) > 25.0D,
                "ambient local motion must remain anchored at the exact exterior hand-off slot, not cross a semantic-object centre");
        helper.assertTrue(FrontierV3AmbientActorExecutor.localTarget(state, farmer, farmerLease, 0L)
                        .distanceToSqr(FrontierV3AmbientActorExecutor.localTarget(state, farmer, farmerLease, 180L)) > 0.01D,
                "a durable ambient work lease must produce a continuing cycle rather than one static target");
        farmerBody.discard(); scoutBody.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
        }));
    }

    @GameTest(batch = "pm-frontier-v3-ambient-local-brain", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void continuousPatrolDoesNotIdleInsideTheNormalArrivalRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos floor = helper.absolutePos(new BlockPos(0, 8, 0)); prepareSquareFloor(level, floor, 2);
        Zombie body = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        if (body == null) throw new IllegalStateException("game test could not create patrol bioform");
        body.setPos(floor.getX() + 0.5D, floor.getY(), floor.getZ() + 0.5D); body.setNoAi(true); body.setPersistenceRequired();
        helper.assertTrue(level.addFreshEntity(body), "the continuous-patrol fixture must enter the loaded world");
        helper.runAfterDelay(1L, () -> {
            // This is deliberately nearer than the ordinary exact-arrival radius. A slow circular
            // patrol target repeatedly enters that radius; treating it as terminal produced a
            // visible stop → one fixed step → stop cadence.
            FrontierV3ControlledMobMotion.followContinuously(level, body,
                    new Vec3(floor.getX() + 0.65D, floor.getY(), floor.getZ() + 0.5D));
            helper.runAfterDelay(2L, () -> {
                helper.assertTrue(body.getX() > floor.getX() + 0.58D,
                        "a continuous local target inside the exact-arrival radius must still yield a small physical step");
                body.discard(); helper.succeed();
            });
        });
    }

    @GameTest(batch = "pm-frontier-v3-ambient-local-brain", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 45)
    public static void continuousPatrolAdvancesOnEveryLoadedServerTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos floor = helper.absolutePos(new BlockPos(0, 8, 0)); prepareSquareFloor(level, floor, 3);
        Zombie body = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        if (body == null) throw new IllegalStateException("game test could not create continuous patrol bioform");
        body.setPos(floor.getX() + 0.5D, floor.getY(), floor.getZ() + 0.5D); body.setNoAi(true); body.setPersistenceRequired();
        helper.assertTrue(level.addFreshEntity(body), "the continuous-patrol fixture must enter the loaded world");
        helper.runAfterDelay(1L, () -> driveContinuousPatrolSamples(helper, level, body, floor, 18, new ArrayList<>(), () -> {
            body.discard(); helper.succeed();
        }));
    }

    @GameTest(batch = "pm-frontier-v3-scout-patrol-cursor", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 140)
    public static void hotScoutFollowsItsLeasedCanonicalPatrolStepRatherThanASeparateLocalCircle(GameTestHelper helper) {
        // Keep this footprint inside the stock template's isolated test cell: the full suite
        // runs many templates in parallel, whereas this proof needs only one four-block step.
        ServerLevel level = helper.getLevel(); BlockPos feet = helper.absolutePos(new BlockPos(3, 8, 3)); prepareMotionArena(level, feet, 5, 3);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:hot-scout-cursor"), 91L), new EphemeralStore(), 10_000);
        FrontierWorldState state = state(runtime); SubjectId scout = new SubjectId("bioform:west-1");
        BodyPosition handoff = bodyAt(feet);
        BodyPosition target = handoff.offset(4, 0, 0);
        AmbientActorLease lease = new AmbientActorLease(scout, handoff, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.SCOUT_PATROL, target);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, scout, handoff), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the exact scout body must materialize at its current patrol cursor");
        // Entity insertion is visible only on the following server tick in a full parallel
        // GameTest run.  Do not inspect/move the pre-index body object as if that were a
        // materialization acknowledgement.
        helper.runAfterDelay(1L, () -> {
            Entity entity = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, scout));
            helper.assertTrue(entity instanceof Zombie, "the exact Scout must be indexed before its HOT patrol step");
            Zombie body = (Zombie) entity;
            drivePursuit(helper, level, runtime, state, scout, body, lease, 100, () -> {
                BlockPos physicalTarget = new BlockPos(target.x(), target.y(), target.z());
                helper.assertTrue(body.isNoAi()
                                && body.distanceToSqr(physicalTarget.getX() + 0.5D, physicalTarget.getY(), physicalTarget.getZ() + 0.5D) < 2.25D,
                        "the HOT scout must approach its one canonical next cursor without vanilla AI or a local substitute; body="
                                + body.position() + " target=" + target);
                body.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
            });
        });
    }

    @GameTest(batch = "pm-frontier-v3-ambient-local-brain", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void controlledMotionMarksEachAcceptedStepForImmediateTrackerReplication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos floor = helper.absolutePos(new BlockPos(0, 8, 0)); prepareSquareFloor(level, floor, 2);
        Zombie body = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        if (body == null) throw new IllegalStateException("game test could not create replication-motion bioform");
        body.setPos(floor.getX() + 0.5D, floor.getY(), floor.getZ() + 0.5D); body.setNoAi(true); body.setPersistenceRequired();
        helper.assertTrue(level.addFreshEntity(body), "the replication-motion fixture must enter the loaded world");
        helper.runAfterDelay(1L, () -> {
            FrontierV3ControlledMobMotion.followContinuously(level, body,
                    new Vec3(floor.getX() + 1.5D, floor.getY(), floor.getZ() + 0.5D));
            helper.runAfterDelay(1L, () -> {
                // Invoke the registered actuator directly at its ordinary entity-tick boundary,
                // before the tracker clears hasImpulse after publishing the position packet.
                FrontierV3ControlledMobMotion.advance(body);
                helper.assertTrue(body.getX() > floor.getX() + 0.5D && body.hasImpulse,
                        "every accepted PM step must request same-tick client replication instead of tracker coalescing");
                body.discard(); helper.succeed();
            });
        });
    }

    @GameTest(batch = "pm-frontier-v3-assembly-headroom", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void exactAssemblyTargetRejectsLoadedObstructionWithoutClimbingToAnotherFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos floor = helper.absolutePos(new BlockPos(4, 8, 0));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
        BlockPosition anchor = new BlockPosition(floor.getX(), floor.getY(), floor.getZ());
        helper.assertTrue(FrontierV3StandingPosition.hasExactHeadroom(level, anchor),
                "a canonical floor with two clear body cells admits its exact assembly cursor");
        helper.assertValueEqual(FrontierV3StandingPosition.aboveExactFloor(level, anchor), floor.above(),
                "the exact cursor resolves only to the feet cell directly above its retained support");
        level.setBlock(floor.above(), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3StandingPosition.hasExactHeadroom(level, anchor),
                "a player block at the exact feet cell is a loaded-world deferral, not an invitation to climb it");
        helper.assertTrue(FrontierV3StandingPosition.aboveExactFloor(level, anchor) == null,
                "an obstruction at the exact feet cell may not be reinterpreted as a higher floor");
        level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3StandingPosition.hasExactHeadroom(level, anchor),
                "headroom checks only the exact canonical body cells; ordinary Minecraft collision remains the support authority");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-transit-motion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 260)
    public static void controlledMotionUsesTheClearLaneBesideRouteSurfaceButNeverPassesThroughAFullWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(3, 8, 3)); prepareMotionArena(level, origin, 8, 5);
        for (int x = 1; x <= 6; x++) level.setBlock(origin.offset(x, 0, 1), Blocks.GRAY_CARPET.defaultBlockState(), 3);
        Villager body = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("game test could not create resident body");
        body.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 2.5D); body.setPersistenceRequired(); body.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(body), "the controlled-motion fixture must enter the loaded world");
        helper.runAfterDelay(1L, () -> driveMotion(helper, level, body,
                new Vec3(origin.getX() + 6.5D, origin.getY(), origin.getZ() + 2.5D), 120, () -> {
            helper.assertTrue(body.getX() > origin.getX() + 4.0D,
                    "a resident must advance through the clear lane beside the materialized route surface");
            body.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 2.5D);
            for (int z = 0; z <= 4; z++) for (int y = 0; y <= 2; y++) level.setBlock(origin.offset(3, y, z), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
            driveMotion(helper, level, body, new Vec3(origin.getX() + 7.5D, origin.getY(), origin.getZ() + 2.5D), 100, () -> {
                helper.assertTrue(body.getX() < origin.getX() + 3.0D,
                        "controlled motion may sidestep but must never cross a full materialized wall");
                body.discard(); helper.succeed();
            });
        }));
    }

    @GameTest(batch = "pm-frontier-v3-ambient-transit-motion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 320)
    public static void controlledMotionStepsOntoThinRouteSurfaceWithoutCrossingAFullWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(3, 8, 3)); prepareMotionArena(level, origin, 8, 5);
        for (int x = 1; x <= 6; x++) level.setBlock(origin.offset(x, 0, 2), Blocks.GRAY_CARPET.defaultBlockState(), 3);
        Villager body = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("game test could not create resident body");
        body.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 2.5D); body.setPersistenceRequired(); body.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(body), "the controlled-motion fixture must enter the loaded world");
        helper.runAfterDelay(1L, () -> driveMotion(helper, level, body,
                new Vec3(origin.getX() + 6.5D, origin.getY(), origin.getZ() + 2.5D), 160, () -> {
            helper.assertTrue(body.getX() > origin.getX() + 4.0D,
                    "a resident must step onto the visible route surface instead of stalling before it");
            body.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 2.5D);
            for (int z = 0; z <= 4; z++) for (int y = 0; y <= 2; y++) level.setBlock(origin.offset(3, y, z), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
            driveMotion(helper, level, body, new Vec3(origin.getX() + 7.5D, origin.getY(), origin.getZ() + 2.5D), 120, () -> {
                helper.assertTrue(body.getX() < origin.getX() + 3.0D,
                        "the low-step fallback must not climb or pass through a full graybox wall");
                body.discard(); helper.succeed();
            });
        }));
    }

    private static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
    }
    private static void prepareSquareFloor(ServerLevel level, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) prepareFloor(level, center.offset(x, 0, z));
    }
    /** Keeps a parallel GameTest fixture inside its own stock-template rectangle. */
    private static void prepareMotionArena(ServerLevel level, BlockPos origin, int length, int width) {
        for (int x = 0; x <= length; x++) for (int z = 0; z < width; z++) prepareFloor(level, origin.offset(x, 0, z));
    }
    private static BodyPosition bodyAt(BlockPos feet) { return new BodyPosition(feet.getX(), feet.getY(), feet.getZ()); }
    private static void driveMotion(GameTestHelper helper, ServerLevel level, net.minecraft.world.entity.Mob body, Vec3 target, int remaining, Runnable complete) {
        if (remaining == 0) { complete.run(); return; }
        FrontierV3ControlledMobMotion.moveToward(level, body, target);
        helper.runAfterDelay(1L, () -> {
            // GameTest callbacks are not ordered relative to EntityTickEvent.Pre. In production
            // the event has normally consumed this one-tick intent; if it has not, consume the
            // same registered actuator here rather than making this collision proof depend on
            // callback ordering.
            FrontierV3ControlledMobMotion.advance(body);
            driveMotion(helper, level, body, target, remaining - 1, complete);
        });
    }
    private static void driveContinuousPatrolSamples(GameTestHelper helper, ServerLevel level, Zombie body, BlockPos floor,
                                                      int remaining, List<Double> positions, Runnable complete) {
        if (remaining == 0) {
            // The target keeps advancing by a small amount.  Once the first intent is applied,
            // every loaded server tick must produce an observed physical delta; a retained
            // intent or normal arrival radius must never reintroduce a stop/go animation.
            for (int index = 1; index < positions.size(); index++) {
                double delta = positions.get(index) - positions.get(index - 1);
                helper.assertTrue(delta > 0.005D && delta <= 0.27D,
                        "continuous patrol must advance once per loaded tick, sample " + index + " had delta " + delta);
            }
            complete.run(); return;
        }
        int sample = positions.size();
        FrontierV3ControlledMobMotion.followContinuously(level, body,
                new Vec3(floor.getX() + 1.5D + sample * 0.02D, floor.getY(), floor.getZ() + 0.5D));
        helper.runAfterDelay(1L, () -> {
            FrontierV3ControlledMobMotion.advance(body);
            positions.add(body.getX());
            driveContinuousPatrolSamples(helper, level, body, floor, remaining - 1, positions, complete);
        });
    }
    private static void drivePursuit(GameTestHelper helper, ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                     FrontierWorldState state, SubjectId actor, net.minecraft.world.entity.Mob body, AmbientActorLease lease,
                                     int remaining, Runnable complete) {
        if (remaining == 0) { complete.run(); return; }
        FrontierV3AmbientActorExecutor.pursueLocalGoal(level, runtime, state, actor, body, lease);
        helper.runAfterDelay(1L, () -> {
            FrontierV3ControlledMobMotion.advance(body);
            drivePursuit(helper, level, runtime, state, actor, body, lease, remaining - 1, complete);
        });
    }
    private static void driveLocalGoals(GameTestHelper helper, ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                        FrontierWorldState state, SubjectId farmer, Villager farmerBody, AmbientActorLease farmerLease,
                                        SubjectId scout, Zombie scoutBody, AmbientActorLease scoutLease, int remaining, Runnable complete) {
        if (remaining == 0) { complete.run(); return; }
        FrontierV3AmbientActorExecutor.pursueLocalGoal(level, runtime, state, farmer, farmerBody, farmerLease);
        FrontierV3AmbientActorExecutor.pursueLocalGoal(level, runtime, state, scout, scoutBody, scoutLease);
        helper.runAfterDelay(1L, () -> driveLocalGoals(helper, level, runtime, state, farmer, farmerBody, farmerLease,
                scout, scoutBody, scoutLease, remaining - 1, complete));
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }
    private static final class EphemeralStore implements FrontierStore {
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
