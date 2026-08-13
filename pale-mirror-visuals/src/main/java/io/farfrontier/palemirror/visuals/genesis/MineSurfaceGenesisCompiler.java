package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.MineFoundationPlan;
import io.farfrontier.palemirror.api.VisualPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Reusable industrial detail grammar shared by every authored surface MineSite. */
final class MineSurfaceGenesisCompiler {
    private MineSurfaceGenesisCompiler() { }

    static void compile(AuthoredMineSitePlan mine, Sink sink) {
        headframe(mine, sink);
        chimney(mine, sink);
        loadingApron(mine, sink);
        safetyFurniture(mine, sink);
    }

    private static void headframe(AuthoredMineSitePlan mine, Sink sink) {
        // Open timber headframe embedded into the mountain portal. The
        // winding/support house remains a separate building on the outer pad.
        for (int side : new int[]{-3, 3}) for (int up = 1; up <= 8; up++) {
            sink.put(local(mine.portal(), side, 0, up, mine.inwardQuarterTurns()),
                    Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        for (int side : new int[]{-4, 4}) for (int inward : new int[]{-1, 1}) for (int up = 1; up <= 4; up++) {
            if (up <= 2 || inward == 1) sink.put(local(mine.portal(), side, inward, up,
                    mine.inwardQuarterTurns()), Blocks.COBBLESTONE_WALL.defaultBlockState());
        }
        for (int right = -3; right <= 3; right++) sink.put(
                local(mine.portal(), right, 0, 8, mine.inwardQuarterTurns()),
                Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        for (int up = 3; up <= 7; up++) sink.put(
                local(mine.portal(), 2, 0, up, mine.inwardQuarterTurns()), Blocks.CHAIN.defaultBlockState());
        sink.put(local(mine.portal(), 2, 0, 2, mine.inwardQuarterTurns()), Blocks.GRINDSTONE.defaultBlockState());
        sink.put(local(mine.portal(), -2, -1, 5, mine.inwardQuarterTurns()), Blocks.LANTERN.defaultBlockState());
    }

    private static void chimney(AuthoredMineSitePlan mine, Sink sink) {
        MineFoundationPlan power = mine.foundations().stream().filter(value -> value.id().equals("power"))
                .findFirst().orElse(null);
        if (power == null) return;
        int x = power.footprint().max().x() + 1;
        int z = power.footprint().max().z() + 1;
        for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) for (int up = 1; up <= 12; up++) {
            sink.put(new BlockPos(x + dx, power.targetY() + up, z + dz),
                    up > 9 ? Blocks.BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
        }
    }

    private static void loadingApron(AuthoredMineSitePlan mine, Sink sink) {
        VisualPoint loading = mine.loadingEndpoint();
        for (int right = -5; right <= 5; right++) for (int inward = -2; inward <= 2; inward++) {
            if (right != 0) sink.put(local(loading, right, inward, -1, mine.inwardQuarterTurns()),
                    Math.floorMod(right + inward, 5) == 0 ? Blocks.STRIPPED_OAK_LOG.defaultBlockState()
                            : Blocks.OAK_PLANKS.defaultBlockState());
        }
        for (int side : new int[]{-5, 5}) for (int inward = -2; inward <= 2; inward++) sink.put(
                local(loading, side, inward, 0, mine.inwardQuarterTurns()), Blocks.SPRUCE_FENCE.defaultBlockState());
        for (int side : new int[]{-5, 5}) {
            for (int up = 1; up <= 4; up++) sink.put(local(loading, side, 2, up, mine.inwardQuarterTurns()),
                    Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            sink.put(local(loading, side, 2, 5, mine.inwardQuarterTurns()), Blocks.LANTERN.defaultBlockState());
        }
        for (int right = -5; right <= 5; right++) sink.put(
                local(loading, right, 2, 4, mine.inwardQuarterTurns()),
                Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        for (int up = 1; up <= 3; up++) sink.put(
                local(loading, 0, 2, up, mine.inwardQuarterTurns()), Blocks.CHAIN.defaultBlockState());
    }

    private static void safetyFurniture(AuthoredMineSitePlan mine, Sink sink) {
        for (MineFoundationPlan foundation : mine.foundations()) {
            if (foundation.id().equals("portal") || foundation.id().equals("crew")) continue;
            int centerZ = (foundation.footprint().min().z() + foundation.footprint().max().z()) / 2;
            int y = foundation.targetY() + 1;
            for (int x : new int[]{foundation.footprint().min().x() - 1,
                    foundation.footprint().max().x() + 1}) {
                sink.put(new BlockPos(x, y, centerZ), Blocks.COBBLESTONE_WALL.defaultBlockState());
                sink.put(new BlockPos(x, y + 1, centerZ), Blocks.SPRUCE_FENCE.defaultBlockState());
                sink.put(new BlockPos(x, y + 2, centerZ), Blocks.LANTERN.defaultBlockState());
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

    @FunctionalInterface
    interface Sink {
        void put(BlockPos position, BlockState state);
    }
}
