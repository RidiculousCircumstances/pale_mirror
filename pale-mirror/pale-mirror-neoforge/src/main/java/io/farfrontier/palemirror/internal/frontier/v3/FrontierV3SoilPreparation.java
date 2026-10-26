package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.agriculture.SoilPreparationCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbility;
import java.util.Optional;

/** Read-only engine provider for declared block-only tool transformations.
 * Labour, wear, mutation journalling and progression stay with their existing owners. */
final class FrontierV3SoilPreparation {
    static Optional<BlockState> result(ServerLevel level, ItemStack tool, BlockPos position, SoilPreparationCatalog.Step step) {
        var chunk = FrontierV3LoadedWorld.chunk(level, position);
        if (chunk == null || FrontierV3LoadedWorld.chunk(level, position.above()) == null) return Optional.empty();
        var before = chunk.getBlockState(position);
        if (!BuiltInRegistries.BLOCK.getKey(before.getBlock()).toString().equals(step.before().blockKind())) return Optional.empty();
        var context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, tool,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
        var after = before.getToolModifiedState(context, ItemAbility.get(step.nativeAbility()), true);
        return after != null && after.equals(FrontierV3FieldBlocks.soil(step.after())) ? Optional.of(after) : Optional.empty();
    }
    private FrontierV3SoilPreparation() { }
}
