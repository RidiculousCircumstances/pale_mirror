package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Optional;

import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.internal.adapter.BlockedSourceItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/** Pinned Crimson item fingerprint policy; generic event code has no Crimson knowledge. */
final class CrimsonItemPolicy {
    private static final int PRIVATE_MODEL_DATA_MIN = 5_450_000;
    private static final int PRIVATE_MODEL_DATA_MAX = 5_459_999;

    private CrimsonItemPolicy() { }

    static Optional<BlockedSourceItem> classify(ItemStack stack, InfectionSourceId source) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model == null || !isPrivateModelData(model.value())) return Optional.empty();
        return Optional.of(new BlockedSourceItem(source.value(),
                BuiltInRegistries.ITEM.getKey(stack.getItem()) + "#model=" + model.value()));
    }

    static boolean isPrivateModelData(int value) {
        return value >= PRIVATE_MODEL_DATA_MIN && value <= PRIVATE_MODEL_DATA_MAX;
    }
}
