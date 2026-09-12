package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Small physical boundary shared by the paired projection/aftermath owners. */
interface FrontierV3AftermathPhysicalWorld {
    boolean naturallyLoaded(BlockPosition position);
    boolean isAir(BlockPosition position);
    boolean hasMaterial(BlockPosition position, GrayboxMaterial material);
    boolean placeMaterial(BlockPosition position, GrayboxMaterial material);
    boolean clear(BlockPosition position);
    FrontierV3GrayboxLedger ledger();

    static FrontierV3AftermathPhysicalWorld minecraft(ServerLevel level) {
        return minecraft(level, 3);
    }

    /**
     * First visibility runs after the ChunkEvent.Load callback has returned.  These immutable
     * static cells need a client update but no vanilla neighbour propagation, which could pull a
     * second chunk synchronously while its first-load event is still completing.
     */
    static FrontierV3AftermathPhysicalWorld firstVisibility(ServerLevel level) {
        return minecraft(level, 2);
    }

    private static FrontierV3AftermathPhysicalWorld minecraft(ServerLevel level, int blockUpdateFlags) {
        return new FrontierV3AftermathPhysicalWorld() {
            @Override public boolean naturallyLoaded(BlockPosition position) { return level.hasChunkAt(block(position)); }
            @Override public boolean isAir(BlockPosition position) { return level.getBlockState(block(position)).isAir(); }
            @Override public boolean hasMaterial(BlockPosition position, GrayboxMaterial material) {
                return level.getBlockState(block(position)).equals(FrontierV3GrayboxExecutor.material(material));
            }
            @Override public boolean placeMaterial(BlockPosition position, GrayboxMaterial material) {
                BlockPos block = block(position);
                return level.setBlock(block, FrontierV3GrayboxExecutor.material(material), blockUpdateFlags)
                        && level.getBlockState(block).equals(FrontierV3GrayboxExecutor.material(material));
            }
            @Override public boolean clear(BlockPosition position) {
                BlockPos block = block(position);
                return level.setBlock(block, Blocks.AIR.defaultBlockState(), 3) && level.getBlockState(block).isAir();
            }
            @Override public FrontierV3GrayboxLedger ledger() { return FrontierV3GrayboxLedger.get(level); }
        };
    }

    private static BlockPos block(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
}
