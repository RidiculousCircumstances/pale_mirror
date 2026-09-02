package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Loaded field/depot receipt proof for the exact 64-slot harvest boundary. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ResourceSiteHarvestGameTests {
    // Keep the 8x8 fixture in the upper half of its declared 38x48x38 air cell.
    // The complete GameTest catalogue includes legacy tests that use low local Y
    // coordinates; sharing their horizontal allocator cell must not make a field
    // baseline depend on their temporary geometry.
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(2, 30, 2);

    private FrontierV3ResourceSiteHarvestGameTests() { }

    // The fixture writes an 8x8 field plus an adjacent chest.  Use the 38x48x38
    // vanilla air template, not the 1x1 `mobs/empty` template, so GameTest's
    // layout allocator reserves the complete physical footprint.
    @GameTest(batch = "pm-frontier-v3-resource-harvest", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void exactMatureFieldBecomesOneTaggedDepotStackAndRecovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(helper.absolutePos(FIXTURE_ORIGIN)); prepare(level, site);
        helper.runAfterDelay(10, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level); PhysicalIntentId intent = new PhysicalIntentId("intent:site-harvest-game-test");
            ledger.reserve(site.id(), intent); helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                    "the harvest fixture must first materialize an exact active stage-zero field");
            ledger.activate(site.id());
            BlockPos chestPosition = helper.absolutePos(FIXTURE_ORIGIN.offset(10, 0, 0));
            level.setBlock(chestPosition, Blocks.AIR.defaultBlockState(), 3); level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
            SubjectId depot = new SubjectId("container:resource-harvest-game-test"); ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, depot);
            helper.assertTrue(chest != null, "the isolated harvest fixture must claim its fresh exact depot chest");
            ExactItemStack output = new ExactItemStack(new SubjectId("item:site-harvest-game-test-wheat"), new SubjectId("settlement:1"), "minecraft:wheat", 64,
                    new InventoryCustody.ContainerSlot(depot, 4));
            helper.assertValueEqual(FrontierV3ResourceSiteHarvestExecutor.precondition(level, site, ledger, chest, output),
                    FrontierV3ResourceSiteHarvestExecutor.Precondition.WAITING_FOR_FIELD_PROJECTION,
                    "a fully owned stage-zero field waits for the bounded projector instead of becoming a false harvest conflict");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 7),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "the fixture must use the production stage projector to mature all crops");
            helper.assertTrue(site.soilSlots().stream().allMatch(soil -> level.getBlockState(new BlockPos(soil.x(), soil.y(), soil.z())).is(Blocks.FARMLAND)),
                    "the fixture must retain all 64 owned farmland cells");
            helper.assertTrue(site.irrigationSlots().stream().allMatch(irrigation -> level.getBlockState(new BlockPos(irrigation.x(), irrigation.y(), irrigation.z()))
                            .equals(Blocks.WATER.defaultBlockState())), "the fixture must retain its four source-water irrigation cells");
            String cropMismatch = site.cropSlots().stream().filter(crop -> !level.getBlockState(new BlockPos(crop.x(), crop.y(), crop.z()))
                    .equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7))).findFirst().map(crop -> crop + "="
                    + level.getBlockState(new BlockPos(crop.x(), crop.y(), crop.z()))).orElse("none");
            helper.assertTrue(cropMismatch.equals("none"), "the fixture must contain all 64 owned mature crops: " + cropMismatch);
            helper.assertValueEqual(FrontierV3ResourceSiteHarvestExecutor.precondition(level, site, ledger, chest, output),
                    FrontierV3ResourceSiteHarvestExecutor.Precondition.READY,
                    "the same exact field becomes harvestable only after its owner projects maturity");
            helper.assertTrue(chest != null && FrontierV3ResourceSiteHarvestExecutor.apply(level, ledger, site, chest, output),
                    "one receipt atomically resets all owned crops and writes one exact tagged output stack");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, 0) && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(4), output),
                    "the visible field and depot retain the only 64-wheat postcondition");
            CompoundTag saved = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(saved, level.registryAccess());
            helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.completePostcondition(level, site, ledger, chest, output),
                    "a restart confirms the already-observed receipt instead of replaying the harvest");
            chest.setItem(4, net.minecraft.world.item.ItemStack.EMPTY);
            helper.assertFalse(FrontierV3ResourceSiteHarvestExecutor.completePostcondition(level, site, ledger, chest, output),
                    "a missing output is conflict evidence and is never replaced by postcondition inspection");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-recovery", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void coldProjectionClaimSurvivesRestartAsTheSameOwnedField(GameTestHelper helper) {
        // Every GameTest has its own template cell. Keep the complete 8x8 footprint
        // inside that cell: a remote local offset can overlap another test's template
        // in the complete suite and is not evidence about the production executor.
        ServerLevel level = helper.getLevel(); ResourceSite site = field(helper.absolutePos(FIXTURE_ORIGIN), "site:resource-harvest-cold-projection");
        prepare(level, site);
        helper.runAfterDelay(10, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
            PhysicalIntentId projection = new PhysicalIntentId("intent:site-projection-resource-harvest-cold-projection");
            PhysicalIntentId preparation = new PhysicalIntentId("intent:site-prepare-resource-harvest-game-test");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.baseline(level, site),
                    "the recovery fixture must retain its isolated complete neutral baseline before the projection write");
            ledger.reserve(site.id(), projection);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                    "a COLD-complete field must first retain the complete neutral-baseline projection");
            ledger.activate(site.id());
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 3),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "the durable projection claim owns its observed field stage");
            CompoundTag saved = ledger.save(new CompoundTag(), level.registryAccess());
            ledger = FrontierV3ResourceSiteLedger.load(saved, level.registryAccess());
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.reconcileAfterRestart(level, ledger, site, preparation, 3),
                    FrontierV3ResourceSiteExecutor.RestartReconciliation.CURRENT,
                    "a restarted field accepts its own persisted COLD projection claim instead of manufacturing a conflict");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, 3)
                            && ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.ACTIVE,
                    "recovery preserves the observed field and active exact ownership");
            helper.succeed();
        });
    }

    private static ResourceSite field(BlockPos origin) { return field(origin, "site:resource-harvest-game-test"); }
    private static ResourceSite field(BlockPos origin, String id) {
        List<BlockPosition> crops = new ArrayList<>(64);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) crops.add(new BlockPosition(origin.getX() + x, origin.getY(), origin.getZ() + z));
        return new ResourceSite(new SubjectId(id), new SubjectId("settlement:1"), new SubjectId("structure:1-farm"), ResourceSiteKind.WHEAT_FIELD, crops);
    }
    private static void prepare(ServerLevel level, ResourceSite site) {
        site.soilSlots().forEach(soil -> { BlockPos position = new BlockPos(soil.x(), soil.y(), soil.z()); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(position, Blocks.GRASS_BLOCK.defaultBlockState(), 3); });
        site.irrigationSlots().forEach(irrigation -> { BlockPos position = new BlockPos(irrigation.x(), irrigation.y(), irrigation.z()); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(position, Blocks.GRASS_BLOCK.defaultBlockState(), 3); });
        site.cropSlots().forEach(crop -> level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), Blocks.AIR.defaultBlockState(), 3));
        int minX = site.cropSlots().stream().mapToInt(BlockPosition::x).min().orElseThrow(), maxX = site.cropSlots().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        site.cropSlots().stream().filter(crop -> crop.x() == minX).forEach(crop -> level.setBlock(new BlockPos(crop.x() - 1, crop.y(), crop.z()), Blocks.GLOWSTONE.defaultBlockState(), 3));
        site.cropSlots().stream().filter(crop -> crop.x() == maxX).forEach(crop -> level.setBlock(new BlockPos(crop.x() + 1, crop.y(), crop.z()), Blocks.GLOWSTONE.defaultBlockState(), 3));
    }

}
