package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Map;

/** Passive carried-resource presentation survives body-owner changes; it cannot transfer or consume stock. */
final class FrontierV3ActorCarryProjection {
    private static final String WITNESS = "pmv3_carried_resource_witness";
    private FrontierV3ActorCarryProjection() { }

    static boolean prepareNew(FrontierWorldState state, SubjectId actorId, Mob body) {
        var declarations = declarations(state, actorId);
        var ledger = state.inventory().fungibleResources();
        for (var carry : declarations) {
            if (ledger.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(carry.accountId()))
                    || stack(ledger, carry).isEmpty()) return false;
            // A family may have prepared its work hand on this still-private body.
            // Accept only the exact declaration; never overwrite a conflicting item.
            if (!FrontierV3ActorResourceSlots.get(body, carry.slot()).isEmpty()
                    && !matches(ledger, carry, body)) return false;
        }
        // A genuinely new body is persisted with its inventory and witness together, not as a second live replica.
        for (var carry : declarations) {
            if (FrontierV3ActorResourceSlots.get(body, carry.slot()).isEmpty())
                FrontierV3ActorResourceSlots.set(body, carry.slot(), stack(ledger, carry));
        }
        rememberConfirmed(state, actorId, body);
        return true;
    }

    static boolean matches(FrontierWorldState state, SubjectId actorId, Mob body) {
        var meal = state.humanPopulation().meals().get(actorId);
        return declarations(state, actorId).stream().allMatch(carry -> meal != null && meal.portable()
                && carry.accountId().equals(meal.actorAccountId())
                ? FrontierV3ResidentMealHandProjection.matchesAmbient(state, actorId, body)
                : matches(state.inventory().fungibleResources(), carry, body));
    }

    /** Only a confirmed scene-resource release or new-body admission may record this proof. */
    static void rememberConfirmed(FrontierWorldState state, SubjectId actorId, Mob body) {
        requireMatches(state, actorId, body);
        body.getPersistentData().putString(WITNESS, declarations(state, actorId).stream()
                .map(carry -> signature(state.inventory().fungibleResources(), carry)).collect(java.util.stream.Collectors.joining(";")));
    }

    /** Pure precondition; scope closure may not publish a receipt before checking its physical cargo. */
    static void requireMatches(FrontierWorldState state, SubjectId actorId, Mob body) {
        if (matches(state, actorId, body)) return;
        String inventory = declarations(state, actorId).stream().map(carry -> {
            var expected = stack(state.inventory().fungibleResources(), carry);
            var actual = FrontierV3ActorResourceSlots.get(body, carry.slot());
            return "account=" + carry.accountId().value() + " slot=" + carry.slot()
                    + " expected=" + expected + " actual=" + actual;
        }).collect(java.util.stream.Collectors.joining("; "));
        throw new IllegalArgumentException("confirmed carried resource differs from physical inventory actor="
                + actorId.value() + " entity=" + body.getUUID() + " [" + inventory + "]");
    }

    static boolean witnessed(FrontierWorldState state, SubjectId actorId, Mob body) {
        var declarations = declarations(state, actorId);
        return !declarations.isEmpty() && matches(state, actorId, body) && body.getPersistentData().getString(WITNESS)
                .equals(declarations.stream().map(carry -> signature(state.inventory().fungibleResources(), carry))
                        .collect(java.util.stream.Collectors.joining(";")));
    }

    private static java.util.List<ActorCarriedResources.Presentation> declarations(FrontierWorldState state, SubjectId actor) {
        var meal = state.humanPopulation().meals().get(actor);
        return UnitInventoryPresentation.inventory(state, actor).values().stream()
                .filter(carry -> meal == null || meal.portable() || !carry.accountId().equals(meal.actorAccountId()))
                .sorted(java.util.Comparator.comparing(ActorCarriedResources.Presentation::accountId)).toList();
    }

    private static boolean matches(FungibleResourceLedger ledger, ActorCarriedResources.Presentation carry, Mob body) {
        ItemStack expected = stack(ledger, carry);
        ItemStack actual = FrontierV3ActorResourceSlots.get(body, carry.slot());
        return !expected.isEmpty() && actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static ItemStack stack(FungibleResourceLedger ledger, ActorCarriedResources.Presentation carry) {
        CustodyAccount account = carry.requireAccount(ledger);
        String kind = ledger.lots().get(account.lotQuantities().keySet().iterator().next()).itemKind();
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind));
        int count = account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
        return item == Items.AIR || count > item.getDefaultMaxStackSize() ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    private static String signature(FungibleResourceLedger ledger, ActorCarriedResources.Presentation carry) {
        return carry.actorId().value() + "|" + carry.accountId().value() + "|" + carry.slot() + "|"
                + carry.requireAccount(ledger).lotQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey().value() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(","));
    }
}
