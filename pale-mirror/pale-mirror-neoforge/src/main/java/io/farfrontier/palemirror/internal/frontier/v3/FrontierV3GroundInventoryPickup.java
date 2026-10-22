package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Encloses Minecraft's actual ground-to-inventory callback. A partial pickup is
 * an applied handoff even though Vanilla returns false while the entity survives. */
public final class FrontierV3GroundInventoryPickup {
    private FrontierV3GroundInventoryPickup() { }
    public static boolean add(Container destination, ItemEntity source, BooleanSupplier nativePickup) {
        if (!(source.level() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return nativePickup.getAsBoolean();
        var runtime = FrontierV3ServerLifecycle.runtimeForNativeMutation(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return nativePickup.getAsBoolean();
        return add(level, runtime, destination, source, nativePickup);
    }
    static boolean add(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Container destination, ItemEntity source, BooleanSupplier nativePickup) {
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        if (journal.blocks(source.getUUID())) return false;
        var state = runtime.decodedState().orElseThrow(); var resources = state.inventory().fungibleResources();
        var accounts = resources.accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Ground ground
                && ground.entityId().equals(source.getUUID())).toList();
        if (accounts.size() > 1) throw new IllegalStateException("ground pickup has competing custody accounts");
        if (accounts.isEmpty()) {
            // An untracked source needs explicit ingress before joining tracked stock.
            if (FrontierV3AutomationSlot.managed(destination, state)) return false;
            return nativePickup.getAsBoolean();
        }
        var account = accounts.getFirst();
        if (source.isRemoved() || journal.blocksAccount(account.id())) return false;
        var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        if (bindings.size() != 1 || !bindings.getFirst().address().equals(new PhysicalStackAddress.WorldEntity(source.getUUID())))
            throw new IllegalStateException("ground pickup lost its exact current source binding");
        var from = bindings.getFirst(); var original = source.getItem();
        if (!matches(original, from)) return false;
        var layout = plan(level, state, destination, original);
        if (layout == null || layout.transfers().isEmpty()) return false;
        var target = layout.slots().getFirst();
        if (journal.blocksAccount(target.account())) return false;
        var outcome = FungiblePhysicalHandoff.transferToSlots(resources, from, target.account(), target.custody(),
                target.epoch(), layout.transfers()).forfeitAffectedClaims(resources);
        if (!outcome.forfeitedClaimIds().isEmpty()) {
            outcome = FungibleClaimForfeitureStateSupport.stampOwnerDiagnostic(state, outcome);
            if (!FungibleClaimForfeitureStateSupport.supports(state, outcome)) return false;
        }
        FungibleResourceHandoffStateSupport.apply(state, outcome); // Validate the whole result before native mutation.
        var sourcePort = layout.remaining() == 0 ? FrontierV3OperationEntityColumns.current(level, source.chunkPosition()).orElse(null)
                : FrontierV3OperationEntityParticipant.nativeObject(source);
        if (sourcePort == null) return false;
        var before = original.copy(); var remainder = original.copyWithCount(layout.remaining());
        var nativeResult = new boolean[1];
        FrontierV3NativeHandoffOperation.apply(level, runtime, source.getUUID(), outcome,
                FrontierV3OperationReceipt.Protocol.NATIVE_INVENTORY_TRANSFER, List.of(sourcePort, target.persistence()),
                () -> !source.isRemoved() && source.getItem() == original && ItemStack.matches(source.getItem(), before)
                        && layout.matches(layout.before()),
                () -> { nativeResult[0] = nativePickup.getAsBoolean(); return true; },
                () -> source.isRemoved() == (layout.remaining() == 0) && ItemStack.matches(source.getItem(), remainder)
                        && layout.matches(layout.after()));
        return nativeResult[0];
    }
    private record Layout(List<FrontierV3AutomationSlot> slots, List<ItemStack> before, List<ItemStack> after,
            List<FungiblePhysicalHandoff.SlotAmount> transfers, int remaining) {
        boolean matches(List<ItemStack> expected) {
            for (int i = 0; i < slots.size(); i++) if (!ItemStack.matches(slots.get(i).stack(), expected.get(i))) return false;
            return true;
        }
    }
    /** Mirrors only the pinned native admission arithmetic; the real write, notifications,
     * cooldown and removal still run in HopperBlockEntity.addItem, never in this planner. */
    private static Layout plan(ServerLevel level, FrontierWorldState state, Container destination, ItemStack source) {
        if (destination.getContainerSize() < 1 || destination.getContainerSize() > 256) return null;
        var slots = new ArrayList<FrontierV3AutomationSlot>(); var before = new ArrayList<ItemStack>();
        var after = new ArrayList<ItemStack>(); var transfers = new ArrayList<FungiblePhysicalHandoff.SlotAmount>();
        int remaining = source.getCount(); var resources = state.inventory().fungibleResources();
        for (int i = 0; i < destination.getContainerSize(); i++) {
            var slot = FrontierV3AutomationSlot.container(level, state, destination, i);
            if (slot == null) return null;
            if (!slots.isEmpty() && (!slot.account().equals(slots.getFirst().account()) || slot.epoch() != slots.getFirst().epoch()
                    || !slot.persistence().identity().equals(slots.getFirst().persistence().identity()))) return null;
            var stack = slot.stack(); var binding = slot.binding(resources);
            if (binding == null ? !stack.isEmpty() : !matches(stack, binding)) return null;
            slots.add(slot); before.add(stack.copy()); int amount = 0;
            if (remaining > 0 && destination.canPlaceItem(i, source)
                    && (!(destination instanceof WorldlyContainer sided) || sided.canPlaceItemThroughFace(i, source, null))) {
                if (stack.isEmpty()) amount = remaining;
                else if (ItemStack.isSameItemSameComponents(stack, source)) amount = Math.min(remaining, source.getMaxStackSize() - stack.getCount());
            }
            if (amount > 0) {
                transfers.add(new FungiblePhysicalHandoff.SlotAmount(slot.address(), amount));
                after.add(source.copyWithCount(stack.getCount() + amount)); remaining -= amount;
            } else after.add(stack.copy());
        }
        return new Layout(List.copyOf(slots), List.copyOf(before), List.copyOf(after), List.copyOf(transfers), remaining);
    }
    private static boolean matches(ItemStack stack, PhysicalStackBinding binding) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() && stack.getCount() == binding.quantity()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(binding.itemKind());
    }
}
