package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ProductionRecipe;
import io.farfrontier.palemirror.frontier.v3.model.ProductionStationSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** One recipe adapter, independent of worker, job family and material names.
 * The caller must retain its durable prepared fence before invoking apply.
 * Only declared input/output ports change; fuel and other inventory are not rewritten.
 */
final class FrontierV3StationRecipeEffect {
    private final Container inventory;
    private final ProductionStationSpec station;
    private final ItemStack beforeInput, remainingInput, output;

    FrontierV3StationRecipeEffect(Container inventory, ProductionStationSpec station,
                                  ProductionRecipe recipe, ItemStack beforeInput,
                                  int consumed, ItemStack output) {
        this.inventory = inventory; this.station = station;
        if (inventory.getContainerSize() <= Math.max(station.inputSlot(), station.outputSlot())
                || beforeInput.isEmpty() || consumed <= 0 || consumed > beforeInput.getCount() || output.isEmpty())
            throw new IllegalArgumentException("station recipe has invalid physical ports or quantities");
        recipe.requireTransformation(station.capability(),
                Map.of(BuiltInRegistries.ITEM.getKey(beforeInput.getItem()).toString(), consumed),
                BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), output.getCount());
        this.beforeInput = beforeInput.copy(); this.output = output.copy();
        this.remainingInput = beforeInput.copyWithCount(beforeInput.getCount() - consumed);
    }
    boolean before() {
        return matches(inventory.getItem(station.inputSlot()), beforeInput)
                && inventory.getItem(station.outputSlot()).isEmpty();
    }
    boolean after() {
        return matches(inventory.getItem(station.inputSlot()), remainingInput)
                && matches(inventory.getItem(station.outputSlot()), output);
    }
    void apply() {
        if (!before()) throw new IllegalStateException("prepared station recipe lost its exact preimage");
        inventory.setItem(station.inputSlot(), remainingInput.copy());
        inventory.setItem(station.outputSlot(), output.copy()); inventory.setChanged();
        if (!after()) throw new IllegalStateException("station recipe failed its physical postcondition");
    }
    private static boolean matches(ItemStack actual, ItemStack expected) {
        return expected.isEmpty() ? actual.isEmpty() : actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }
}
