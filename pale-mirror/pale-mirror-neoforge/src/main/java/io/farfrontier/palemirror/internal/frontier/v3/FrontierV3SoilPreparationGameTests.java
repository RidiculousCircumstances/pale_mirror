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

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 40)
    public static void firstMaterializationRetainsAdmittedCoarseGroundWithoutCreditingPreparation(GameTestHelper helper) {
        var level = helper.getLevel();
        var original = helper.absolutePos(new BlockPos(4, 8, 4));
        var extension = helper.absolutePos(new BlockPos(6, 8, 4));
        var first = fieldCell(1, original); var added = fieldCell(2, extension);
        var coarse = ResourceFieldCycle.Soil.fromBlock("minecraft:coarse_dirt");
        var layout = new ResourceFieldLayout(1, 2, List.of(first), List.of())
                .extend(List.of(added), 3, Map.of(added.id(), coarse));
        var site = new ResourceSite(new SubjectId("site:coarse-first-materialization"),
                new SubjectId("settlement:coarse-first-materialization"),
                new SubjectId("structure:coarse-first-materialization"), ResourceSiteKind.WHEAT_FIELD, layout);
        var target = ResourceFieldCycle.restore(site.id(), layout, 1, Map.of(
                first.id(), new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND,
                        ResourceFieldCycle.Crop.GROWING, 0, false, false),
                added.id(), new ResourceFieldCycle.CellState(coarse, ResourceFieldCycle.Crop.ABSENT, 0, false, false)));
        for (var pos : List.of(original, extension)) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(extension, Blocks.STONE.defaultBlockState(), 3);
        helper.runAtTickTime(1, () -> {
            FrontierV3ResourceFieldInitialWriter.reserve(level, site,
                    new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:coarse-first-materialization"), target);
            refuseForeignGround(helper, site, added, original, () -> {
                level.setBlock(extension, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                finishInitial(helper, site, () -> {
                    var witness = FrontierV3ResourceFieldWitness.claimed(site.id(), 1, ResourceFieldPhysicalSurface.fromCycle(target));
                    FrontierV3ResourceFieldInitialWriter.activate(level, site, target, witness);
                    helper.assertTrue(level.getBlockState(extension).is(Blocks.COARSE_DIRT)
                            && level.getBlockState(extension.above()).isAir()
                            && target.expectedWorkOutcome(added.id()) == ResourceFieldCycle.WorkOutcome.TILLED
                            && target.harvestedCount() == 0, "materialization retains declared ground and cannot stand in for real tillage");
                    helper.succeed();
                });
            });
        });
    }
    private static void refuseForeignGround(GameTestHelper helper, ResourceSite site, ResourceFieldLayout.Cell added,
                                            BlockPos original, Runnable completed) {
        var level = helper.getLevel();
        var refused = FrontierV3ResourceFieldInitialWriter.writePreparedBatch(level, site, writes -> true);
        if (refused.status() == FrontierV3ResourceFieldInitialWriter.Status.RETRY) {
            helper.runAfterDelay(1, () -> refuseForeignGround(helper, site, added, original, completed)); return;
        }
        helper.assertTrue(refused.status() == FrontierV3ResourceFieldInitialWriter.Status.FOREIGN
                && refused.rejected().orElseThrow().step().index() == 2
                && refused.rejected().orElseThrow().step().position().equals(added.soil().support())
                && refused.rejected().orElseThrow().observed().orElseThrow().is(Blocks.STONE)
                && level.getBlockState(original).is(Blocks.DIRT), "foreign extension blocks the whole unprepared batch before any write");
        completed.run();
    }
    private static void finishInitial(GameTestHelper helper, ResourceSite site, Runnable completed) {
        var owner = (FrontierV3ResourceSiteLedger.FieldInitialization) FrontierV3ResourceSiteLedger.get(helper.getLevel()).fieldClaim(site.id());
        if (owner.cursor().complete()) { completed.run(); return; }
        var result = FrontierV3ResourceFieldInitialWriter.writePreparedBatch(helper.getLevel(), site, writes -> true);
        if (result.status() == FrontierV3ResourceFieldInitialWriter.Status.FOREIGN
                || result.status() == FrontierV3ResourceFieldInitialWriter.Status.AMBIGUOUS)
            helper.fail("initial projection rejected admitted ground: " + result);
        helper.runAfterDelay(1, () -> finishInitial(helper, site, completed));
    }
    private static ResourceFieldLayout.Cell fieldCell(long id, BlockPos soil) {
        var support = SurfaceAnchor.at(soil.getX(), soil.getY(), soil.getZ());
        return new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(id), support.support().offset(0, 1, 0), support, support);
    }
    private FrontierV3SoilPreparationGameTests() { }
}
