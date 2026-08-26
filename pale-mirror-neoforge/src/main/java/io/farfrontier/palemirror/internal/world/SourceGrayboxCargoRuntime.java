package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxCargoObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Real field-cargo containers and their exact reverse observation path.
 *
 * <p>Each immutable cargo descriptor owns one PM-tagged barrel placed on its
 * palette pallet. Ordinary item movement becomes an exact operation or field
 * post receipt. A foreign or destroyed barrel is visibly blocked and is never
 * adopted or rebuilt over player state.</p>
 */
final class SourceGrayboxCargoRuntime {
    private static final int ITEMS_PER_SOURCE_UNIT = ReferenceGrayboxCargoObservation.ITEMS_PER_SOURCE_UNIT;

    boolean reconcileInbound(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer) {
        Map<String, ReferenceGrayboxSnapshot.Cargo> cargoes = byId(data.snapshot());
        boolean changed = false;
        for (SourceGrayboxCargoLedger.Binding binding : data.cargoLedger().bindings()) {
            ReferenceGrayboxSnapshot.Cargo cargo = cargoes.get(binding.cargoId());
            // A source day can consume or retire a cargo descriptor after the
            // inbound pass that made the preceding player change canonical.
            // This is not a foreign obstruction: materialize() owns the
            // controlled retirement below, where it can retain mixed player
            // contents instead of permanently blocking an otherwise empty
            // PM barrel.
            if (cargo == null || binding.state() == SourceGrayboxCargoLedger.State.BLOCKED) continue;
            if (!sameCargo(binding, cargo)) {
                changed |= block(level, data, materializer, binding, "cargo-owner-or-resource-mismatch");
                continue;
            }
            BlockPos target = position(cargo);
            if (binding.state() == SourceGrayboxCargoLedger.State.ACTIVE && !atTarget(binding, target)) {
                binding = relocate(data, binding, target);
                changed = true;
                recordRelocationPending(level, materializer, binding, data.snapshot().stateRevision());
            } else if (binding.state() == SourceGrayboxCargoLedger.State.RELOCATING && !atTarget(binding, target)) {
                binding = retarget(data, binding, target);
                changed = true;
                recordRelocationPending(level, materializer, binding, data.snapshot().stateRevision());
            }
            if (!level.hasChunkAt(position(binding))) continue;
            BlockEntity entity = level.getBlockEntity(position(binding));
            if (!(entity instanceof BarrelBlockEntity barrel) || !SourceGrayboxWarehouseRuntime.matches(barrel, binding.id(), binding.resource())) {
                changed |= lossOrConflict(level, data, materializer, binding, 0, "container-missing-or-foreign");
                continue;
            }
            int actual = SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()));
            if (actual != binding.observedItems()) {
                changed |= applyDelta(level, data, materializer, binding, actual, "container-item-change");
            }
        }
        return changed;
    }

    void materialize(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer) {
        ReferenceGrayboxSnapshot snapshot = data.snapshot();
        Set<String> activeCargoIds = new HashSet<>();
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) {
            activeCargoIds.add(cargo.id());
            materialize(level, data, materializer, cargo, snapshot.stateRevision());
        }
        retireAbsent(level, data, materializer, activeCargoIds, snapshot.stateRevision());
    }

    private static void materialize(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                    ReferenceGrayboxSnapshot.Cargo cargo, String revision) {
        BlockPos target = position(cargo);
        String id = bindingId(cargo);
        SourceGrayboxCargoLedger.Binding binding = data.cargoLedger().binding(id);
        if (binding != null && !sameCargo(binding, cargo)) {
            block(level, data, materializer, binding, "cargo-owner-or-resource-mismatch");
            return;
        }
        if (binding != null && binding.state() == SourceGrayboxCargoLedger.State.BLOCKED) {
            recordConflict(level, materializer, binding, revision, false, "retained-foreign-obstruction");
            return;
        }
        if (binding != null && binding.state() == SourceGrayboxCargoLedger.State.ACTIVE && !atTarget(binding, target)) {
            binding = relocate(data, binding, target);
            recordRelocationPending(level, materializer, binding, revision);
        } else if (binding != null && binding.state() == SourceGrayboxCargoLedger.State.RELOCATING && !atTarget(binding, target)) {
            binding = retarget(data, binding, target);
            recordRelocationPending(level, materializer, binding, revision);
        }
        if (binding != null && binding.state() == SourceGrayboxCargoLedger.State.RELOCATING) {
            binding = completeRelocation(level, data, materializer, binding, revision);
            if (binding == null) return;
        }
        if (binding != null) materializer.clearContainerRelocationPending(level, relocationPendingId(binding));
        materializeAtTarget(level, data, materializer, cargo, revision, binding);
    }

    /** Installs/reconciles a barrel only after it is the sole retained physical custody point. */
    private static void materializeAtTarget(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                            ReferenceGrayboxSnapshot.Cargo cargo, String revision,
                                            SourceGrayboxCargoLedger.Binding binding) {
        BlockPos position = position(cargo);
        if (!level.hasChunkAt(position)) return;
        String id = bindingId(cargo);
        if (binding != null && !atTarget(binding, position)) {
            throw new IllegalStateException("settled source graybox cargo binding has not reached its target: " + id);
        }
        BarrelBlockEntity barrel = SourceGrayboxWarehouseRuntime.ensureContainer(level, position, id, resource(cargo));
        if (barrel == null) {
            SourceGrayboxCargoLedger.Binding blocked = binding == null ? binding(id, cargo, position, 0, SourceGrayboxCargoLedger.State.BLOCKED)
                    : binding.blocked();
            if (data.cargoLedger().put(blocked)) data.markCargoLedgerDirty();
            recordConflict(level, materializer, blocked, revision, false, "foreign-obstruction");
            return;
        }
        int actual = SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(resource(cargo)));
        SourceGrayboxCargoLedger.Binding active = binding == null ? binding(id, cargo, position, actual, SourceGrayboxCargoLedger.State.ACTIVE) : binding;
        if (binding == null && data.cargoLedger().put(active)) data.markCargoLedgerDirty();
        int expected = expectedItems(cargo);
        if (expected > SourceGrayboxWarehouseRuntime.BARREL_CAPACITY) {
            expected = SourceGrayboxWarehouseRuntime.BARREL_CAPACITY;
            recordConflict(level, materializer, active, revision, true, "capacity-exhausted");
        }
        reconcileProjectedItems(level, data, materializer, active, expected, revision);
    }

    /**
     * Moves custody, never just its map marker.  Both endpoints must already
     * be loaded: no ticket is acquired for an operation's former position, and
     * no second barrel is created while its exact old contents could still be
     * opened by a player.
     */
    static SourceGrayboxCargoLedger.Binding completeRelocation(
            ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
            SourceGrayboxCargoLedger.Binding binding, String revision
    ) {
        BlockPos oldPosition = position(binding);
        BlockPos target = targetPosition(binding);
        if (oldPosition.equals(target)) {
            SourceGrayboxCargoLedger.Binding arrived = binding.arrived();
            if (data.cargoLedger().replace(arrived)) data.markCargoLedgerDirty();
            materializer.clearContainerRelocationPending(level, relocationPendingId(binding));
            return arrived;
        }
        if (!level.hasChunkAt(oldPosition) || !level.hasChunkAt(target)) {
            recordRelocationPending(level, materializer, binding, revision);
            return null;
        }
        BlockEntity entity = level.getBlockEntity(oldPosition);
        if (!(entity instanceof BarrelBlockEntity barrel) || !SourceGrayboxWarehouseRuntime.matches(barrel, binding.id(), binding.resource())) {
            lossOrConflict(level, data, materializer, binding, 0, "relocation-source-missing-or-foreign");
            return null;
        }
        int carried = SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()));
        if (carried != binding.observedItems()) {
            // The accepted source receipt may alter the cargo projection or
            // retire it outright. Publish that new immutable source frame
            // before attempting a physical move rather than moving a stale
            // quantity to the new position.
            applyDelta(level, data, materializer, binding, carried, "relocation-source-item-change");
            return null;
        }
        if (!releaseOldCustody(level, materializer, binding, barrel, carried, revision)) return null;
        SourceGrayboxCargoLedger.Binding arrived = binding.arrived();
        if (data.cargoLedger().replace(arrived)) data.markCargoLedgerDirty();
        materializer.clearContainerRelocationPending(level, relocationPendingId(binding));
        return arrived;
    }

    /** Removes only the tracked resource; a mixed barrel becomes an ordinary player barrel in place. */
    private static boolean releaseOldCustody(ServerLevel level, SourceGrayboxMaterializer materializer,
                                             SourceGrayboxCargoLedger.Binding binding, BarrelBlockEntity barrel,
                                             int carried, String revision) {
        if (carried > 0 && SourceGrayboxWarehouseRuntime.remove(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()), carried) != carried) {
            recordConflict(level, materializer, binding, revision, true, "relocation-source-write-failure");
            return false;
        }
        BlockPos oldPosition = position(binding);
        if (barrel.isEmpty()) {
            if (level.setBlock(oldPosition, Blocks.AIR.defaultBlockState(), 3)) return true;
            if (carried > 0 && SourceGrayboxWarehouseRuntime.insert(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()), carried) != carried) {
                throw new IllegalStateException("source graybox cargo could not restore failed relocation custody");
            }
            recordConflict(level, materializer, binding, revision, true, "relocation-source-clear-failure");
            return false;
        }
        if (!SourceGrayboxWarehouseRuntime.releaseContainer(barrel, binding.id(), binding.resource())) {
            throw new IllegalStateException("source graybox cargo lost ownership while relocating: " + binding.id());
        }
        return true;
    }

    private static void reconcileProjectedItems(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                                SourceGrayboxCargoLedger.Binding binding, int target, String revision) {
        BlockEntity entity = level.getBlockEntity(position(binding));
        if (!(entity instanceof BarrelBlockEntity barrel) || !SourceGrayboxWarehouseRuntime.matches(barrel, binding.id(), binding.resource())) {
            lossOrConflict(level, data, materializer, binding, 0, "container-lost-during-projection");
            return;
        }
        int actual = SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()));
        int remaining = target - actual;
        if (remaining > 0) remaining -= SourceGrayboxWarehouseRuntime.insert(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()), remaining);
        if (remaining < 0) remaining += SourceGrayboxWarehouseRuntime.remove(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()), -remaining);
        if (remaining != 0) recordConflict(level, materializer, binding, revision, true, "container-capacity-or-write-failure");
        int observed = SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()));
        if (data.cargoLedger().replace(binding.withObservedItems(observed))) data.markCargoLedgerDirty();
        barrel.setChanged();
    }

    private static void retireAbsent(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                     Set<String> activeCargoIds, String revision) {
        for (SourceGrayboxCargoLedger.Binding binding : data.cargoLedger().bindings()) {
            if (activeCargoIds.contains(binding.cargoId()) || binding.state() == SourceGrayboxCargoLedger.State.BLOCKED
                    || !level.hasChunkAt(position(binding))) continue;
            BlockEntity entity = level.getBlockEntity(position(binding));
            if (!(entity instanceof BarrelBlockEntity barrel) || !SourceGrayboxWarehouseRuntime.matches(barrel, binding.id(), binding.resource())) {
                block(level, data, materializer, binding, "retired-container-missing-or-foreign");
                continue;
            }
            int carried = SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()));
            if (carried > 0 && SourceGrayboxWarehouseRuntime.remove(barrel, SourceGrayboxWarehouseRuntime.item(binding.resource()), carried) != carried) {
                block(level, data, materializer, binding, "retired-container-write-failure");
                continue;
            }
            if (!barrel.isEmpty() || !level.setBlock(position(binding), Blocks.AIR.defaultBlockState(), 3)) {
                block(level, data, materializer, binding, "retired-container-has-foreign-items");
                continue;
            }
            if (data.cargoLedger().remove(binding.id())) data.markCargoLedgerDirty();
            materializer.clearContainerRelocationPending(level, relocationPendingId(binding));
        }
    }

    private static boolean applyDelta(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                      SourceGrayboxCargoLedger.Binding binding, int actual, String reason) {
        int delta = actual - binding.observedItems();
        if (delta == 0) return false;
        ReferenceGrayboxSnapshot snapshot = data.snapshot();
        String eventId = "cargo:" + binding.id() + ":" + snapshot.stateRevision() + ":" + actual;
        ReferenceGrayboxObservationOutcome outcome = data.observe(new ReferenceGrayboxCargoObservation(
                ReferenceGrayboxCargoObservation.VERSION, eventId, snapshot.stateRevision(), binding.cargoId(), binding.resource(), delta));
        if (!outcome.applied()) {
            SourceGrayboxCargoLedger.Binding blocked = binding.withObservedItems(actual).blocked();
            if (data.cargoLedger().replace(blocked)) data.markCargoLedgerDirty();
            recordConflict(level, materializer, blocked, snapshot.stateRevision(), true, reason + ":" + outcome.status().name().toLowerCase(Locale.ROOT));
            return false;
        }
        if (data.cargoLedger().replace(binding.withObservedItems(actual))) data.markCargoLedgerDirty();
        return true;
    }

    private static boolean lossOrConflict(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                          SourceGrayboxCargoLedger.Binding binding, int actual, String reason) {
        if (binding.state() == SourceGrayboxCargoLedger.State.BLOCKED) return false;
        boolean applied = applyDelta(level, data, materializer, binding, actual, reason);
        block(level, data, materializer, binding.withObservedItems(actual), reason);
        return applied;
    }

    private static boolean block(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                 SourceGrayboxCargoLedger.Binding binding, String reason) {
        SourceGrayboxCargoLedger.Binding blocked = binding.blocked();
        boolean changed = data.cargoLedger().replace(blocked);
        if (changed) data.markCargoLedgerDirty();
        materializer.clearContainerRelocationPending(level, relocationPendingId(binding));
        recordConflict(level, materializer, blocked, data.snapshot().stateRevision(), true, reason);
        return changed;
    }

    private static void recordConflict(ServerLevel level, SourceGrayboxMaterializer materializer, SourceGrayboxCargoLedger.Binding binding,
                                       String revision, boolean installed, String reason) {
        materializer.recordContainerConflict(level, "cargo-conflict:" + binding.id() + ":" + reason, binding.cargoId(), revision,
                position(binding), installed);
    }

    private static void recordRelocationPending(ServerLevel level, SourceGrayboxMaterializer materializer,
                                                SourceGrayboxCargoLedger.Binding binding, String revision) {
        materializer.recordContainerRelocationPending(level, "cargo-relocation:" + binding.id(), binding.cargoId(), revision,
                targetPosition(binding));
    }

    private static String relocationPendingId(SourceGrayboxCargoLedger.Binding binding) {
        return "cargo-relocation:" + binding.id();
    }

    private static Map<String, ReferenceGrayboxSnapshot.Cargo> byId(ReferenceGrayboxSnapshot snapshot) {
        Map<String, ReferenceGrayboxSnapshot.Cargo> result = new HashMap<>();
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) {
            if (result.putIfAbsent(cargo.id(), cargo) != null) throw new IllegalStateException("duplicate source cargo ID: " + cargo.id());
        }
        return Map.copyOf(result);
    }

    private static SourceGrayboxCargoLedger.Binding binding(String id, ReferenceGrayboxSnapshot.Cargo cargo, BlockPos position,
                                                            int observedItems, SourceGrayboxCargoLedger.State state) {
        return new SourceGrayboxCargoLedger.Binding(id, cargo.id(), cargo.ownerKind(), cargo.ownerId(), resource(cargo),
                position.getX(), position.getY(), position.getZ(), observedItems, state);
    }

    private static SourceGrayboxCargoLedger.Binding relocate(SourceGrayboxSavedData data, SourceGrayboxCargoLedger.Binding binding,
                                                              BlockPos target) {
        SourceGrayboxCargoLedger.Binding relocating = binding.relocating(target.getX(), target.getY(), target.getZ());
        if (data.cargoLedger().replace(relocating)) data.markCargoLedgerDirty();
        return relocating;
    }

    private static SourceGrayboxCargoLedger.Binding retarget(SourceGrayboxSavedData data, SourceGrayboxCargoLedger.Binding binding,
                                                              BlockPos target) {
        SourceGrayboxCargoLedger.Binding relocating = binding.retarget(target.getX(), target.getY(), target.getZ());
        if (data.cargoLedger().replace(relocating)) data.markCargoLedgerDirty();
        return relocating;
    }

    private static boolean sameCargo(SourceGrayboxCargoLedger.Binding binding, ReferenceGrayboxSnapshot.Cargo cargo) {
        return binding.cargoId().equals(cargo.id()) && binding.ownerKind().equals(cargo.ownerKind()) && binding.ownerId() == cargo.ownerId()
                && binding.resource() == resource(cargo);
    }

    private static boolean atTarget(SourceGrayboxCargoLedger.Binding binding, BlockPos position) {
        return binding.targetX() == position.getX() && binding.targetY() == position.getY() && binding.targetZ() == position.getZ();
    }

    private static int expectedItems(ReferenceGrayboxSnapshot.Cargo cargo) {
        double raw = cargo.quantity() * ITEMS_PER_SOURCE_UNIT;
        if (!Double.isFinite(raw) || raw < 0.0d || raw > Integer.MAX_VALUE) {
            throw new IllegalStateException("graybox cargo item quantity is invalid");
        }
        return (int) Math.floor(raw + 1.0e-9d);
    }

    private static io.farfrontier.palemirror.frontier.reference.ReferenceResource resource(ReferenceGrayboxSnapshot.Cargo cargo) {
        try {
            return io.farfrontier.palemirror.frontier.reference.ReferenceResource.valueOf(cargo.resource().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("graybox cargo has an unknown resource: " + cargo.resource(), invalid);
        }
    }

    private static String bindingId(ReferenceGrayboxSnapshot.Cargo cargo) {
        return "cargo-container:" + cargo.id();
    }

    private static BlockPos position(SourceGrayboxCargoLedger.Binding binding) {
        return new BlockPos(binding.x(), binding.y(), binding.z());
    }

    private static BlockPos targetPosition(SourceGrayboxCargoLedger.Binding binding) {
        return new BlockPos(binding.targetX(), binding.targetY(), binding.targetZ());
    }

    private static BlockPos position(ReferenceGrayboxSnapshot.Cargo cargo) {
        return new BlockPos(cargo.rectangle().centreX(), ReferenceGrayboxLayout.GROUND_Y + 1, cargo.rectangle().centreZ());
    }
}
