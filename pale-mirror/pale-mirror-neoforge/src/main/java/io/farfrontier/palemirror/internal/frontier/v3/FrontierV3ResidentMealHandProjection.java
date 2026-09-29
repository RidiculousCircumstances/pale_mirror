package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/** The resident's self-care offhand is projected only from one unbound COLD bread account. */
final class FrontierV3ResidentMealHandProjection {
    private FrontierV3ResidentMealHandProjection() { }

    static boolean prepareAmbientNew(FrontierWorldState state, SubjectId actorId, Mob body) {
        ResidentMeal meal = state.humanPopulation().meals().get(actorId);
        if (meal == null) return true;
        if (meal.pendingPhysicalStep().isPresent()) return false;
        if (meal.phase() != ResidentMeal.Phase.CONSUME) return body.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty();
        CustodyAccount account = state.inventory().fungibleResources().accounts().get(meal.actorAccountId());
        if (account == null || !account.custody().equals(new ResourceCustody.Actor(actorId))
                || !account.lotQuantities().equals(Map.of(meal.lotId(), 1))
                || !account.claimQuantities().equals(Map.of(meal.claimId(), 1))
                || state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(meal.actorAccountId()))
                || !body.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) return false;
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BREAD, 1));
        return true;
    }

    static boolean matchesAmbient(FrontierWorldState state, SubjectId actorId, Mob body) {
        ResidentMeal meal = state.humanPopulation().meals().get(actorId);
        if (meal == null) return true;
        ItemStack held = body.getItemBySlot(EquipmentSlot.OFFHAND);
        boolean bread = held.getCount() == 1
                && ItemStack.isSameItemSameComponents(held, new ItemStack(Items.BREAD, 1));
        if (meal.pendingPhysicalStep().isPresent())
            return meal.phase() == ResidentMeal.Phase.TAKE || meal.phase() == ResidentMeal.Phase.CONSUME
                    ? held.isEmpty() || bread : false;
        return meal.phase() == ResidentMeal.Phase.CONSUME ? bread : held.isEmpty();
    }
}
