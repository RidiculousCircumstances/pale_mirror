package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Map;

/** Native provider and retained-prefix evidence; full actor execution remains the ordinary field owner. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SoilPreparationGameTests {
    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void configuredPreparationMatchesNativeToolStepsAndRejectsChangedOrObstructedGround(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var hoe = new ItemStack(Items.IRON_HOE); var coarse = ResourceFieldCycle.Soil.fromBlock("minecraft:coarse_dirt");
        level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3); level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        for (var step : ResourceFieldCycle.Soil.catalog().path(coarse)) {
            var preview = FrontierV3SoilPreparation.result(level, hoe, pos, step).orElseThrow();
            helper.assertTrue(level.getBlockState(pos).equals(FrontierV3FieldBlocks.soil(step.before())), "preview must not mutate soil");
            helper.assertTrue(FrontierV3SoilPreparation.result(level, new ItemStack(Items.IRON_PICKAXE), pos, step).isEmpty(),
                    "a wrong native tool cannot borrow a declared transformation");
            var context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, hoe,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            hoe.useOn(context);
            helper.assertTrue(level.getBlockState(pos).equals(preview), "actual native use must reach this exact intermediate result");
        }
        var step = ResourceFieldCycle.Soil.catalog().step(coarse);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3SoilPreparation.result(level, hoe, pos, step).isEmpty(), "player replacement is not preparation permission");
        level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3); level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3SoilPreparation.result(level, hoe, pos, step).isEmpty(), "occupied crop space prevents native tillage");
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void coarseWorkWitnessRetainsTheIntermediateDirtAcrossSerialization(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var site = new SubjectId("site:soil-native-recovery"); var id = new ResourceFieldLayout.CellId(1);
        var support = SurfaceAnchor.at(pos.getX(), pos.getY(), pos.getZ());
        var layout = new ResourceFieldLayout(1, 2, List.of(new ResourceFieldLayout.Cell(id,
                support.support().offset(0, 1, 0), support, support)), List.of());
        var coarse = ResourceFieldCycle.Soil.fromBlock("minecraft:coarse_dirt");
        var cycle = ResourceFieldCycle.restore(site, layout, 1,
                Map.of(id, new ResourceFieldCycle.CellState(coarse, ResourceFieldCycle.Crop.ABSENT, 0, false, false)));
        var transition = cycle.physicalWorkTransition(id).orElseThrow();
        var pending = FrontierV3ResourceFieldWitness.claimed(site, 1, ResourceFieldPhysicalSurface.fromCycle(cycle))
                .begin(transition, "soil:native-first-step");
        level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3); level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.runAtTickTime(1, () -> {
            var before = FrontierV3ResourceFieldObservation.observe(level, cycle, pending, id, "soil:before");
            helper.assertTrue(before.disposition() == FrontierV3ResourceFieldObservation.Disposition.CURRENT, "coarse dirt is a known predecessor");
            level.setBlock(pos, FrontierV3SoilPreparation.result(level, new ItemStack(Items.IRON_HOE), pos,
                    ResourceFieldCycle.Soil.catalog().step(coarse)).orElseThrow(), 3);
            var applied = FrontierV3ResourceFieldObservation.observe(level, cycle, pending, id, "soil:applied");
            var recovered = FrontierV3ResourceFieldWitness.read(pending.confirm(id, applied).write());
            helper.assertTrue(recovered.cell(id).committed().soil() == ResourceFieldCycle.Soil.DIRT
                            && recovered.cell(id).pending().orElseThrow().transition().before().soil() == coarse,
                    "recovery preserves both original intent and actual intermediate dirt");
            var oldFormat = recovered.write(); oldFormat.putInt("format", 10);
            boolean rejected = false;
            try { FrontierV3ResourceFieldWitness.read(oldFormat); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "old soil-tag grammar must be rejected, never reinterpreted");
            helper.assertTrue(cycle.worked(id).expectedWorkOutcome(id) == ResourceFieldCycle.WorkOutcome.TILLED,
                    "first step cannot declare final farmland or plant a crop");
            helper.succeed();
        });
    }
    private FrontierV3SoilPreparationGameTests() { }
}
