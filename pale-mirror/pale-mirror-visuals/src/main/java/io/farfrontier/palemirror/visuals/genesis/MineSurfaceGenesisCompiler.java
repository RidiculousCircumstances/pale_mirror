package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.MineFoundationPlan;
import io.farfrontier.palemirror.api.VisualPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Reusable industrial detail grammar shared by every authored surface MineSite. */
final class MineSurfaceGenesisCompiler {
    private MineSurfaceGenesisCompiler() { }

    static void compile(AuthoredMineSitePlan mine, FrontierPalette palette,
                        java.util.Set<Long> access, Sink sink) {
        headframe(mine, palette, sink);
        publicPortalLandmark(mine, palette, sink);
        loadingApron(mine, palette, sink);
        if (mine.role() == io.farfrontier.palemirror.api.AuthoredMineRole.ALTERNATE) return;
        safetyFurniture(mine, palette, access, sink);
    }

    private static void headframe(AuthoredMineSitePlan mine, FrontierPalette palette, Sink sink) {
        // A freestanding, grounded portal house owns the real adit axis. The
        // imported winding house is deliberately offset beside it; no roof or
        // machinery is allowed to masquerade as the underground threshold.
        for (int side : new int[]{-4, -3, 3, 4}) for (int inward = -2; inward <= 2; inward++) {
            sink.put(local(mine.portal(), side, inward, -1, mine.inwardQuarterTurns()), palette.foundation());
            if (Math.abs(side) == 4 || inward >= 1) {
                sink.put(local(mine.portal(), side, inward, 0, mine.inwardQuarterTurns()), palette.foundation());
            }
        }
        for (int side : new int[]{-3, 3}) for (int inward : new int[]{-2, 0}) {
            for (int up = 0; up <= 6; up++) {
                sink.put(local(mine.portal(), side, inward, up, mine.inwardQuarterTurns()), palette.log());
            }
        }
        for (int inward : new int[]{-2, 0}) for (int right = -3; right <= 3; right++) {
            sink.put(local(mine.portal(), right, inward, 6, mine.inwardQuarterTurns()), palette.log());
        }
        Direction tangent = direction(mine.inwardQuarterTurns()).getClockWise();
        for (int inward = -3; inward <= 1; inward++) {
            sink.put(local(mine.portal(), 0, inward, 9, mine.inwardQuarterTurns()), palette.planks());
            for (int right = 1; right <= 4; right++) {
                int roofY = 9 - right;
                sink.put(local(mine.portal(), right, inward, roofY, mine.inwardQuarterTurns()),
                        roofStair(palette, tangent));
                sink.put(local(mine.portal(), -right, inward, roofY, mine.inwardQuarterTurns()),
                        roofStair(palette, tangent.getOpposite()));
            }
        }
        for (int side : new int[]{-2, 2}) {
            sink.put(local(mine.portal(), side, -2, 5, mine.inwardQuarterTurns()),
                    Blocks.LANTERN.defaultBlockState().setValue(
                            net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        sink.put(local(mine.portal(), 0, -2, 7, mine.inwardQuarterTurns()),
                Blocks.WAXED_CUT_COPPER.defaultBlockState());
    }

    private static void loadingApron(AuthoredMineSitePlan mine, FrontierPalette palette, Sink sink) {
        VisualPoint loading = mine.loadingEndpoint();
        for (int right = -5; right <= 5; right++) for (int inward = -2; inward <= 2; inward++) {
            if (right != 0) sink.put(local(loading, right, inward, -1, mine.inwardQuarterTurns()),
                    Math.floorMod(right + inward, 5) == 0 ? palette.log() : palette.planks());
        }
        for (int side : new int[]{-5, 5}) for (int inward = -2; inward <= 2; inward++) sink.put(
                local(loading, side, inward, 0, mine.inwardQuarterTurns()),
                inward == 2 ? palette.log() : fence(palette));
        for (int side : new int[]{-5, 5}) {
            for (int up = 0; up <= 4; up++) sink.put(local(loading, side, 2, up, mine.inwardQuarterTurns()),
                    palette.log());
            sink.put(local(loading, side - Integer.signum(side), 2, 3, mine.inwardQuarterTurns()),
                    Blocks.LANTERN.defaultBlockState());
        }
        for (int right = -5; right <= 5; right++) sink.put(
                local(loading, right, 2, 4, mine.inwardQuarterTurns()),
                palette.log());
        for (int up = 1; up <= 3; up++) sink.put(
                local(loading, 0, 2, up, mine.inwardQuarterTurns()), Blocks.CHAIN.defaultBlockState());
    }

    /** The offset winding-house door receives a short porch connected to the main portal road. */
    private static void publicPortalLandmark(AuthoredMineSitePlan mine, FrontierPalette palette, Sink sink) {
        var portalModule = mine.surfaceBuildings().stream()
                .filter(value -> value.buildingId().equals("portal"))
                .flatMap(value -> value.modules().stream())
                .filter(value -> value.ports().stream().anyMatch(port ->
                        port.kind() == io.farfrontier.palemirror.api.VisualPortKind.PUBLIC_ENTRANCE))
                .findFirst().orElseThrow();
        var entrance = portalModule.ports().stream()
                .filter(value -> value.kind() == io.farfrontier.palemirror.api.VisualPortKind.PUBLIC_ENTRANCE)
                .findFirst().orElseThrow();
        net.minecraft.core.Direction outward = switch (Math.floorMod(entrance.outwardQuarterTurns(), 4)) {
            case 0 -> net.minecraft.core.Direction.EAST;
            case 1 -> net.minecraft.core.Direction.SOUTH;
            case 2 -> net.minecraft.core.Direction.WEST;
            default -> net.minecraft.core.Direction.NORTH;
        };
        net.minecraft.core.Direction tangent = outward.getClockWise();
        BlockPos threshold = new BlockPos(entrance.position().x(), entrance.position().y(), entrance.position().z());
        for (int depth = 1; depth <= 5; depth++) for (int across = -2; across <= 2; across++) {
            BlockPos floor = threshold.relative(outward, depth).relative(tangent, across).below();
            sink.put(floor, Math.abs(across) == 2 ? palette.foundation()
                    : across == 0 ? Blocks.CUT_COPPER.defaultBlockState()
                    : Blocks.POLISHED_ANDESITE.defaultBlockState());
            sink.put(floor.above(), Blocks.AIR.defaultBlockState());
            sink.put(floor.above(2), Blocks.AIR.defaultBlockState());
        }
        for (int side : new int[]{-3, 3}) for (int up = 0; up <= 5; up++) {
            sink.put(threshold.relative(outward).relative(tangent, side).above(up - 1),
                    up == 0 ? palette.foundation() : palette.log());
        }
        for (int side = -3; side <= 3; side++) {
            sink.put(threshold.relative(outward).relative(tangent, side).above(4), palette.log());
        }
        for (int side : new int[]{-2, 2}) {
            sink.put(threshold.relative(outward).relative(tangent, side).above(3),
                    Blocks.LANTERN.defaultBlockState().setValue(
                            net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        sink.put(threshold.relative(outward).above(4), Blocks.WAXED_CUT_COPPER.defaultBlockState());
    }

    private static void safetyFurniture(AuthoredMineSitePlan mine, FrontierPalette palette,
                                        java.util.Set<Long> access, Sink sink) {
        for (MineFoundationPlan foundation : mine.foundations()) {
            if (foundation.id().equals("portal") || foundation.id().equals("crew")) continue;
            int centerZ = (foundation.footprint().min().z() + foundation.footprint().max().z()) / 2;
            for (int x : new int[]{foundation.footprint().min().x() - 1,
                    foundation.footprint().max().x() + 1}) {
                if (occupied(access, x, centerZ)) continue;
                // Furniture beside a graded pad must use that pad's absolute
                // datum. A generic natural-surface lookup made railings and
                // lamps hover when the surrounding hillside differed.
                sink.put(new BlockPos(x, foundation.targetY(), centerZ), palette.foundation());
                sink.put(new BlockPos(x, foundation.targetY() + 1, centerZ), wall(palette));
                sink.put(new BlockPos(x, foundation.targetY() + 2, centerZ),
                        Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    private static BlockPos local(VisualPoint origin, int right, int inward, int up, int direction) {
        int dx = switch (Math.floorMod(direction, 4)) {
            case 0 -> inward; case 1 -> -right; case 2 -> -inward; default -> right;
        };
        int dz = switch (Math.floorMod(direction, 4)) {
            case 0 -> right; case 1 -> inward; case 2 -> -right; default -> -inward;
        };
        return new BlockPos(origin.x() + dx, origin.y() + up, origin.z() + dz);
    }

    private static BlockState fence(FrontierPalette palette) {
        if (palette.planks().is(Blocks.ACACIA_PLANKS)) return Blocks.ACACIA_FENCE.defaultBlockState();
        if (palette.planks().is(Blocks.OAK_PLANKS)) return Blocks.OAK_FENCE.defaultBlockState();
        return Blocks.SPRUCE_FENCE.defaultBlockState();
    }

    private static BlockState wall(FrontierPalette palette) {
        return palette.planks().is(Blocks.ACACIA_PLANKS)
                ? Blocks.SANDSTONE_WALL.defaultBlockState() : Blocks.COBBLESTONE_WALL.defaultBlockState();
    }

    private static BlockState roofStair(FrontierPalette palette, Direction facing) {
        BlockState state = palette.planks().is(Blocks.ACACIA_PLANKS)
                ? Blocks.ACACIA_STAIRS.defaultBlockState()
                : palette.planks().is(Blocks.OAK_PLANKS)
                ? Blocks.OAK_STAIRS.defaultBlockState() : Blocks.SPRUCE_STAIRS.defaultBlockState();
        return state.setValue(StairBlock.FACING, facing);
    }

    private static Direction direction(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static boolean occupied(java.util.Set<Long> access, int x, int z) {
        return access.contains(net.minecraft.world.level.ChunkPos.asLong(x, z));
    }

    interface Sink {
        void put(BlockPos position, BlockState state);
    }
}
