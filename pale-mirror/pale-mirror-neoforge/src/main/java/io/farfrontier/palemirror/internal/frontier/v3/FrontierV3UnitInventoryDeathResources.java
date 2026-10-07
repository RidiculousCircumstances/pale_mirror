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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Portable inventory's own death hook. Declared job/meal owners settle their claims first. */
final class FrontierV3UnitInventoryDeathResources {
    private static final String RECEIPT = "pmv3_retired_inventory_drop_v1";
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
        return () -> {
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
                if (level.getEntity(carrier) != null) continue; // Never replay insertion after an absent source.
                var drop = new ItemEntity(level, body.getX(), body.getY() + 0.25D, body.getZ(), actual.copy());
                drop.setUUID(carrier);
                var receipt = new UnitInventoryDispositionObserved(id, account.id(), binding.authorityEpoch(),
                        UnitInventoryDispositionObserved.Outcome.WORLD_DROP, Optional.of(carrier));
                drop.getPersistentData().putByteArray(RECEIPT,
                        io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs().encode(receipt));
                if (!level.addFreshEntity(drop)) continue;
                FrontierV3ActorResourceSlots.set(body, source.slot(), ItemStack.EMPTY);
                if (level.getEntity(carrier) == drop && !drop.isRemoved() && ItemStack.matches(drop.getItem(), expected))
                    submit(runtime, receipt);
            }
        };
    }
    static boolean reconcileOneDrop(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierWorldState state) {
        for (var account : state.inventory().fungibleResources().accounts().values()) {
            if (!(account.custody() instanceof ResourceCustody.Actor actor) || !account.claimQuantities().isEmpty()) continue;
            var body = state.actorLocations().get(actor.actorId());
            if (body == null || body.condition().status() != ActorLifeStatus.DEAD) continue;
            var retired = state.fencedRecovery().tombstones().get(ActorBodyId.recoveryBindingId(actor.actorId()));
            if (retired == null) continue;
            var id = new ActorBodyId(actor.actorId(), retired.retiredEpoch());
            var bindings = state.inventory().fungibleResources().bindings().values().stream()
                    .filter(binding -> binding.accountId().equals(account.id())).toList();
            if (bindings.size() != 1) continue;
            var binding = bindings.getFirst();
            var carrier = dropId(state, id, account.id(), binding.authorityEpoch());
            if (!(level.getEntity(carrier) instanceof ItemEntity drop) || drop.isRemoved()
                    || !drop.getPersistentData().contains(RECEIPT, net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)) continue;
            UnitInventoryDispositionObserved receipt;
            try {
                receipt = (UnitInventoryDispositionObserved) io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition
                        .payloadCodecs().decode("frontier.unit_inventory_disposition_observed", drop.getPersistentData().getByteArray(RECEIPT));
            } catch (IllegalArgumentException invalid) { continue; }
            var expected = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(binding.itemKind())), binding.quantity());
            if (!receipt.body().equals(id) || !receipt.accountId().equals(account.id())
                    || receipt.sourceEpoch() != binding.authorityEpoch()
                    || !receipt.worldCarrier().equals(Optional.of(carrier))
                    || receipt.outcome() != UnitInventoryDispositionObserved.Outcome.WORLD_DROP
                    || !ItemStack.matches(drop.getItem(), expected)) continue;
            if (submit(runtime, receipt)) return true;
        }
        return false;
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
