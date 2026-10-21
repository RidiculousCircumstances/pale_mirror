package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3AutomationTransfers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.VanillaInventoryCodeHooks;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.Optional;
import java.util.function.Function;

@Mixin(value = VanillaInventoryCodeHooks.class, remap = false)
abstract class FrontierV3AutomationTransfersMixin {
    @Redirect(method = "extractHook", at = @At(value = "INVOKE", target = "Ljava/util/Optional;map(Ljava/util/function/Function;)Ljava/util/Optional;"))
    private static Optional<Boolean> frontierV3$extract(Optional<Pair<IItemHandler, Object>> selected,
            Function<Pair<IItemHandler, Object>, Boolean> transfer, Level level, Hopper destination) {
        return FrontierV3AutomationTransfers.extract(selected, transfer, level, destination);
    }
    @Redirect(method = "insertHook", at = @At(value = "INVOKE", target = "Ljava/util/Optional;map(Ljava/util/function/Function;)Ljava/util/Optional;"))
    private static Optional<Boolean> frontierV3$insert(Optional<Pair<IItemHandler, Object>> selected,
            Function<Pair<IItemHandler, Object>, Boolean> transfer, HopperBlockEntity source) {
        return FrontierV3AutomationTransfers.insert(selected, transfer, source);
    }
}
