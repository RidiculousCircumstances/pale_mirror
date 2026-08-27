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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical continuity proofs for source-directed HOT actor movement. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxActorBehaviorGameTests {
    private SourceGrayboxActorBehaviorGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void hotActorPhysicallyCatchesAnAdvancedSourceAnchorWithoutTeleporting(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        String residentId = baseline.residents().getFirst().id();
        BlockPos start = anchor.offset(2, 0, 2);
        BlockPos advanced = anchor.offset(14, 0, 2);
        ReferenceGrayboxSnapshot initial = fixture(baseline, residentId, new ReferenceGrayboxLayout.Point(start.getX(), start.getZ()), "1".repeat(64));
        ReferenceGrayboxSnapshot movedSource = fixture(baseline, residentId, new ReferenceGrayboxLayout.Point(advanced.getX(), advanced.getZ()), "2".repeat(64));
        ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(initial);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        Map<String, Entity> admitted = new LinkedHashMap<>();

        helper.assertTrue(execution.prepare(residentId, SourceGrayboxActorExecutionRuntime.HOLDER, 1L), "the exact source resident needs one admission lease");
        materializer.apply(helper.getLevel(), initial, execution, admitted);
        String lease = execution.actor(residentId).orElseThrow().leaseId();
        helper.assertTrue(execution.activate(residentId, lease, SourceGrayboxActorExecutionRuntime.HOLDER, 2L), "the materialized resident must become HOT");
        Villager resident = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(24), entity ->
                entity.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).stream().findFirst().orElseThrow();
        resident.setPos(start.getX() + 0.5d, ReferenceGrayboxLayout.GROUND_Y + 1, start.getZ() + 0.5d);
        helper.assertTrue(execution.capture(residentId, lease, SourceGrayboxActorExecutionRuntime.HOLDER, (start.getX() * 16) + 8,
                (start.getZ() * 16) + 8, 3L), "the executor must retain the actual HOT hand-off before source advance");
        BlockPos wall = anchor.offset(7, 0, 2);
        for (int z = anchor.getZ() - 22; z <= anchor.getZ() + 22; z++) {
            helper.getLevel().setBlock(new BlockPos(wall.getX(), ReferenceGrayboxLayout.GROUND_Y, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new BlockPos(wall.getX(), ReferenceGrayboxLayout.GROUND_Y + 1, z), Blocks.STONE.defaultBlockState(), 3);
        }

        helper.assertTrue(execution.reconcile(movedSource), "the source update must retain physical HOT position while advancing its anchor");
        materializer.apply(helper.getLevel(), movedSource, execution, admitted);
        helper.assertValueEqual(resident.blockPosition().getX(), start.getX(), "publication must not assign a new source destination to a HOT Villager");

        SourceGrayboxActorBehaviorRuntime behavior = new SourceGrayboxActorBehaviorRuntime();
        behavior.tick(helper.getLevel(), movedSource, execution, materializer, admitted, 10L);
        helper.assertTrue(resident.getX() > start.getX() + 0.5d && resident.getX() < wall.getX() - 0.45d,
                "the first local brain step must make bounded physical progress toward the advanced source anchor");
        for (long tick = 11L; tick < 120L; tick++) {
            double priorX = resident.getX();
            double priorZ = resident.getZ();
            behavior.tick(helper.getLevel(), movedSource, execution, materializer, admitted, tick);
            helper.assertTrue(Math.hypot(resident.getX() - priorX, resident.getZ() - priorZ) <= 0.075001d,
                    "later route-seeking steps around a real wall must stay within physical speed, never assign the source destination");
        }
        helper.succeed();
    }

    private static ReferenceGrayboxSnapshot fixture(ReferenceGrayboxSnapshot baseline, String residentId,
                                                     ReferenceGrayboxLayout.Point position, String revision) {
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), revision, baseline.bounds(), baseline.cells(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.Resident(residentId, 1, "guard", "worker", "operation", 991,
                        "healthy", "line", position, "resident.guard")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
