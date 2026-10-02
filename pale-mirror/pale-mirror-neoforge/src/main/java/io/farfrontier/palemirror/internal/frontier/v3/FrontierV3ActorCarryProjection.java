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
        var declaration = ActorCarryCapabilities.workCargo(state, actorId);
        if (declaration.isEmpty()) return true;
        var carry = declaration.orElseThrow();
        var ledger = state.inventory().fungibleResources();
        if (ledger.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(carry.accountId()))
                || !FrontierV3ActorResourceSlots.get(body, carry.slot()).isEmpty()) return false;
        ItemStack expected = stack(ledger, carry);
        if (expected.isEmpty()) return false;
        // A genuinely new body is persisted with its inventory and witness together, not as a second live replica.
        FrontierV3ActorResourceSlots.set(body, carry.slot(), expected);
        rememberConfirmed(state, actorId, body);
        return true;
    }

    static boolean matches(FrontierWorldState state, SubjectId actorId, Mob body) {
        var carry = ActorCarryCapabilities.workCargo(state, actorId);
        return carry.isEmpty() || matches(state.inventory().fungibleResources(), carry.orElseThrow(), body);
    }

    /** Only a confirmed scene-resource release or new-body admission may record this proof. */
    static void rememberConfirmed(FrontierWorldState state, SubjectId actorId, Mob body) {
        ActorCarryCapabilities.workCargo(state, actorId).ifPresent(carry -> {
            if (!matches(state.inventory().fungibleResources(), carry, body))
                throw new IllegalArgumentException("confirmed carried resource differs from physical inventory");
            body.getPersistentData().putString(WITNESS, signature(state.inventory().fungibleResources(), carry));
        });
    }

    static boolean witnessed(FrontierWorldState state, SubjectId actorId, Mob body) {
        var carry = ActorCarryCapabilities.workCargo(state, actorId);
        return carry.isPresent() && matches(state.inventory().fungibleResources(), carry.orElseThrow(), body)
                && body.getPersistentData().getString(WITNESS)
                    .equals(signature(state.inventory().fungibleResources(), carry.orElseThrow()));
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
