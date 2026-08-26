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
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
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
