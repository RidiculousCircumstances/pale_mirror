package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Self-care portion uses an inventory slot independent of carried work resources and equipment. */
final class FrontierV3ResidentMealHandProjection {
    private FrontierV3ResidentMealHandProjection() { }
    static final ActorItemSlot SLOT = ResidentMeal.CARRIED_PORTION_SLOT;

    static boolean prepareAmbientNew(FrontierWorldState state, SubjectId actorId, Mob body) {
        ResidentMeal meal = state.humanPopulation().meals().get(actorId);
        if (meal == null) return true;
        if (meal.portable()) return true; // Common unit-inventory presentation owns the whole reserve.
        if (meal.pendingPhysicalStep().isPresent()) return false;
        if (!meal.carriesFood()) return FrontierV3ActorResourceSlots.get(body, meal.inventorySlot()).isEmpty();
        CustodyAccount account = state.inventory().fungibleResources().accounts().get(meal.actorAccountId());
        if (account == null || !account.custody().equals(new ResourceCustody.Actor(actorId))
                || !account.lotQuantities().equals(meal.portion().lotQuantities())
                || !account.claimQuantities().equals(Map.of(meal.claimId(), meal.portion().quantity()))
                || state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(meal.actorAccountId()))
                || !FrontierV3ActorResourceSlots.get(body, meal.inventorySlot()).isEmpty()) return false;
        FrontierV3ActorResourceSlots.set(body, meal.inventorySlot(), FrontierV3ResidentMealItems.stack(meal.portion()));
        return true;
    }

    static boolean matchesAmbient(FrontierWorldState state, SubjectId actorId, Mob body) {
        ResidentMeal meal = state.humanPopulation().meals().get(actorId);
        if (meal == null) return true;
        ItemStack held = FrontierV3ActorResourceSlots.get(body, meal.inventorySlot());
        var portion = meal.portable() ? FrontierV3ResidentMealItems.heldPortion(state, meal) : meal.portion();
        boolean food = FrontierV3ResidentMealItems.matches(held, portion);
        if (meal.pendingPhysicalStep().isPresent())
            return meal.phase() == ResidentMeal.Phase.TAKE || meal.phase() == ResidentMeal.Phase.CONSUME
                    ? food || (meal.portable() ? held.getCount() == portion.quantity() - meal.portion().quantity()
                        && (held.isEmpty() || ItemStack.isSameItemSameComponents(held, FrontierV3ResidentMealItems.stack(portion)))
                        : held.isEmpty()) : false;
        return meal.carriesFood() ? food : held.isEmpty();
    }
}
