package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.internal.effect.EffectLeaseLedger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical proof that only exact leases create bodies and HOT bodies retain their location. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxActorMaterializerGameTests {
    private SourceGrayboxActorMaterializerGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotLeaseCreatesOnlyItsExactActorAndNeverSnapsItBackToTheSnapshot(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        String residentId = baseline.residents().getFirst().id();
        ReferenceGrayboxSnapshot ordinary = SourceGrayboxMaterializerGameTests.fixture(anchor, baseline, residentId, 1.0d, "execution-lease");
        String coldBioformId = "bioform:execution:1";
        ReferenceGrayboxSnapshot snapshot = withColdBioform(anchor, ordinary, coldBioformId);
        ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", 1L), "only an explicit lease may request a physical source resident");
        String lease = execution.actor(residentId).orElseThrow().leaseId();
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        Map<String, Entity> admitted = new LinkedHashMap<>();

        materializer.apply(helper.getLevel(), snapshot, execution, admitted);
        Villager resident = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(24), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).stream().findFirst().orElseThrow();
        helper.assertValueEqual(resident.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_REVISION), snapshot.stateRevision(),
                "the physical death observation must retain its complete current source snapshot revision");
        helper.assertValueEqual(resident.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ACTOR_REVISION),
                execution.actor(residentId).orElseThrow().sourceRevision(),
                "the executor must independently retain the exact body's stable semantic revision");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(anchor).inflate(24), value ->
                        value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(coldBioformId)).isEmpty(),
                "cold source bioforms must not become off-screen duplicate Zombies merely because a neighbouring chunk is loaded");
        helper.assertTrue(execution.activate(residentId, lease, "gametest:actor-runtime", 2L), "the body becomes hot only after lease-owned materialization");
        resident.setPos(anchor.getX() + 15.5d, ReferenceGrayboxLayout.GROUND_Y + 1, anchor.getZ() + 15.5d);
        helper.assertTrue(execution.capture(residentId, lease, "gametest:actor-runtime",
                (anchor.getX() + 15) * 16 + 8, (anchor.getZ() + 15) * 16 + 8, 3L), "the actual physical location is captured before republishing");

        materializer.apply(helper.getLevel(), snapshot, execution, admitted);
        helper.assertValueEqual(resident.getX(), anchor.getX() + 15.5d, "a HOT resident must retain live X instead of snapshot reset");
        helper.assertValueEqual(resident.getZ(), anchor.getZ() + 15.5d, "a HOT resident must retain live Z instead of snapshot reset");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-actor-admission", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedActorUsesDeterministicFreeApronInsteadOfEnteringAPmWall(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        String residentId = "resident:actor-admission:free";
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.fixture(anchor, baseline, residentId, 1.0d, "safe-admission");
        ReferenceGrayboxSnapshot.Resident resident = snapshot.residents().getFirst();
        BlockPos blocked = new BlockPos(resident.position().x(), ReferenceGrayboxLayout.GROUND_Y + 1, resident.position().z());
        helper.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(blocked.above(), Blocks.STONE.defaultBlockState(), 3);

        AdmissionBorder border = AdmissionBorder.openAround(helper.getLevel().getWorldBorder(), resident.position());
        try {
            // The stable first apron candidate for this identity is deliberately
            // empty.  Check the resolver against the raw physical world before
            // the wider materializer publishes labels/claims, so a failure tells
            // us whether admission or publication introduced the obstruction.
            Villager probe = new Villager(EntityType.VILLAGER, helper.getLevel());
            Vec3 desired = new Vec3(resident.position().x() + 0.5d, ReferenceGrayboxLayout.GROUND_Y + 1,
                    resident.position().z() + 0.5d);
            Vec3 expectedApron = desired.add(3.0d, 0.0d, -3.0d);
            probe.moveTo(expectedApron.x, expectedApron.y, expectedApron.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().noCollision(probe, probe.getBoundingBox()),
                    "the raw deterministic apron must be physically clear before source presentation writes");
            helper.assertTrue(SourceGrayboxActorSpawnResolver.resolve(helper.getLevel(), probe, desired, residentId).isPresent(),
                    "the raw deterministic apron must be accepted before source presentation writes");

            ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
            helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", 1L), "the exact source resident must hold the one preparation lease");
            SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
            Map<String, Entity> admitted = new LinkedHashMap<>();
            materializer.apply(helper.getLevel(), snapshot, execution, admitted);

            SourceGrayboxPresentationLedger.Claim admissionConflict = SourceGrayboxPresentationLedger.get(helper.getLevel())
                    .claim("actor-obstruction:RESIDENT:" + residentId);
            helper.assertTrue(admitted.containsKey(SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT")),
                    "a blocked source point with a free local apron must admit one physical resident body; conflict=" + admissionConflict);
            Villager body = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(24), value ->
                    value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).stream().findFirst().orElseThrow();
            helper.assertTrue(helper.getLevel().noCollision(body, body.getBoundingBox()),
                    "a PM actor must never be admitted with its body intersecting a physical wall");
            helper.assertTrue(body.blockPosition().getX() != blocked.getX() || body.blockPosition().getZ() != blocked.getZ(),
                    "a blocked source point must use a deterministic nearby open point rather than suffocating in place");
            helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel())
                            .claim("actor-obstruction:RESIDENT:" + residentId) == null,
                    "a recovered local collision must not leave a false persistent obstruction warning");
        } finally {
            border.restore();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-actor-admission", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void fullyBlockedPreparationDefersAndExplainsInsteadOfCreatingASuffocatingActor(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        String residentId = "resident:actor-admission:blocked";
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.fixture(anchor, baseline, residentId, 1.0d, "blocked-admission");
        ReferenceGrayboxSnapshot.Resident resident = snapshot.residents().getFirst();
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            BlockPos obstruction = new BlockPos(resident.position().x() + x, ReferenceGrayboxLayout.GROUND_Y + 1, resident.position().z() + z);
            helper.getLevel().setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(obstruction.above(), Blocks.STONE.defaultBlockState(), 3);
        }

        AdmissionBorder border = AdmissionBorder.openAround(helper.getLevel().getWorldBorder(), resident.position());
        try {
            ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
            helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", 1L), "the exact source resident must hold the one preparation lease");
            SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
            materializer.apply(helper.getLevel(), snapshot, execution, new LinkedHashMap<>());

            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(24), value ->
                            value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).isEmpty(),
                    "no free physical body means a retained preparation, never an embedded Villager");
            SourceGrayboxPresentationLedger.Claim obstruction = SourceGrayboxPresentationLedger.get(helper.getLevel())
                    .claim("actor-obstruction:RESIDENT:" + residentId);
            helper.assertTrue(obstruction != null && obstruction.conflicted() && !obstruction.installed(),
                    "an unmaterialized source actor must retain a visible non-owning obstruction fact for recovery");
        } finally {
            border.restore();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-actor-admission", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void blockedPreparationSettlesColdThenRetriesOnlyAtItsBoundedStagger(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        String residentId = "resident:actor-admission:retry";
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.fixture(anchor, baseline, residentId, 1.0d, "blocked-retry");
        ReferenceGrayboxSnapshot.Resident resident = snapshot.residents().getFirst();
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            BlockPos obstruction = new BlockPos(resident.position().x() + x, ReferenceGrayboxLayout.GROUND_Y + 1, resident.position().z() + z);
            helper.getLevel().setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(obstruction.above(), Blocks.STONE.defaultBlockState(), 3);
        }

        AdmissionBorder border = AdmissionBorder.openAround(helper.getLevel().getWorldBorder(), resident.position());
        try {
            ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
            helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", 10L), "the exact source resident must reserve one preparation lease");
            var preparing = execution.actor(residentId).orElseThrow();
            SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
            Map<String, Entity> admitted = new LinkedHashMap<>();
            materializer.apply(helper.getLevel(), snapshot, execution, admitted);
            boolean obstructed = SourceGrayboxActorMaterializer.isActorObstructed(SourceGrayboxPresentationLedger.get(helper.getLevel()), preparing);
            helper.assertTrue(SourceGrayboxActorExecutionRuntime.mustReleaseBlockedPreparation(preparing, false, obstructed),
                    "a no-body PREPARING lease with a retained collision fact must settle COLD in the runtime");
            helper.assertTrue(execution.cancelPreparation(residentId, preparing.leaseId(), "gametest:actor-runtime", 20L),
                    "the one rejected preparation lease must return to cold source custody");
            var cold = execution.actor(residentId).orElseThrow();
            helper.assertValueEqual(cold.mode().name(), "COLD", "a blocked preparation is never retained as a perpetual executor");
            helper.assertTrue(!SourceGrayboxActorExecutionRuntime.allowsPreparation(cold, true, 30L),
                    "a retained obstruction must not churn a fresh COLD lease every executor turn");

            long retryTick = -1L;
            for (long tick = 30L; tick <= 420L; tick += 10L) if (SourceGrayboxActorExecutionRuntime.allowsPreparation(cold, true, tick)) {
                retryTick = tick;
                break;
            }
            helper.assertTrue(retryTick >= 220L && retryTick <= 410L,
                    "the deterministic obstruction retry must be delayed and bounded; retry=" + retryTick);
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                BlockPos obstruction = new BlockPos(resident.position().x() + x, ReferenceGrayboxLayout.GROUND_Y + 1, resident.position().z() + z);
                helper.getLevel().setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(obstruction.above(), Blocks.AIR.defaultBlockState(), 3);
            }
            helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", retryTick),
                    "the source actor may re-enter preparation only after its bounded collision retry");
            materializer.apply(helper.getLevel(), snapshot, execution, admitted);
            helper.assertTrue(admitted.containsKey(SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT")),
                    "removing the real obstruction must allow one later source-owned body, not leave a permanent gap");
        } finally {
            border.restore();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-actor-admission", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotActorEscapesNewSourceGeometryBeforeTheWorldCanSuffocateIt(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        String residentId = "resident:actor-admission:hot";
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.fixture(anchor, baseline, residentId, 1.0d, "hot-admission");
        ReferenceGrayboxSnapshot.Resident resident = snapshot.residents().getFirst();
        AdmissionBorder border = AdmissionBorder.openAround(helper.getLevel().getWorldBorder(), resident.position());
        try {
            ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
            helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", 1L), "the exact source resident must reserve its lease");
            String lease = execution.actor(residentId).orElseThrow().leaseId();
            SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
            Map<String, Entity> admitted = new LinkedHashMap<>();
            materializer.apply(helper.getLevel(), snapshot, execution, admitted);
            helper.assertTrue(execution.activate(residentId, lease, "gametest:actor-runtime", 2L), "the test body must become HOT");
            Villager body = (Villager) admitted.get(SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT"));
            BlockPos embedded = body.blockPosition();
            helper.getLevel().setBlock(embedded, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(embedded.above(), Blocks.STONE.defaultBlockState(), 3);

            materializer.apply(helper.getLevel(), snapshot, execution, admitted);

            helper.assertTrue(!body.isRemoved(), "a live source actor must be recovered instead of being left to suffocate");
            helper.assertTrue(helper.getLevel().noCollision(body, body.getBoundingBox()),
                    "a source scene introduced around a HOT body must be resolved before the entity tick");
            helper.assertTrue(body.blockPosition().getX() != embedded.getX() || body.blockPosition().getZ() != embedded.getZ(),
                    "HOT recovery must retain continuity by moving only to a deterministic nearby free body cell");
            helper.assertTrue(materializer.actorRecoveries().hasAny(),
                    "a recovered HOT body must request an immediate exact hand-off capture, not wait for the normal cadence");
            helper.assertTrue(materializer.actorRecoveries().consume(residentId, "RESIDENT"),
                    "the recovery marker must identify only the one source-owned actor whose physical position changed");
            helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel())
                            .claim("actor-obstruction:RESIDENT:" + residentId) == null,
                    "a successful HOT recovery must not retain a stale obstruction warning");
        } finally {
            border.restore();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void retiredLeaseIsNeverDiscardedByGenericSnapshotRetirement(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        String residentId = "resident:retired:1";
        ReferenceGrayboxSnapshot present = SourceGrayboxMaterializerGameTests.fixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot(),
                residentId, 1.0d, "retired-lease");
        ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(present);
        helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", 1L), "test must reserve the unique resident executor");
        String lease = execution.actor(residentId).orElseThrow().leaseId();
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        Map<String, Entity> admitted = new LinkedHashMap<>();
        materializer.apply(helper.getLevel(), present, execution, admitted);
        helper.assertTrue(execution.activate(residentId, lease, "gametest:actor-runtime", 2L), "the created body must activate the lease");

        ReferenceGrayboxSnapshot absent = withoutResidents(present);
        helper.assertTrue(execution.reconcile(absent), "the canonical departure must retire its still-live executor");
        materializer.apply(helper.getLevel(), absent, execution, admitted);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(24), value ->
                        value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).size() == 1,
                "generic snapshot retirement must not discard a RETIRED body before the executor acknowledges it");

        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotGuardAndEngagingBioformMoveTowardOneAnotherUnderTheControlledBrain(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        String residentId = "resident:brain:guard";
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.fixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot(),
                residentId, 1.0d, "controlled-brain");
        String bioformId = snapshot.bioforms().getFirst().id();
        ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", 1L), "the guard lease must be prepared");
        helper.assertTrue(execution.prepare(bioformId, "gametest:actor-runtime", 1L), "the bioform lease must be prepared");
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        Map<String, Entity> admitted = new LinkedHashMap<>();
        materializer.apply(helper.getLevel(), snapshot, execution, admitted);
        helper.assertTrue(execution.activate(residentId, execution.actor(residentId).orElseThrow().leaseId(), "gametest:actor-runtime", 2L),
                "the resident must become HOT after its body exists");
        helper.assertTrue(execution.activate(bioformId, execution.actor(bioformId).orElseThrow().leaseId(), "gametest:actor-runtime", 2L),
                "the bioform must become HOT after its body exists");
        Villager guard = (Villager) materializer.actorEntity(helper.getLevel(), admitted, execution.actor(residentId).orElseThrow());
        Zombie bioform = (Zombie) materializer.actorEntity(helper.getLevel(), admitted, execution.actor(bioformId).orElseThrow());
        double before = guard.distanceToSqr(bioform);

        SourceGrayboxActorBehaviorRuntime brain = new SourceGrayboxActorBehaviorRuntime();
        for (long tick = 3L; tick < 18L; tick++) brain.tick(helper.getLevel(), snapshot, execution, materializer, admitted, tick);

        helper.assertTrue(guard.distanceToSqr(bioform) < before,
                "HOT guard and engaging bioform must make visible controlled progress instead of remaining frozen snapshot markers");
        helper.assertTrue(guard.isNoAi() && bioform.isNoAi(),
                "the live source brain must remain the sole local controller instead of enabling uncontrolled vanilla goals");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void exactSourceLineRoleFightsButMedicRoleStaysSupport(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        String residentId = "resident:combat:line";
        ReferenceGrayboxSnapshot baseline = SourceGrayboxMaterializerGameTests.fixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot(),
                residentId, 1.0d, "source-line-combat");
        ReferenceGrayboxSnapshot snapshot = withResidentRole(baseline, residentId, "line");
        ReferenceGrayboxSnapshot.Resident line = snapshot.residents().getFirst();
        ReferenceGrayboxSnapshot.Resident medic = new ReferenceGrayboxSnapshot.Resident("resident:combat:medic", 1, "worker", "worker",
                "operation", 47, "healthy", "medic", line.position(), "resident.worker");
        helper.assertTrue(SourceGrayboxActorBehaviorRuntime.guard(line),
                "the exact Python-source line role must defend itself in the HOT executor");
        helper.assertTrue(!SourceGrayboxActorBehaviorRuntime.guard(medic),
                "a source medic must remain local support rather than becoming an invented melee guard");

        String bioformId = snapshot.bioforms().getFirst().id();
        ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", 1L), "the line unit must receive its exact lease");
        helper.assertTrue(execution.prepare(bioformId, "gametest:actor-runtime", 1L), "the bioform must receive its exact lease");
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        Map<String, Entity> admitted = new LinkedHashMap<>();
        materializer.apply(helper.getLevel(), snapshot, execution, admitted);
        helper.assertTrue(execution.activate(residentId, execution.actor(residentId).orElseThrow().leaseId(), "gametest:actor-runtime", 2L),
                "the line unit must become HOT after physical materialization");
        helper.assertTrue(execution.activate(bioformId, execution.actor(bioformId).orElseThrow().leaseId(), "gametest:actor-runtime", 2L),
                "the bioform must become HOT after physical materialization");
        Villager lineBody = (Villager) materializer.actorEntity(helper.getLevel(), admitted, execution.actor(residentId).orElseThrow());
        Zombie bioform = (Zombie) materializer.actorEntity(helper.getLevel(), admitted, execution.actor(bioformId).orElseThrow());
        lineBody.setPos(anchor.getX() + 8.5d, ReferenceGrayboxLayout.GROUND_Y + 1, anchor.getZ() + 4.5d);
        bioform.setPos(anchor.getX() + 9.7d, ReferenceGrayboxLayout.GROUND_Y + 1, anchor.getZ() + 4.5d);
        // This test applies a controlled hit in the same server turn in which
        // the synthetic Zombie was materialized. Clear native spawn immunity
        // so the assertion isolates the source role gate rather than vanilla
        // post-spawn timing.
        bioform.invulnerableTime = 0;
        float before = bioform.getHealth();

        new SourceGrayboxActorCombatRuntime().tick(helper.getLevel(), new FixtureCombatOwner(snapshot, execution), materializer, admitted, 5L);
        helper.assertTrue(bioform.getHealth() < before,
                "a nearby source line unit must create one observed local combat effect instead of standing as a noncombatant");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotMeleeUsesOneDurableEffectReceiptInsteadOfNativeAiRepeats(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        String residentId = "resident:combat:guard";
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.fixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot(),
                residentId, 1.0d, "controlled-combat");
        String bioformId = snapshot.bioforms().getFirst().id();
        ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        helper.assertTrue(execution.prepare(residentId, "gametest:actor-runtime", 1L), "the guard must receive an exact lease");
        helper.assertTrue(execution.prepare(bioformId, "gametest:actor-runtime", 1L), "the bioform must receive an exact lease");
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        Map<String, Entity> admitted = new LinkedHashMap<>();
        materializer.apply(helper.getLevel(), snapshot, execution, admitted);
        helper.assertTrue(execution.activate(residentId, execution.actor(residentId).orElseThrow().leaseId(), "gametest:actor-runtime", 2L),
                "the guard body must become HOT");
        helper.assertTrue(execution.activate(bioformId, execution.actor(bioformId).orElseThrow().leaseId(), "gametest:actor-runtime", 2L),
                "the bioform body must become HOT");
        Villager guard = (Villager) materializer.actorEntity(helper.getLevel(), admitted, execution.actor(residentId).orElseThrow());
        Zombie bioform = (Zombie) materializer.actorEntity(helper.getLevel(), admitted, execution.actor(bioformId).orElseThrow());
        guard.setPos(anchor.getX() + 8.5d, ReferenceGrayboxLayout.GROUND_Y + 1, anchor.getZ() + 4.5d);
        bioform.setPos(anchor.getX() + 9.7d, ReferenceGrayboxLayout.GROUND_Y + 1, anchor.getZ() + 4.5d);
        float before = bioform.getHealth();
        FixtureCombatOwner owner = new FixtureCombatOwner(snapshot, execution);

        SourceGrayboxActorCombatRuntime combat = new SourceGrayboxActorCombatRuntime();
        combat.tick(helper.getLevel(), owner, materializer, admitted, 5L);
        float afterFirstHit = bioform.getHealth();
        combat.tick(helper.getLevel(), owner, materializer, admitted, 10L);

        helper.assertTrue(afterFirstHit < before, "a guard's HOT source action must cause real Minecraft damage");
        helper.assertValueEqual(bioform.getHealth(), afterFirstHit,
                "the durable cooldown must prevent a second tick-loop hit before its expiry");
        var receipt = owner.effectLeases().leases().stream()
                .filter(value -> value.actorSlotId().equals(residentId)).findFirst().orElseThrow();
        helper.assertValueEqual(receipt.state().name(), "COMPLETED", "the physical hit needs one terminal receipt");
        helper.assertValueEqual(receipt.nativeReference(), bioform.getUUID(), "the receipt must bind the inspected physical target");
        helper.assertTrue(receipt.receipt().contains("beforeHealth16=") && receipt.receipt().contains("afterHealth16="),
                "the terminal receipt must preserve the observed physical impact rather than a bare cooldown");
        helper.succeed();
    }

    private static ReferenceGrayboxSnapshot withColdBioform(BlockPos anchor, ReferenceGrayboxSnapshot fixture, String bioformId) {
        return new ReferenceGrayboxSnapshot(fixture.day(), fixture.profileId(), fixture.stateRevision(), fixture.bounds(), fixture.cells(),
                fixture.settlements(), fixture.facilities(), fixture.resourceSites(), fixture.routes(), fixture.hiveOrgans(),
                List.of(new ReferenceGrayboxSnapshot.Bioform(bioformId, 1, "harvester",
                        new ReferenceGrayboxLayout.Point(anchor.getX() + 10, anchor.getZ() + 4), "engaging", false, "bioform.harvester")),
                fixture.residents(), fixture.fieldPosts(), fixture.fieldLinks(), fixture.activities(), fixture.cargoes(), fixture.interactions(),
                fixture.sectors(), fixture.chrysalises(), fixture.events());
    }

    private record AdmissionBorder(WorldBorder border, double centerX, double centerZ, double size) {
        static AdmissionBorder openAround(WorldBorder border, ReferenceGrayboxLayout.Point point) {
            AdmissionBorder previous = new AdmissionBorder(border, border.getCenterX(), border.getCenterZ(), border.getSize());
            border.setCenter(point.x() + 0.5d, point.z() + 0.5d);
            border.setSize(64.0d);
            return previous;
        }

        void restore() {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }

    private static ReferenceGrayboxSnapshot withoutResidents(ReferenceGrayboxSnapshot present) {
        return new ReferenceGrayboxSnapshot(present.day(), present.profileId(), present.stateRevision(), present.bounds(), present.cells(),
                present.settlements(), present.facilities(), present.resourceSites(), present.routes(), present.hiveOrgans(), present.bioforms(),
                List.of(), present.fieldPosts(), present.fieldLinks(), present.activities(), present.cargoes(), present.interactions(),
                present.sectors(), present.chrysalises(), present.events());
    }

    private static ReferenceGrayboxSnapshot withResidentRole(ReferenceGrayboxSnapshot present, String residentId, String role) {
        ReferenceGrayboxSnapshot.Resident original = present.residents().getFirst();
        ReferenceGrayboxSnapshot.Resident line = new ReferenceGrayboxSnapshot.Resident(residentId, original.homeSettlementId(), "worker",
                original.economicClass(), original.location(), original.locationId(), original.condition(), role, original.position(), original.colour());
        return new ReferenceGrayboxSnapshot(present.day(), present.profileId(), present.stateRevision(), present.bounds(), present.cells(),
                present.settlements(), present.facilities(), present.resourceSites(), present.routes(), present.hiveOrgans(), present.bioforms(),
                List.of(line), present.fieldPosts(), present.fieldLinks(), present.activities(), present.cargoes(), present.interactions(),
                present.sectors(), present.chrysalises(), present.events());
    }

    private static final class FixtureCombatOwner implements SourceGrayboxCombatOwner {
        private final ReferenceGrayboxSnapshot snapshot;
        private final ReferenceGrayboxActorExecutionState execution;
        private final EffectLeaseLedger effects = new EffectLeaseLedger();

        private FixtureCombatOwner(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxActorExecutionState execution) {
            this.snapshot = snapshot;
            this.execution = execution;
        }

        @Override public ReferenceGrayboxSnapshot snapshot() { return snapshot; }
        @Override public ReferenceGrayboxActorExecutionState actorExecution() { return execution; }
        @Override public long actorExecutionGameTime(long observedGameTime) { return observedGameTime; }
        @Override public java.util.Optional<ReferenceGrayboxActorExecutionState.CombatAction> reserveActorCombat(
                String id, String leaseId, String holder, long gameTick, long cooldownTicks) {
            return execution.reserveCombatAction(id, leaseId, holder, gameTick, cooldownTicks);
        }
        @Override public EffectLeaseLedger effectLeases() { return effects; }
        @Override public void markEffectLeaseDirty() { }
    }
}
