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

    static void compile(AuthoredMineSitePlan mine, FrontierPalette palette, Sink sink) {
        headframe(mine, palette, sink);
        loadingApron(mine, palette, sink);
        safetyFurniture(mine, palette, sink);
        industrialThreshold(mine, palette, sink);
        spoilHeaps(mine, sink);
    }

    private static void headframe(AuthoredMineSitePlan mine, FrontierPalette palette, Sink sink) {
        // Open timber headframe embedded into the mountain portal. The
        // winding/support house remains a separate building on the outer pad.
        for (int side : new int[]{-3, 3}) for (int up = 1; up <= 8; up++) {
            sink.put(local(mine.portal(), side, 0, up, mine.inwardQuarterTurns()),
                    palette.log());
        }
        for (int side : new int[]{-4, 4}) for (int inward : new int[]{-1, 1}) for (int up = 1; up <= 4; up++) {
            if (up <= 2 || inward == 1) sink.put(local(mine.portal(), side, inward, up,
                    mine.inwardQuarterTurns()), palette.foundation());
        }
        for (int right = -3; right <= 3; right++) sink.put(
                local(mine.portal(), right, 0, 8, mine.inwardQuarterTurns()),
                palette.log());
        for (int up = 3; up <= 7; up++) sink.put(
                local(mine.portal(), 2, 0, up, mine.inwardQuarterTurns()), Blocks.CHAIN.defaultBlockState());
        sink.put(local(mine.portal(), 2, 0, 2, mine.inwardQuarterTurns()), Blocks.GRINDSTONE.defaultBlockState());
        sink.put(local(mine.portal(), -2, -1, 5, mine.inwardQuarterTurns()), Blocks.LANTERN.defaultBlockState());
        sink.put(local(mine.portal(), 0, 0, 9, mine.inwardQuarterTurns()),
                Blocks.WAXED_CUT_COPPER.defaultBlockState());
    }

    private static void loadingApron(AuthoredMineSitePlan mine, FrontierPalette palette, Sink sink) {
        VisualPoint loading = mine.loadingEndpoint();
        for (int right = -5; right <= 5; right++) for (int inward = -2; inward <= 2; inward++) {
            if (right != 0) sink.put(local(loading, right, inward, -1, mine.inwardQuarterTurns()),
                    Math.floorMod(right + inward, 5) == 0 ? palette.log() : palette.planks());
        }
        for (int side : new int[]{-5, 5}) for (int inward = -2; inward <= 2; inward++) sink.put(
                local(loading, side, inward, 0, mine.inwardQuarterTurns()), fence(palette));
        for (int side : new int[]{-5, 5}) {
            for (int up = 1; up <= 4; up++) sink.put(local(loading, side, 2, up, mine.inwardQuarterTurns()),
                    palette.log());
            sink.put(local(loading, side, 2, 5, mine.inwardQuarterTurns()), Blocks.LANTERN.defaultBlockState());
        }
        for (int right = -5; right <= 5; right++) sink.put(
                local(loading, right, 2, 4, mine.inwardQuarterTurns()),
                palette.log());
        for (int up = 1; up <= 3; up++) sink.put(
                local(loading, 0, 2, up, mine.inwardQuarterTurns()), Blocks.CHAIN.defaultBlockState());
    }

    private static void safetyFurniture(AuthoredMineSitePlan mine, FrontierPalette palette, Sink sink) {
        for (MineFoundationPlan foundation : mine.foundations()) {
            if (foundation.id().equals("portal") || foundation.id().equals("crew")) continue;
            int centerZ = (foundation.footprint().min().z() + foundation.footprint().max().z()) / 2;
            int y = foundation.targetY() + 1;
            for (int x : new int[]{foundation.footprint().min().x() - 1,
                    foundation.footprint().max().x() + 1}) {
                sink.put(new BlockPos(x, y, centerZ), palette.foundation());
                sink.put(new BlockPos(x, y + 1, centerZ), fence(palette));
                sink.put(new BlockPos(x, y + 2, centerZ), Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    private static void industrialThreshold(AuthoredMineSitePlan mine, FrontierPalette palette, Sink sink) {
        for (int side : new int[]{-15, 15}) {
            for (int up = 0; up <= 3; up++) {
                sink.put(local(mine.portal(), side, -24, up, mine.inwardQuarterTurns()),
                        up == 0 ? palette.foundation() : palette.log());
            }
            sink.put(local(mine.portal(), side, -24, 4, mine.inwardQuarterTurns()),
                    Blocks.LANTERN.defaultBlockState());
            for (int inward = -30; inward <= -20; inward += 2) {
                sink.put(local(mine.portal(), side, inward, 0, mine.inwardQuarterTurns()), fence(palette));
            }
        }
        for (int right = -4; right <= 4; right++) {
            sink.put(local(mine.portal(), right, -25, 0, mine.inwardQuarterTurns()),
                    Math.floorMod(right, 2) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                            : Blocks.CUT_COPPER.defaultBlockState());
        }
    }

    private static void spoilHeaps(AuthoredMineSitePlan mine, Sink sink) {
        MineFoundationPlan power = mine.foundations().stream().filter(value -> value.id().equals("power"))
                .findFirst().orElse(null);
        if (power == null) return;
        for (int side : new int[]{-1, 1}) {
            BlockPos horizontal = local(mine.portal(), side * 33, -51, 0, mine.inwardQuarterTurns());
            BlockPos center = new BlockPos(horizontal.getX(), power.targetY() + 1, horizontal.getZ());
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                int radius = Math.abs(dx) + Math.abs(dz);
                if (radius > 4) continue;
                int height = radius <= 1 ? 3 : radius <= 3 ? 2 : 1;
                for (int up = 0; up < height; up++) sink.put(center.offset(dx, up, dz),
                        Math.floorMod(dx * 3 + dz + up, 4) == 0
                                ? Blocks.TUFF.defaultBlockState()
                                : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
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

    @FunctionalInterface
    interface Sink {
        void put(BlockPos position, BlockState state);
    }
}
