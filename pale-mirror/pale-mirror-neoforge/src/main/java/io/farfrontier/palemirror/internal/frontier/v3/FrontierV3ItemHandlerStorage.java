package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Native inventory bridge only. Canonical storage and the common operation journal own mutations. */
public final class FrontierV3ItemHandlerStorage {
    private record Binding(WeakReference<BlockEntity> owner, StorageBlockKind kind) { }
    private static final java.lang.ref.ReferenceQueue<IItemHandler> COLLECTED = new java.lang.ref.ReferenceQueue<>();
    private static final class Identity extends WeakReference<IItemHandler> {
        private final int hash;
        Identity(IItemHandler handler) { super(handler,COLLECTED); hash=System.identityHashCode(handler); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this==other || other instanceof Identity key && get()!=null && get()==key.get();
        }
    }
    // Both directions are weak and keys use reference identity, including providers overriding equals.
    private static final Map<Identity, Binding> BINDINGS = new HashMap<>();
    private static void cleanup() { for(var key=COLLECTED.poll();key!=null;key=COLLECTED.poll()) BINDINGS.remove(key); }
    public static synchronized void register(BlockEntity owner, StorageBlockKind kind, IItemHandlerModifiable inventory) {
        if (!(owner instanceof FrontierV3ItemHandlerOwner declared) || declared.frontierV3$storageKind() != kind || declared.frontierV3$inventory() != inventory)
            throw new IllegalArgumentException("item handler registration lacks its provider declaration");
        cleanup();
        var key=new Identity(inventory); var old=BINDINGS.get(key);
        if (old != null && old.owner().get() != null && old.owner().get() != owner)
            throw new IllegalStateException("native inventory has competing owners");
        if (old==null || old.owner().get()!=owner) BINDINGS.put(key, new Binding(new WeakReference<>(owner), kind));
    }
    static synchronized BlockEntity owner(IItemHandler inventory) {
        cleanup(); var binding = BINDINGS.get(new Identity(inventory));
        var owner = binding == null ? null : binding.owner().get();
        if (owner == null || owner.isRemoved()) return null;
        var declared = (FrontierV3ItemHandlerOwner) owner;
        if (declared.frontierV3$storageKind() != binding.kind() || declared.frontierV3$inventory() != inventory)
            throw new IllegalArgumentException("native inventory binding is stale");
        return owner;
    }
    static Container inventory(BlockEntity owner) {
        if (!(owner instanceof FrontierV3ItemHandlerOwner declared)) return null;
        var inventory = declared.frontierV3$inventory();
        register(owner, declared.frontierV3$storageKind(), inventory);
        return new Slots(owner, inventory);
    }
    static BlockEntity entity(Container inventory) {
        return inventory instanceof Slots slots ? slots.owner() : inventory instanceof BlockEntity block ? block : null;
    }
    static boolean same(Container left, Container right) {
        return left == right || left instanceof Slots a && right instanceof Slots b && a.owner() == b.owner() && a.handler() == b.handler();
    }
    record Slot(IItemHandler handler, int index) { }
    static Slot leaf(IItemHandler inventory, int index) { return leaf(inventory, index, Collections.newSetFromMap(new IdentityHashMap<>())); }
    private static Slot leaf(IItemHandler inventory, int index, Set<IItemHandler> seen) {
        if (index < 0 || index >= inventory.getSlots() || seen.size() >= 54 || !seen.add(inventory))
            throw new IllegalArgumentException("invalid or cyclic item handler composition");
        if (inventory instanceof FrontierV3ItemHandlerParts composition) {
            var parts = composition.frontierV3$parts();
            int slots = parts.stream().mapToInt(IItemHandler::getSlots).reduce(0, Math::addExact);
            if (slots != inventory.getSlots()) throw new IllegalArgumentException("item handler composition capacity mismatch");
            for (var part : parts) {
                if (index < part.getSlots()) return leaf(part, index, seen);
                index -= part.getSlots();
            }
            throw new IllegalArgumentException("missing composed inventory slot");
        }
        return new Slot(inventory, index);
    }
    /** Optional native drop provider delegates exact slot births to the ordinary container journal. */
    public static boolean drop(IItemHandler handler, net.minecraft.world.level.Level level, int x, int y, int z) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || !FrontierV3PhysicalWorld.isPhysical(server)) return false;
        var owner = owner(handler);
        if (owner == null || owner.getPersistentData().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY).isBlank()) return false;
        var inventory = inventory(owner);
        for(int slot=0;slot<inventory.getContainerSize();slot++) {
            var stack=inventory.getItem(slot);
            if(!stack.isEmpty()) FrontierV3ContainerDrops.drop(level,x,y,z,stack,inventory);
        }
        return true;
    }
    private record Slots(BlockEntity owner, IItemHandlerModifiable handler) implements Container {
        @Override public int getContainerSize() { return handler.getSlots(); }
        @Override public boolean isEmpty() { for(int i=0;i<getContainerSize();i++) if(!getItem(i).isEmpty()) return false; return true; }
        @Override public ItemStack getItem(int slot) { return handler.getStackInSlot(slot); }
        @Override public ItemStack removeItem(int slot,int count) { var result=handler.extractItem(slot,count,false);setChanged();return result; }
        @Override public ItemStack removeItemNoUpdate(int slot) { var stack=getItem(slot);handler.setStackInSlot(slot,ItemStack.EMPTY);return stack; }
        @Override public void setItem(int slot,ItemStack stack) { handler.setStackInSlot(slot,stack);setChanged(); }
        @Override public void setChanged() { owner.setChanged(); }
        @Override public boolean stillValid(Player player) { return !owner.isRemoved() && player.distanceToSqr(owner.getBlockPos().getCenter()) <= 64; }
        @Override public void clearContent() { for(int i=0;i<getContainerSize();i++) setItem(i,ItemStack.EMPTY); }
        @Override public boolean canPlaceItem(int slot,ItemStack stack) { return handler.isItemValid(slot,stack); }
    }
    private FrontierV3ItemHandlerStorage() { }
}
