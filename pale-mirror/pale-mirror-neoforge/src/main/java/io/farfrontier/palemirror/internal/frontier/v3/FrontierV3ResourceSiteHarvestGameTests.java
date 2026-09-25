package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestLineage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
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
import java.util.Optional;

/** Loaded field/depot receipt proof for the exact 64-slot harvest boundary. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ResourceSiteHarvestGameTests {
    private FrontierV3ResourceSiteHarvestGameTests() { }

    @GameTest(batch = "pm-frontier-v3-resource-harvest", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 40)
    public static void secondHarvestAfterRegrowthWritesItsOwnOutputWithoutReplacingTheFirst(GameTestHelper helper) {
        var level = helper.getLevel(); var site = field(fixtureOrigin(helper), "site:two-harvest-receipts");
        prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            var ledger = FrontierV3ResourceSiteLedger.fixture();
            ledger.reserve(site.id(), new PhysicalIntentId("intent:two-harvest-field"));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site), "initial field is physically owned");
            ledger.activate(site.id());
            var last = site.cropSlots().getLast();
            var chestPos = new BlockPos(last.x() + 3, last.y(), last.z());
            level.setBlock(chestPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(chestPos.below(), Blocks.STONE.defaultBlockState(), 3);
            var depot = new SubjectId("container:two-harvest-receipts");
            var chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPos, depot);
            helper.assertTrue(chest != null, "exact depot must be claimed");
            var first = new ExactItemStack(new SubjectId("item:first-harvest-wheat"), new SubjectId("settlement:1"),
                    "minecraft:wheat", 64, new InventoryCustody.ContainerSlot(depot, 4));
            var second = new ExactItemStack(new SubjectId("item:second-harvest-wheat"), new SubjectId("settlement:1"),
                    "minecraft:wheat", 64, new InventoryCustody.ContainerSlot(depot, 5));
            FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 7);
            helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.apply(level, ledger, site, chest, first), "first physical receipt succeeds");
            ledger = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            helper.assertTrue(ledger.hasHarvestReceipt(site.id(), first), "first fence survives recovery");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 0),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "regrowth retires terminal AIR cursor");
            ledger = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 7);
            helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.apply(level, ledger, site, chest, second), "second output has its own receipt");
            helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(4), first), "first physical output is not replaced");
            helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(5), second), "second physical output is exact");
            helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.completePostcondition(level, site, ledger, chest, second), "second terminal receipt is reconcilable");
            helper.assertFalse(FrontierV3ResourceSiteHarvestExecutor.apply(level, ledger, site, chest, second), "retry must not mint output again");
            helper.succeed();
        });
    }

    // The fixture writes an 8x8 field plus an adjacent chest.  Use the 38x48x38
    // vanilla air template, not the 1x1 `mobs/empty` template, so GameTest's
    // layout allocator reserves the complete physical footprint.
    @GameTest(batch = "pm-frontier-v3-resource-harvest", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void exactMatureFieldBecomesOneTaggedDepotStackAndRecovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper)); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture(); PhysicalIntentId intent = new PhysicalIntentId("intent:site-harvest-game-test");
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
                    "one receipt writes one exact tagged output stack without unbounded regrowth");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 64)
                            && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(4), output),
                    "the terminal AIR cursor and depot retain the exact 64-wheat receipt until bounded regrowth");
            FrontierV3ResourceSiteLedger.NativeGrowthFence nativeFence = new FrontierV3ResourceSiteLedger.NativeGrowthFence(
                    "crop-grow-pre", site.cropSlots().getFirst(), 0, ResourceSiteLifecycle.MATURE_STAGE);
            ledger.recordNativeGrowthFence(site.id(), nativeFence);
            CompoundTag saved = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(saved, level.registryAccess());
            helper.assertValueEqual(ledger.nativeGrowthFence(site.id()), nativeFence,
                    "the first blocked native-growth edge survives restart separately from the stable field claim");
            helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.completePostcondition(level, site, ledger, chest, output),
                    "a restart confirms the already-observed receipt instead of replaying the harvest");
            chest.setItem(4, net.minecraft.world.item.ItemStack.EMPTY);
            helper.assertFalse(FrontierV3ResourceSiteHarvestExecutor.completePostcondition(level, site, ledger, chest, output),
                    "a missing output is conflict evidence and is never replaced by postcondition inspection");
            helper.assertFalse(FrontierV3ResourceSiteHarvestExecutor.completeRunning(level, site, ledger, chest, output),
                    "a fenced receipt removed before confirmation is visible conflict evidence, never a replacement mint");
            ledger.restoreOne(site.id(), 63);
            helper.assertFalse(ledger.hasHarvestReceipt(site.id(), output),
                    "the first confirmed successor restore retires only the predecessor receipt fence");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-harvest", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void forcedNativeGrowthIsRestoredToTheExactOwnedStage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-native-post-fence"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
            ledger.reserve(site.id(), new PhysicalIntentId("intent:site-harvest-native-post-fence"));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                    "native post-fence fixture must begin with one exact owned stage-zero field"); ledger.activate(site.id());
            BlockPosition crop = site.cropSlots().getFirst(); BlockPos position = new BlockPos(crop.x(), crop.y(), crop.z());
            level.setBlock(position, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1), 2);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.restoreNativeGrowthPostcondition(level, ledger, site, position),
                    "a native post-event override must be returned to the exact active owner stage");
            helper.assertTrue(level.getBlockState(position).equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0)),
                    "the post-fence must retain the exact stage-zero crop instead of adopting native progress");
            helper.assertValueEqual(ledger.nativeGrowthFence(site.id()), new FrontierV3ResourceSiteLedger.NativeGrowthFence(
                    "crop-grow-post", crop, 1, 0), "the restored native event retains one exact diagnostic witness");
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
            helper.assertFalse(FrontierV3ResourceSiteExecutor.restoreNativeGrowthPostcondition(level, ledger, site, position),
                    "the native-growth veto must not recreate a removed crop");
            helper.assertTrue(level.getBlockState(position).isAir(), "missing crop remains physical evidence");
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
            helper.assertFalse(FrontierV3ResourceSiteExecutor.restoreNativeGrowthPostcondition(level, ledger, site, position),
                    "the native-growth veto must not overwrite foreign player/world blocks");
            helper.assertTrue(level.getBlockState(position).is(Blocks.STONE), "foreign physical state remains untouched");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-recovery", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void composedColdTerminalReceiptAdmitsOnlyItsNamedSuccessorProjection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-composed-terminal"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
            PhysicalIntentId intent = new PhysicalIntentId("intent:site-harvest-resource-harvest-composed-terminal");
            ledger.reserve(site.id(), intent);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site), "the exact predecessor receipt begins with one owned field");
            ledger.activate(site.id());
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, ResourceSiteLifecycle.MATURE_STAGE),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "the predecessor receipt begins mature");
            for (int cursor = 0; cursor < site.cropSlots().size(); cursor++) {
                level.setBlock(new BlockPos(site.cropSlots().get(cursor).x(), site.cropSlots().get(cursor).y(), site.cropSlots().get(cursor).z()), Blocks.AIR.defaultBlockState(), 3);
                ledger.harvestOne(site.id(), cursor + 1);
            }
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 64)
                            && !FrontierV3ResourceSiteExecutor.baseline(level, site),
                    "the COLD terminal receipt retains its exact irrigated/farmland predecessor surface, not a neutral baseline");
            ResourceSiteHarvestLineage lineage = new ResourceSiteHarvestLineage(
                    new SubjectId("job:site-harvest-resource-harvest-composed-terminal"), new SubjectId("task:settlement-1-harvest"),
                    new SubjectId("resident:1-13"),
                    new SubjectId("custody:field-actor-site-harvest-resource-harvest-composed-terminal"),
                    new SubjectId("custody:container-1-depot"),
                    new SubjectId("item:site-harvest-resource-harvest-composed-terminal-wheat"), 1L,
                    new BodyPosition(site.cropSlots().getLast().x(), site.cropSlots().getLast().y(), site.cropSlots().getLast().z()), intent,
                    new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 0), true, Optional.empty(), Optional.empty());
            ResourceSiteLifecycle successor = new ResourceSiteLifecycle(site.id(), ResourceSitePhase.GROWING, 2L, 3,
                    Optional.empty(), Optional.empty(), Optional.of(lineage));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.allowsComposedTerminalLedgerRehydration(successor, 3, 0, true, true),
                    "only the exact composed terminal lineage may re-establish its current successor projection");
            helper.assertFalse(FrontierV3ResourceSiteExecutor.allowsComposedTerminalLedgerRehydration(successor, 3, 0, true, false),
                    "the same terminal-looking surface without its named canonical consumer remains conflict evidence");
            FrontierV3ResourceSiteLedger successorLedger = FrontierV3ResourceSiteLedger.fixture();
            successorLedger.reserveComposedTerminalSuccessor(site.id(), new PhysicalIntentId("intent:site-projection-resource-harvest-composed-terminal"));
            successorLedger.activate(site.id());
            successorLedger.beginProjection(site.id(), new FrontierV3ResourceSiteLedger.ProjectionTransition(
                    "growth:" + site.id().value() + ":e2", ResourceSiteLifecycle.MATURE_STAGE, 64, 3, 0, 0,
                    site.cropSlots().size(), FrontierV3ResourceSiteLedger.ProjectionMode.ADVANCE));
            for (int cursor = 0; cursor < 8; cursor++) {
                BlockPosition crop = site.cropSlots().get(cursor);
                level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3), 3);
                successorLedger.advanceProjection(site.id(), cursor + 1);
            }
            CompoundTag persisted = successorLedger.save(new CompoundTag(), level.registryAccess());
            successorLedger = FrontierV3ResourceSiteLedger.load(persisted, level.registryAccess());
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, site, successorLedger.claim(site.id())),
                    "restart accepts the exact terminal-receipt prefix instead of treating its remaining farmland as foreign baseline drift");
            for (int cursor = 8; cursor < site.cropSlots().size(); cursor++) {
                BlockPosition crop = site.cropSlots().get(cursor);
                level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3), 3);
                successorLedger.advanceProjection(site.id(), cursor + 1);
            }
            successorLedger.completeProjection(site.id()); successorLedger.updateStage(site.id(), 3);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, 3)
                            && successorLedger.claim(site.id()).harvestedCropSlots() == 0,
                    "the restarted exact successor ends with its current stage and no retired terminal cursor");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-harvest", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void oneObservedCropPersistsAsPartialFieldWithoutCreatingTheDepotOutput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-partial-progress"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
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
    public static void coldCropReceiptMaterializesItsCurrentPartialFieldOnNaturalFirstLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-cold-current"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectHarvestProgress(level, ledger, site, 1),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED,
                    "a naturally loaded COLD receipt must project its exact first completed crop rather than recreate crop-0");
            helper.assertTrue(ledger.claim(site.id()).harvestedCropSlots() == 1
                            && FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 1),
                    "the resource ledger and visible field retain the same one-crop deferred aftermath");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectHarvestProgress(level, ledger, site, 1),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.CURRENT,
                    "repeated natural observation must not replay or reset the COLD crop receipt");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-recovery", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void coldTerminalCompletesAnExactRunningHotPrefixBeforeItsOneReceipt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-cold-terminal-prefix"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
            PhysicalIntentId intent = new PhysicalIntentId("intent:site-harvest-cold-terminal-prefix");
            ledger.reserve(site.id(), intent);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                    "the prefix fixture begins with one exact owned field"); ledger.activate(site.id());
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, ResourceSiteLifecycle.MATURE_STAGE),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "the prefix fixture begins mature");
            for (int index = 0; index < 21; index++) {
                BlockPosition crop = site.cropSlots().get(index);
                level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), Blocks.AIR.defaultBlockState(), 3);
                ledger.harvestOne(site.id(), index + 1);
            }
            CompoundTag saved = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(saved, level.registryAccess());
            helper.assertTrue(ledger.claim(site.id()).harvestedCropSlots() == 21
                            && FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 21),
                    "restart retains the exact owned HOT prefix before COLD terminal completion");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectHarvestProgress(level, ledger, site, 64),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED,
                    "the one exact terminal suffix advances from the retained prefix instead of being relabelled as drift");
            helper.assertTrue(ledger.claim(site.id()).harvestedCropSlots() == 64
                            && FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 64),
                    "the completed terminal cursor remains the separate exact receipt witness");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-harvest", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void completedHotCursorReceiptsOnceWhileRestartOnlyConfirms(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-running-completion"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
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
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 64),
                    "the terminal receipt must leave regrowth to the bounded lifecycle projector");
            CompoundTag saved = ledger.save(new CompoundTag(), level.registryAccess());
            ledger = FrontierV3ResourceSiteLedger.load(saved, level.registryAccess());
            helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.completeRunning(level, site, ledger, chest, output),
                    "a restarted inspector confirms the fenced receipt instead of replaying it");
            chest.setItem(2, net.minecraft.world.item.ItemStack.EMPTY);
            helper.assertFalse(FrontierV3ResourceSiteHarvestExecutor.completeRunning(level, site, ledger, chest, output),
                    "a missing restarted output is visible conflict evidence, never a duplicate receipt");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-recovery", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void successorRegrowthKeepsOneRestartableReverseHarvestPrefix(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-successor-regrowth"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
            ledger.reserve(site.id(), new PhysicalIntentId("intent:site-harvest-successor-regrowth"));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site), "successor fixture must begin from its owned neutral field");
            ledger.activate(site.id());
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 7),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "successor fixture must begin mature");
            for (int index = 0; index < site.cropSlots().size(); index++) {
                BlockPosition crop = site.cropSlots().get(index); level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), Blocks.AIR.defaultBlockState(), 3);
                ledger.harvestOne(site.id(), index + 1);
            }
            // Restore the suffix in the only order represented by the claim: after restoring
            // N-1, exactly [0,N) remains AIR.  A persisted middle cursor is therefore an
            // exact restart witness, not an incomplete/foreign successor field.
            for (int index = site.cropSlots().size() - 1; index >= site.cropSlots().size() - 8; index--) {
                BlockPosition crop = site.cropSlots().get(index);
                level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
                ledger.restoreOne(site.id(), index);
            }
            CompoundTag saved = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(saved, level.registryAccess());
            helper.assertTrue(ledger.claim(site.id()).harvestedCropSlots() == 56
                            && FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 56),
                    "a restarted successor must recognize its bounded reverse-regrowth prefix as the one owned current field");
            BlockPosition foreign = site.cropSlots().get(55); level.setBlock(new BlockPos(foreign.x(), foreign.y(), foreign.z()), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
            helper.assertFalse(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 56),
                    "a non-prefix regrowth remains foreign/damaged evidence and is never adopted as successor progress");
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
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
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

    @GameTest(batch = "pm-frontier-v3-resource-recovery", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void coldHarvestPrefixRestartsFromConfirmedNeutralField(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-cold-prefix"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            PhysicalIntentId preparationId = new PhysicalIntentId("intent:site-prepare-resource-harvest-cold-prefix");
            BlockPosition origin = site.cropSlots().getFirst();
            PhysicalIntent preparation = new PhysicalIntent(preparationId, PhysicalIntentKind.RESOURCE_SITE_PREPARATION,
                    PhysicalIntentStatus.PREPARED, site.id(), PhysicalIntentRoleBinding.sitePreparation(site.id(), new SubjectId("job:site-prepare-resource-harvest-cold-prefix")),
                    new FixedPosition(FixedScalar.whole(origin.x()), FixedScalar.whole(origin.y()), FixedScalar.whole(origin.z())), 0,
                    PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION).withStatus(PhysicalIntentStatus.CONFIRMED,
                    Optional.of(new PhysicalObservationId("observation:site-prepare-resource-harvest-cold-prefix")));
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
            helper.assertTrue(FrontierV3ResourceSiteExecutor.baseline(level, site),
                    "the unvisited restart fixture must begin as the complete neutral field, not a fabricated partial prefix");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.reconcileHarvestAfterRestart(level, ledger, site, preparation, 63),
                    FrontierV3ResourceSiteExecutor.RestartReconciliation.RECREATED,
                    "a confirmed COLD prefix recreates complete owned infrastructure then its exact 63-slot aftermath after restart");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 63)
                            && ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.ACTIVE,
                    "restart retains water/support, every crop slot, and the exact COLD prefix under one active field claim");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-recovery", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full",
            timeoutTicks = 40)
    public static void coldHarvestRestartExtendsAnOwnedPartialFieldWithoutReplacingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-harvest-cold-owned-prefix"); prepare(level, site);
        runWhenLit(helper, level, site, () -> {
            PhysicalIntentId preparationId = new PhysicalIntentId("intent:site-prepare-resource-harvest-cold-owned-prefix");
            BlockPosition origin = site.cropSlots().getFirst();
            PhysicalIntent preparation = new PhysicalIntent(preparationId, PhysicalIntentKind.RESOURCE_SITE_PREPARATION,
                    PhysicalIntentStatus.PREPARED, site.id(), PhysicalIntentRoleBinding.sitePreparation(site.id(), new SubjectId("job:site-prepare-resource-harvest-cold-owned-prefix")),
                    new FixedPosition(FixedScalar.whole(origin.x()), FixedScalar.whole(origin.y()), FixedScalar.whole(origin.z())), 0,
                    PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION).withStatus(PhysicalIntentStatus.CONFIRMED,
                    Optional.of(new PhysicalObservationId("observation:site-prepare-resource-harvest-cold-owned-prefix")));
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.classifyHarvestRestart(level, ledger, site, preparation, 63).state(),
                    FrontierV3ResourceSiteExecutor.HarvestRestartPhysicalState.NEUTRAL_UNCLAIMED,
                    "a complete neutral facility is typed separately from an owned partial field");
            ledger.reserve(site.id(), new PhysicalIntentId("intent:site-projection-resource-harvest-cold-owned-prefix"));
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.classifyHarvestRestart(level, ledger, site, preparation, 63).state(),
                    FrontierV3ResourceSiteExecutor.HarvestRestartPhysicalState.UNKNOWN,
                    "a pending claim is not silently adopted as an active physical field after restart");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site), "the owned-prefix fixture must first materialize one complete field");
            ledger.activate(site.id());
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 7),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "the owned-prefix fixture must start from the mature current field");
            for (int index = 0; index < 12; index++) {
                BlockPosition crop = site.cropSlots().get(index);
                level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), Blocks.AIR.defaultBlockState(), 3);
                ledger.harvestOne(site.id(), index + 1);
            }
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 12),
                    "the pre-restart world must retain the exact owned 12-slot COLD aftermath");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.classifyHarvestRestart(level, ledger, site, preparation, 63).state(),
                    FrontierV3ResourceSiteExecutor.HarvestRestartPhysicalState.OWNED_BEHIND,
                    "one plan-identified owned partial field is eligible only for its canonical COLD catch-up");
            // The durable projection claim is the only legitimate owner for a COLD-first
            // field.  The preparation intent may no longer exist after that hand-off; restart
            // must classify the same owned partial field rather than manufacture CONFLICT.
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.classifyHarvestRestart(level, ledger, site, null, 63).state(),
                    FrontierV3ResourceSiteExecutor.HarvestRestartPhysicalState.OWNED_BEHIND,
                    "an active projection claim remains typed owned-behind without a stale preparation intent");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.reconcileHarvestAfterRestart(level, ledger, site, null, 63),
                    FrontierV3ResourceSiteExecutor.RestartReconciliation.RECREATED,
                    "restart extends a matching owned prefix to the durable COLD cursor instead of misclassifying it as foreign drift");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 63)
                            && ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.ACTIVE,
                    "the repaired field keeps its water/support and all slots while advancing only the remaining owned COLD aftermath");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.classifyHarvestRestart(level, ledger, site, null, 63).state(),
                    FrontierV3ResourceSiteExecutor.HarvestRestartPhysicalState.OWNED_EXACT,
                    "the canonical slot-plan projection distinguishes current ownership from a merely matching-looking facility");
            BlockPosition foreign = site.cropSlots().getFirst(); level.setBlock(new BlockPos(foreign.x(), foreign.y(), foreign.z()), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.classifyHarvestRestart(level, ledger, site, preparation, 63).state(),
                    FrontierV3ResourceSiteExecutor.HarvestRestartPhysicalState.FOREIGN_OR_DAMAGED,
                    "a damaged owned slot remains a typed conflict and is never reinterpreted as a restart prefix");
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
        return new ResourceSite(new SubjectId(id), new SubjectId("settlement:1"), new SubjectId("structure:1-farm"), ResourceSiteKind.WHEAT_FIELD, io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan.initialGrayboxLayout(crops));
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
