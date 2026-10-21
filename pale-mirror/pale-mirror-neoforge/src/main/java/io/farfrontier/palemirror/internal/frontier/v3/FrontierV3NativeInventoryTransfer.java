package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Shared causal resource transfer. Providers select native endpoints; the domain
 * retains title/claims, and the common operation owns durability and recovery. */
final class FrontierV3NativeInventoryTransfer {
    private FrontierV3NativeInventoryTransfer() { }
    static boolean apply(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3AutomationSlot source, FrontierV3AutomationSlot destination, int amount, BooleanSupplier nativeTransfer) {
        if (source == null || destination == null) return false;
        var state = runtime.decodedState().orElseThrow(); var resources = state.inventory().fungibleResources();
        var from = source.binding(resources); var to = destination.binding(resources);
        // An ordinary untracked hopper remains ordinary Minecraft. A declared storage
        // endpoint, however, needs an explicit ingress outcome rather than silent adoption.
        if (from == null && to == null && source.custody() instanceof ResourceCustody.WorldCarrier
                && destination.custody() instanceof ResourceCustody.WorldCarrier
                && !resources.accounts().containsKey(source.account()) && !resources.accounts().containsKey(destination.account()))
            return nativeTransfer.getAsBoolean();
        if (from == null || !matches(source.stack(), from) || amount < 1 || amount > from.quantity()
                || (to == null ? !destination.stack().isEmpty() : !matches(destination.stack(), to))) return false;
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        if (journal.blocksAccount(source.account()) || journal.blocksAccount(destination.account())) return false;
        var outcome = FungiblePhysicalHandoff.transferToSlot(resources, from, amount, destination.account(),
                destination.custody(), destination.epoch(), destination.address()).forfeitAffectedClaims(resources);
        if (!outcome.forfeitedClaimIds().isEmpty()) {
            outcome = FungibleClaimForfeitureStateSupport.stampOwnerDiagnostic(state, outcome);
            if (!FungibleClaimForfeitureStateSupport.supports(state, outcome)) return false;
        }
        // Reject invalid accounting before consuming the native attempt.
        FungibleResourceHandoffStateSupport.apply(state, outcome);
        var beforeSource = source.stack().copy(); var beforeDestination = destination.stack().copy();
        var afterSource = beforeSource.copy(); afterSource.shrink(amount);
        var afterDestination = beforeSource.copyWithCount(beforeDestination.getCount() + amount);
        var applied = new boolean[1];
        FrontierV3NativeHandoffOperation.apply(level, runtime, UUID.randomUUID(), outcome,
                FrontierV3OperationReceipt.Protocol.NATIVE_INVENTORY_TRANSFER,
                List.of(source.persistence(), destination.persistence()),
                () -> ItemStack.matches(source.stack(), beforeSource) && ItemStack.matches(destination.stack(), beforeDestination),
                () -> { applied[0] = nativeTransfer.getAsBoolean(); return applied[0]; },
                () -> ItemStack.matches(source.stack(), afterSource) && ItemStack.matches(destination.stack(), afterDestination));
        return applied[0];
    }
    private static boolean matches(ItemStack stack, PhysicalStackBinding binding) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() && stack.getCount() == binding.quantity()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(binding.itemKind());
    }
}
