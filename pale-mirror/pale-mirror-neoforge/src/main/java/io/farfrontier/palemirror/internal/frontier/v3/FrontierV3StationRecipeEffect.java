package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ProductionRecipe;
import io.farfrontier.palemirror.frontier.v3.model.ProductionStationSpec;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.ProductionRecipeOutcome;
import java.util.Optional;
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
    private final Optional<ExactItemStack> maintenanceTarget;

    FrontierV3StationRecipeEffect(Container inventory, ProductionStationSpec station,
                                  ProductionRecipe recipe, ItemStack beforeInput,
                                  int consumed, ItemStack output) {
        this(inventory, station, recipe, beforeInput, consumed, output, Optional.empty());
    }
    FrontierV3StationRecipeEffect(Container inventory, ProductionStationSpec station,
            ProductionRecipe recipe, ItemStack beforeInput, int consumed, ItemStack output,
            Optional<ExactItemStack> maintenanceTarget) {
        this.inventory = inventory; this.station = station;
        this.maintenanceTarget = java.util.Objects.requireNonNull(maintenanceTarget);
        if ((recipe.outcome() instanceof ProductionRecipeOutcome.Repair) != maintenanceTarget.isPresent()
                || maintenanceTarget.isPresent() && (station.maintenanceSlot().isEmpty()
                    || inventory.getContainerSize() <= station.maintenanceSlot().getAsInt()))
            throw new IllegalArgumentException("recipe maintenance target has no declared physical port");
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
                && inventory.getItem(station.outputSlot()).isEmpty()
                && maintenanceTarget.map(target -> FrontierV3ExactItemPresentation.exactMatch(
                    inventory.getItem(station.maintenanceSlot().orElseThrow()), target)).orElse(true);
    }
    boolean after() {
        return matches(inventory.getItem(station.inputSlot()), remainingInput)
                && matches(inventory.getItem(station.outputSlot()), output)
                && (maintenanceTarget.isEmpty() || inventory.getItem(station.maintenanceSlot().orElseThrow()).isEmpty());
    }
    void apply() {
        if (!before()) throw new IllegalStateException("prepared station recipe lost its exact preimage");
        inventory.setItem(station.inputSlot(), remainingInput.copy());
        if (maintenanceTarget.isPresent()) inventory.setItem(station.maintenanceSlot().orElseThrow(), ItemStack.EMPTY);
        inventory.setItem(station.outputSlot(), output.copy()); inventory.setChanged();
        if (!after()) throw new IllegalStateException("station recipe failed its physical postcondition");
    }
    private static boolean matches(ItemStack actual, ItemStack expected) {
        return expected.isEmpty() ? actual.isEmpty() : actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }
}
