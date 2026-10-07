package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FoodPortion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Minecraft item translation only; nutritional definitions belong to the retained ruleset. */
final class FrontierV3ResidentMealItems {
    private FrontierV3ResidentMealItems() { }
    static FoodPortion heldPortion(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state,
                                   io.farfrontier.palemirror.frontier.v3.model.ResidentMeal meal) {
        var account = state.inventory().fungibleResources().accounts().get(meal.actorAccountId());
        if (account == null) throw new IllegalArgumentException("held meal inventory disappeared");
        return new FoodPortion(meal.portion().itemKind(), meal.portion().nutritionPerItem(), account.lotQuantities());
    }
    static ItemStack stack(FoodPortion portion) {
        var id = ResourceLocation.tryParse(portion.itemKind());
        var item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (item == Items.AIR || portion.quantity() > item.getDefaultMaxStackSize())
            throw new IllegalArgumentException("registered meal has no compatible Minecraft stack");
        return new ItemStack(item, portion.quantity());
    }
    static boolean matches(ItemStack actual, FoodPortion portion) {
        return actual.getCount() == portion.quantity() && ItemStack.isSameItemSameComponents(actual, stack(portion));
    }
}
