package io.farfrontier.palemirror.visuals.gametest;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.foundry.FoundryAuditEngine;
import io.farfrontier.palemirror.visuals.genesis.FrontierClimate;
import io.farfrontier.palemirror.visuals.genesis.FrontierGenesisCompiler;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionPlanner;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime rule coverage for authored doors and their terrain-following access sills. */
@GameTestHolder(PaleMirrorVisualsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundrySettlementEntranceGameTests {
    private FoundrySettlementEntranceGameTests() { }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 200)
    public static void distinguishesWalkableSillFromSolidEntranceBlockage(GameTestHelper helper) {
        BlockPos testOrigin = helper.absolutePos(BlockPos.ZERO);
        VisualPoint anchor = new VisualPoint(testOrigin.getX(), testOrigin.getY() + 1, testOrigin.getZ());
        var seed = new FrontierRegionPlanner().plan(7_311_942L, 0, anchor, FrontierClimate.TEMPERATE,
                (x, z) -> anchor.y());
        var assay = seed.settlementSite().modules().stream()
                .filter(value -> value.instanceId().equals("assay_office_shell")).findFirst().orElseThrow();
        var port = assay.ports().stream().filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                .findFirst().orElseThrow();
        Direction outward = direction(port.outwardQuarterTurns());
        BlockPos entrance = block(port.position());
        BlockPos first = entrance.relative(outward);
        BlockPos second = entrance.relative(outward, 2);
        helper.getLevel().getChunkAt(first);
        helper.getLevel().getChunkAt(second);
        helper.getLevel().setBlock(first, Blocks.STONE_SLAB.defaultBlockState(), 2);
        helper.getLevel().setBlock(first.above(), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(second, Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(second.above(), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(second.above(2), Blocks.AIR.defaultBlockState(), 2);
        var catalog = new FrontierGenesisCompiler().compile(List.of(seed));
        var engine = new FoundryAuditEngine();

        var walkable = engine.audit(catalog, seed.planId(), helper.getLevel(), FoundryAuditPhase.SETTLED);
        helper.assertTrue(noBlockedEntrance(walkable, assay.instanceId()),
                "a slab followed by a full upper tread is a walkable two-half-step transition");

        helper.getLevel().setBlock(second.above(), Blocks.STONE.defaultBlockState(), 2);
        var blocked = engine.audit(catalog, seed.planId(), helper.getLevel(), FoundryAuditPhase.SETTLED);
        helper.assertTrue(!noBlockedEntrance(blocked, assay.instanceId()),
                "a full block above the upper tread must remain a locatable headroom error");

        helper.getLevel().setBlock(second.above(), Blocks.AIR.defaultBlockState(), 2);
        var recovered = engine.audit(catalog, seed.planId(), helper.getLevel(), FoundryAuditPhase.RELOADED);
        helper.assertTrue(noBlockedEntrance(recovered, assay.instanceId()),
                "restoring headroom must clear the entrance finding after reload");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 200)
    public static void compiledAssayPorchMatchesItsPublicEntranceContract(GameTestHelper helper) {
        BlockPos testOrigin = helper.absolutePos(BlockPos.ZERO);
        VisualPoint anchor = new VisualPoint(testOrigin.getX(), testOrigin.getY() + 1, testOrigin.getZ());
        var seed = new FrontierRegionPlanner().plan(7_311_942L, 0, anchor, FrontierClimate.TEMPERATE,
                (x, z) -> anchor.y());
        var assay = seed.settlementSite().modules().stream()
                .filter(value -> value.instanceId().equals("assay_office_shell")).findFirst().orElseThrow();
        var port = assay.ports().stream().filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                .findFirst().orElseThrow();
        Direction outward = direction(port.outwardQuarterTurns());
        BlockPos entrance = block(port.position());
        var catalog = new FrontierGenesisCompiler().compile(List.of(seed));
        for (int distance = 1; distance <= 2; distance++) for (int up = 0; up <= 2; up++) {
            BlockPos position = entrance.relative(outward, distance).above(up);
            helper.getLevel().setBlock(position, compiledState(catalog, position), 2);
        }

        var report = new FoundryAuditEngine().audit(
                catalog, seed.planId(), helper.getLevel(), FoundryAuditPhase.SETTLED);

        helper.assertTrue(noBlockedEntrance(report, assay.instanceId()),
                "the pinned assay porch must be physically traversable from its sidecar port; cells="
                        + throatStates(helper, entrance, outward) + ", port=" + entrance
                        + ", portState=" + compiledState(catalog, entrance));
        helper.succeed();
    }

    private static net.minecraft.world.level.block.state.BlockState compiledState(
            io.farfrontier.palemirror.visuals.genesis.CompiledGenesisCatalog catalog, BlockPos position) {
        return catalog.chunks().values().stream().map(value -> value.blocks().get(position))
                .filter(java.util.Objects::nonNull).map(value -> value.state()).findFirst()
                .orElse(Blocks.AIR.defaultBlockState());
    }

    private static String throatStates(GameTestHelper helper, BlockPos entrance, Direction outward) {
        java.util.List<String> states = new java.util.ArrayList<>();
        for (int distance = 1; distance <= 2; distance++) for (int up = 0; up <= 2; up++) {
            BlockPos position = entrance.relative(outward, distance).above(up);
            states.add(distance + "/" + up + "=" + helper.getLevel().getBlockState(position));
        }
        return states.toString();
    }

    private static boolean noBlockedEntrance(io.farfrontier.palemirror.api.FoundryAuditReport report,
                                             String moduleId) {
        return report.findings().stream().noneMatch(value -> value.ruleId().equals("navigation.entrance.blocked")
                && value.targetId().equals(moduleId));
    }

    private static Direction direction(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static BlockPos block(VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
