package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
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
}
