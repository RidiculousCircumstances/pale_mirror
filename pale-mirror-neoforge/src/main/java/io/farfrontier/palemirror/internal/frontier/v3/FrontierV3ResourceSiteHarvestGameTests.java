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
    private FrontierV3ResourceSiteHarvestGameTests() { }

    // The fixture writes an 8x8 field plus an adjacent chest.  Use the 38x48x38
    // vanilla air template, not the 1x1 `mobs/empty` template, so GameTest's
    // layout allocator reserves the complete physical footprint.
    @GameTest(batch = "pm-frontier-v3-resource-harvest", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void exactMatureFieldBecomesOneTaggedDepotStackAndRecovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper)); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level); PhysicalIntentId intent = new PhysicalIntentId("intent:site-harvest-game-test");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.baseline(level, site), "the harvest fixture must begin from its exact neutral baseline: " + baselineIssue(level, site));
            ledger.reserve(site.id(), intent); helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                    "the harvest fixture must first materialize an exact active stage-zero field: " + fieldIssue(level, site));
            ledger.activate(site.id());
            BlockPosition lastCrop = site.cropSlots().getLast();
            BlockPos chestPosition = new BlockPos(lastCrop.x(), lastCrop.y(), lastCrop.z()).offset(3, 0, 0);
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

    @GameTest(batch = "pm-frontier-v3-resource-harvest", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void oneObservedCropPersistsAsPartialFieldWithoutCreatingTheDepotOutput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-partial-progress"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
            ledger.reserve(site.id(), new PhysicalIntentId("intent:site-harvest-partial-progress"));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.baseline(level, site), "partial fixture must begin from its exact neutral baseline: " + baselineIssue(level, site));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                    "partial fixture must materialize its exact owned field: " + fieldIssue(level, site)); ledger.activate(site.id());
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 7),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "partial fixture must begin with mature crops");
            BlockPosition first = site.cropSlots().getFirst(); level.setBlock(new BlockPos(first.x(), first.y(), first.z()), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 1),
                    "one AIR cell followed by 63 mature cells is the sole first-crop postcondition");
            ledger.harvestOne(site.id(), 1);
            BlockPosition lastCrop = site.cropSlots().getLast(); BlockPos chestPosition = new BlockPos(lastCrop.x(), lastCrop.y(), lastCrop.z()).offset(3, 0, 0);
            level.setBlock(chestPosition, Blocks.AIR.defaultBlockState(), 3); level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
            SubjectId depot = new SubjectId("container:resource-harvest-partial-progress");
            ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, depot);
            ExactItemStack output = new ExactItemStack(new SubjectId("item:site-harvest-partial-progress-wheat"), new SubjectId("settlement:1"), "minecraft:wheat", 64,
                    new InventoryCustody.ContainerSlot(depot, 3));
            helper.assertValueEqual(FrontierV3ResourceSiteHarvestExecutor.precondition(level, site, ledger, chest, output),
                    FrontierV3ResourceSiteHarvestExecutor.Precondition.HARVESTING,
                    "an owned partial crop frontier is in progress rather than a false physical conflict");
            CompoundTag saved = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(saved, level.registryAccess());
            helper.assertTrue(ledger.claim(site.id()).harvestedCropSlots() == 1
                            && FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 1),
                    "restart retains the exact partial cursor and never projects a whole-field endpoint");
            helper.assertTrue(level.getBlockState(new BlockPos(site.cropSlots().get(1).x(), site.cropSlots().get(1).y(), site.cropSlots().get(1).z()))
                            .equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)),
                    "the next unobserved crop remains a real mature block");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-harvest", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void completedHotCursorReceiptsOnceWhileRestartOnlyConfirms(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-running-completion"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
            PhysicalIntentId intent = new PhysicalIntentId("intent:site-harvest-running-completion"); ledger.reserve(site.id(), intent);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.baseline(level, site), "completion fixture must begin from its exact neutral baseline: " + baselineIssue(level, site));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                    "completion fixture must materialize its exact owned field: " + fieldIssue(level, site)); ledger.activate(site.id());
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 7),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "completion fixture must begin mature");
            for (int index = 0; index < site.cropSlots().size(); index++) {
                BlockPosition crop = site.cropSlots().get(index); level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), Blocks.AIR.defaultBlockState(), 3);
                ledger.harvestOne(site.id(), index + 1);
            }
            BlockPosition lastCrop = site.cropSlots().getLast(); BlockPos chestPosition = new BlockPos(lastCrop.x(), lastCrop.y(), lastCrop.z()).offset(3, 0, 0);
            level.setBlock(chestPosition, Blocks.AIR.defaultBlockState(), 3); level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
            SubjectId depot = new SubjectId("container:resource-harvest-running-completion"); ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, depot);
            ExactItemStack output = new ExactItemStack(new SubjectId("item:site-harvest-running-completion-wheat"), new SubjectId("settlement:1"), "minecraft:wheat", 64,
                    new InventoryCustody.ContainerSlot(depot, 2));
            helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.completeRunning(level, site, ledger, chest, output),
                    "the final observed HOT cursor must perform its one exact depot receipt");
            helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.completeRunning(level, site, ledger, chest, output),
                    "a restarted inspector confirms the existing receipt instead of replaying it");
            chest.setItem(2, net.minecraft.world.item.ItemStack.EMPTY);
            helper.assertFalse(FrontierV3ResourceSiteHarvestExecutor.completeRunning(level, site, ledger, chest, output),
                    "a missing restarted output is visible conflict evidence, never a duplicate receipt");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-recovery", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void coldProjectionClaimSurvivesRestartAsTheSameOwnedField(GameTestHelper helper) {
        // Every GameTest has its own template cell. Keep the complete 8x8 footprint
        // inside that cell: a remote local offset can overlap another test's template
        // in the complete suite and is not evidence about the production executor.
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-cold-projection");
        prepare(level, site);
        runWhenLit(helper, level, site, () -> {
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
    /** Keeps all field, irrigation, light-border and depot cells inside this test's allocated full-air template. */
    private static BlockPos fixtureOrigin(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(4, 30, 4));
    }
    private static ResourceSite field(BlockPos origin, String id) {
        List<BlockPosition> crops = new ArrayList<>(64);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) crops.add(new BlockPosition(origin.getX() + x, origin.getY(), origin.getZ() + z));
        return new ResourceSite(new SubjectId(id), new SubjectId("settlement:1"), new SubjectId("structure:1-farm"), ResourceSiteKind.WHEAT_FIELD, crops);
    }
    private static void prepare(ServerLevel level, ResourceSite site) {
        // Fixture state must not receive an unrelated random grass tick while
        // this test is proving the executor's exact ownership transition.
        site.soilSlots().forEach(soil -> { BlockPos position = new BlockPos(soil.x(), soil.y(), soil.z()); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(position, Blocks.DIRT.defaultBlockState(), 3); });
        site.irrigationSlots().forEach(irrigation -> { BlockPos position = new BlockPos(irrigation.x(), irrigation.y(), irrigation.z()); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(position, Blocks.DIRT.defaultBlockState(), 3); });
        site.cropSlots().forEach(crop -> level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), Blocks.AIR.defaultBlockState(), 3));
        int minX = site.cropSlots().stream().mapToInt(BlockPosition::x).min().orElseThrow(), maxX = site.cropSlots().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        site.cropSlots().stream().filter(crop -> crop.x() == minX).forEach(crop -> level.setBlock(new BlockPos(crop.x() - 1, crop.y(), crop.z()), Blocks.GLOWSTONE.defaultBlockState(), 3));
        site.cropSlots().stream().filter(crop -> crop.x() == maxX).forEach(crop -> level.setBlock(new BlockPos(crop.x() + 1, crop.y(), crop.z()), Blocks.GLOWSTONE.defaultBlockState(), 3));
    }

    /** The field has no sky in this GameTest template, so wait for real block-light propagation before placing survival-checked crops. */
    private static void runWhenLit(GameTestHelper helper, ServerLevel level, ResourceSite site, Runnable action) {
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(fieldLit(level, site), "harvest fixture light must settle: " + lightingIssue(level, site)))
                .thenExecute(action);
    }

    private static boolean fieldLit(ServerLevel level, ResourceSite site) {
        return site.cropSlots().stream().allMatch(slot -> level.getRawBrightness(new BlockPos(slot.x(), slot.y(), slot.z()), 0) >= 8);
    }

    private static String lightingIssue(ServerLevel level, ResourceSite site) {
        return site.cropSlots().stream().filter(slot -> level.getRawBrightness(new BlockPos(slot.x(), slot.y(), slot.z()), 0) < 8).findFirst()
                .map(slot -> "crop=" + slot + "/light=" + level.getRawBrightness(new BlockPos(slot.x(), slot.y(), slot.z()), 0)).orElse("lit");
    }

    private static String fieldIssue(ServerLevel level, ResourceSite site) {
        return site.soilSlots().stream().filter(slot -> !level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())).is(Blocks.FARMLAND))
                .findFirst().map(slot -> "soil=" + slot + "/" + level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())))
                .orElseGet(() -> site.cropSlots().stream().filter(slot -> !level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z()))
                                .equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0))).findFirst()
                        .map(slot -> "crop=" + slot + "/" + level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())))
                        .orElseGet(() -> site.irrigationSlots().stream().filter(slot -> !level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z()))
                                        .equals(Blocks.WATER.defaultBlockState())).findFirst()
                                .map(slot -> "irrigation=" + slot + "/" + level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())))
                                .orElse("baseline=" + FrontierV3ResourceSiteExecutor.baseline(level, site))));
    }

    private static String baselineIssue(ServerLevel level, ResourceSite site) {
        return site.soilSlots().stream().filter(slot -> !level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())).is(Blocks.DIRT))
                .findFirst().map(slot -> "soil=" + slot + "/" + level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())))
                .orElseGet(() -> site.cropSlots().stream().filter(slot -> !level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())).isAir()).findFirst()
                        .map(slot -> "crop=" + slot + "/" + level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())))
                        .orElseGet(() -> site.irrigationSlots().stream().filter(slot -> !level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())).is(Blocks.DIRT))
                                .findFirst().map(slot -> "irrigation=" + slot + "/" + level.getBlockState(new BlockPos(slot.x(), slot.y(), slot.z())))
                                .orElse("baseline=" + FrontierV3ResourceSiteExecutor.baseline(level, site))));
    }

}
