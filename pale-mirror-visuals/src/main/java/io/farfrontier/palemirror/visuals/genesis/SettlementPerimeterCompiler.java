package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Terrain-following visual compiler for authored walls and their gates. */
final class SettlementPerimeterCompiler {
    private SettlementPerimeterCompiler() { }

    static void palisade(List<VisualPoint> points, FrontierPalette palette,
                         SettlementFixtureOccupancy fixtures, SettlementGenesisCompiler.Sink sink) {
        for (int index = 0; index < points.size(); index++) {
            VisualPoint point = points.get(index);
            if (index % 6 == 0) {
                sink.surfaceBlock(point.x(), point.z(), 0, palette.foundation());
                sink.surfaceBlock(point.x(), point.z(), 1, palette.log());
                if (index % 18 == 0 && fixtures.reserve(point.x(), point.z(), 3)) {
                    sink.surfaceBlock(point.x(), point.z(), 2, Blocks.LANTERN.defaultBlockState());
                }
            } else {
                sink.surfaceBlock(point.x(), point.z(), 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
                sink.surfaceBlock(point.x(), point.z(), 1, SettlementGenesisCompiler.fence(palette));
            }
        }
    }

    static void gate(List<VisualPoint> points, FrontierPalette palette, SettlementGenesisCompiler.Sink sink) {
        if (points.isEmpty()) return;
        VisualPoint first = points.getFirst();
        VisualPoint last = points.getLast();
        Direction facing = Math.abs(last.x() - first.x()) >= Math.abs(last.z() - first.z())
                ? Direction.NORTH : Direction.EAST;
        BlockState gate = fenceGate(palette).setValue(FenceGateBlock.FACING, facing);
        int centerIndex = points.size() / 2;
        int openingRadius = points.size() >= 7 ? 1 : 0;
        for (int index = 0; index < points.size(); index++) {
            VisualPoint point = points.get(index);
            sink.surfaceBlock(point.x(), point.z(), -1, palette.foundation());
            boolean opening = Math.abs(index - centerIndex) <= openingRadius;
            boolean jamb = Math.abs(index - centerIndex) == openingRadius + 1;
            if (jamb) {
                for (int up = 0; up <= 3; up++) sink.surfaceBlock(point.x(), point.z(), up, palette.log());
            } else if (opening) {
                sink.surfaceBlock(point.x(), point.z(), 0, gate);
            } else {
                sink.surfaceBlock(point.x(), point.z(), 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
                sink.surfaceBlock(point.x(), point.z(), 1, SettlementGenesisCompiler.fence(palette));
            }
            sink.surfaceBlock(point.x(), point.z(), 4, palette.log());
        }
        VisualPoint center = points.get(centerIndex);
        sink.surfaceBlock(center.x(), center.z(), 3, Blocks.LANTERN.defaultBlockState().setValue(
                net.minecraft.world.level.block.LanternBlock.HANGING, true));
    }

    private static BlockState fenceGate(FrontierPalette palette) {
        if (palette.planks().is(Blocks.ACACIA_PLANKS)) return Blocks.ACACIA_FENCE_GATE.defaultBlockState();
        if (palette.planks().is(Blocks.OAK_PLANKS)) return Blocks.OAK_FENCE_GATE.defaultBlockState();
        return Blocks.SPRUCE_FENCE_GATE.defaultBlockState();
    }
}
