package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Final-write tunnel grammar that joins the portal and every underground authored room. */
final class MineUndergroundGenesisCompiler {
    private MineUndergroundGenesisCompiler() { }

    static void compile(AuthoredMineSitePlan mine, Sink sink) {
        int section = 0;
        for (List<MineUndergroundLayout.Node> corridor : MineUndergroundLayout.corridors()) {
            for (int segment = 1; segment < corridor.size(); segment++) {
                MineUndergroundLayout.Node from = corridor.get(segment - 1);
                MineUndergroundLayout.Node to = corridor.get(segment);
                int rightDelta = to.right() - from.right();
                int inwardDelta = to.inward() - from.inward();
                int upDelta = to.up() - from.up();
                int steps = Math.max(1, Math.max(Math.abs(rightDelta),
                        Math.max(Math.abs(inwardDelta), Math.abs(upDelta))));
                boolean transverseAlongRight = Math.abs(inwardDelta) >= Math.abs(rightDelta);
                for (int step = segment == 1 ? 0 : 1; step <= steps; step++) {
                    MineUndergroundLayout.Node node = new MineUndergroundLayout.Node(
                            from.right() + rightDelta * step / steps,
                            from.inward() + inwardDelta * step / steps,
                            from.up() + upDelta * step / steps);
                    section(mine, node, transverseAlongRight, section++, sink);
                }
            }
        }
        junction(mine, sink);
    }

    private static void section(AuthoredMineSitePlan mine, MineUndergroundLayout.Node node,
                                boolean transverseAlongRight, int section, Sink sink) {
        for (int offset = -2; offset <= 2; offset++) {
            put(sink, mine, transverse(node, offset, -1, transverseAlongRight),
                    Math.floorMod(section + offset, 5) == 0
                            ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                            : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        }
        for (int up = 0; up <= 2; up++) for (int offset = -2; offset <= 2; offset++) {
            put(sink, mine, transverse(node, offset, up, transverseAlongRight), Blocks.AIR.defaultBlockState());
        }
        for (int offset = -1; offset <= 1; offset++) {
            put(sink, mine, transverse(node, offset, 3, transverseAlongRight), Blocks.AIR.defaultBlockState());
        }
        put(sink, mine, transverse(node, -2, 3, transverseAlongRight),
                Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        put(sink, mine, transverse(node, 2, 3, transverseAlongRight),
                Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        for (int offset = -1; offset <= 1; offset++) {
            put(sink, mine, transverse(node, offset, 4, transverseAlongRight),
                    section % 4 == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                            : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        }
        if (section % 7 == 0) {
            for (int up = 0; up <= 2; up++) {
                put(sink, mine, transverse(node, -2, up, transverseAlongRight),
                        Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                put(sink, mine, transverse(node, 2, up, transverseAlongRight),
                        Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
            for (int offset = -2; offset <= 2; offset++) {
                put(sink, mine, transverse(node, offset, 3, transverseAlongRight),
                        Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
            if (section % 14 == 0) {
                put(sink, mine, transverse(node, 0, 2, transverseAlongRight),
                        Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
    }

    private static void junction(AuthoredMineSitePlan mine, Sink sink) {
        MineUndergroundLayout.Node junction = MineUndergroundLayout.JUNCTION;
        for (int right = -4; right <= 4; right++) for (int inward = -4; inward <= 4; inward++) {
            int radius = right * right + inward * inward;
            if (radius > 20) continue;
            MineUndergroundLayout.Node floor = new MineUndergroundLayout.Node(
                    junction.right() + right, junction.inward() + inward, junction.up() - 1);
            put(sink, mine, floor, radius % 5 == 0
                    ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            int height = radius <= 10 ? 4 : 3;
            for (int up = 0; up < height; up++) put(sink, mine, new MineUndergroundLayout.Node(
                    floor.right(), floor.inward(), junction.up() + up), Blocks.AIR.defaultBlockState());
        }
        for (int right : new int[]{-3, 3}) for (int inward : new int[]{-2, 2}) {
            for (int up = 0; up <= 3; up++) put(sink, mine, new MineUndergroundLayout.Node(
                    junction.right() + right, junction.inward() + inward, junction.up() + up),
                    Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        put(sink, mine, new MineUndergroundLayout.Node(junction.right(), junction.inward(), junction.up() + 3),
                Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static MineUndergroundLayout.Node transverse(MineUndergroundLayout.Node node, int offset, int up,
                                                          boolean alongRight) {
        return new MineUndergroundLayout.Node(node.right() + (alongRight ? offset : 0),
                node.inward() + (alongRight ? 0 : offset), node.up() + up);
    }

    private static void put(Sink sink, AuthoredMineSitePlan mine,
                            MineUndergroundLayout.Node node, BlockState state) {
        sink.put(local(mine.portal(), node, mine.inwardQuarterTurns()), state);
    }

    static BlockPos local(VisualPoint origin, MineUndergroundLayout.Node node, int direction) {
        int dx = switch (Math.floorMod(direction, 4)) {
            case 0 -> node.inward(); case 1 -> -node.right();
            case 2 -> -node.inward(); default -> node.right();
        };
        int dz = switch (Math.floorMod(direction, 4)) {
            case 0 -> node.right(); case 1 -> node.inward();
            case 2 -> -node.right(); default -> -node.inward();
        };
        return new BlockPos(origin.x() + dx, origin.y() + node.up(), origin.z() + dz);
    }

    @FunctionalInterface
    interface Sink {
        void put(BlockPos position, BlockState state);
    }
}
