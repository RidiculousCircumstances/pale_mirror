package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxCargoObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceResource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Real HOT/COLD chest-minecart execution for operation cargo only. */
final class SourceGrayboxOperationCargoCarrierRuntime {
    static final String ENTITY_ID = "pale_mirror_source_graybox_operation_cargo";
    static final String ENTITY_RESOURCE = "pale_mirror_source_graybox_operation_cargo_resource";
    private static final double SPEED = 0.075d;
    private static final double ARRIVAL = 0.35d;
    private static final int ITEMS_PER_SOURCE_UNIT = ReferenceGrayboxCargoObservation.ITEMS_PER_SOURCE_UNIT;

    boolean tick(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer) {
        Map<String, ReferenceGrayboxSnapshot.Cargo> cargoes = operationCargoes(data.snapshot());
        boolean changed = false;
        for (ReferenceGrayboxSnapshot.Cargo cargo : cargoes.values()) changed |= ensureBinding(level, data, cargo);
        for (SourceGrayboxOperationCargoCarrierLedger.Binding binding : data.operationCarrierLedger().bindings()) {
            ReferenceGrayboxSnapshot.Cargo cargo = cargoes.get(binding.cargoId());
            if (cargo == null) {
                changed |= retire(level, data, binding);
                continue;
            }
            if (!same(binding, cargo) || binding.mode() == SourceGrayboxOperationCargoCarrierLedger.Mode.BLOCKED) continue;
            changed |= execute(level, data, materializer, binding, cargo);
        }
        return changed;
    }

    /** A v26 barrel becomes a carrier only after its exact retained custody is naturally loaded and cleanly released. */
    static boolean ensureBinding(ServerLevel level, SourceGrayboxSavedData data, ReferenceGrayboxSnapshot.Cargo cargo) {
        String id = bindingId(cargo);
        if (data.operationCarrierLedger().binding(id) != null) return false;
        // v26 operation cargo lived in the general cargo-barrel ledger. Its
        // former physical binding has a deliberately different identity.
        SourceGrayboxCargoLedger.Binding legacy = data.cargoLedger().binding("cargo-container:" + cargo.id());
        if (legacy != null) {
            if (legacy.state() != SourceGrayboxCargoLedger.State.ACTIVE || !atTarget(legacy, position(cargo)) || !level.hasChunkAt(position(legacy))) return false;
            BlockEntity entity = level.getBlockEntity(position(legacy));
            if (!(entity instanceof BarrelBlockEntity barrel) || !SourceGrayboxWarehouseRuntime.matches(barrel, legacy.id(), legacy.resource())) return false;
            int actual = SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(legacy.resource()));
            if (actual != legacy.observedItems()) return false; // legacy receipt gets first chance to become canonical
            if (actual > 0 && SourceGrayboxWarehouseRuntime.remove(barrel, SourceGrayboxWarehouseRuntime.item(legacy.resource()), actual) != actual) return false;
            if (barrel.isEmpty() && !level.setBlock(position(legacy), Blocks.AIR.defaultBlockState(), 3)) return false;
            else if (!SourceGrayboxWarehouseRuntime.releaseContainer(barrel, legacy.id(), legacy.resource())) return false;
            data.cargoLedger().remove(legacy.id());
            data.markCargoLedgerDirty();
            return put(data, binding(cargo, actual, SourceGrayboxOperationCargoCarrierLedger.Mode.COLD));
        }
        return put(data, binding(cargo, 0, SourceGrayboxOperationCargoCarrierLedger.Mode.COLD));
    }

    private static boolean execute(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                   SourceGrayboxOperationCargoCarrierLedger.Binding binding, ReferenceGrayboxSnapshot.Cargo cargo) {
        MinecartChest carrier = carrier(level, binding);
        BlockPos target = position(cargo);
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(level);
        if (binding.mode() == SourceGrayboxOperationCargoCarrierLedger.Mode.COLD) {
            if (!zone.hot(target) || !level.hasChunkAt(target)) return false;
            int expected = expectedItems(cargo);
            if (expected > SourceGrayboxWarehouseRuntime.BARREL_CAPACITY) {
                return loseAndBlock(level, data, materializer, binding, binding.observedItems(), "carrier-capacity-exhausted");
            }
            // A COLD binding owns no live PM cart.  A retained tagged cart is
            // a recovery conflict, not an invitation to overwrite player
            // contents while trying to rehydrate the canonical source stock.
            if (carrier != null && !carrier.isEmpty()) {
                return loseAndBlock(level, data, materializer, binding, binding.observedItems(), "cold-carrier-has-contents");
            }
            if (carrier == null) carrier = spawn(level, binding, target);
            if (carrier == null) return false;
            return put(data, hydrateForHot(carrier, binding, cargo));
        }
        if (carrier == null) {
            BlockPos actual = actualPosition(binding);
            if (!level.hasChunkAt(actual)) return put(data, binding.withMode(SourceGrayboxOperationCargoCarrierLedger.Mode.COLD));
            return lossOrConflict(level, data, materializer, binding, 0, "carrier-missing-or-foreign");
        }
        int actual = count(carrier, item(binding.resource()));
        if (actual != binding.observedItems()) {
            if (!applyDelta(level, data, materializer, binding, actual, "carrier-item-change")) return true;
            return true; // next tick must use the accepted immutable source frame
        }
        int expected = expectedItems(cargo);
        if (expected > SourceGrayboxWarehouseRuntime.BARREL_CAPACITY) return loseAndBlock(level, data, materializer, binding, actual, "carrier-capacity-exhausted");
        if (actual != expected) {
            int reconciled = adjust(carrier, item(binding.resource()), expected);
            if (reconciled != expected) return loseAndBlock(level, data, materializer, binding, reconciled, "carrier-write-failure");
            binding = binding.withObservedItems(reconciled);
            put(data, binding);
        }
        if (!zone.hot(carrier.blockPosition()) && zone.safeToDrain(carrier.blockPosition())) {
            SourceGrayboxOperationCargoCarrierLedger.Binding cold = binding.at(sixteenths(carrier.getX()), sixteenths(carrier.getZ()))
                    .withMode(SourceGrayboxOperationCargoCarrierLedger.Mode.COLD);
            drainForCold(carrier, binding);
            return put(data, cold);
        }
        moveToward(carrier, target);
        if (level.getGameTime() % 10L == 0L) return put(data, binding.at(sixteenths(carrier.getX()), sixteenths(carrier.getZ())));
        return false;
    }

    private static boolean retire(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxOperationCargoCarrierLedger.Binding binding) {
        MinecartChest carrier = carrier(level, binding);
        if (carrier != null) {
            drainForCold(carrier, binding);
        }
        boolean changed = data.operationCarrierLedger().remove(binding.id());
        if (changed) data.markOperationCarrierLedgerDirty();
        return changed;
    }

    private static MinecartChest spawn(ServerLevel level, SourceGrayboxOperationCargoCarrierLedger.Binding binding, BlockPos target) {
        MinecartChest carrier = new MinecartChest(level, target.getX() + 0.5d, target.getY(), target.getZ() + 0.5d);
        carrier.setUUID(uuid(binding.id()));
        carrier.setNoGravity(true);
        carrier.setCustomName(net.minecraft.network.chat.Component.literal("[CARGO] " + binding.resource().name() + " · operation " + binding.operationId()));
        carrier.setCustomNameVisible(true);
        carrier.getPersistentData().putString(ENTITY_ID, binding.id());
        carrier.getPersistentData().putString(ENTITY_RESOURCE, binding.resource().name());
        if (!level.addFreshEntity(carrier)) return null;
        return carrier;
    }

    private static boolean applyDelta(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                      SourceGrayboxOperationCargoCarrierLedger.Binding binding, int actual, String reason) {
        int delta = actual - binding.observedItems();
        if (delta == 0) return true;
        ReferenceGrayboxObservationOutcome outcome = observeDelta(data, binding, actual);
        if (!outcome.applied()) return loseAndBlock(level, data, materializer, binding, actual, reason + ":" + outcome.status().name());
        return put(data, binding.withObservedItems(actual));
    }

    /**
     * A missing HOT carrier is a real physical loss, not a cue to reset source
     * cargo. Its exact last observed stack count crosses the normal typed
     * boundary before the executor remains visibly blocked.
     */
    private static boolean lossOrConflict(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                          SourceGrayboxOperationCargoCarrierLedger.Binding binding, int actual, String reason) {
        int delta = actual - binding.observedItems();
        boolean changed = false;
        if (delta != 0) {
            ReferenceGrayboxObservationOutcome outcome = observeDelta(data, binding, actual);
            if (!outcome.applied()) return loseAndBlock(level, data, materializer, binding, actual, reason + ":" + outcome.status().name());
            binding = binding.withObservedItems(actual);
            changed = put(data, binding);
        }
        // A successfully observed loss must still be shown as a conflict so
        // the executor cannot recreate the object Minecraft lost.
        return loseAndBlock(level, data, materializer, binding, actual, reason) || changed;
    }

    private static ReferenceGrayboxObservationOutcome observeDelta(SourceGrayboxSavedData data,
                                                                     SourceGrayboxOperationCargoCarrierLedger.Binding binding,
                                                                     int actual) {
        int delta = actual - binding.observedItems();
        String revision = data.snapshot().stateRevision();
        String eventId = "operation-carrier:" + binding.id() + ":" + revision + ":" + actual;
        return data.observe(new ReferenceGrayboxCargoObservation(
                ReferenceGrayboxCargoObservation.VERSION, eventId, revision, binding.cargoId(), binding.resource(), delta));
    }

    private static boolean loseAndBlock(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                        SourceGrayboxOperationCargoCarrierLedger.Binding binding, int actual, String reason) {
        SourceGrayboxOperationCargoCarrierLedger.Binding blocked = binding.withObservedItems(actual).blocked();
        boolean changed = put(data, blocked);
        materializer.recordContainerConflict(level, "operation-carrier-conflict:" + binding.id() + ":" + reason,
                binding.cargoId(), data.snapshot().stateRevision(), actualPosition(binding), true);
        return changed;
    }

    private static void moveToward(MinecartChest carrier, BlockPos target) {
        Vec3 delta = new Vec3(target.getX() + 0.5d - carrier.getX(), 0.0d, target.getZ() + 0.5d - carrier.getZ());
        double distance = delta.horizontalDistance();
        if (distance <= ARRIVAL) {
            carrier.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 movement = delta.scale(Math.min(SPEED, distance) / distance);
        carrier.move(MoverType.SELF, movement);
        carrier.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * COLD is source custody, not a physical zero.  Hydrating a new HOT cart
     * from the immutable source frame must happen before the first item-delta
     * observation; otherwise the intentionally absent COLD stack looks like
     * a player withdrawal and corrupts canonical cargo on re-entry.
     */
    static SourceGrayboxOperationCargoCarrierLedger.Binding hydrateForHot(
            MinecartChest carrier, SourceGrayboxOperationCargoCarrierLedger.Binding cold,
            ReferenceGrayboxSnapshot.Cargo cargo
    ) {
        if (cold.mode() != SourceGrayboxOperationCargoCarrierLedger.Mode.COLD || !same(cold, cargo)) {
            throw new IllegalArgumentException("operation carrier HOT hydration requires matching COLD custody");
        }
        int expected = expectedItems(cargo);
        if (expected > SourceGrayboxWarehouseRuntime.BARREL_CAPACITY) {
            throw new IllegalArgumentException("operation carrier HOT hydration exceeds capacity");
        }
        int actual = adjust(carrier, item(cold.resource()), expected);
        if (actual != expected) throw new IllegalStateException("operation carrier HOT hydration did not restore canonical cargo");
        return cold.withObservedItems(actual).at(sixteenths(carrier.getX()), sixteenths(carrier.getZ()))
                .withMode(SourceGrayboxOperationCargoCarrierLedger.Mode.HOT);
    }

    static boolean owns(MinecartChest carrier, SourceGrayboxOperationCargoCarrierLedger.Binding binding) {
        return carrier.getUUID().equals(uuid(binding.id())) && carrier.getPersistentData().getString(ENTITY_ID).equals(binding.id())
                && carrier.getPersistentData().getString(ENTITY_RESOURCE).equals(binding.resource().name());
    }

    private static MinecartChest carrier(ServerLevel level, SourceGrayboxOperationCargoCarrierLedger.Binding binding) {
        var entity = level.getEntity(uuid(binding.id()));
        return entity instanceof MinecartChest carrier && owns(carrier, binding) ? carrier : null;
    }

    private static boolean put(SourceGrayboxSavedData data, SourceGrayboxOperationCargoCarrierLedger.Binding binding) {
        boolean changed = data.operationCarrierLedger().binding(binding.id()) == null
                ? data.operationCarrierLedger().put(binding) : data.operationCarrierLedger().replace(binding);
        if (changed) data.markOperationCarrierLedgerDirty();
        return changed;
    }

    private static SourceGrayboxOperationCargoCarrierLedger.Binding binding(ReferenceGrayboxSnapshot.Cargo cargo, int observed,
                                                                              SourceGrayboxOperationCargoCarrierLedger.Mode mode) {
        BlockPos point = position(cargo);
        return new SourceGrayboxOperationCargoCarrierLedger.Binding(bindingId(cargo), cargo.id(), cargo.ownerId(), resource(cargo), observed,
                point.getX() * 16 + 8, point.getZ() * 16 + 8, mode);
    }

    private static Map<String, ReferenceGrayboxSnapshot.Cargo> operationCargoes(ReferenceGrayboxSnapshot snapshot) {
        Map<String, ReferenceGrayboxSnapshot.Cargo> result = new HashMap<>();
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) if (cargo.ownerKind().equals("operation")) {
            if (result.put(cargo.id(), cargo) != null) throw new IllegalStateException("duplicate operation cargo ID: " + cargo.id());
        }
        return Map.copyOf(result);
    }

    private static boolean same(SourceGrayboxOperationCargoCarrierLedger.Binding binding, ReferenceGrayboxSnapshot.Cargo cargo) {
        return binding.cargoId().equals(cargo.id()) && binding.operationId() == cargo.ownerId() && binding.resource() == resource(cargo);
    }

    private static boolean atTarget(SourceGrayboxCargoLedger.Binding binding, BlockPos target) {
        return binding.targetX() == target.getX() && binding.targetY() == target.getY() && binding.targetZ() == target.getZ();
    }

    private static int expectedItems(ReferenceGrayboxSnapshot.Cargo cargo) {
        double raw = cargo.quantity() * ITEMS_PER_SOURCE_UNIT;
        if (!Double.isFinite(raw) || raw < 0.0d || raw > Integer.MAX_VALUE) throw new IllegalStateException("operation carrier quantity is invalid");
        return (int) Math.floor(raw + 1.0e-9d);
    }

    private static ReferenceResource resource(ReferenceGrayboxSnapshot.Cargo cargo) {
        try { return ReferenceResource.valueOf(cargo.resource().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("operation carrier resource is invalid", invalid); }
    }

    private static Item item(ReferenceResource resource) {
        return SourceGrayboxWarehouseRuntime.item(resource);
    }

    private static int count(MinecartChest carrier, Item item) {
        int count = 0;
        for (int slot = 0; slot < carrier.getContainerSize(); slot++) {
            if (carrier.getItem(slot).is(item)) count += carrier.getItem(slot).getCount();
        }
        return count;
    }

    private static int remove(MinecartChest carrier, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < carrier.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = carrier.getItem(slot);
            if (!stack.is(item)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            if (stack.isEmpty()) carrier.setItem(slot, ItemStack.EMPTY);
            remaining -= taken;
        }
        carrier.setChanged();
        return amount - remaining;
    }

    private static int adjust(MinecartChest carrier, Item item, int expected) {
        int actual = count(carrier, item);
        if (actual > expected) remove(carrier, item, actual - expected);
        int remaining = expected - count(carrier, item);
        for (int slot = 0; slot < carrier.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = carrier.getItem(slot);
            if (!stack.isEmpty() && !stack.is(item)) continue;
            int accepted = Math.min(64 - (stack.isEmpty() ? 0 : stack.getCount()), remaining);
            if (accepted == 0) continue;
            if (stack.isEmpty()) carrier.setItem(slot, new ItemStack(item, accepted));
            else stack.grow(accepted);
            remaining -= accepted;
        }
        carrier.setChanged();
        return count(carrier, item);
    }
    /**
     * COLD retains PM-owned cargo in the source ledger. It must never delete a
     * player item sharing the real chest minecart: only the tracked resource
     * leaves, while a mixed cart becomes an ordinary foreign cart.
     */
    static void drainForCold(MinecartChest carrier, SourceGrayboxOperationCargoCarrierLedger.Binding binding) {
        int owned = count(carrier, item(binding.resource()));
        if (owned > 0 && remove(carrier, item(binding.resource()), owned) != owned) {
            throw new IllegalStateException("operation carrier could not release its exact owned resource");
        }
        if (carrier.isEmpty()) carrier.discard();
        else release(carrier);
    }
    private static void release(MinecartChest carrier) {
        carrier.getPersistentData().remove(ENTITY_ID);
        carrier.getPersistentData().remove(ENTITY_RESOURCE);
        carrier.setCustomName(null);
        carrier.setCustomNameVisible(false);
    }

    private static String bindingId(ReferenceGrayboxSnapshot.Cargo cargo) {
        return "operation-carrier:" + cargo.id();
    }

    private static BlockPos position(ReferenceGrayboxSnapshot.Cargo cargo) {
        return new BlockPos(cargo.rectangle().centreX(), ReferenceGrayboxLayout.GROUND_Y + 1, cargo.rectangle().centreZ());
    }

    private static BlockPos position(SourceGrayboxCargoLedger.Binding binding) {
        return new BlockPos(binding.x(), binding.y(), binding.z());
    }

    private static BlockPos actualPosition(SourceGrayboxOperationCargoCarrierLedger.Binding binding) {
        return new BlockPos(Math.floorDiv(binding.actualXSixteenths(), 16), ReferenceGrayboxLayout.GROUND_Y + 1,
                Math.floorDiv(binding.actualZSixteenths(), 16));
    }

    private static int sixteenths(double value) {
        return Math.toIntExact(Math.round(value * 16.0d));
    }

    private static UUID uuid(String id) {
        return UUID.nameUUIDFromBytes(("source-graybox:operation-carrier:" + id).getBytes(StandardCharsets.UTF_8));
    }
}
