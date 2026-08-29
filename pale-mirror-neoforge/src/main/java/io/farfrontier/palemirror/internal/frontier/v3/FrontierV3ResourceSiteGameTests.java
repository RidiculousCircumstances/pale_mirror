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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Materialized ownership and recovery boundary for one exact 8x8 wheat field. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ResourceSiteGameTests {
    private FrontierV3ResourceSiteGameTests() { }

    @GameTest(batch = "pm-frontier-v3-resource-site", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ownedFieldWritesAllSlotsAndRecoversItsPendingProvenance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(helper.absolutePos(new BlockPos(8, 8, 8)));
        prepareBaseline(level, site);
        helper.runAfterDelay(10, () -> {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
            PhysicalIntentId intent = new PhysicalIntentId("intent:site-prepare-resource-site-game-test"); ledger.reserve(site.id(), intent);
            CompoundTag pending = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(pending, level.registryAccess());
            helper.assertTrue(ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.PENDING && FrontierV3ResourceSiteExecutor.baseline(level, site),
                    "a restart retains pending ownership without treating the grass baseline as a completed field");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.placeWholeField(level, site), "one executor pass writes every prevalidated soil and crop slot");
            ledger.activate(site.id()); CompoundTag active = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ResourceSiteLedger.load(active, level.registryAccess());
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, 0) && ledger.claim(site.id()).status() == FrontierV3ResourceSiteLedger.Status.ACTIVE,
                    "the exact 64 farmland and 64 wheat cells plus active provenance survive a SavedData reload");
            helper.assertValueEqual(FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 3), FrontierV3ResourceSiteExecutor.StageProjectionResult.UPDATED,
                    "a canonical COLD growth stage updates only the owned field cells");
            helper.assertTrue(FrontierV3ResourceSiteExecutor.matches(level, site, 3) && ledger.claim(site.id()).stage() == 3,
                    "the ledger retains the observed physical stage needed for later drift detection");
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

    @GameTest(batch = "pm-frontier-v3-resource-site", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void foreignFieldCellIsNeverAdoptedOrOverwritten(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); ResourceSite site = field(helper.absolutePos(new BlockPos(32, 8, 8)));
        prepareBaseline(level, site); BlockPosition foreign = site.cropSlots().getFirst(); BlockPos position = minecraft(foreign);
        level.setBlock(position, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3ResourceSiteExecutor.baseline(level, site) || FrontierV3ResourceSiteExecutor.placeWholeField(level, site),
                "a foreign crop cell rejects the whole field instead of mixing owned and player/world geometry");
        helper.assertTrue(level.getBlockState(position).is(Blocks.DIAMOND_BLOCK) && site.soilSlots().stream()
                        .allMatch(soil -> level.getBlockState(minecraft(soil)).is(Blocks.GRASS_BLOCK)),
                "rejection leaves the foreign block and every untouched soil-capital cell exactly as observed");
        helper.succeed();
    }

    private static ResourceSite field(BlockPos origin) {
        List<BlockPosition> crops = new ArrayList<>(64);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) crops.add(new BlockPosition(origin.getX() + x, origin.getY(), origin.getZ() + z));
        return new ResourceSite(new SubjectId("site:resource-site-game-test"), new SubjectId("settlement:1"), new SubjectId("structure:1-farm"),
                ResourceSiteKind.WHEAT_FIELD, crops);
    }
    private static void prepareBaseline(ServerLevel level, ResourceSite site) {
        site.soilSlots().forEach(soil -> { BlockPos position = minecraft(soil); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(position, Blocks.GRASS_BLOCK.defaultBlockState(), 3); });
        int minX = site.cropSlots().stream().mapToInt(BlockPosition::x).min().orElseThrow(), maxX = site.cropSlots().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        site.cropSlots().stream().filter(crop -> crop.x() == minX).forEach(crop -> level.setBlock(minecraft(crop).west(), Blocks.GLOWSTONE.defaultBlockState(), 3));
        site.cropSlots().stream().filter(crop -> crop.x() == maxX).forEach(crop -> level.setBlock(minecraft(crop).east(), Blocks.GLOWSTONE.defaultBlockState(), 3));
    }
    private static BlockPos minecraft(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
}
