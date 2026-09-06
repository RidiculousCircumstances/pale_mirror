package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredOpenSpacePlan;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.VisualPoint;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Shared microgeometry primitives for the settlement public realm. */
final class SettlementPublicRealm {
    private SettlementPublicRealm() { }

    static BlockState stairState(LinearFeaturePlan feature, int segment, FrontierPalette palette) {
        VisualPoint from = feature.nodes().get(segment - 1);
        VisualPoint to = feature.nodes().get(segment);
        VisualPoint low = from.y() <= to.y() ? from : to;
        VisualPoint high = from.y() <= to.y() ? to : from;
        Direction facing = Direction.getNearest(high.x() - low.x(), 0, high.z() - low.z()).getOpposite();
        return palette.pavingStairs().setValue(StairBlock.FACING, facing);
    }

    static boolean perimeter(AuthoredOpenSpacePlan space, int x, int z) {
        return x == space.bounds().min().x() || x == space.bounds().max().x()
                || z == space.bounds().min().z() || z == space.bounds().max().z();
    }

    static boolean cardinalEntrance(AuthoredOpenSpacePlan space, int x, int z) {
        int centerX = (space.bounds().min().x() + space.bounds().max().x()) / 2;
        int centerZ = (space.bounds().min().z() + space.bounds().max().z()) / 2;
        return (Math.abs(x - centerX) <= 1
                && (z == space.bounds().min().z() || z == space.bounds().max().z()))
                || (Math.abs(z - centerZ) <= 1
                && (x == space.bounds().min().x() || x == space.bounds().max().x()));
    }

    static void lowLamp(int x, int z, FrontierPalette palette, SettlementGenesisCompiler.Sink sink) {
        sink.surfaceBlock(x, z, 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
        sink.surfaceBlock(x, z, 1, SettlementGenesisCompiler.fence(palette));
        sink.surfaceBlock(x, z, 2, Blocks.LANTERN.defaultBlockState());
    }

    static void streetLamp(int x, int z, int dx, int dz, int side,
                           FrontierPalette palette, SettlementGenesisCompiler.Sink sink) {
        sink.surfaceBlock(x, z, 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
        sink.surfaceBlock(x, z, 1, palette.log());
        sink.surfaceBlock(x, z, 2, palette.log());
        int bracketX = x - dz * side;
        int bracketZ = z + dx * side;
        sink.surfaceBlock(bracketX, bracketZ, 2, SettlementGenesisCompiler.fence(palette));
        sink.surfaceBlock(bracketX, bracketZ, 1,
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    static BlockState door(FrontierPalette palette) {
        if (palette.planks().is(Blocks.ACACIA_PLANKS)) return Blocks.ACACIA_DOOR.defaultBlockState();
        if (palette.planks().is(Blocks.OAK_PLANKS)) return Blocks.OAK_DOOR.defaultBlockState();
        return Blocks.SPRUCE_DOOR.defaultBlockState();
    }

    static Direction direction(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    static BlockPos block(VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
