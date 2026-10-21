package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Portable inventory's own death hook. Declared job/meal owners settle their claims first. */
final class FrontierV3UnitInventoryDeathResources {
    private FrontierV3UnitInventoryDeathResources() { }
    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var initial = runtime.decodedState().orElseThrow();
        // Death may happen in the same server turn as first body admission, before
        // the periodic observer runs. The positive pre-loot stack can establish its
        // ordinary inventory binding now; an absent lookup can never do this.
        for (var placement : UnitInventoryPresentation.inventory(initial, id.actorId()).values()) {
            var current = runtime.decodedState().orElseThrow();
            var resources = current.inventory().fungibleResources();
            var account = resources.accounts().get(placement.accountId());
            if (account == null || account.actorPresentation().isEmpty() || !account.claimQuantities().isEmpty()
                    || resources.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(account.id()))) continue;
            placement.requireAccount(resources);
            var kind = resources.lots().get(account.lotQuantities().keySet().iterator().next()).itemKind();
            int quantity = account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
            var actual = FrontierV3ActorResourceSlots.get(body, placement.slot());
            var expected = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind)), quantity);
            if (!ItemStack.matches(actual, expected)) continue;
            FrontierV3CommandSubmission.submit(runtime, "unit-inventory-pre-death", account.id().value(),
                    new UnitInventoryBoundObserved(id, account.id(), new FungiblePhysicalObservation.Stack(
                            FrontierV3ActorResourceSlots.address(id.actorId(), body, placement.slot()), kind, quantity)));
        }
        initial = runtime.decodedState().orElseThrow();
        var beforeLoot = new java.util.HashMap<ActorItemSlot, ItemStack>();
        for (var slot : FrontierV3ActorResourceSlots.supportedSlots(body)) beforeLoot.put(slot, FrontierV3ActorResourceSlots.get(body, slot).copy());
        return () -> settle(level, runtime, body, id, beforeLoot);
    }
    static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot) {
        ActorBodyAuthority.requireRetiredDeath(runtime.decodedState().orElseThrow(), id);
        settle(level, runtime, body, id, snapshot.slots(level));
    }
    static boolean settled(FrontierWorldState state, ActorBodyId id) {
        return UnitInventory.accounts(state.inventory().fungibleResources(), id.actorId()).isEmpty();
    }
    private static void settle(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, java.util.Map<ActorItemSlot, ItemStack> beforeLoot) {
            if (ActorInventoryInteractionFences.pending(runtime.decodedState().orElseThrow(), id.actorId())) return;
            // Earlier resource owners may just have confirmed an applied prepared take.
            // Use the actual pre-loot slot snapshot, not only accounts known before settlement.
            var sources = UnitInventoryPresentation.inventory(runtime.decodedState().orElseThrow(), id.actorId()).values().stream()
                    .sorted(java.util.Comparator.comparing(ActorCarriedResources.Presentation::accountId)).toList();
            for (var source : sources) {
                var state = runtime.decodedState().orElseThrow();
                if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
                var account = state.inventory().fungibleResources().accounts().get(source.accountId());
                if (account == null || !account.claimQuantities().isEmpty()
                        || !account.custody().equals(new ResourceCustody.Actor(id.actorId()))) continue;
                var bindings = state.inventory().fungibleResources().bindings().values().stream()
                        .filter(binding -> binding.accountId().equals(account.id())).toList();
                if (bindings.size() != 1 || !bindings.getFirst().address().equals(
                        FrontierV3ActorResourceSlots.address(id.actorId(), body, source.slot()))) continue;
                var binding = bindings.getFirst();
                var actual = FrontierV3ActorResourceSlots.get(body, source.slot());
                var original = beforeLoot.get(source.slot());
                if (original == null) throw new IllegalStateException("declared personal resource slot has no native body capability: " + source.slot());
                if (original.isEmpty() && actual.isEmpty()) {
                    submit(runtime, new UnitInventoryDispositionObserved(id, account.id(), binding.authorityEpoch(),
                            UnitInventoryDispositionObserved.Outcome.MISSING_BEFORE_LOOT, Optional.empty()));
                    continue;
                }
                var expected = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(binding.itemKind())), binding.quantity());
                if (!ItemStack.matches(actual, expected) || !ItemStack.matches(actual, original)) continue;
                var carrier = dropId(state, id, account.id(), binding.authorityEpoch());
                if (level.getEntity(carrier) != null || FrontierV3ResourceHandoffLedger.get(level).blocks(carrier)) continue;
                var drop = new ItemEntity(level, body.getX(), body.getY() + 0.25D, body.getZ(), actual.copy());
                drop.setUUID(carrier);
                var receipt = new UnitInventoryDispositionObserved(id, account.id(), binding.authorityEpoch(),
                        UnitInventoryDispositionObserved.Outcome.WORLD_DROP, Optional.of(FrontierV3EntityResourceDropOperation.ground(drop)));
                FrontierV3EntityResourceDropOperation.perform(level, runtime, body, source.slot(), drop, receipt);
            }
    }
    private static UUID dropId(FrontierWorldState state, ActorBodyId body,
                               io.farfrontier.palemirror.frontier.v3.api.SubjectId account, long epoch) {
        return UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + ":inventory-death:" + body.actorId().value()
                + ":" + body.physicalEpoch() + ":" + account.value() + ":" + epoch).getBytes(StandardCharsets.UTF_8));
    }
    private static boolean submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, UnitInventoryDispositionObserved receipt) {
        return FrontierV3CommandSubmission.submit(runtime, "unit-inventory-death", receipt.accountId().value(), receipt)
                instanceof CommandResult.Accepted;
    }
}
