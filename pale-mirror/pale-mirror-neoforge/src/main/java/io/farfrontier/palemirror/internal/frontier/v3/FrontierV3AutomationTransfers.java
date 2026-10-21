package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.apache.commons.lang3.tuple.Pair;
import java.util.Optional;
import java.util.function.Function;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3SidedInventoryAccessor;

/** Thin NeoForge provider adapter. The native callback still owns insertion rules,
 * extraction and cooldowns; only its actual selected endpoints grant authority. */
public final class FrontierV3AutomationTransfers {
    private static final ThreadLocal<HopperBlockEntity> DENIED_INSERT = new ThreadLocal<>();
    private FrontierV3AutomationTransfers() { }
    public static Optional<Boolean> extract(Optional<Pair<IItemHandler, Object>> selected,
            Function<Pair<IItemHandler, Object>, Boolean> nativeTransfer, Level world, Hopper destination) {
        if (!(world instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return selected.map(nativeTransfer);
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return selected.map(nativeTransfer);
        return selected.map(pair -> extract(level, runtime, pair.getKey(), pair.getValue(), destination, () -> nativeTransfer.apply(pair)));
    }
    static boolean extract(ServerLevel level, FrontierV3ServerRuntime<io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState, ?> runtime,
            IItemHandler handler, Object source, Container destination, java.util.function.BooleanSupplier nativeTransfer) {
        var state = runtime.decodedState().orElseThrow();
        if (!managed(handler, source, state) && !managed(destination, state)) return nativeTransfer.getAsBoolean();
        // Same ordered dry-run that the native hook uses. It does not write inventory.
        for (int i = 0; i < handler.getSlots(); i++) {
            var extracted = handler.extractItem(i, 1, true);
            if (extracted.isEmpty()) continue;
            for (int j = 0; j < destination.getContainerSize(); j++) {
                var current = destination.getItem(j);
                if (destination.canPlaceItem(j, extracted) && (current.isEmpty()
                        || current.getCount() < current.getMaxStackSize() && current.getCount() < destination.getMaxStackSize()
                            && ItemStack.isSameItemSameComponents(extracted, current))) {
                    return FrontierV3NativeInventoryTransfer.apply(level, runtime,
                            FrontierV3AutomationSlot.handler(level, state, handler, i),
                            FrontierV3AutomationSlot.container(level, state, destination, j), 1, nativeTransfer);
                }
            }
        }
        return false;
    }
    public static Optional<Boolean> insert(Optional<Pair<IItemHandler, Object>> selected,
            Function<Pair<IItemHandler, Object>, Boolean> nativeTransfer, HopperBlockEntity source) {
        if (!(source.getLevel() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return selected.map(nativeTransfer);
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return selected.map(nativeTransfer);
        return selected.map(pair -> {
            var state = runtime.decodedState().orElseThrow();
            if (!managed(source, state) && !managed(pair.getKey(), pair.getValue(), state)) return nativeTransfer.apply(pair);
            boolean result = insert(level, runtime, source, pair.getKey(), () -> nativeTransfer.apply(pair));
            // NeoForge otherwise falls back to a second, uninstrumented Vanilla attempt
            // when insertHook returns false. Preserve the refusal for THIS outer call.
            if (!result) DENIED_INSERT.set(source);
            return result;
        });
    }
    static boolean insert(ServerLevel level, FrontierV3ServerRuntime<io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState, ?> runtime,
            Container source, IItemHandler handler, java.util.function.BooleanSupplier nativeTransfer) {
        var state = runtime.decodedState().orElseThrow();
        for (int i = 0; i < source.getContainerSize(); i++) {
            var stack = source.getItem(i);
            if (stack.isEmpty()) continue;
            for (int j = 0; j < handler.getSlots(); j++) {
                if (handler.insertItem(j, stack.copyWithCount(1), true).isEmpty())
                    return FrontierV3NativeInventoryTransfer.apply(level, runtime,
                            FrontierV3AutomationSlot.container(level, state, source, i),
                            FrontierV3AutomationSlot.handler(level, state, handler, j), 1, nativeTransfer);
            }
        }
        return false;
    }
    public static void beginEjection() { DENIED_INSERT.remove(); }
    public static boolean deniedEjection(HopperBlockEntity source) { return DENIED_INSERT.get() == source; }
    public static void endEjection() { DENIED_INSERT.remove(); }
    private static boolean managed(IItemHandler handler, Object selected, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state) {
        if (handler instanceof InvWrapper wrapper) return managed(wrapper.getInv(), state);
        if (handler instanceof FrontierV3SidedInventoryAccessor access) return managed(access.frontierV3$inventory(), state);
        return selected instanceof Container container && managed(container, state);
    }
    private static boolean managed(Container container, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state) {
        return FrontierV3AutomationSlot.managed(container, state);
    }
}
