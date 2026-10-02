package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** Native evidence for the read-only, one-cell loaded-world classification seam. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ResourceFieldObservationGameTests {
    private static final SubjectId SITE = new SubjectId("site:field-observation-test");
    private FrontierV3ResourceFieldObservationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void soundCropWithBlockedWorkerHeadroomHasAnIndependentAccessObservation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos soil = helper.absolutePos(new BlockPos(4, 0, 4));
        var cell = cell(1, soil);
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState()
                .setValue(CropBlock.AGE, 7), 3);
        level.setBlock(soil.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(FrontierV3ResourceFieldWorkAccessExecutor.read(level, cell).orElseThrow()
                            .equals(new FrontierV3ResourceFieldWorkAccessExecutor.Reading(false, "minecraft:air")),
                    "an intact crop does not obstruct standing headroom");
            level.setBlock(soil.above(2), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(FrontierV3ResourceFieldWorkAccessExecutor.read(level, cell).orElseThrow()
                            .equals(new FrontierV3ResourceFieldWorkAccessExecutor.Reading(true, "minecraft:stone"))
                            && level.getBlockState(soil.above()).is(Blocks.WHEAT),
                    "the physical workstation is blocked while the crop remains intact");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void growthAdmissionRejectsForeignDimensionAndUnregisteredRuntime(GameTestHelper helper) {
        var configuration = FrontierWorldRuntimeDefinition.configuration(FrontierV3PhysicalWorld.WORLD_ID, 91L);
        var unregistered = FrontierV3ServerRuntime.failedStart(configuration,
                new FrontierFileStore(java.nio.file.Path.of("build/disposable-unopened-field-runtime-admission"),
                        FrontierWorldRuntimeDefinition.payloadCodecs()),
                10_000, new IllegalStateException("unregistered native admission fixture"));
        helper.runAtTickTime(1, () -> {
            var cellId = new ResourceFieldLayout.CellId(1);
            helper.assertTrue(rejects(() -> FrontierV3ResourceFieldGrowthProjector.projectCurrentOne(
                            helper.getLevel(), unregistered, SITE, cellId)),
                    "an ordinary GameTest level is not the physical Frontier dimension");
            helper.assertTrue(!FrontierV3ServerLifecycle.ownsRuntime(helper.getLevel(), unregistered),
                    "an unregistered runtime cannot borrow this GameTest server's canonical ownership");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void initialFieldWriterPersistsEachRealBlockBoundary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos soil = helper.absolutePos(new BlockPos(4, 0, 4));
        level.setBlock(soil.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), 3);
        SubjectId siteId = new SubjectId("site:field-durable-writer-test");
        var layout = new ResourceFieldLayout(1, 2, List.of(cell(1, soil)), List.of());
        var site = new ResourceSite(siteId, new SubjectId("settlement:field-durable-writer-test"),
                new SubjectId("structure:field-durable-writer-test"), ResourceSiteKind.WHEAT_FIELD, layout);
        helper.runAtTickTime(1, () -> {
            FrontierV3ResourceFieldInitialWriter.reserve(level, site,
                    new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:field-durable-writer-test"));
            helper.assertTrue(persistedField(level, siteId) instanceof FrontierV3ResourceSiteLedger.FieldInitialization initial
                            && initial.cursor().nextWrite() == 0 && !initial.cursor().prepared()
                            && level.getBlockState(soil).is(Blocks.DIRT),
                    "reservation is durable before the first physical effect");
            helper.assertTrue(FrontierV3ResourceFieldInitialWriter.writeOne(level, site)
                            == FrontierV3ResourceFieldInitialWriter.Result.ADVANCED
                            && persistedField(level, siteId) instanceof FrontierV3ResourceSiteLedger.FieldInitialization first
                            && first.cursor().nextWrite() == 1 && !first.cursor().prepared()
                            && level.getBlockState(soil).is(Blocks.FARMLAND),
                    "first actual block write and its cursor are published together");
            helper.assertTrue(FrontierV3ResourceFieldInitialWriter.writeOne(level, site)
                            == FrontierV3ResourceFieldInitialWriter.Result.CURSOR_COMPLETE
                            && persistedField(level, siteId) instanceof FrontierV3ResourceSiteLedger.FieldInitialization complete
                            && complete.cursor().complete() && level.getBlockState(soil.above()).is(Blocks.WHEAT),
                    "last real crop write has a durable completed cursor");
            var target = ResourceFieldCycle.seeded(siteId, layout, 1);
            var witness = FrontierV3ResourceFieldWitness.claimed(siteId, 1, ResourceFieldPhysicalSurface.fromCycle(target));
            FrontierV3ResourceFieldInitialWriter.activate(level, site, target, witness);
            helper.assertTrue(persistedField(level, siteId) instanceof FrontierV3ResourceSiteLedger.FieldOwnership,
                    "only a complete physical field becomes a durably owned field");
            var activeLedger = FrontierV3ResourceSiteLedger.get(level);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(level, activeLedger, site, soil.above()),
                    "the cell-owned field vetoes vanilla crop growth without consulting a legacy stage claim");
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1), 3);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.restoreNativeGrowthPostcondition(
                            level, activeLedger, site, soil.above())
                            && level.getBlockState(soil.above()).getValue(CropBlock.AGE) == 0,
                    "a forced native increment is restored only from its same-event exact cell predecessor");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(level, activeLedger, site, soil.above()),
                    "a later native attempt takes a new exact pre-event snapshot");
            level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1), 3);
            helper.assertTrue(!FrontierV3ResourceSiteExecutor.restoreNativeGrowthPostcondition(
                            level, activeLedger, site, soil.above())
                            && level.getBlockState(soil).is(Blocks.DIRT),
                    "a changed supporting soil is not silently repaired by the growth veto");
            level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState(), 3);
            var grown = target.advanceGrowth(cell(1, soil).id());
            var accepted = new FrontierCanonicalState<>(new WorldId("frontier:field-durable-writer-test"),
                    new Revision(5), new SimInstant(5), grown);
            helper.assertTrue(FrontierV3ResourceFieldGrowthProjector.projectAccepted(level, site, accepted,
                            cell(1, soil).id()) == FrontierV3ResourceFieldGrowthProjector.Result.ADVANCED
                            && level.getBlockState(soil.above()).getValue(CropBlock.AGE) == 1
                            && persistedField(level, siteId) instanceof FrontierV3ResourceSiteLedger.FieldOwnership projected
                            && projected.witness().cell(cell(1, soil).id()).pending().isEmpty()
                            && projected.witness().cell(cell(1, soil).id()).committed().growthStage() == 1,
                    "one preaccepted canonical growth target reaches the real crop and durable cell claim");
            helper.assertTrue(FrontierV3ResourceFieldGrowthProjector.projectAccepted(level, site, accepted,
                            cell(1, soil).id()) == FrontierV3ResourceFieldGrowthProjector.Result.CURRENT,
                    "the same canonical growth target cannot be written again");
            var cellId = cell(1, soil).id();
            var secondGrowth = grown.advanceGrowth(cellId);
            var acceptedSecond = new FrontierCanonicalState<>(accepted.worldId(), new Revision(6),
                    new SimInstant(6), secondGrowth);
            var ledger = FrontierV3ResourceSiteLedger.get(level);
            var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(siteId);
            var secondTransition = ResourceFieldCellTransition.between(siteId, 1, 1, cellId,
                    ResourceFieldPhysicalSurface.Condition.of(grown.cell(cellId)),
                    ResourceFieldPhysicalSurface.Condition.of(secondGrowth.cell(cellId)));
            var beforeSecondGrowth = FrontierV3ResourceFieldObservation.observe(level, secondGrowth,
                    owner.witness(), cellId, "native:second-growth-before");
            ledger.replaceFieldClaim(owner, owner.withWitness(owner.witness().beginCanonicalProjection(
                    acceptedSecond, secondTransition, "projection:prepared-second", beforeSecondGrowth)));
            ledger.persist(level);
            helper.assertTrue(persistedField(level, siteId) instanceof FrontierV3ResourceSiteLedger.FieldOwnership beforeSecond
                            && beforeSecond.witness().cell(cellId).pending().orElseThrow().completedSteps() == 0
                            && level.getBlockState(soil.above()).getValue(CropBlock.AGE) == 1,
                    "restart can find the accepted projection before any block effect");
            helper.assertTrue(FrontierV3ResourceFieldGrowthProjector.projectAccepted(level, site, acceptedSecond, cellId)
                            == FrontierV3ResourceFieldGrowthProjector.Result.ADVANCED
                            && level.getBlockState(soil.above()).getValue(CropBlock.AGE) == 2
                            && ((FrontierV3ResourceSiteLedger.FieldOwnership) persistedField(level, siteId))
                                    .witness().cell(cellId).pending().isEmpty(),
                    "a saved pre-effect projection resumes from its exact physical predecessor");
            var thirdGrowth = secondGrowth.advanceGrowth(cellId);
            var acceptedThird = new FrontierCanonicalState<>(accepted.worldId(), new Revision(7),
                    new SimInstant(7), thirdGrowth);
            owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(siteId);
            var thirdTransition = ResourceFieldCellTransition.between(siteId, 1, 1, cellId,
                    ResourceFieldPhysicalSurface.Condition.of(secondGrowth.cell(cellId)),
                    ResourceFieldPhysicalSurface.Condition.of(thirdGrowth.cell(cellId)));
            var beforeThirdGrowth = FrontierV3ResourceFieldObservation.observe(level, thirdGrowth,
                    owner.witness(), cellId, "native:third-growth-before");
            ledger.replaceFieldClaim(owner, owner.withWitness(owner.witness().beginCanonicalProjection(
                    acceptedThird, thirdTransition, "projection:already-written-third", beforeThirdGrowth)));
            ledger.persist(level);
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3), 3);
            helper.assertTrue(FrontierV3ResourceFieldGrowthProjector.projectAccepted(level, site, acceptedThird, cellId)
                            == FrontierV3ResourceFieldGrowthProjector.Result.ADVANCED
                            && ((FrontierV3ResourceSiteLedger.FieldOwnership) persistedField(level, siteId))
                                    .witness().cell(cellId).pending().isEmpty(),
                    "a crash after the block effect confirms the observed later prefix without replaying work");
            var fifthGrowth = thirdGrowth.advanceGrowth(cellId).advanceGrowth(cellId);
            var acceptedFifth = new FrontierCanonicalState<>(accepted.worldId(), new Revision(9), new SimInstant(9), fifthGrowth);
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 4), 3);
            helper.assertTrue(FrontierV3ResourceFieldGrowthProjector.projectAccepted(level, site, acceptedFifth, cellId)
                            == FrontierV3ResourceFieldGrowthProjector.Result.ADVANCED
                            && level.getBlockState(soil.above()).getValue(CropBlock.AGE) == 5
                            && ((FrontierV3ResourceSiteLedger.FieldOwnership) persistedField(level, siteId))
                                    .witness().cell(cellId).committed().growthStage() == 5
                            && fifthGrowth.harvestedCount() == 0,
                    "external growth inside a lagging accepted biological target is inspected, then projected without yield");
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 6), 3);
            helper.assertTrue(FrontierV3ResourceFieldGrowthProjector.projectAccepted(level, site, acceptedFifth, cellId)
                            == FrontierV3ResourceFieldGrowthProjector.Result.PHYSICAL_CONFLICT,
                    "growth beyond canonical authority requires a world-change receipt, not projection adoption");
            level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(FrontierV3ResourceFieldGrowthProjector.projectAccepted(level, site, acceptedThird,
                            cell(1, soil).id()) == FrontierV3ResourceFieldGrowthProjector.Result.PHYSICAL_CONFLICT
                            && level.getBlockState(soil.above()).isAir(),
                    "a missing crop is a local observed loss, not permission to silently regrow it");
            helper.succeed();
        });
    }

    private static FrontierV3ResourceSiteLedger.FieldClaim persistedField(ServerLevel level, SubjectId siteId) {
        try {
            var root = NbtIo.readCompressed(FrontierV3ResourceSiteLedger.storageFile(level), NbtAccounter.unlimitedHeap());
            return FrontierV3ResourceSiteLedger.load(root.getCompound("data"), level.registryAccess()).fieldClaim(siteId);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void initialFieldPlanReadsRealNeutralSoilAndIrrigation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos water = helper.absolutePos(new BlockPos(5, 0, 4));
        BlockPos firstSoil = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos secondSoil = helper.absolutePos(new BlockPos(6, 0, 4));
        var layout = new ResourceFieldLayout(1, 3, List.of(cell(1, firstSoil), cell(2, secondSoil)),
                List.of(new BlockPosition(water.getX(), water.getY(), water.getZ())));
        var site = new ResourceSite(SITE, new SubjectId("settlement:field-observation-test"),
                new SubjectId("structure:field-observation-test"), ResourceSiteKind.WHEAT_FIELD, layout);
        for (BlockPos position : List.of(water, firstSoil, secondSoil))
            level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(water, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(firstSoil, Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(secondSoil, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3);
        level.setBlock(firstSoil.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(secondSoil.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(FrontierV3ResourceFieldInitialPlan.writeCount(site) == 5,
                    "one irrigation plus two soil/crop pairs is the complete plan");
            for (int index = 0; index < 5; index++)
                helper.assertTrue(FrontierV3ResourceFieldInitialPlan.observe(level, site, index).disposition()
                                == FrontierV3ResourceFieldInitialPlan.Disposition.BEFORE,
                        "every declared neutral field predecessor must be recognized at step " + index);
            level.setBlock(secondSoil.below(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(FrontierV3ResourceFieldInitialPlan.observe(level, site, 3).disposition()
                            == FrontierV3ResourceFieldInitialPlan.Disposition.FOREIGN,
                    "a support block without terrain beneath it is not a valid initial field predecessor");
            level.setBlock(secondSoil.below(), Blocks.STONE.defaultBlockState(), 3);
            for (BlockPos soil : List.of(firstSoil, secondSoil)) {
                level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
                level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0), 3);
            }
            var target = ResourceFieldCycle.seeded(SITE, layout, 1);
            var witness = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.fromCycle(target));
            helper.assertTrue(rejectsState(() -> FrontierV3ResourceFieldInitialPlan.requireCompletePhysicalField(
                            level, site, target, witness)),
                    "a completed cursor cannot adopt a field whose declared irrigation is missing");
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
            FrontierV3ResourceFieldInitialPlan.requireCompletePhysicalField(level, site, target, witness);
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void initialFieldCursorRecoversMidWriteWithoutAdoptingForeignBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos firstSoil = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos secondSoil = helper.absolutePos(new BlockPos(6, 0, 4));
        var first = cell(1, firstSoil);
        var second = cell(2, secondSoil);
        var layout = new ResourceFieldLayout(1, 3, List.of(first, second), List.of());
        var site = new ResourceSite(SITE, new SubjectId("settlement:field-observation-test"),
                new SubjectId("structure:field-observation-test"), ResourceSiteKind.WHEAT_FIELD, layout);
        for (BlockPos position : List.of(firstSoil, secondSoil)) {
            level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(position, Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        helper.runAtTickTime(1, () -> {
            var ledger = FrontierV3ResourceSiteLedger.fixture();
            ledger.reserveFieldInitialization(site, new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(
                    "intent:field-observation-initial"));
            for (int index = 0; index < 2; index++) {
                ledger.prepareFieldInitialization(site, FrontierV3ResourceFieldInitialPlan.observe(level, site, index));
                var observed = FrontierV3ResourceFieldInitialPlan.applyPrepared(level, site, ledger);
                helper.assertTrue(observed.disposition() == FrontierV3ResourceFieldInitialPlan.Disposition.APPLIED,
                        "the real block write must reach its exact declared postcondition");
                ledger.advanceFieldInitialization(site, observed);
            }
            ledger = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), null), null);
            var halfway = (FrontierV3ResourceSiteLedger.FieldInitialization) ledger.fieldClaim(SITE);
            helper.assertTrue(halfway.cursor().nextWrite() == 2,
                    "restart must retain the first soil/crop pair rather than replay it");
            var oldReview = FrontierV3ResourceFieldInitialPlan.observe(level, site, 1);
            var retained = ledger;
            helper.assertTrue(rejectsState(() -> retained.advanceFieldInitialization(site, oldReview)),
                    "a prior real block review cannot advance the next write cursor");

            level.setBlock(secondSoil, Blocks.FARMLAND.defaultBlockState(), 3);
            var preexisting = FrontierV3ResourceFieldInitialPlan.observe(level, site, 2);
            helper.assertTrue(preexisting.disposition() == FrontierV3ResourceFieldInitialPlan.Disposition.APPLIED
                            && rejectsState(() -> retained.prepareFieldInitialization(site, preexisting)),
                    "a target-looking block without our durable predecessor permission cannot be adopted");
            level.setBlock(secondSoil, Blocks.DIRT.defaultBlockState(), 3);
            retained.prepareFieldInitialization(site, FrontierV3ResourceFieldInitialPlan.observe(level, site, 2));
            var wrongSite = new ResourceSite(new SubjectId("site:field-observation-other"),
                    site.settlementId(), site.facilityId(), ResourceSiteKind.WHEAT_FIELD, layout);
            helper.assertTrue(rejectsState(() -> FrontierV3ResourceFieldInitialPlan.applyPrepared(level, wrongSite, retained)),
                    "an identical layout cannot borrow another site's write permission");
            level.setBlock(secondSoil, Blocks.FARMLAND.defaultBlockState(), 3);
            var unproven = FrontierV3ResourceFieldInitialPlan.applyPrepared(level, site, retained);
            helper.assertTrue(unproven.disposition() == FrontierV3ResourceFieldInitialPlan.Disposition.APPLIED
                            && !unproven.writtenThisCall()
                            && rejectsState(() -> retained.advanceFieldInitialization(site, unproven)),
                    "a saved permission alone cannot claim a target block written by another actor");
            level.setBlock(secondSoil, Blocks.STONE.defaultBlockState(), 3);
            var blocked = FrontierV3ResourceFieldInitialPlan.observe(level, site, 2);
            helper.assertTrue(blocked.disposition() == FrontierV3ResourceFieldInitialPlan.Disposition.FOREIGN
                            && rejectsState(() -> retained.advanceFieldInitialization(site, blocked))
                            && level.getBlockState(secondSoil).is(Blocks.STONE),
                    "initial projection cannot overwrite or account for a foreign block");
            level.setBlock(secondSoil, Blocks.DIRT.defaultBlockState(), 3);
            for (int index = 2; index < FrontierV3ResourceFieldInitialPlan.writeCount(site); index++) {
                if (index > 2) ledger.prepareFieldInitialization(site, FrontierV3ResourceFieldInitialPlan.observe(level, site, index));
                ledger.advanceFieldInitialization(site, FrontierV3ResourceFieldInitialPlan.applyPrepared(level, site, ledger));
            }
            var target = ResourceFieldCycle.seeded(SITE, layout, 1);
            var witness = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.fromCycle(target));
            level.setBlock(secondSoil.above(), Blocks.AIR.defaultBlockState(), 3);
            var retainedLedger = ledger;
            helper.assertTrue(rejectsState(() -> retainedLedger.activateField(level, site, target, witness))
                            && ledger.fieldClaim(SITE) instanceof FrontierV3ResourceSiteLedger.FieldInitialization,
                    "the completed write cursor cannot activate a field with a missing real crop");
            level.setBlock(secondSoil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0), 3);
            ledger.activateField(level, site, target, witness);
            var recovered = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), null), null);
            helper.assertTrue(recovered.fieldClaim(SITE) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owned
                            && owned.witness().matchesCycle(target)
                            && level.getBlockState(firstSoil).is(Blocks.FARMLAND)
                            && level.getBlockState(secondSoil.above()).is(Blocks.WHEAT),
                    "a complete observed cursor becomes one durable cell owner of the actual initial field");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void loadedCellIsTypedWithoutAdoptingForeignBlocksOrLoadingAnotherChunk(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos soil = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos crop = soil.above();
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 5), 3);
        helper.runAtTickTime(1, () -> {
            var cell = cell(1, soil);
            var observed = FrontierV3ResourceFieldObservation.read(level, cell, "native:loaded-cell");
            helper.assertTrue(observed instanceof FrontierV3ResourceFieldObservation.Owned owned
                            && owned.condition().soil() == ResourceFieldCycle.Soil.FARMLAND
                            && owned.condition().crop() == ResourceFieldCycle.Crop.GROWING
                            && owned.condition().growthStage() == 5,
                    "loaded wheat must retain its exact growth stage without claiming yield");

            level.setBlock(crop, Blocks.AIR.defaultBlockState(), 3);
            var missing = FrontierV3ResourceFieldObservation.read(level, cell, "native:crop-removed");
            helper.assertTrue(missing instanceof FrontierV3ResourceFieldObservation.Owned owned
                            && owned.condition().crop() == ResourceFieldCycle.Crop.ABSENT,
                    "one missing crop is a local owned-state observation, not field destruction");

            level.setBlock(crop, Blocks.STONE.defaultBlockState(), 3);
            var foreign = FrontierV3ResourceFieldObservation.read(level, cell, "native:foreign-crop");
            helper.assertTrue(foreign instanceof FrontierV3ResourceFieldObservation.Foreign incident
                            && incident.incident().observedCrop().getString("Name").equals("minecraft:stone")
                            && incident.incident().causationId().equals("native:foreign-crop"),
                    "a foreign block retains its exact NBT identity and cause, never an inferred owned crop");
            helper.assertTrue(level.getBlockState(crop).is(Blocks.STONE), "read-only inspection must not repair the foreign block");

            BlockPos far = soil.offset(512, 0, 512);
            helper.assertFalse(level.hasChunkAt(far), "the off-fixture cell must start unloaded");
            var unloaded = FrontierV3ResourceFieldObservation.read(level, cell(2, far), "native:unloaded-cell");
            helper.assertTrue(unloaded == FrontierV3ResourceFieldObservation.Unloaded.INSTANCE
                            && !level.hasChunkAt(far),
                    "unloaded inspection must neither load nor classify the remote cell");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void onlyActualLoadedWorldStepsAdvanceTheDurableCellCursor(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos soil = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos crop = soil.above();
        var cell = cell(1, soil);
        var layout = new ResourceFieldLayout(1, 2, List.of(cell), List.of());
        var cycle = ResourceFieldCycle.seeded(SITE, layout, 1);
        var id = cell.id();
        var dirt = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.DIRT,
                ResourceFieldCycle.Crop.ABSENT, 0);
        var bare = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0);
        var planted = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.GROWING, 0);
        level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(crop, Blocks.AIR.defaultBlockState(), 3);
        helper.runAtTickTime(1, () -> {
            var pending = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.restore(
                    layout, Map.of(id, dirt))).begin(ResourceFieldCellTransition.between(SITE, 1, 1, id, dirt, planted),
                    "farmer:native:till-plant");
            helper.assertTrue(rejects(() -> FrontierV3ResourceFieldObservation.observe(level,
                            ResourceFieldCycle.seeded(SITE, layout, 2), pending, id, "native:stale-epoch")),
                    "same loaded blocks in another cycle cannot authorize the old physical witness");
            var before = FrontierV3ResourceFieldObservation.observe(level, cycle, pending, id, "native:before-till");
            helper.assertTrue(before.disposition() == FrontierV3ResourceFieldObservation.Disposition.CURRENT
                            && rejects(() -> pending.confirm(id, before)),
                    "unchanged loaded world cannot advance a durable physical cursor");
            level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
            var observedSoil = FrontierV3ResourceFieldObservation.observe(level, cycle, pending, id, "native:tilled");
            helper.assertTrue(observedSoil.disposition() == FrontierV3ResourceFieldObservation.Disposition.NEXT_STEP_APPLIED,
                    "actual tilling must be identified as the next physical step");
            var one = FrontierV3ResourceFieldWitness.read(pending.confirm(id, observedSoil).write());
            helper.assertTrue(one.cell(id).committed().equals(bare)
                            && one.cell(id).pending().orElseThrow().completedSteps() == 1
                            && rejects(() -> one.confirm(id, observedSoil)),
                    "restart retains the soil-only prefix and rejects the stale review");
            level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0), 3);
            var successorCycle = ResourceFieldCycle.seeded(SITE, layout, 2);
            var oldEpochReview = FrontierV3ResourceFieldObservation.observePendingPredecessor(level,
                    successorCycle, one, id, "native:predecessor-effect");
            var oldEpochRecovered = FrontierV3ResourceFieldWitness.read(one.confirm(id, oldEpochReview).write());
            helper.assertTrue(oldEpochReview.disposition() == FrontierV3ResourceFieldObservation.Disposition.NEXT_STEP_APPLIED
                            && oldEpochRecovered.cell(id).pending().orElseThrow().completedSteps() == 2
                            && rejects(() -> FrontierV3ResourceFieldObservation.observePendingPredecessor(level,
                                    successorCycle, FrontierV3ResourceFieldWitness.claimed(SITE, 1,
                                            ResourceFieldPhysicalSurface.restore(layout, Map.of(id, dirt))), id,
                                    "native:no-pending")),
                    "a later COLD epoch may inspect only the real retained predecessor effect, never adopt an unowned cell");
            var observedCrop = FrontierV3ResourceFieldObservation.observe(level, cycle, one, id, "native:planted");
            var complete = FrontierV3ResourceFieldWitness.read(one.confirm(id, observedCrop).write());
            helper.assertTrue(complete.cell(id).committed().equals(planted)
                            && complete.cell(id).pending().orElseThrow().completedSteps() == 2
                            && rejects(() -> complete.begin(ResourceFieldCellTransition.between(SITE, 1, 1, id, dirt, planted),
                                    "farmer:native:replay")),
                    "the second actual block write retains its cause until canonical work is acknowledged");
            var allegedCanonical = new FrontierCanonicalState<>(new WorldId("frontier:field-test"),
                    new Revision(8), new SimInstant(8), cycle);
            var current = FrontierV3ResourceFieldObservation.observe(level, cycle, complete, id, "native:work-not-projection");
            helper.assertTrue(rejects(() -> complete.acknowledgeCanonicalProjection(allegedCanonical, id,
                            "farmer:native:till-plant", current)),
                    "physical-first farmer work cannot be retired by a matching-looking canonical state");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void canonicalFirstGrowthProjectionRetiresOnlyAfterRealBlockAndSameWorld(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos soil = helper.absolutePos(new BlockPos(4, 0, 4));
        var cell = cell(1, soil);
        var layout = new ResourceFieldLayout(1, 2, List.of(cell), List.of());
        var seeded = ResourceFieldCycle.seeded(SITE, layout, 1);
        var target = seeded.advanceGrowth(cell.id());
        var accepted = new FrontierCanonicalState<>(new WorldId("frontier:field-growth-test"),
                new Revision(5), new SimInstant(5), target);
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0), 3);
        helper.runAtTickTime(1, () -> {
            var transition = ResourceFieldCellTransition.between(SITE, 1, 1, cell.id(),
                    ResourceFieldPhysicalSurface.Condition.of(seeded.cell(cell.id())),
                    ResourceFieldPhysicalSurface.Condition.of(target.cell(cell.id())));
            var predecessor = FrontierV3ResourceFieldWitness.claimed(SITE, 1,
                    ResourceFieldPhysicalSurface.fromCycle(seeded));
            var physicalBefore = FrontierV3ResourceFieldObservation.observe(level, target, predecessor, cell.id(),
                    "native:growth-authorized-before");
            var fabricatedBefore = FrontierV3ResourceFieldObservation.compare(predecessor, layout, cell.id(),
                    physicalBefore.reading());
            helper.assertTrue(rejects(() -> predecessor.beginCanonicalProjection(accepted, transition,
                            "projection:growth-5", fabricatedBefore)),
                    "a caller-supplied lookalike condition is not a real loaded-world predecessor");
            var pending = FrontierV3ResourceFieldWitness.read(predecessor.beginCanonicalProjection(accepted, transition,
                    "projection:growth-5", physicalBefore).write());
            var unchanged = FrontierV3ResourceFieldObservation.observe(level, target, pending, cell.id(), "native:growth-before");
            helper.assertTrue(unchanged.disposition() == FrontierV3ResourceFieldObservation.Disposition.CURRENT
                            && rejects(() -> pending.acknowledgeCanonicalProjection(accepted, cell.id(),
                                    "projection:growth-5", unchanged)),
                    "canonical authority alone cannot confirm an unwritten physical growth step");
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1), 3);
            var afterWrite = FrontierV3ResourceFieldObservation.observe(level, target, pending, cell.id(), "native:growth-after");
            var complete = FrontierV3ResourceFieldWitness.read(pending.confirm(cell.id(), afterWrite).write());
            var current = FrontierV3ResourceFieldObservation.observe(level, target, complete, cell.id(), "native:growth-current");
            var foreignWorld = new FrontierCanonicalState<>(new WorldId("frontier:other"),
                    accepted.revision(), accepted.instant(), target);
            helper.assertTrue(rejects(() -> complete.acknowledgeCanonicalProjection(foreignWorld, cell.id(),
                            "projection:growth-5", current))
                            && rejects(() -> complete.acknowledgeCanonicalProjection(accepted, cell.id(),
                                    "projection:other", current)),
                    "a foreign canonical world or cause cannot retire the retained physical projection");
            var acknowledged = FrontierV3ResourceFieldWitness.read(complete.acknowledgeCanonicalProjection(
                    accepted, cell.id(), "projection:growth-5", current).write());
            helper.assertTrue(acknowledged.cell(cell.id()).pending().isEmpty()
                            && acknowledged.cell(cell.id()).committed().equals(transition.after()),
                    "same accepted canonical target and actual loaded block retire only this projection cause");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void restartAfterBothPhysicalWritesCanConfirmTheExactLaterPrefix(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos soil = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos crop = soil.above();
        var cell = cell(1, soil);
        var layout = new ResourceFieldLayout(1, 2, List.of(cell), List.of());
        var cycle = ResourceFieldCycle.seeded(SITE, layout, 1);
        var dirt = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.DIRT,
                ResourceFieldCycle.Crop.ABSENT, 0);
        var planted = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.GROWING, 0);
        level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(crop, Blocks.AIR.defaultBlockState(), 3);
        helper.runAtTickTime(1, () -> {
            var persistedBeforeEffect = FrontierV3ResourceFieldWitness.read(FrontierV3ResourceFieldWitness.claimed(SITE, 1,
                    ResourceFieldPhysicalSurface.restore(layout, Map.of(cell.id(), dirt)))
                    .begin(ResourceFieldCellTransition.between(SITE, 1, 1, cell.id(), dirt, planted),
                            "farmer:native:crash-after-both-writes").write());
            level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
            level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0), 3);
            var observed = FrontierV3ResourceFieldObservation.observe(level, cycle, persistedBeforeEffect, cell.id(),
                    "native:crash-recovery");
            helper.assertTrue(observed.disposition() == FrontierV3ResourceFieldObservation.Disposition.LATER_STEP_APPLIED
                            && observed.completedSteps() == 2,
                    "the exact naturally loaded terminal block state must identify the later committed prefix");
            var recovered = FrontierV3ResourceFieldWitness.read(persistedBeforeEffect.confirm(cell.id(), observed).write());
            helper.assertTrue(recovered.cell(cell.id()).committed().equals(planted)
                            && recovered.cell(cell.id()).pending().orElseThrow().completedSteps() == 2
                            && rejects(() -> recovered.confirm(cell.id(), observed)),
                    "the crash bridge retains a completed physical cause without replaying the same observation");
            helper.succeed();
        });
    }

    private static boolean rejects(Runnable operation) {
        try { operation.run(); return false; }
        catch (IllegalArgumentException expected) { return true; }
    }

    private static boolean rejectsState(Runnable operation) {
        try { operation.run(); return false; }
        catch (IllegalStateException expected) { return true; }
    }

    private static ResourceFieldLayout.Cell cell(long id, BlockPos soil) {
        var support = SurfaceAnchor.at(soil.getX(), soil.getY(), soil.getZ());
        BlockPosition crop = support.support().offset(0, 1, 0);
        return new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(id), crop, support, support);
    }
}
