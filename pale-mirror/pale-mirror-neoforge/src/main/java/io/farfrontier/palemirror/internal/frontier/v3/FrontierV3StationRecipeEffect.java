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
import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** One recipe adapter, independent of worker, job family and material names.
 * The caller must retain its durable prepared fence before invoking apply.
 * Only declared input/output ports change; fuel and other inventory are not rewritten.
 */
final class FrontierV3StationRecipeEffect {
    private final Container inventory;
    private final ProductionStationSpec station;
    private final Map<Integer, ItemStack> beforeInput, remainingInput;
    private final ItemStack output;
    private final Optional<ExactItemStack> maintenanceTarget;

    FrontierV3StationRecipeEffect(Container inventory, ProductionStationSpec station,
                                  ProductionRecipe recipe, ItemStack beforeInput,
                                  int consumed, ItemStack output) {
        this(inventory, station, recipe, beforeInput, consumed, output, Optional.empty());
    }
    FrontierV3StationRecipeEffect(Container inventory, ProductionStationSpec station,
            ProductionRecipe recipe, ItemStack beforeInput, int consumed, ItemStack output,
            Optional<ExactItemStack> maintenanceTarget) {
        this(inventory, station, recipe, beforeInput, consumed, output, maintenanceTarget, station.singleInputSlot());
    }
    FrontierV3StationRecipeEffect(Container inventory, ProductionStationSpec station, ProductionRecipe recipe,
            ItemStack beforeInput, int consumed, ItemStack output, Optional<ExactItemStack> maintenanceTarget, int inputSlot) {
        this.inventory = inventory; this.station = station;
        this.maintenanceTarget = java.util.Objects.requireNonNull(maintenanceTarget);
        if ((recipe.outcome() instanceof ProductionRecipeOutcome.Repair) != maintenanceTarget.isPresent()
                || maintenanceTarget.isPresent() && (station.maintenanceSlot().isEmpty()
                    || inventory.getContainerSize() <= station.maintenanceSlot().getAsInt()))
            throw new IllegalArgumentException("recipe maintenance target has no declared physical port");
        if (!station.inputSlots().contains(inputSlot) || inventory.getContainerSize() <= Math.max(inputSlot, station.outputSlot())
                || beforeInput.isEmpty() || consumed <= 0 || consumed > beforeInput.getCount() || output.isEmpty())
            throw new IllegalArgumentException("station recipe has invalid physical ports or quantities");
        recipe.requireTransformation(station.capability(),
                Map.of(BuiltInRegistries.ITEM.getKey(beforeInput.getItem()).toString(), consumed),
                BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), output.getCount());
        this.beforeInput = Map.of(inputSlot, beforeInput.copy()); this.output = output.copy();
        this.remainingInput = Map.of(inputSlot, beforeInput.copyWithCount(beforeInput.getCount() - consumed));
    }
    FrontierV3StationRecipeEffect(Container inventory, ProductionStationSpec station, ProductionRecipe recipe,
            List<FungiblePhysicalObservation.Stack> before, List<FungiblePhysicalObservation.Stack> after,
            ItemStack output, Optional<ExactItemStack> maintenanceTarget) {
        this.inventory = inventory; this.station = station; this.maintenanceTarget = maintenanceTarget;
        if (inventory.getContainerSize() != station.inventorySize() || output.isEmpty()
                || (recipe.outcome() instanceof ProductionRecipeOutcome.Repair) != maintenanceTarget.isPresent())
            throw new IllegalArgumentException("recipe effect has foreign equipment ports or maintenance declaration");
        this.beforeInput = inputs(before, station); this.remainingInput = inputs(after, station); this.output = output.copy();
        var consumed = new HashMap<String, Integer>();
        this.beforeInput.values().stream().filter(stack -> !stack.isEmpty()).forEach(stack -> consumed.merge(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Math::addExact));
        this.remainingInput.values().stream().filter(stack -> !stack.isEmpty()).forEach(stack -> consumed.merge(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), -stack.getCount(), Math::addExact));
        consumed.values().removeIf(count -> count == 0);
        recipe.requireTransformation(station.capability(), consumed, BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), output.getCount());
    }
    private static Map<Integer, ItemStack> inputs(List<FungiblePhysicalObservation.Stack> image, ProductionStationSpec station) {
        var slots = new HashMap<Integer, ItemStack>();
        for (int slot : station.inputSlots()) slots.put(slot, ItemStack.EMPTY);
        for (var stack : image) {
            if (!(stack.address() instanceof PhysicalStackAddress.ContainerSlot address)
                    || !address.slot().containerId().equals(station.containerId())) throw new IllegalArgumentException("recipe has foreign physical input");
            int slot = address.slot().slot();
            if (slot == station.outputSlot()) continue;
            if (!slots.containsKey(slot) || !slots.get(slot).isEmpty()) throw new IllegalArgumentException("recipe has repeated or foreign input port");
            slots.put(slot, new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(stack.itemKind())), stack.quantity()));
        }
        return Map.copyOf(slots);
    }
    boolean before() {
        return beforeInput.entrySet().stream().allMatch(entry -> matches(inventory.getItem(entry.getKey()), entry.getValue()))
                && inventory.getItem(station.outputSlot()).isEmpty()
                && maintenanceTarget.map(target -> FrontierV3ExactItemPresentation.exactMatch(
                    inventory.getItem(station.maintenanceSlot().orElseThrow()), target)).orElse(true);
    }
    boolean after() {
        return remainingInput.entrySet().stream().allMatch(entry -> matches(inventory.getItem(entry.getKey()), entry.getValue()))
                && matches(inventory.getItem(station.outputSlot()), output)
                && (maintenanceTarget.isEmpty() || inventory.getItem(station.maintenanceSlot().orElseThrow()).isEmpty());
    }
    void apply() {
        if (!before()) throw new IllegalStateException("prepared station recipe lost its exact preimage");
        remainingInput.forEach((slot, stack) -> inventory.setItem(slot, stack.copy()));
        if (maintenanceTarget.isPresent()) inventory.setItem(station.maintenanceSlot().orElseThrow(), ItemStack.EMPTY);
        inventory.setItem(station.outputSlot(), output.copy()); inventory.setChanged();
        if (!after()) throw new IllegalStateException("station recipe failed its physical postcondition");
    }
    private static boolean matches(ItemStack actual, ItemStack expected) {
        return expected.isEmpty() ? actual.isEmpty() : actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }
}
