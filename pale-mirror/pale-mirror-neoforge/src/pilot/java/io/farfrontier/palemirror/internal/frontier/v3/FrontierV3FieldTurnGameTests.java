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
    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 300)
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
            FrontierV3ResourceFieldInitialWriter.reserve(level, a, new PhysicalIntentId("intent:field-turn-a"));
            FrontierV3ResourceFieldInitialWriter.reserve(level, b, new PhysicalIntentId("intent:field-turn-b"));
            FrontierV3ResourceFieldInitialWriter.writeBatch(level, a);
            var paused = (FrontierV3ResourceSiteLedger.FieldInitialization) ledger.fieldClaim(a.id());
            int prefix = paused.cursor().nextWrite();
            var sites = List.of(new ResourceSiteLifecycle(a.id(), ResourceSitePhase.GROWING, 1L, 0, Optional.empty()),
                    new ResourceSiteLifecycle(b.id(), ResourceSitePhase.GROWING, 1L, 0, Optional.empty()));
            finishProjection(helper, b, () -> {
                var selected = FrontierV3ResourceSiteExecutor.projectionCandidates(sites, Set.of(),
                        site -> site.siteId().equals(b.id()));
                helper.assertValueEqual(selected.size(), 1, "paused A cannot monopolize B's demanded turn");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, b, 0), "B completes its physical cells");
            helper.assertValueEqual(((FrontierV3ResourceSiteLedger.FieldInitialization) ledger.fieldClaim(a.id()))
                    .cursor().nextWrite(), prefix, "B cannot consume A's cursor");
            FrontierV3ResourceSiteExecutor.forget(runtime);
            var reloaded = FrontierV3ResourceSiteLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(),
                    level.registryAccess()), level.registryAccess());
            helper.assertValueEqual(((FrontierV3ResourceSiteLedger.FieldInitialization) reloaded.fieldClaim(a.id()))
                    .cursor().nextWrite(), prefix, "SavedData retains A's exact cursor");
            finishProjection(helper, a, () -> {
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, a, 0), "A completes after return");
            helper.succeed();
            });
            });
        } finally { FrontierV3ResourceSiteExecutor.forget(runtime); }
    }
    private static void finishProjection(GameTestHelper helper, ResourceSite site, Runnable complete) {
        var ledger = FrontierV3ResourceSiteLedger.get(helper.getLevel());
        var before = ((FrontierV3ResourceSiteLedger.FieldInitialization) ledger.fieldClaim(site.id())).cursor();
        if (before.complete()) { complete.run(); return; }
        var result = FrontierV3ResourceFieldInitialWriter.writeBatch(helper.getLevel(), site);
        if (result == FrontierV3ResourceFieldInitialWriter.Result.FOREIGN
                || result == FrontierV3ResourceFieldInitialWriter.Result.AMBIGUOUS)
            helper.fail("declared field projection lost its exact predecessor: " + result);
        var after = ((FrontierV3ResourceSiteLedger.FieldInitialization) ledger.fieldClaim(site.id())).cursor();
        helper.assertTrue(after.nextWrite() >= before.nextWrite()
                && after.nextWrite() - before.nextWrite() <= FrontierV3ResourceSiteExecutor.projectionWriteBudget(),
                "one physical turn retains monotonic bounded projection progress");
        helper.runAfterDelay(1, () -> finishProjection(helper, site, complete));
    }
    private static ResourceSite field(BlockPos origin, String id) {
        var crops = new ArrayList<BlockPosition>();
        for (int x = 0; x < 8; x++) for (int index = 0; index < 8; index++) {
            int z = x % 2 == 0 ? index : 7 - index;
            crops.add(new BlockPosition(origin.getX()+x, origin.getY(), origin.getZ()+z));
        }
        return new ResourceSite(new SubjectId(id), new SubjectId("settlement:1"), new SubjectId("structure:1-farm"), ResourceSiteKind.WHEAT_FIELD, io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan.initialGrayboxLayout(crops));
    }
    private static BlockPos pos(BlockPosition p) { return new BlockPos(p.x(), p.y(), p.z()); }
    private static final class EphemeralStore implements FrontierStore {
        public RecoveryImage recover(WorldId world) { return new RecoveryImage(world, Optional.empty(), List.of()); }
        public AppendReceipt append(TransactionRecord transaction, Durability durability) { return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value()); }
        public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("no checkpoint in adapter fixture"); }
        public CompactionReceipt compact(WorldId world, Revision revision) { throw new UnsupportedOperationException("no compaction in adapter fixture"); }
    }
}
