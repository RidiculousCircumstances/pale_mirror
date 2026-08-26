package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxWarehouseObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceResource;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Real, bounded graybox warehouse containers and their reverse observation path.
 *
 * <p>There is no item-to-population shortcut here: the source snapshot says
 * which settlement/resource exists, a barrel stores ordinary stackable items,
 * and a changed count crosses the typed domain boundary before it can alter
 * source stock. A broken or foreign barrel remains a retained conflict; it is
 * never rebuilt over a player's block.</p>
 */
final class SourceGrayboxWarehouseRuntime {
    /** A 2x2 shelf, four barrels tall: readable as a storehouse, not a mast. */
    static final int BARRELS_PER_RESOURCE = 16;
    private static final int SHELF_WIDTH = 2;
    private static final int SHELF_DEPTH = 2;
    private static final int SHELF_HEIGHT = BARRELS_PER_RESOURCE / (SHELF_WIDTH * SHELF_DEPTH);
    static final int ITEMS_PER_SOURCE_UNIT = ReferenceGrayboxWarehouseObservation.ITEMS_PER_SOURCE_UNIT;
    static final int BARREL_CAPACITY = 27 * 64;
    private static final int MAX_RESOURCE_ITEMS = BARRELS_PER_RESOURCE * BARREL_CAPACITY;
    private static final String BINDING_KEY = "pale_mirror_source_graybox_warehouse_binding";
    private static final String RESOURCE_KEY = "pale_mirror_source_graybox_warehouse_resource";

    /**
     * Observe player/container changes before source-day advancement. Each
     * accepted delta immediately changes canonical stock; source production
     * and consumption are reconciled into containers only afterwards.
     */
    boolean reconcileInbound(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer) {
        boolean changed = false;
        for (SourceGrayboxWarehouseLedger.Binding binding : data.warehouseLedger().bindings()) {
            if (binding.state() != SourceGrayboxWarehouseLedger.State.ACTIVE || !level.hasChunkAt(position(binding))) continue;
            BlockEntity blockEntity = level.getBlockEntity(position(binding));
            if (!(blockEntity instanceof BarrelBlockEntity barrel) || !matches(barrel, binding)) {
                changed |= recordLossOrConflict(level, data, materializer, binding, 0, "container-missing-or-foreign");
                continue;
            }
            int actual = count(barrel, item(binding.resource()));
            if (actual != binding.observedItems()) {
                changed |= applyDelta(level, data, materializer, binding, actual, "container-item-change");
            }
        }
        return changed;
    }

    /** Installs missing empty PM barrels, then brings their item contents to the canonical source projection. */
    void materialize(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer) {
        ReferenceGrayboxSnapshot snapshot = data.snapshot();
        for (ReferenceGrayboxSnapshot.Warehouse warehouse : snapshot.warehouses()) {
            Map<ReferenceResource, Integer> expected = expectedItems(warehouse);
            for (ReferenceResource resource : ReferenceResource.values()) {
                List<SourceGrayboxWarehouseLedger.Binding> bindings = bindingsFor(level, data, materializer, warehouse, resource, snapshot.stateRevision());
                // This runtime never forces the other half of a warehouse
                // into memory. Defer the complete resource bay until all of
                // its fixed containers are naturally loaded.
                if (bindings.size() != BARRELS_PER_RESOURCE) continue;
                int target = expected.get(resource);
                int capacity = bindings.stream().filter(binding -> binding.state() == SourceGrayboxWarehouseLedger.State.ACTIVE)
                        .mapToInt(ignored -> BARREL_CAPACITY).sum();
                if (target > capacity) {
                    recordConflict(level, materializer, warehouse, resource, 0, snapshot.stateRevision(), true, "capacity-exhausted");
                    target = capacity;
                }
                reconcileProjectedItems(level, data, materializer, warehouse, resource, bindings, target, snapshot.stateRevision());
            }
        }
    }

    private static List<SourceGrayboxWarehouseLedger.Binding> bindingsFor(
            ServerLevel level,
            SourceGrayboxSavedData data,
            SourceGrayboxMaterializer materializer,
            ReferenceGrayboxSnapshot.Warehouse warehouse,
            ReferenceResource resource,
            String revision
    ) {
        java.util.ArrayList<SourceGrayboxWarehouseLedger.Binding> result = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < BARRELS_PER_RESOURCE; ordinal++) {
            BlockPos position = position(warehouse, resource, ordinal);
            if (!level.hasChunkAt(position)) continue;
            String id = bindingId(warehouse, resource, ordinal);
            SourceGrayboxWarehouseLedger.Binding binding = data.warehouseLedger().binding(id);
            if (binding != null && !sameBinding(binding, warehouse, resource, position)) {
                throw new IllegalStateException("source graybox warehouse binding disagrees with the source layout: " + id);
            }
            if (binding != null && binding.state() == SourceGrayboxWarehouseLedger.State.BLOCKED) {
                recordConflict(level, materializer, warehouse, resource, ordinal, revision, false, "retained-foreign-obstruction");
                result.add(binding);
                continue;
            }
            BarrelBlockEntity barrel = ensureContainer(level, position, id, resource);
            if (barrel == null) {
                SourceGrayboxWarehouseLedger.Binding blocked = binding == null
                        ? new SourceGrayboxWarehouseLedger.Binding(id, warehouse.settlementId(), resource, position.getX(), position.getY(), position.getZ(),
                                0, SourceGrayboxWarehouseLedger.State.BLOCKED)
                        : binding.blocked();
                if (data.warehouseLedger().put(blocked)) data.markWarehouseLedgerDirty();
                recordConflict(level, materializer, warehouse, resource, ordinal, revision, false, "foreign-obstruction");
                result.add(blocked);
                continue;
            }
            int observed = count(barrel, item(resource));
            SourceGrayboxWarehouseLedger.Binding active = binding == null
                    ? new SourceGrayboxWarehouseLedger.Binding(id, warehouse.settlementId(), resource, position.getX(), position.getY(), position.getZ(),
                            observed, SourceGrayboxWarehouseLedger.State.ACTIVE)
                    : binding;
            if (binding == null && data.warehouseLedger().put(active)) data.markWarehouseLedgerDirty();
            result.add(active);
        }
        return List.copyOf(result);
    }

    private static void reconcileProjectedItems(
            ServerLevel level,
            SourceGrayboxSavedData data,
            SourceGrayboxMaterializer materializer,
            ReferenceGrayboxSnapshot.Warehouse warehouse,
            ReferenceResource resource,
            List<SourceGrayboxWarehouseLedger.Binding> bindings,
            int target,
            String revision
    ) {
        int actual = 0;
        java.util.ArrayList<SourceGrayboxWarehouseLedger.Binding> active = new java.util.ArrayList<>();
        for (SourceGrayboxWarehouseLedger.Binding binding : bindings) {
            if (binding.state() != SourceGrayboxWarehouseLedger.State.ACTIVE) continue;
            BlockEntity entity = level.getBlockEntity(position(binding));
            if (!(entity instanceof BarrelBlockEntity barrel) || !matches(barrel, binding)) {
                recordLossOrConflict(level, data, materializer, binding, 0, "container-lost-during-projection");
                continue;
            }
            actual += count(barrel, item(resource));
            active.add(binding);
        }
        int remaining = target - actual;
        if (remaining > 0) {
            for (SourceGrayboxWarehouseLedger.Binding binding : active) {
                BarrelBlockEntity barrel = (BarrelBlockEntity) level.getBlockEntity(position(binding));
                remaining -= insert(barrel, item(resource), remaining);
                if (remaining == 0) break;
            }
        } else if (remaining < 0) {
            int remove = -remaining;
            for (SourceGrayboxWarehouseLedger.Binding binding : active) {
                BarrelBlockEntity barrel = (BarrelBlockEntity) level.getBlockEntity(position(binding));
                remove -= remove(barrel, item(resource), remove);
                if (remove == 0) break;
            }
            remaining = -remove;
        }
        if (remaining != 0) {
            recordConflict(level, materializer, warehouse, resource, 0, revision, true, "container-capacity-or-write-failure");
        }
        for (SourceGrayboxWarehouseLedger.Binding binding : active) {
            BlockEntity entity = level.getBlockEntity(position(binding));
            if (!(entity instanceof BarrelBlockEntity barrel) || !matches(barrel, binding)) continue;
            int observed = count(barrel, item(resource));
            SourceGrayboxWarehouseLedger.Binding refreshed = binding.withObservedItems(observed);
            if (data.warehouseLedger().replace(refreshed)) data.markWarehouseLedgerDirty();
            barrel.setChanged();
        }
    }

    private static boolean applyDelta(
            ServerLevel level,
            SourceGrayboxSavedData data,
            SourceGrayboxMaterializer materializer,
            SourceGrayboxWarehouseLedger.Binding binding,
            int actual,
            String reason
    ) {
        int delta = actual - binding.observedItems();
        if (delta == 0) return false;
        ReferenceGrayboxSnapshot snapshot = data.snapshot();
        String eventId = "warehouse:" + binding.id() + ":" + snapshot.stateRevision() + ":" + actual;
        ReferenceGrayboxObservationOutcome outcome = data.observe(new ReferenceGrayboxWarehouseObservation(
                ReferenceGrayboxWarehouseObservation.VERSION, eventId, snapshot.stateRevision(), binding.settlementId(), binding.resource(), delta));
        if (!outcome.applied()) {
            SourceGrayboxWarehouseLedger.Binding blocked = binding.withObservedItems(actual).blocked();
            if (data.warehouseLedger().replace(blocked)) data.markWarehouseLedgerDirty();
            recordBindingConflict(level, materializer, binding, snapshot.stateRevision(), true,
                    reason + ":" + outcome.status().name().toLowerCase(Locale.ROOT));
            return false;
        }
        SourceGrayboxWarehouseLedger.Binding refreshed = binding.withObservedItems(actual);
        if (data.warehouseLedger().replace(refreshed)) data.markWarehouseLedgerDirty();
        return true;
    }

    private static boolean recordLossOrConflict(
            ServerLevel level,
            SourceGrayboxSavedData data,
            SourceGrayboxMaterializer materializer,
            SourceGrayboxWarehouseLedger.Binding binding,
            int actual,
            String reason
    ) {
        if (binding.state() == SourceGrayboxWarehouseLedger.State.BLOCKED) return false;
        boolean applied = applyDelta(level, data, materializer, binding, actual, reason);
        SourceGrayboxWarehouseLedger.Binding blocked = binding.withObservedItems(actual).blocked();
        if (data.warehouseLedger().replace(blocked)) data.markWarehouseLedgerDirty();
        recordBindingConflict(level, materializer, binding, data.snapshot().stateRevision(), true, reason);
        return applied;
    }

    /**
     * Installs exactly one PM-owned container into an empty location.
     *
     * <p>A pre-existing untagged barrel is player/foreign state, not an
     * invitation to adopt it.  Otherwise a player could place a barrel before
     * publication and have its inventory silently enter the source economy.</p>
     */
    static BarrelBlockEntity ensureContainer(ServerLevel level, BlockPos position, String id, ReferenceResource resource) {
        boolean installedNow = false;
        if (level.getBlockState(position).isAir()) {
            if (!level.setBlock(position, Blocks.BARREL.defaultBlockState(), 3)) return null;
            installedNow = true;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof BarrelBlockEntity barrel)) return null;
        String existing = barrel.getPersistentData().getString(BINDING_KEY);
        if (!existing.isBlank() && !existing.equals(id)) return null;
        String existingResource = barrel.getPersistentData().getString(RESOURCE_KEY);
        if (!existingResource.isBlank() && !existingResource.equals(resource.name())) return null;
        if (existing.isBlank()) {
            if (!installedNow) return null;
            barrel.getPersistentData().putString(BINDING_KEY, id);
            barrel.getPersistentData().putString(RESOURCE_KEY, resource.name());
            barrel.setChanged();
        }
        return barrel;
    }

    static boolean matches(BarrelBlockEntity barrel, SourceGrayboxWarehouseLedger.Binding binding) {
        return matches(barrel, binding.id(), binding.resource());
    }

    static boolean matches(BarrelBlockEntity barrel, String id, ReferenceResource resource) {
        return id.equals(barrel.getPersistentData().getString(BINDING_KEY))
                && resource.name().equals(barrel.getPersistentData().getString(RESOURCE_KEY));
    }

    static int count(BarrelBlockEntity barrel, Item item) {
        int total = 0;
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
            ItemStack stack = barrel.getItem(slot);
            if (stack.is(item)) total = Math.addExact(total, stack.getCount());
        }
        return total;
    }

    static int insert(BarrelBlockEntity barrel, Item item, int wanted) {
        int inserted = 0;
        for (int slot = 0; slot < barrel.getContainerSize() && inserted < wanted; slot++) {
            ItemStack stack = barrel.getItem(slot);
            if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                int add = Math.min(wanted - inserted, stack.getMaxStackSize() - stack.getCount());
                stack.grow(add);
                inserted += add;
            }
        }
        for (int slot = 0; slot < barrel.getContainerSize() && inserted < wanted; slot++) {
            if (!barrel.getItem(slot).isEmpty()) continue;
            int add = Math.min(wanted - inserted, item.getDefaultMaxStackSize());
            barrel.setItem(slot, new ItemStack(item, add));
            inserted += add;
        }
        if (inserted > 0) barrel.setChanged();
        return inserted;
    }

    static int remove(BarrelBlockEntity barrel, Item item, int wanted) {
        int removed = 0;
        for (int slot = 0; slot < barrel.getContainerSize() && removed < wanted; slot++) {
            ItemStack stack = barrel.getItem(slot);
            if (!stack.is(item)) continue;
            int take = Math.min(wanted - removed, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) barrel.setItem(slot, ItemStack.EMPTY);
            removed += take;
        }
        if (removed > 0) barrel.setChanged();
        return removed;
    }

    private static Map<ReferenceResource, Integer> expectedItems(ReferenceGrayboxSnapshot.Warehouse warehouse) {
        EnumMap<ReferenceResource, Integer> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceGrayboxSnapshot.Stockpile stockpile : warehouse.stockpiles()) {
            ReferenceResource resource;
            try {
                resource = ReferenceResource.valueOf(stockpile.resource().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("graybox warehouse has an unknown resource: " + stockpile.resource(), invalid);
            }
            double rawItems = stockpile.quantity() * ITEMS_PER_SOURCE_UNIT;
            if (!Double.isFinite(rawItems) || rawItems < 0.0d || rawItems > Integer.MAX_VALUE) {
                throw new IllegalStateException("graybox warehouse item quantity is invalid");
            }
            result.put(resource, (int) Math.floor(rawItems + 1.0e-9d));
        }
        if (result.size() != ReferenceResource.values().length) throw new IllegalStateException("graybox warehouse stockpile coverage is invalid");
        return Map.copyOf(result);
    }

    private static void recordConflict(
            ServerLevel level,
            SourceGrayboxMaterializer materializer,
            ReferenceGrayboxSnapshot.Warehouse warehouse,
            ReferenceResource resource,
            int ordinal,
            String revision,
            boolean installed,
            String reason
    ) {
        BlockPos position = position(warehouse, resource, ordinal);
        materializer.recordWarehouseConflict(level, "warehouse-conflict:" + warehouse.id() + ":" + resource.name().toLowerCase(Locale.ROOT)
                + ":" + ordinal + ":" + reason, warehouse.id(), revision, position, installed);
    }

    private static void recordBindingConflict(
            ServerLevel level,
            SourceGrayboxMaterializer materializer,
            SourceGrayboxWarehouseLedger.Binding binding,
            String revision,
            boolean installed,
            String reason
    ) {
        materializer.recordWarehouseConflict(level, "warehouse-conflict:" + binding.id() + ":" + reason,
                "settlement:" + binding.settlementId() + ":warehouse", revision, position(binding), installed);
    }

    private static boolean sameBinding(SourceGrayboxWarehouseLedger.Binding binding, ReferenceGrayboxSnapshot.Warehouse warehouse,
                                       ReferenceResource resource, BlockPos position) {
        return binding.settlementId() == warehouse.settlementId() && binding.resource() == resource
                && binding.x() == position.getX() && binding.y() == position.getY() && binding.z() == position.getZ();
    }

    private static String bindingId(ReferenceGrayboxSnapshot.Warehouse warehouse, ReferenceResource resource, int ordinal) {
        return warehouse.id() + ":" + resource.name().toLowerCase(Locale.ROOT) + ":" + ordinal;
    }

    private static BlockPos position(SourceGrayboxWarehouseLedger.Binding binding) {
        return new BlockPos(binding.x(), binding.y(), binding.z());
    }

    private static BlockPos position(ReferenceGrayboxSnapshot.Warehouse warehouse, ReferenceResource resource, int ordinal) {
        if (ordinal < 0 || ordinal >= BARRELS_PER_RESOURCE) throw new IllegalArgumentException("warehouse shelf ordinal is invalid");
        int resourceIndex = resource.ordinal();
        int x = warehouse.rectangle().x() + 1 + resourceIndex % 3 * 3 + ordinal % SHELF_WIDTH;
        int z = warehouse.rectangle().z() + 1 + resourceIndex / 3 * 3 + ordinal / SHELF_WIDTH % SHELF_DEPTH;
        int y = io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout.GROUND_Y + 1
                + ordinal / (SHELF_WIDTH * SHELF_DEPTH);
        return new BlockPos(x, y, z);
    }

    static int labelFloorY() {
        return io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout.GROUND_Y + 1 + SHELF_HEIGHT + 2;
    }

    static Item item(ReferenceResource resource) {
        return switch (resource) {
            case FOOD -> Items.BREAD;
            case SEEDS -> Items.WHEAT_SEEDS;
            case TIMBER -> Items.OAK_LOG;
            case ORE -> Items.IRON_INGOT;
            case ENERGY -> Items.REDSTONE;
            case TOOLS -> Items.STICK;
            case MEDICINE -> Items.GHAST_TEAR;
            case WEAPONS -> Items.IRON_NUGGET;
            case AMMO -> Items.ARROW;
        };
    }
}
