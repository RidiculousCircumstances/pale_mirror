package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Optional;

/** Real block receipts remain historical effects while the accepted plant keeps growing. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3IndependentPlantGrowthGameTests {
    private FrontierV3IndependentPlantGrowthGameTests() { }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void acceptedPlantGrowthDoesNotInvalidateTheExactSowingReceipt(GameTestHelper helper) {
        var level = helper.getLevel();
        var soil = helper.absolutePos(new BlockPos(4, 0, 4));
        var site = new SubjectId("site:independent-plant-receipt");
        var support = SurfaceAnchor.at(soil.getX(), soil.getY(), soil.getZ());
        var id = new ResourceFieldLayout.CellId(1);
        var layout = new ResourceFieldLayout(1, 2, List.of(new ResourceFieldLayout.Cell(id,
                support.support().offset(0, 1, 0), support, support)), List.of());
        var bare = ResourceFieldCycle.seeded(site, layout, 1).cropRemoved(id);
        var planted = bare.planted(id);
        var latest = planted.advanceGrowth(id);
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.runAtTickTime(1, () -> {
            var claimed = FrontierV3ResourceFieldWitness.claimed(site, 1, ResourceFieldPhysicalSurface.fromCycle(bare));
            var transition = ResourceFieldCellTransition.between(site, 1, 1, id,
                    ResourceFieldPhysicalSurface.Condition.of(bare.cell(id)), ResourceFieldPhysicalSurface.Condition.of(planted.cell(id)));
            var pending = claimed.begin(transition, "farmer:native:independent-sowing");
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState(), 3);
            var complete = pending.confirm(id, FrontierV3ResourceFieldObservation.observe(level, latest, pending, id, "native:sown"));
            var physical = FrontierV3ResourceFieldObservation.observe(level, latest, complete, id, "native:accepted-sowing");
            var accepted = new FrontierCanonicalState<>(new WorldId("frontier:independent-plants"), new Revision(2), new SimInstant(2), latest);
            var closed = complete.acknowledgeWork(accepted, id, "farmer:native:independent-sowing", physical, Optional.empty());
            helper.assertTrue(closed.cell(id).pending().isEmpty() && closed.cell(id).committed().growthStage() == 0
                    && latest.cell(id).growthStage() == 1, "sowing receipt confirms actual age0, never rewinds canonical age1");
            var lost = new FrontierCanonicalState<>(accepted.worldId(), accepted.revision(), accepted.instant(), latest.cropRemoved(id));
            helper.assertTrue(rejects(() -> complete.acknowledgeWork(lost, id,
                    "farmer:native:independent-sowing", physical, Optional.empty())), "growth allowance cannot conceal crop loss");

            var growth = ResourceFieldCellTransition.between(site, 1, 1, id,
                    closed.cell(id).committed(), ResourceFieldPhysicalSurface.Condition.of(latest.cell(id)));
            var growthBefore = FrontierV3ResourceFieldObservation.observe(level, latest, closed, id, "native:growth-before");
            var projection = closed.beginCanonicalProjection(accepted, growth, "projection:native:independent-growth", growthBefore);
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1), 3);
            projection = projection.confirm(id, FrontierV3ResourceFieldObservation.observe(level, latest, projection, id, "native:growth-written"));
            var grownAgain = new FrontierCanonicalState<>(accepted.worldId(), new Revision(3), new SimInstant(3), latest.advanceGrowth(id));
            var currentPhysical = FrontierV3ResourceFieldObservation.observe(level, grownAgain.state(), projection, id, "native:later-growth");
            var retired = projection.acknowledgeCanonicalProjection(grownAgain, id, "projection:native:independent-growth", currentPhysical);
            helper.assertTrue(retired.cell(id).pending().isEmpty() && retired.cell(id).committed().growthStage() == 1
                    && grownAgain.state().cell(id).growthStage() == 2, "late projection receipt retains actual age1 before projecting current age2");
            helper.succeed();
        });
    }

    private static boolean rejects(Runnable action) {
        try { action.run(); return false; } catch (IllegalArgumentException expected) { return true; }
    }
}
