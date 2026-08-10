package io.farfrontier.palemirror.internal.integration.item;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Explicit temporary policy while source items/recipes are outside the PM
 * product scope.  Crimson is a datapack and encodes its items as vanilla
 * stacks, so its private custom-model range must be guarded in addition to
 * the normal Spore registry namespace check.
 */
public final class ExcludedSourceItemFirewall {
    public static final String SPORE_SOURCE = "spore";
    public static final String CRIMSON_SOURCE = "crimson";
    private static final int CRIMSON_MODEL_DATA_MIN = 5_450_000;
    private static final int CRIMSON_MODEL_DATA_MAX = 5_459_999;

    private ExcludedSourceItemFirewall() { }

    public static Optional<BlockedItem> classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (SPORE_SOURCE.equals(itemId.getNamespace())) {
            return Optional.of(new BlockedItem(SPORE_SOURCE, itemId + "#" + stack.getCount()));
        }
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model != null && isCrimsonPrivateModelData(model.value())) {
            return Optional.of(new BlockedItem(CRIMSON_SOURCE, itemId + "#model=" + model.value()));
        }
        return Optional.empty();
    }

    public static boolean blocks(ItemStack stack) { return classify(stack).isPresent(); }

    /** Pure helper kept testable without the Minecraft runtime on the unit-test classpath. */
    public static boolean isCrimsonPrivateModelData(int value) {
        return value >= CRIMSON_MODEL_DATA_MIN && value <= CRIMSON_MODEL_DATA_MAX;
    }

    public record BlockedItem(String sourceId, String fingerprint) { }
}
