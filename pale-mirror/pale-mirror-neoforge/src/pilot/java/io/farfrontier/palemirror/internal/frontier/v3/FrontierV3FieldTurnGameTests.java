package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Real bounded writer and physical SavedData cursors; demand is an explicit test input, not a player visit claim. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3FieldTurnGameTests {
    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 40)
    public static void pausedFieldDoesNotOwnOtherFieldsTurnsAndResumesItsExactPrefix(GameTestHelper helper) {
        var level = helper.getLevel();
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:field-turns"), 42L);
        var runtime = FrontierV3ServerRuntime.start(config, new EphemeralStore(), 20_000);
        var state = runtime.decodedState().orElseThrow();
        var a = field(helper.absolutePos(new BlockPos(4, 30, 4)), "site:1-wheat-field");
        var b = field(helper.absolutePos(new BlockPos(16, 30, 4)), "site:2-wheat-field");
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        try {
            for (var site : List.of(a, b)) {
                for (var soil : site.soilSlots()) {
                    level.setBlock(pos(soil).below(), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(pos(soil), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 2);
                }
                for (var water : site.irrigationSlots()) {
                    level.setBlock(pos(water).below(), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(pos(water), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 2);
                }
                site.cropSlots().forEach(crop -> level.setBlock(pos(crop), Blocks.AIR.defaultBlockState(), 2));
            }
            var first = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, a, 0, 0);
            helper.assertTrue(first == FrontierV3ResourceSiteExecutor.StageProjectionResult.DEFERRED, "A starts a bounded partial write");
            int prefix = ledger.claim(a.id()).projection().nextWrite();
            helper.assertValueEqual(prefix, FrontierV3ResourceSiteExecutor.projectionWriteBudget(), "one bounded batch only");
            var sites = List.of(new ResourceSiteLifecycle(a.id(), ResourceSitePhase.GROWING, 1L, 0, Optional.empty()),
                    new ResourceSiteLifecycle(b.id(), ResourceSitePhase.GROWING, 1L, 0, Optional.empty()));
            for (int turn = 0; turn < 20 && (ledger.claim(b.id()) == null || ledger.claim(b.id()).projection() != null); turn++) {
                var selected = FrontierV3ResourceSiteExecutor.projectionCandidates(sites, Set.of(), site -> site.siteId().equals(b.id()));
                helper.assertValueEqual(selected.size(), 1, "only demanded B is eligible while A remains in flight");
                FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, b, 0, 0);
            }
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, b, 0), "B completes all physical cells");
            helper.assertValueEqual(ledger.claim(a.id()).projection().nextWrite(), prefix, "B cannot consume A's cursor");
            FrontierV3ResourceSiteExecutor.forget(runtime);
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, a, ledger.claim(a.id())), "A remains recoverable after volatile-cache loss");
            FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, a, 0, 0);
            helper.assertValueEqual(ledger.claim(a.id()).projection().nextWrite(), prefix + FrontierV3ResourceSiteExecutor.projectionWriteBudget(), "A resumes rather than replaying its first batch");
            for (int turn = 0; turn < 20 && ledger.claim(a.id()).projection() != null; turn++) {
                FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, a, 0, 0);
            }
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, a, 0), "A completes after return");
            helper.succeed();
        } finally { FrontierV3ResourceSiteExecutor.forget(runtime); }
    }
    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 40)
    public static void resolvedHarvestRestoresGrowingAndReadySuccessorsWithoutOverwritingForeignCells(GameTestHelper helper) {
        var level = helper.getLevel();
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:field-successor"), 42L), new EphemeralStore(), 20_000);
        var baseline = runtime.decodedState().orElseThrow();
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        try {
            // Domain lineage is a declared adapter precondition, not evidence of a completed economic cycle.
            for (int variant = 0; variant < 3; variant++) {
                // SavedData is shared across concurrent GameTests in the same ServerLevel.
                var site = field(helper.absolutePos(new BlockPos(4 + variant * 12, 30, 4)), "site:" + (variant + 10) + "-wheat-field");
                int stage = variant == 0 ? 2 : 7;
                var lineage = new ResourceSiteHarvestLineage(new SubjectId("job:site-harvest-1-wheat-field-2"),
                        new SubjectId("task:settlement-1-harvest"), new SubjectId("resident:1-31"),
                        new SubjectId("item:site-harvest-1-wheat-field-2-wheat"), 2L, new BodyPosition(0, 64, 0),
                        new PhysicalIntentId("intent:site-harvest-1-wheat-field-2"),
                        new InventoryCustody.ContainerSlot(new SubjectId("container:1"), 1), true, Optional.empty(), Optional.empty());
                var lifecycle = new ResourceSiteLifecycle(site.id(), stage == 7 ? ResourceSitePhase.READY : ResourceSitePhase.GROWING,
                        3L, stage, Optional.empty(), Optional.empty(), Optional.of(lineage));
                var state = baseline.withResourceSites(baseline.resourceSites().replace(lifecycle));
                site.soilSlots().forEach(p -> level.setBlock(pos(p), Blocks.FARMLAND.defaultBlockState(), 2));
                site.irrigationSlots().forEach(p -> level.setBlock(pos(p), Blocks.WATER.defaultBlockState(), 2));
                site.cropSlots().forEach(p -> level.setBlock(pos(p), Blocks.AIR.defaultBlockState(), 2));
                ledger.reserveComposedTerminalSuccessor(site.id(), lineage.predecessorIntentId());
                ledger.activate(site.id());
                if (variant < 2) {
                    var pending = new ResourceSiteHarvestLineage(lineage.predecessorJobId(), lineage.predecessorTaskId(),
                            lineage.workerId(), lineage.outputItemId(), lineage.completedGrowthEpoch(), lineage.terminalBody(),
                            lineage.predecessorIntentId(), lineage.outputSlot(), false, Optional.empty(), Optional.empty());
                    var pendingLifecycle = new ResourceSiteLifecycle(site.id(), lifecycle.phase(), 3L, stage,
                            Optional.empty(), Optional.empty(), Optional.of(pending));
                    var pendingState = baseline.withResourceSites(baseline.resourceSites().replace(pendingLifecycle));
                    for (int attempt = 0; attempt < 2; attempt++) {
                        var held = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, pendingState, site, stage, 0);
                        helper.assertTrue(held == FrontierV3ResourceSiteExecutor.StageProjectionResult.DEFERRED, "unresolved receipt retains the terminal field, including READY");
                        helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 64), "pending receipt field is untouched");
                        helper.assertTrue(ledger.claim(site.id()).projection() == null, "pending receipt grants no regrowth writer");
                        FrontierV3ResourceSiteExecutor.forget(runtime);
                    }
                    // Changing this explicit adapter precondition is not an economic receipt/command test.
                }
                if (variant == 2) level.setBlock(pos(site.cropSlots().getLast()), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                var result = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, site, stage, 0);
                if (variant == 2) {
                    helper.assertTrue(result == FrontierV3ResourceSiteExecutor.StageProjectionResult.CONFLICT, "foreign predecessor must reject before any writes");
                    helper.assertTrue(level.getBlockState(pos(site.cropSlots().getLast())).is(Blocks.DIAMOND_BLOCK), "foreign block is preserved");
                    helper.assertValueEqual(ledger.claim(site.id()).harvestedCropSlots(), 64, "rejection retains terminal receipt cursor");
                    helper.assertTrue(ledger.claim(site.id()).projection() == null, "rejection does not start a projection");
                    continue;
                }
                helper.assertTrue(result == FrontierV3ResourceSiteExecutor.StageProjectionResult.DEFERRED, "successor uses bounded writes");
                if (stage == 7) helper.assertValueEqual(ledger.claim(site.id()).harvestedCropSlots(), 56, "mature successor restores reverse cursor");
                FrontierV3ResourceSiteExecutor.forget(runtime);
                helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesPersistedProjectionPrefix(level, site, ledger.claim(site.id())), "successor prefix survives cache loss");
                for (int turn = 0; turn < 10 && ledger.claim(site.id()).projection() != null; turn++) {
                    result = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, site, stage, 0);
                }
                helper.assertTrue(result == FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "successor terminates");
                helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, stage), "all successor crops match canonical stage");
                helper.assertValueEqual(ledger.claim(site.id()).harvestedCropSlots(), 0, "successor begins with no harvested slots");
            }
            helper.succeed();
        } finally { FrontierV3ResourceSiteExecutor.forget(runtime); }
    }
    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 40)
    public static void activeSuccessorValidatesItsWholePredecessorBeforeTheFirstWrite(GameTestHelper helper) {
        var level = helper.getLevel();
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:active-field-successor"), 42L), new EphemeralStore(), 20_000);
        var baseline = runtime.decodedState().orElseThrow();
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        try {
            for (int variant = 0; variant < 3; variant++) {
                var site = field(helper.absolutePos(new BlockPos(4 + variant * 12, 30, 4)), "site:" + (variant + 7) + "-wheat-field");
                var surfaces = new ArrayList<>(site.cropSlots().stream().map(p -> new SurfaceAnchor(p.offset(0, -1, 0))).toList());
                var last = surfaces.getLast();
                for (int tail = 1; tail <= 4; tail++) surfaces.add(new SurfaceAnchor(last.support().offset(tail, 0, 0)));
                var topology = TraversalTopology.corridor(new TraversalTopologyId("topology:active-field-" + variant), 1L,
                        site.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), surfaces);
                var job = new ResourceSiteHarvestJob(new SubjectId("job:site-harvest-active-" + variant),
                        new SubjectId("task:active-field-" + variant), site.id(), new SubjectId("resident:1-31"),
                        new SubjectId("item:site-harvest-active-" + variant), new InventoryCustody.ContainerSlot(new SubjectId("container:1"), 1),
                        new PhysicalIntentId("intent:site-harvest-active-" + variant), new ResourceSiteHarvestProgress(3, -1), topology, 3);
                var predecessor = new PhysicalIntentId("intent:site-harvest-predecessor-" + variant);
                Optional<ResourceSiteHarvestLineage> pending = variant == 2 ? Optional.of(new ResourceSiteHarvestLineage(
                        new SubjectId("job:site-harvest-predecessor-" + variant), new SubjectId("task:predecessor-" + variant),
                        job.workerId(), new SubjectId("item:site-harvest-predecessor-" + variant), 2L,
                        surfaces.getLast().standingBody(), predecessor, new InventoryCustody.ContainerSlot(job.outputSlot().containerId(), 0),
                        false, Optional.empty(), Optional.empty()).bindSuccessor(job)) : Optional.empty();
                var lifecycle = new ResourceSiteLifecycle(site.id(), ResourceSitePhase.HARVESTING, 3L, 7,
                        Optional.of(job), Optional.empty(), pending);
                var state = baseline.withResourceSites(baseline.resourceSites().replace(lifecycle));
                site.soilSlots().forEach(p -> level.setBlock(pos(p), Blocks.FARMLAND.defaultBlockState(), 2));
                site.irrigationSlots().forEach(p -> level.setBlock(pos(p), Blocks.WATER.defaultBlockState(), 2));
                site.cropSlots().forEach(p -> level.setBlock(pos(p), Blocks.AIR.defaultBlockState(), 2));
                ledger.reserveComposedTerminalSuccessor(site.id(), predecessor);
                ledger.activate(site.id());
                if (variant == 1) level.setBlock(pos(site.cropSlots().getLast()), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                var result = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, site, 7, 3);
                if (variant == 2) {
                    helper.assertTrue(result == FrontierV3ResourceSiteExecutor.StageProjectionResult.DEFERRED, "active successor waits for predecessor receipt");
                    helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 64), "active successor cannot erase pending receipt surface");
                    helper.assertTrue(ledger.claim(site.id()).projection() == null, "active pending receipt cannot begin regrowth");
                    var lineage = pending.orElseThrow();
                    var running = new PhysicalIntent(predecessor, PhysicalIntentKind.RESOURCE_SITE_HARVEST,
                            PhysicalIntentStatus.RUNNING, site.id(), PhysicalIntentRoleBinding.siteHarvest(site.id(),
                            lineage.predecessorJobId(), lineage.workerId(), lineage.outputItemId()),
                            new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                            PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED, PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST);
                    state = state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(Map.of(predecessor, running)));
                    // Explicit partial predecessor fixture, before any projection has started.
                    for (int cursor = 63; cursor >= 21; cursor--) {
                        level.setBlock(pos(site.cropSlots().get(cursor)), Blocks.WHEAT.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 2);
                        ledger.restoreOne(site.id(), cursor);
                    }
                    result = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, site, 7, 3);
                    helper.assertTrue(result == FrontierV3ResourceSiteExecutor.StageProjectionResult.DEFERRED, "partial predecessor completes bounded terminal suffix first");
                    helper.assertTrue(FrontierV3ResourceSiteDeferredTerminalPrefix.awaitingProjection(level, state, site, running),
                            "receipt executor must wait during the mixed intermediate prefix");
                    FrontierV3ResourceSiteExecutor.forget(runtime);
                    for (int turn = 0; turn < 10 && ledger.claim(site.id()).projection() != null; turn++)
                        result = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, site, 7, 3);
                    helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 64), "old prefix becomes terminal receipt before new growth");
                    helper.assertTrue(!FrontierV3ResourceSiteDeferredTerminalPrefix.awaitingProjection(level, state, site, running), "complete old field is ready for its receipt executor");
                    var chestPosition = helper.absolutePos(new BlockPos(30, 30, 18));
                    level.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 2);
                    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPosition);
                    var output = new ExactItemStack(lineage.outputItemId(), site.settlementId(), "minecraft:wheat", 64, lineage.outputSlot());
                    helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.completeRunning(level, site, ledger, chest, output), "old terminal field produces its exact physical output");
                    helper.assertTrue(FrontierV3ResourceSiteHarvestExecutor.completeRunning(level, site, ledger, chest, output), "repeated physical receipt acknowledges rather than duplicates output");
                    helper.assertValueEqual(chest.getItem(0).getCount(), 64, "one predecessor output only");
                    // Resolved lineage with retired predecessor intent is a supplied adapter
                    // boundary here, not a registered-command/retirement proof.
                    var observation = new PhysicalObservationId("observation:partial-field-receipt");
                    state = state.withChanges(FrontierWorldStateUpdate.begin().resourceSites(state.resourceSites()
                            .replace(lifecycle.confirmDeferredHarvestReceipt(predecessor, observation))).physicalIntents(Map.of()));
                    result = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, site, 7, 3);
                    for (int turn = 0; turn < 10 && ledger.claim(site.id()).projection() != null; turn++)
                        result = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, site, 7, 3);
                    helper.assertTrue(result == FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "successor resumes after old receipt confirmation");
                    helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 3), "same successor progress survives old receipt completion");
                    helper.assertValueEqual(chest.getItem(0).getCount(), 64, "regrowth cannot replay old wheat");
                } else if (variant == 1) {
                    helper.assertTrue(result == FrontierV3ResourceSiteExecutor.StageProjectionResult.CONFLICT, "damaged active predecessor rejects before first batch");
                    helper.assertTrue(level.getBlockState(pos(site.cropSlots().getLast())).is(Blocks.DIAMOND_BLOCK), "first reverse write cannot overwrite a foreign cell");
                    helper.assertValueEqual(ledger.claim(site.id()).harvestedCropSlots(), 64, "rejected active predecessor retains cursor");
                    helper.assertTrue(ledger.claim(site.id()).projection() == null, "rejected active predecessor has no write authority");
                } else {
                    helper.assertTrue(result == FrontierV3ResourceSiteExecutor.StageProjectionResult.DEFERRED, "lawful active successor starts bounded restoration");
                    FrontierV3ResourceSiteExecutor.forget(runtime);
                    for (int turn = 0; turn < 10 && ledger.claim(site.id()).projection() != null; turn++)
                        result = FrontierV3ResourceSiteExecutor.projectLifecycleBounded(level, runtime, state, site, 7, 3);
                    helper.assertTrue(result == FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED, "active successor restoration terminates");
                    helper.assertTrue(FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, 3), "retains successor's three harvested cells");
                }
            }
            helper.succeed();
        } finally { FrontierV3ResourceSiteExecutor.forget(runtime); }
    }
    private static ResourceSite field(BlockPos origin, String id) {
        var crops = new ArrayList<BlockPosition>();
        for (int x = 0; x < 8; x++) for (int index = 0; index < 8; index++) {
            int z = x % 2 == 0 ? index : 7 - index;
            crops.add(new BlockPosition(origin.getX()+x, origin.getY(), origin.getZ()+z));
        }
        return new ResourceSite(new SubjectId(id), new SubjectId("settlement:1"), new SubjectId("structure:1-farm"), ResourceSiteKind.WHEAT_FIELD, crops);
    }
    private static BlockPos pos(BlockPosition p) { return new BlockPos(p.x(), p.y(), p.z()); }
    private static final class EphemeralStore implements FrontierStore {
        public RecoveryImage recover(WorldId world) { return new RecoveryImage(world, Optional.empty(), List.of()); }
        public AppendReceipt append(TransactionRecord transaction, Durability durability) { return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value()); }
        public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("no checkpoint in adapter fixture"); }
        public CompactionReceipt compact(WorldId world, Revision revision) { throw new UnsupportedOperationException("no compaction in adapter fixture"); }
    }
}
