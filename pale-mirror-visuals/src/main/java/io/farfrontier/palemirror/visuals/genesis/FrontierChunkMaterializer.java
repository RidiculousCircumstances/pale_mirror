package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import io.farfrontier.palemirror.visuals.runtime.VisualGenesisSavedData;

/** First-generation-only renderer. It never writes outside the currently loaded target chunk. */
public final class FrontierChunkMaterializer {
    private final AuthoredTemplateLibrary templates = new AuthoredTemplateLibrary();
    public boolean intersects(AuthoredRegionSeed seed, ChunkPos chunk) {
        int margin = 2;
        return chunk.getMaxBlockX() >= seed.settlementBounds().min().x() - margin
                && chunk.getMinBlockX() <= seed.settlementBounds().max().x() + margin
                && chunk.getMaxBlockZ() >= seed.settlementBounds().min().z() - margin
                && chunk.getMinBlockZ() <= seed.settlementBounds().max().z() + margin;
    }

    public void materialize(ServerLevel level, ChunkPos chunk, AuthoredRegionSeed seed, VisualGenesisSavedData ledger) {
        FrontierClimate climate = FrontierClimate.valueOf(seed.climate().toUpperCase(java.util.Locale.ROOT));
        FrontierPalette palette = FrontierPalette.forClimate(climate);
        grade(level, chunk, seed, palette);
        renderRoadsAndWall(level, chunk, seed, palette);
        for (VisualModulePlacement module : seed.modules()) {
            templates.placeReady(level, seed, module, ledger);
        }
    }

    private static void grade(ServerLevel level, ChunkPos chunk, AuthoredRegionSeed seed, FrontierPalette palette) {
        int y = seed.anchor().y();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int dx = x - seed.anchor().x();
                int dz = z - seed.anchor().z();
                if (dx * dx + dz * dz > FrontierRegionPlanner.SETTLEMENT_RADIUS * FrontierRegionPlanner.SETTLEMENT_RADIUS) continue;
                for (int clear = y; clear <= y + 18; clear++) set(level, chunk, new BlockPos(x, clear, z), Blocks.AIR.defaultBlockState());
                for (int fill = y - 8; fill < y - 1; fill++) {
                    BlockPos pos = new BlockPos(x, fill, z);
                    if (level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) set(level, chunk, pos, Blocks.DIRT.defaultBlockState());
                }
                set(level, chunk, new BlockPos(x, y - 1, z), palette.foundation());
                set(level, chunk, new BlockPos(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
    }

    private static void renderRoadsAndWall(ServerLevel level, ChunkPos chunk, AuthoredRegionSeed seed, FrontierPalette palette) {
        int y = seed.anchor().y();
        int gateDx = Integer.signum(seed.freightGate().x() - seed.anchor().x());
        int gateDz = Integer.signum(seed.freightGate().z() - seed.anchor().z());
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
            int dx = x - seed.anchor().x(); int dz = z - seed.anchor().z();
            double radius = Math.sqrt(dx * dx + dz * dz);
            boolean radial = Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
            boolean ringRoad = radius >= 28 && radius <= 32;
            if (radial || ringRoad) set(level, chunk, new BlockPos(x, y, z), (Math.floorMod(x + z, 5) == 0
                    ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).defaultBlockState());
            boolean gateOpening = Math.abs(dx - gateDx * 82) <= 5 && Math.abs(dz - gateDz * 82) <= 5;
            if (radius >= 81.4 && radius <= 82.6 && !gateOpening) {
                for (int h = 1; h <= 5; h++) set(level, chunk, new BlockPos(x, y + h, z), palette.log());
            }
        }
        renderGate(level, chunk, seed.freightGate(), palette);
    }

    private static void renderGate(ServerLevel level, ChunkPos chunk, VisualPoint gate, FrontierPalette palette) {
        int y = gate.y();
        for (int dx = -4; dx <= 4; dx += 8) for (int h = 1; h <= 7; h++)
            set(level, chunk, new BlockPos(gate.x() + dx, y + h, gate.z()), palette.log());
        for (int dx = -4; dx <= 4; dx++) set(level, chunk, new BlockPos(gate.x() + dx, y + 7, gate.z()), palette.log());
    }

    private static void set(ServerLevel level, ChunkPos chunk, BlockPos pos, BlockState state) {
        if (pos.getX() < chunk.getMinBlockX() || pos.getX() > chunk.getMaxBlockX()
                || pos.getZ() < chunk.getMinBlockZ() || pos.getZ() > chunk.getMaxBlockZ()) return;
        if (!level.hasChunk(chunk.x, chunk.z)) return;
        level.setBlock(pos, state, 18);
    }
}
