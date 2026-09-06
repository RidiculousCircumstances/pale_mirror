package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Materialized ownership and recovery boundary for one exact 8x8 wheat field. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ResourceSiteGameTests {
    private FrontierV3ResourceSiteGameTests() { }

    @GameTest(batch = "pm-frontier-v3-resource-site-harvest-standing", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void harvestStandingAdmitsOnlyItsOwnPassThroughCropCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = fixtureOrigin(helper);
        BlockPosition anchor = new BlockPosition(floor.getX(), floor.getY(), floor.getZ());
        level.setBlock(floor.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(floor, Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(floor.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
        level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3StandingPosition.aboveExactFloor(level, anchor) == null,
                "ordinary exact standing keeps a mature crop out of a generic feet column");
        helper.assertTrue(FrontierV3StandingPosition.aboveExactFloor(level, floor.above()) == null,
                "a pass-through crop is never reclassified as a supporting floor one cell above its farmland");
        helper.assertFalse(FrontierV3StandingPosition.hasExactStandingColumn(level,
                        new BlockPosition(floor.getX(), floor.getY() + 1, floor.getZ())),
                "a retained COLD cursor cannot claim a crop block itself as physical support");
        helper.assertTrue(FrontierV3StandingPosition.hasExactHarvestFieldStandingColumn(level, anchor),
                "the registered field-worker rule retains the exact farmland support and clear headroom");
        helper.assertValueEqual(FrontierV3StandingPosition.aboveExactHarvestFieldFloor(level, floor), floor.above(),
                "only harvest resolves the real pass-through crop cell above its retained farmland floor");
        helper.assertValueEqual(FrontierV3ResourceSiteHarvestSceneExecutor.harvestStandingPosition(level, floor), floor.above(),
                "the registered harvest provider admits its exact crop-foot station without changing generic standing");
        BlockPos approachFloor = floor.offset(3, 0, 0);
        level.setBlock(approachFloor, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(approachFloor.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(approachFloor.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3ResourceSiteHarvestSceneExecutor.harvestStandingPosition(level, approachFloor), approachFloor.above(),
                "the same registered provider admits an ordinary retained approach column before the field station");
        level.setBlock(floor.above(), Blocks.OAK_FENCE.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3StandingPosition.hasExactHarvestFieldStandingColumn(level, anchor),
                "a full collision obstruction is not disguised as field vegetation");
        helper.assertTrue(FrontierV3StandingPosition.aboveExactHarvestFieldFloor(level, floor) == null,
                "the harvest exception never climbs or passes through a fence at the exact feet cell");
        helper.assertTrue(FrontierV3ResourceSiteHarvestSceneExecutor.harvestStandingPosition(level, floor) == null,
                "the registered provider keeps a fence/full block as an obstruction rather than falling back to a different surface");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-resource-site-owned", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void ownedFieldWritesAllSlotsAndRecoversItsPendingProvenance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper));
        prepareGrayboxBaseline(level, site);
        helper.runAfterDelay(10, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
            PhysicalIntentId intent = new PhysicalIntentId("intent:site-prepare-resource-site-game-test");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.reconcileAfterRestart(level, ledger, site, intent, 3),
                    FrontierV3ResourceSiteExecutor.RestartReconciliation.CONFLICT,
                    "a neutral footprint with no durable confirmed claim is never permission to recreate a field after restart");
            ledger.reserve(site.id(), intent);
            CompoundTag pending = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(pending, level.registryAccess());
            helper.assertTrue(ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.PENDING && FrontierV3ResourceSiteExecutor.baseline(level, site),
                    "a restart retains pending ownership without treating the neutral graybox footing as a completed field");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                    "one executor pass writes every prevalidated soil and crop slot: " + firstFieldMismatch(level, site));
            ledger.activate(site.id()); CompoundTag active = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(active, level.registryAccess());
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, 0) && ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.ACTIVE,
                    "the exact 64 farmland, 64 wheat and four source-water cells plus active provenance survive a SavedData reload");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 3), FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED,
                    "a canonical COLD growth stage updates only the owned field cells");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, 3) && ledger.claim(site.id()).stage() == 3,
                    "the ledger retains the observed physical stage needed for later drift detection");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.reconcileAfterRestart(level, ledger, site, intent, 3),
                    FrontierV3ResourceSiteExecutor.RestartReconciliation.CURRENT,
                    "a complete confirmed field remains its own exact recovery postcondition");
            prepareGrayboxBaseline(level, site);
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.reconcileAfterRestart(level, ledger, site, intent, 3),
                    FrontierV3ResourceSiteExecutor.RestartReconciliation.RECREATED,
                    "a confirmed field whose whole physical write was lost at crash may rebuild only from the complete neutral baseline");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, 3) && ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.ACTIVE,
                    "recovery restores exact confirmed ownership and growth stage, not an unowned template");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(level, ledger, site, minecraft(site.cropSlots().getFirst())),
                    "an exact owned crop suppresses Vanilla random growth until its next canonical stage");
            BlockPos changed = minecraft(site.cropSlots().getFirst()); level.setBlock(changed, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 4), FrontierV3ResourceSiteExecutor.StageProjectionResult.CONFLICT,
                    "a foreign crop change is conflict evidence, never authority to advance or repair the field");
            helper.assertTrue(level.getBlockState(changed).is(Blocks.DIAMOND_BLOCK) && ledger.claim(site.id()).stage() == 3,
                    "a conflict leaves both the player/world block and the last confirmed field stage intact for reconciliation");
            helper.assertFalse(FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(level, ledger, site, changed),
                    "a foreign replacement is not captured by the canonical crop-growth guard");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-site-cold", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void coldCompletedFieldProjectsOnceFromNeutralBaselineButNeverOverwritesForeignBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-site-cold-game-test");
        prepareGrayboxBaseline(level, site);
        helper.runAfterDelay(10, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 5), FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED,
                    "a COLD-completed field may materialize its canonical stage from one neutral unloaded-world baseline");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, 5) && ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.ACTIVE,
                    "one deterministic projection claim owns the complete canonical field after its first loaded visit");
            BlockPos changed = minecraft(site.cropSlots().getFirst()); level.setBlock(changed, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 6), FrontierV3ResourceSiteExecutor.StageProjectionResult.CONFLICT,
                    "a later foreign block remains conflict evidence instead of a desired-state overwrite");
            helper.assertTrue(level.getBlockState(changed).is(Blocks.DIAMOND_BLOCK), "the foreign world block remains untouched after conflict");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-site-foreign", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void foreignFieldCellIsNeverAdoptedOrOverwritten(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper));
        prepareBaseline(level, site); BlockPosition foreign = site.cropSlots().getFirst(); BlockPos position = minecraft(foreign);
        level.setBlock(position, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3ResourceSiteExecutor.baseline(level, site) || FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                "a foreign crop cell rejects the whole field instead of mixing owned and player/world geometry");
        helper.assertTrue(level.getBlockState(position).is(Blocks.DIAMOND_BLOCK) && site.soilSlots().stream()
                        .allMatch(soil -> level.getBlockState(minecraft(soil)).is(Blocks.DIRT)),
                "rejection leaves the foreign block and every untouched soil-capital cell exactly as observed");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-resource-site-irrigation", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void irrigationIsOwnedFieldInfrastructureAndItsLossIsNeverRepairedBlindly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-site-irrigation-game-test");
        prepareBaseline(level, site);
        helper.runAfterDelay(10, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
            ledger.reserve(site.id(), new PhysicalIntentId("intent:site-irrigation-game-test"));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site), "the fixture must create the complete irrigated field");
            ledger.activate(site.id()); BlockPos water = minecraft(site.irrigationSlots().getFirst());
            level.setBlock(water, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 1), FrontierV3ResourceSiteExecutor.StageProjectionResult.CONFLICT,
                    "a missing source-water cell is field conflict evidence, not a request to recreate irrigation");
            helper.assertTrue(level.getBlockState(water).is(Blocks.DIAMOND_BLOCK), "the foreign replacement must remain physically untouched");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-site-explosion", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void externalBlastRetainsOneFieldWitnessAcrossSavedDataReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-site-explosion-game-test"); prepareBaseline(level, site);
        helper.runAfterDelay(10, () -> {
            FrontierV3ResourceSiteLedger claims = FrontierV3ResourceSiteLedger.get(level);
            claims.reserve(site.id(), new PhysicalIntentId("intent:site-explosion-game-test"));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.baseline(level, site), "the blast fixture must retain its neutral baseline: " + firstBaselineMismatch(level, site));
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                    "the blast fixture must own one complete field before capture: " + firstFieldMismatch(level, site));
            claims.activate(site.id()); helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, claims, site, 4),
                    FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "the exact field witness must retain its current crop stage");
            BlockPos changed = minecraft(site.cropSlots().getFirst()); FrontierV3ResourceSiteExplosionLedger evidence = FrontierV3ResourceSiteExplosionLedger.get(level);
            helper.assertTrue(evidence.captureExternal(level, level.getGameTime(), List.of(changed), List.of(site), claims),
                    "a real pre-impact field cell becomes one durable site witness rather than a generic scar");
            evidence = FrontierV3ResourceSiteExplosionLedger.load(evidence.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            level.setBlock(changed, Blocks.AIR.defaultBlockState(), 3);
            FrontierV3ResourceSiteExplosionLedger.Ready ready = evidence.nextReady(level.getGameTime() + 1L).orElseThrow();
            helper.assertValueEqual(ready.candidate().siteId(), site.id(), "reload retains the exact affected field identity");
            helper.assertValueEqual(ready.candidate().expectedStage(), 4, "reload retains the exact canonical crop stage used for post-impact inspection");
            helper.assertValueEqual(ready.candidate().witness(), site.cropSlots().getFirst(), "reload retains the one exact blast witness cell");
            helper.assertFalse(FrontierV3ResourceSiteExecutor.matches(level, site, ready.candidate().expectedStage()),
                    "the real changed cell is visible to reconciliation and is never rewritten by the observation queue");
            evidence.resolve(ready); helper.succeed();
        });
    }

    private static ResourceSite field(BlockPos origin) {
        return field(origin, "site:resource-site-game-test");
    }
    /** Fits every managed field cell and light border inside this test's independently allocated full-air template. */
    private static BlockPos fixtureOrigin(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(4, 30, 4));
    }
    private static ResourceSite field(BlockPos origin, String id) {
        List<BlockPosition> crops = new ArrayList<>(64);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) crops.add(new BlockPosition(origin.getX() + x, origin.getY(), origin.getZ() + z));
        return new ResourceSite(new SubjectId(id), new SubjectId("settlement:1"), new SubjectId("structure:1-farm"),
                ResourceSiteKind.WHEAT_FIELD, crops);
    }
    private static void prepareBaseline(ServerLevel level, ResourceSite site) {
        // fixtureOrigin keeps every managed slot inside this test's own full-air template.
        site.soilSlots().forEach(soil -> { BlockPos position = minecraft(soil); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            // Production accepts grass or dirt; use dirt in the delayed fixture because
            // grass can receive a random tick before the ownership assertion runs.
            level.setBlock(position, Blocks.DIRT.defaultBlockState(), 3); });
        site.irrigationSlots().forEach(irrigation -> { BlockPos position = minecraft(irrigation); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(position, Blocks.DIRT.defaultBlockState(), 3); });
        site.cropSlots().forEach(crop -> level.setBlock(minecraft(crop), Blocks.AIR.defaultBlockState(), 3));
        int minX = site.cropSlots().stream().mapToInt(BlockPosition::x).min().orElseThrow(), maxX = site.cropSlots().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        site.cropSlots().stream().filter(crop -> crop.x() == minX).forEach(crop -> level.setBlock(minecraft(crop).west(), Blocks.GLOWSTONE.defaultBlockState(), 3));
        site.cropSlots().stream().filter(crop -> crop.x() == maxX).forEach(crop -> level.setBlock(minecraft(crop).east(), Blocks.GLOWSTONE.defaultBlockState(), 3));
    }
    private static void prepareGrayboxBaseline(ServerLevel level, ResourceSite site) {
        // fixtureOrigin keeps every managed slot inside this test's own full-air template.
        site.soilSlots().forEach(soil -> { BlockPos position = minecraft(soil); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(position, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3); });
        site.irrigationSlots().forEach(irrigation -> { BlockPos position = minecraft(irrigation); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(position, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3); });
        site.cropSlots().forEach(crop -> level.setBlock(minecraft(crop), Blocks.AIR.defaultBlockState(), 3));
        int minX = site.cropSlots().stream().mapToInt(BlockPosition::x).min().orElseThrow(), maxX = site.cropSlots().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        site.cropSlots().stream().filter(crop -> crop.x() == minX).forEach(crop -> level.setBlock(minecraft(crop).west(), Blocks.GLOWSTONE.defaultBlockState(), 3));
        site.cropSlots().stream().filter(crop -> crop.x() == maxX).forEach(crop -> level.setBlock(minecraft(crop).east(), Blocks.GLOWSTONE.defaultBlockState(), 3));
    }
    private static BlockPos minecraft(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
    private static String firstBaselineMismatch(ServerLevel level, ResourceSite site) {
        return site.cropSlots().stream().filter(position -> !level.getBlockState(minecraft(position)).isAir())
                .findFirst().map(position -> "crop " + position + "=" + level.getBlockState(minecraft(position)))
                .or(() -> site.soilSlots().stream().filter(position -> {
                    BlockPos minecraft = minecraft(position); return !level.getBlockState(minecraft).is(Blocks.DIRT) || level.getBlockState(minecraft.below()).isAir();
                }).findFirst().map(position -> "soil " + position + "=" + level.getBlockState(minecraft(position)) + ", below=" + level.getBlockState(minecraft(position).below())))
                .or(() -> site.irrigationSlots().stream().filter(position -> {
                    BlockPos minecraft = minecraft(position); return !level.getBlockState(minecraft).is(Blocks.DIRT) || level.getBlockState(minecraft.below()).isAir();
                }).findFirst().map(position -> "irrigation " + position + "=" + level.getBlockState(minecraft(position)) + ", below=" + level.getBlockState(minecraft(position).below())))
                .orElse("baseline appears valid");
    }
    private static String firstFieldMismatch(ServerLevel level, ResourceSite site) {
        return site.soilSlots().stream().filter(position -> !level.getBlockState(minecraft(position)).is(Blocks.FARMLAND))
                .findFirst().map(position -> "soil " + position + "=" + level.getBlockState(minecraft(position)))
                .or(() -> site.cropSlots().stream().filter(position -> !level.getBlockState(minecraft(position))
                                .equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0)))
                        .findFirst().map(position -> "crop " + position + "=" + level.getBlockState(minecraft(position))))
                .or(() -> site.irrigationSlots().stream().filter(position -> !level.getBlockState(minecraft(position)).equals(Blocks.WATER.defaultBlockState()))
                        .findFirst().map(position -> "irrigation " + position + "=" + level.getBlockState(minecraft(position))))
                .orElse("unreported-state-mismatch");
    }
}
