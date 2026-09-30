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

    @GameTest(batch = "pm-frontier-v3-resource-site-prefix", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void blankInitialProjectionDoesNotInferSoilAuthority(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper));
        site.managedSlots().forEach(slot -> level.setBlock(minecraft(slot), Blocks.AIR.defaultBlockState(), 2));
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
        ledger.reserve(site.id(), new PhysicalIntentId("intent:site-prefix-blank"));
        ledger.beginProjection(site.id(), new FrontierV3ResourceSiteLedger.ProjectionTransition(
                "job:site-prefix-cold-harvest", 0, 0, 7, 10, 0, 132, FrontierV3ResourceSiteLedger.ProjectionMode.INITIAL));
        ledger = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, site, ledger.claim(site.id())),
                "explicit blank predecessor survives reload without becoming a soil predecessor");
        BlockPos soil = minecraft(site.soilSlots().getFirst());
        level.setBlock(soil.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 2);
        helper.assertFalse(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, site, ledger.claim(site.id())),
                "even supported dirt is foreign when the retained predecessor was AIR");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-resource-site-prefix", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void partialInitialProjectionRetainsSoilAndRejectsForeignSuffixAfterReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper));
        prepareGrayboxBaseline(level, site);
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
        ledger.reserve(site.id(), new PhysicalIntentId("intent:site-prefix-initial"));
        var order = FrontierV3ResourceSiteExecutor.initialProjectionSlotOrder(site);
        ledger.beginProjection(site.id(), new FrontierV3ResourceSiteLedger.ProjectionTransition(
                "growth:site:resource-site-game-test:e1", 0, 0, 0, 0, 0, order.size(),
                FrontierV3ResourceSiteLedger.ProjectionMode.INITIAL_SOIL));
        helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, site, ledger.claim(site.id())),
                "initial cursor starts on supported neutral soil, not AIR in every slot");
        for (int index = 0; index < 8; index++) {
            BlockPosition slot = order.get(index);
            var block = site.irrigationSlots().contains(slot) ? Blocks.WATER.defaultBlockState()
                    : site.soilSlots().contains(slot) ? Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7)
                    : Blocks.WHEAT.defaultBlockState();
            level.setBlock(minecraft(slot), block, 2); ledger.advanceProjection(site.id(), index + 1);
        }
        ledger = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, site, ledger.claim(site.id())),
                "saved partial cursor accepts its hydrated prefix and untouched supported suffix");
        BlockPos changed = minecraft(order.get(10)); level.setBlock(changed, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        helper.assertFalse(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, site, ledger.claim(site.id())),
                "a later foreign suffix is not permission to resume cached writes");
        helper.assertTrue(level.getBlockState(changed).is(Blocks.DIAMOND_BLOCK), "validation preserves foreign evidence");
        helper.assertValueEqual(ledger.claim(site.id()).projection().nextWrite(), 8, "validation never consumes progress");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-resource-site-prefix", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void partialGrowthProjectionAcceptsHydrationButRejectsChangedCommittedCrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper));
        prepareGrayboxBaseline(level, site);
        helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site), "initial physical field exists");
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
        ledger.reserve(site.id(), new PhysicalIntentId("intent:site-prefix-growth")); ledger.activate(site.id());
        ledger.beginProjection(site.id(), new FrontierV3ResourceSiteLedger.ProjectionTransition(
                "growth:site:resource-site-game-test:e1", 0, 0, 3, 0, 0, 64,
                FrontierV3ResourceSiteLedger.ProjectionMode.ADVANCE));
        for (int index = 0; index < 8; index++) {
            level.setBlock(minecraft(site.cropSlots().get(index)), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3), 2);
            ledger.advanceProjection(site.id(), index + 1);
        }
        level.setBlock(minecraft(site.soilSlots().getFirst()), Blocks.FARMLAND.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 2);
        ledger = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, site, ledger.claim(site.id())),
                "growth cursor distinguishes hydration from crop-stage drift");
        level.setBlock(minecraft(site.cropSlots().getFirst()), Blocks.AIR.defaultBlockState(), 2);
        helper.assertFalse(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, site, ledger.claim(site.id())),
                "removing a committed crop invalidates continuation without repairing it");
        helper.succeed();
    }

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
        java.util.UUID absentFarmer = new java.util.UUID(0L, 781L);
        helper.assertTrue(FrontierV3HarvestSceneStandingAdmission.obstructedBodyFreeColumn(level,
                        absentFarmer, new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(
                                floor.getX(), floor.getY(), floor.getZ())),
                "a body-free scene must not claim the stone below projected farmland as its exact support");
        helper.assertFalse(FrontierV3HarvestSceneStandingAdmission.obstructedBodyFreeColumn(level,
                        absentFarmer, new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(
                                floor.getX(), floor.getY() + 1, floor.getZ())),
                "the same farmer may be admitted at the valid crop-foot station");
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

    @GameTest(batch = "pm-frontier-v3-resource-site-owned", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 120)
    public static void ownedFieldWritesAllSlotsAndRecoversItsPendingProvenance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper));
        prepareGrayboxBaseline(level, site);
        whenFieldLit(helper, site, 100, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
            PhysicalIntentId intent = new PhysicalIntentId("intent:site-prepare-resource-site-game-test");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.reconcileAfterRestart(level, ledger, site, intent, 3),
                    FrontierV3ResourceSiteExecutor.RestartReconciliation.CONFLICT,
                    "a neutral footprint with no durable confirmed claim is never permission to recreate a field after restart");
            ledger.reserve(site.id(), intent);
            CompoundTag pending = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(pending, level.registryAccess());
            helper.assertTrue(ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.PENDING && FrontierV3ResourceSiteExecutor.baseline(level, site),
                    "a restart retains pending ownership without treating the neutral graybox footing as a completed field");
            boolean placed = FrontierV3ResourceSiteExecutor.placeWholeField(level, site);
            if (!placed) {
                BlockPosition mismatch = site.cropSlots().stream()
                        .filter(crop -> !level.getBlockState(minecraft(crop)).equals(Blocks.WHEAT.defaultBlockState()))
                        .findFirst().orElse(site.cropSlots().getLast());
                helper.fail("one executor pass writes every prevalidated soil and crop slot: " + firstFieldMismatch(level, site)
                        + ", crop light=" + level.getMaxLocalRawBrightness(minecraft(mismatch))
                        + ", east lamp=" + level.getBlockState(minecraft(mismatch).east()));
            }
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
            helper.assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(level, ledger, site, changed),
                    "an active claim preserves a foreign replacement as local conflict evidence rather than allowing further native mutation");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-resource-site-cold", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void coldCompletedFieldProjectsOnceFromNeutralBaselineButNeverOverwritesForeignBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(fixtureOrigin(helper), "site:resource-site-cold-game-test");
        prepareGrayboxBaseline(level, site);
        helper.runAfterDelay(10, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
            helper.assertTrue(FrontierV3ResourceSiteExecutor.baseline(level, site),
                    "neutral field predecessor must be current: " + firstGrayboxBaselineMismatch(level, site));
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
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.fixture();
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
            FrontierV3ResourceSiteLedger claims = FrontierV3ResourceSiteLedger.fixture();
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
                ResourceSiteKind.WHEAT_FIELD, io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan.initialGrayboxLayout(crops));
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
    /** The enclosed GameTest cell must finish propagating its border lamps before wheat is placed. */
    private static void whenFieldLit(GameTestHelper helper, ResourceSite site, int remainingTicks, Runnable action) {
        ServerLevel level = helper.getLevel();
        BlockPosition dark = site.cropSlots().stream()
                .filter(crop -> !CropBlock.hasSufficientLight(level, minecraft(crop)))
                .findFirst().orElse(null);
        if (dark == null) {
            action.run();
        } else if (remainingTicks == 0) {
            helper.fail("field fixture never reached Minecraft crop-survival light at " + dark
                    + ": light=" + level.getMaxLocalRawBrightness(minecraft(dark))
                    + ", east lamp=" + level.getBlockState(minecraft(dark).east()));
        } else {
            helper.runAfterDelay(1, () -> whenFieldLit(helper, site, remainingTicks - 1, action));
        }
    }
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
    private static String firstGrayboxBaselineMismatch(ServerLevel level, ResourceSite site) {
        return site.managedSlots().stream().filter(slot -> {
            BlockPos position = minecraft(slot);
            if (site.cropSlots().contains(slot)) return !level.getBlockState(position).isAir();
            return !level.getBlockState(position).is(Blocks.LIGHT_GRAY_CONCRETE)
                    || level.getBlockState(position.below()).isAir();
        }).findFirst().map(slot -> slot + "=" + level.getBlockState(minecraft(slot))
                + ", below=" + level.getBlockState(minecraft(slot).below())).orElse("no mismatched slot");
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
