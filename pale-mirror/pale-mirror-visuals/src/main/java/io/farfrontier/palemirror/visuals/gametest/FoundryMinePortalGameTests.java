package io.farfrontier.palemirror.visuals.gametest;

import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.foundry.FoundryAuditEngine;
import io.farfrontier.palemirror.visuals.genesis.CompiledChunkSlice;
import io.farfrontier.palemirror.visuals.genesis.CompiledGenesisCatalog;
import io.farfrontier.palemirror.visuals.genesis.FrontierClimate;
import io.farfrontier.palemirror.visuals.genesis.FrontierGenesisCompiler;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionPlanner;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Negative Foundry coverage kept separate from the general integration showcase. */
@GameTestHolder(PaleMirrorVisualsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundryMinePortalGameTests {
    private FoundryMinePortalGameTests() { }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 200)
    public static void rejectsBlockedMinePortal(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(9_182_733L, 0, new VisualPoint(8_000, 72, -4_000),
                FrontierClimate.TEMPERATE);
        CompiledGenesisCatalog original = new FrontierGenesisCompiler().compile(List.of(seed));
        BlockPos portal = block(seed.primaryMineSite().portal());
        long chunkKey = new ChunkPos(portal).toLong();
        CompiledChunkSlice originalSlice = original.chunks().get(chunkKey);
        var blocks = new LinkedHashMap<>(originalSlice.blocks());
        blocks.put(portal, new CompiledChunkSlice.CompiledBlock(Blocks.STONE.defaultBlockState()));
        var brokenSlice = new CompiledChunkSlice(originalSlice.chunkKey(), originalSlice.stamp(),
                originalSlice.terrain(), originalSlice.vegetation(), originalSlice.decorations(),
                originalSlice.rails(), blocks);
        var chunks = new LinkedHashMap<>(original.chunks());
        chunks.put(chunkKey, brokenSlice);
        var broken = new CompiledGenesisCatalog(original.version(), original.hash() + "-blocked-portal",
                original.manifests(), chunks);
        var report = new FoundryAuditEngine().auditCompiled(broken, seed.planId());
        helper.assertTrue(!report.passed(), "a blocked real adit must fail the Foundry gate");
        helper.assertTrue(report.findings().stream().anyMatch(value -> value.ruleId().equals(
                        "navigation.mine_portal.clearance") && value.severity() == FoundrySeverity.BLOCKER
                        && value.position().equals(seed.primaryMineSite().portal())),
                "Foundry must locate the blocking cell at the canonical mine portal");
        helper.succeed();
    }

    private static BlockPos block(VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
