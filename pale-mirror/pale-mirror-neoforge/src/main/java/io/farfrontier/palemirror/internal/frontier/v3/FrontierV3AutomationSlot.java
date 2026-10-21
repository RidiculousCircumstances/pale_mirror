package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3CompoundContainerAccessor;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3SidedInventoryAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import java.util.*;

/** Provider mapping for the actual native slot selected by automation. This is not
 * a search for a nearby container or a matching stack. */
record FrontierV3AutomationSlot(Container inventory, int slot, SubjectId account, ResourceCustody custody,
        PhysicalStackAddress address, long epoch, FrontierV3MinecraftOperation.Participant persistence) {
    private static final String CARRIER = "pm_v3_automation_carrier";
    /** Read-only declaration check, deliberately independent of current admission.
     * A fenced/missing lease must not turn a managed endpoint into an untracked one. */
    static boolean managed(Container container, FrontierWorldState state) {
        if (container instanceof CompoundContainer && container instanceof FrontierV3CompoundContainerAccessor access)
            return managed(access.frontierV3$first(), state) || managed(access.frontierV3$second(), state);
        if (!(container instanceof net.minecraft.world.level.block.entity.BlockEntity entity)) return false;
        if (!entity.getPersistentData().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY).isBlank()) return true;
        return accounted(entity, state);
    }
    static boolean accounted(net.minecraft.world.level.block.entity.BlockEntity entity, FrontierWorldState state) {
        if (!entity.getPersistentData().hasUUID(CARRIER)) return false;
        UUID id = entity.getPersistentData().getUUID(CARRIER);
        var account = state.inventory().fungibleResources().accounts().get(accountId(id));
        if (account != null && !account.custody().equals(new ResourceCustody.WorldCarrier(id)))
            throw new IllegalStateException("native automation declaration has foreign custody");
        return account != null;
    }
    private static SubjectId accountId(UUID id) { return new SubjectId("custody:automation-" + id); }
    static FrontierV3AutomationSlot handler(ServerLevel level, FrontierWorldState state, IItemHandler handler, int slot) {
        if (handler instanceof InvWrapper wrapper) return container(level, state, wrapper.getInv(), slot);
        if (handler instanceof SidedInvWrapper && handler instanceof FrontierV3SidedInventoryAccessor access) {
            int actual = SidedInvWrapper.getSlot(access.frontierV3$inventory(), slot, access.frontierV3$side());
            return actual < 0 ? null : container(level, state, access.frontierV3$inventory(), actual);
        }
        return null; // Unknown capability layout cannot authorize a managed resource mutation.
    }
    static FrontierV3AutomationSlot container(ServerLevel level, FrontierWorldState state, Container inventory, int slot) {
        if (slot < 0 || slot >= inventory.getContainerSize()) throw new IllegalArgumentException("invalid automation slot");
        if (inventory instanceof CompoundContainer && inventory instanceof FrontierV3CompoundContainerAccessor access) {
            int size = access.frontierV3$first().getContainerSize();
            return slot < size ? container(level, state, access.frontierV3$first(), slot)
                    : container(level, state, access.frontierV3$second(), slot - size);
        }
        if (inventory instanceof HopperBlockEntity hopper) {
            var port = FrontierV3OperationBlockEntityParticipant.current(hopper);
            var data = hopper.getPersistentData();
            if (!data.hasUUID(CARRIER)) { data.putUUID(CARRIER, UUID.randomUUID()); hopper.setChanged(); }
            UUID id = data.getUUID(CARRIER); var position = hopper.getBlockPos();
            var custody = new ResourceCustody.WorldCarrier(id);
            var account = accountId(id);
            return new FrontierV3AutomationSlot(inventory, slot, account, custody,
                    new PhysicalStackAddress.HopperSlot(new BlockPosition(position.getX(), position.getY(), position.getZ()), slot),
                    port.identity().incarnation(), port);
        }
        var physical = FrontierV3PhysicalContainer.observedFixed(level, state, inventory).orElse(null);
        if (physical == null) return null;
        var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(physical.containerId()));
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                || !ReferenceContainerCustody.hasOperationalCustody(state, physical.containerId())
                || FrontierV3ContainerEffectFence.pending(level, state, physical.containerId())) return null;
        var custody = new ResourceCustody.Container(physical.containerId());
        var account = FungibleResourceCustodySupport.accountAtContainer(state, physical.containerId()).map(CustodyAccount::id)
                .orElse(ReferenceContainerCustody.scopeId(physical.containerId()));
        return new FrontierV3AutomationSlot(inventory, slot, account, custody,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(physical.containerId(), slot)),
                lease.authorityEpoch(), physical.persistence().get());
    }
    ItemStack stack() { return inventory.getItem(slot); }
    PhysicalStackBinding binding(FungibleResourceLedger resources) {
        var matches = resources.bindings().values().stream().filter(value -> value.accountId().equals(account)
                && value.address().equals(address)).toList();
        if (matches.size() > 1) throw new IllegalStateException("automation slot has competing physical bindings");
        var binding = matches.isEmpty() ? null : matches.getFirst();
        if (binding != null && binding.authorityEpoch() != epoch)
            throw new IllegalStateException("automation slot has a stale custody epoch");
        return binding;
    }
}
