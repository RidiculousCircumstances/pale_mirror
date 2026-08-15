package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.PerimeterModuleKind;
import io.farfrontier.palemirror.api.PerimeterModulePlan;
import io.farfrontier.palemirror.api.PerimeterPlan;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The sole writer for the socket-checked, terrain-following frontier boundary. */
final class SettlementPerimeterCompiler {
    private SettlementPerimeterCompiler() { }

    static void compile(PerimeterPlan plan, FrontierPalette palette,
                        SettlementFixtureOccupancy fixtures, SettlementGenesisCompiler.Sink sink) {
        for (PerimeterModulePlan module : plan.modules()) {
            switch (module.kind()) {
                case STRAIGHT, STEP_UP, STEP_DOWN, CORNER -> wall(module, palette, fixtures, sink);
                case PEDESTRIAN_GATE, FREIGHT_GATE -> gate(module, palette, sink);
                case WATCH_POST -> watchPost(module, palette, fixtures, sink);
            }
        }
    }

    private static void wall(PerimeterModulePlan module, FrontierPalette palette,
                             SettlementFixtureOccupancy fixtures, SettlementGenesisCompiler.Sink sink) {
        for (int offset = -module.length() / 2; offset <= module.length() / 2; offset++) {
            int x = alongX(module, offset);
            int z = alongZ(module, offset);
            boolean post = Math.abs(offset) == module.length() / 2 || Math.floorMod(offset, 5) == 0;
            sink.terrain(x, z, module.anchor().y(), post ? palette.foundation()
                    : Blocks.COBBLESTONE.defaultBlockState(), palette.foundation());
            sink.surfaceBlock(x, z, -1, post ? palette.foundation() : Blocks.COBBLESTONE.defaultBlockState());
            sink.surfaceBlock(x, z, 0, post ? palette.log() : Blocks.COBBLESTONE_WALL.defaultBlockState());
            if (post) sink.surfaceBlock(x, z, 1, SettlementGenesisCompiler.fence(palette));
        }
        int x = alongX(module, 0);
        int z = alongZ(module, 0);
        if (fixtures.reserve(x, z, 2) && Math.floorMod(module.moduleId().hashCode(), 4) == 0) {
            sink.surfaceBlock(x, z, 2, Blocks.LANTERN.defaultBlockState());
        }
    }

    private static void gate(PerimeterModulePlan module, FrontierPalette palette,
                             SettlementGenesisCompiler.Sink sink) {
        int opening = module.kind() == PerimeterModuleKind.FREIGHT_GATE ? 5 : 1;
        int openingHalf = opening / 2;
        Direction facing = module.quarterTurns() % 2 == 0 ? Direction.NORTH : Direction.EAST;
        BlockState pedestrianGate = fenceGate(palette).setValue(FenceGateBlock.FACING, facing)
                .setValue(FenceGateBlock.OPEN, true);
        int jamb = openingHalf + 1;
        for (int offset = -module.length() / 2; offset <= module.length() / 2; offset++) {
            int x = alongX(module, offset);
            int z = alongZ(module, offset);
            sink.terrain(x, z, module.anchor().y(), palette.foundation(), palette.foundation());
            sink.surfaceBlock(x, z, -1, palette.foundation());
            if (Math.abs(offset) <= openingHalf) {
                // A freight opening is physical clearance, not five gates in a row.
                sink.surfaceBlock(x, z, 0, module.kind() == PerimeterModuleKind.FREIGHT_GATE
                        ? Blocks.AIR.defaultBlockState() : pedestrianGate);
                sink.surfaceBlock(x, z, 1, Blocks.AIR.defaultBlockState());
            } else if (Math.abs(offset) == jamb) {
                sink.surfaceBlock(x, z, 0, palette.log());
                sink.surfaceBlock(x, z, 1, palette.log());
                sink.surfaceBlock(x, z, 2, palette.log());
            } else {
                sink.surfaceBlock(x, z, 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
                sink.surfaceBlock(x, z, 1, SettlementGenesisCompiler.fence(palette));
            }
        }
        for (int offset = -jamb; offset <= jamb; offset++) {
            sink.surfaceBlock(alongX(module, offset), alongZ(module, offset), 3, palette.log());
        }
        sink.surfaceBlock(alongX(module, 0), alongZ(module, 0), 2,
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static void watchPost(PerimeterModulePlan module, FrontierPalette palette,
                                  SettlementFixtureOccupancy fixtures, SettlementGenesisCompiler.Sink sink) {
        int x = module.anchor().x();
        int z = module.anchor().z();
        if (!fixtures.reserve(x, z, 2)) return;
        sink.terrain(x, z, module.anchor().y(), palette.foundation(), palette.foundation());
        sink.surfaceBlock(x, z, 2, palette.log());
        sink.surfaceBlock(x, z, 3, Blocks.LANTERN.defaultBlockState());
    }

    private static int alongX(PerimeterModulePlan module, int offset) {
        return module.anchor().x() + (module.quarterTurns() % 2 == 0 ? offset : 0);
    }

    private static int alongZ(PerimeterModulePlan module, int offset) {
        return module.anchor().z() + (module.quarterTurns() % 2 == 0 ? 0 : offset);
    }

    private static BlockState fenceGate(FrontierPalette palette) {
        if (palette.planks().is(Blocks.ACACIA_PLANKS)) return Blocks.ACACIA_FENCE_GATE.defaultBlockState();
        if (palette.planks().is(Blocks.OAK_PLANKS)) return Blocks.OAK_FENCE_GATE.defaultBlockState();
        return Blocks.SPRUCE_FENCE_GATE.defaultBlockState();
    }
}
