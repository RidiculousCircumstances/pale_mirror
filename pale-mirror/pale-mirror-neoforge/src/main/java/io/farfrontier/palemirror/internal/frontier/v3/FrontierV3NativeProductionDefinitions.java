package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import java.util.*;

/** Startup compilation boundary for the explicitly supported native crafting subset and fuel properties.
 * The machine remains the sole executor. Unsupported dynamic recipe types are never guessed. */
final class FrontierV3NativeProductionDefinitions {
    static void validate(MinecraftServer server, ProductionCatalog catalog) {
        for (var declared : catalog.recipes()) {
            if (declared.nativeCraftingRecipe().isEmpty()) continue;
            var source = server.getRecipeManager().byKey(ResourceLocation.parse(declared.nativeCraftingRecipe().orElseThrow().value()))
                    .orElseThrow(() -> new IllegalArgumentException("declared native crafting recipe is absent: " + declared.id().value())).value();
            if (!(source instanceof CraftingRecipe)) throw new IllegalArgumentException("unsupported native production recipe type: " + declared.id().value());
            var product = source.getResultItem(server.registryAccess());
            if (product.isEmpty() || !BuiltInRegistries.ITEM.getKey(product.getItem()).toString().equals(declared.product().itemKind())
                    || product.getCount() != declared.product().quantity()) throw new IllegalArgumentException("native recipe output differs from pinned production definition: " + declared.id().value());
            var requirements = new ArrayList<ProductionRecipe.Ingredient>();
            for (var ingredient : source.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                var accepted = new TreeSet<String>();
                for (var candidate : ingredient.getItems()) {
                    if (candidate.hasCraftingRemainingItem()) throw new IllegalArgumentException("container-return crafting is not an admitted production recipe");
                    accepted.add(BuiltInRegistries.ITEM.getKey(candidate.getItem()).toString());
                }
                requirements.add(new ProductionRecipe.Ingredient(new ProductionRecipe.InputId("cell_" + requirements.size()), accepted, 1));
            }
            var nativeProjection = new ProductionRecipe(declared.id(), declared.capability(), requirements, declared.product(),
                    declared.requiresCombustion(), declared.processingMode(), declared.outcome());
            var inputs = new TreeMap<String, Integer>();
            for (var requirement : declared.ingredients()) {
                if (requirement.acceptedItems().size() != 1) throw new IllegalArgumentException("native alternatives require an explicit recipe compiler: " + declared.id().value());
                inputs.merge(requirement.acceptedItems().iterator().next(), requirement.quantity(), Math::addExact);
            }
            nativeProjection.requireTransformation(declared.capability(), inputs, declared.product().itemKind(), declared.product().quantity());
        }
        for (var fuel : catalog.fuels()) {
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(fuel.itemKind())).orElseThrow();
            long nativeTicks = new ItemStack(item).getBurnTime(RecipeType.SMELTING);
            if (nativeTicks != fuel.burnTicks()) throw new IllegalArgumentException("native burn duration differs from pinned fuel: " + fuel.itemKind());
        }
    }
    private FrontierV3NativeProductionDefinitions() { }
}
