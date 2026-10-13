package io.farfrontier.palemirror.internal.production;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Physical installed equipment. Processing/custody are not block-placement policy. */
public final class ProductionBenchBlock extends BaseEntityBlock {
    public static final MapCodec<ProductionBenchBlock> CODEC = simpleCodec(ProductionBenchBlock::new);
    public ProductionBenchBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public MapCodec<ProductionBenchBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProductionBenchBlockEntity(pos, state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ProductionBenchBlockEntity bench) player.openMenu(bench);
        return InteractionResult.CONSUME;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        Containers.dropContentsOnDestroy(state, next, level, pos);
        super.onRemove(state, level, pos, next, moving);
    }
}
