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
            if (binding.state() != SourceGrayboxCargoLedger.State.ACTIVE || !level.hasChunkAt(position(binding))) continue;
            ReferenceGrayboxSnapshot.Cargo cargo = cargoes.get(binding.cargoId());
            // A source day can consume or retire a cargo descriptor after the
            // inbound pass that made the preceding player change canonical.
            // This is not a foreign obstruction: materialize() owns the
            // controlled retirement below, where it can retain mixed player
            // contents instead of permanently blocking an otherwise empty
            // PM barrel.
            if (cargo == null) continue;
            if (!sameBinding(binding, cargo, position(binding))) {
                changed |= block(level, data, materializer, binding, "cargo-retired-or-layout-mismatch");
                continue;
            }
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
        BlockPos position = position(cargo);
        if (!level.hasChunkAt(position)) return;
        String id = bindingId(cargo);
        SourceGrayboxCargoLedger.Binding binding = data.cargoLedger().binding(id);
        if (binding != null && !sameBinding(binding, cargo, position)) {
            throw new IllegalStateException("source graybox cargo binding disagrees with its source layout: " + id);
        }
        if (binding != null && binding.state() == SourceGrayboxCargoLedger.State.BLOCKED) {
            recordConflict(level, materializer, binding, revision, false, "retained-foreign-obstruction");
            return;
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
        int target = expectedItems(cargo);
        if (target > SourceGrayboxWarehouseRuntime.BARREL_CAPACITY) {
            target = SourceGrayboxWarehouseRuntime.BARREL_CAPACITY;
            recordConflict(level, materializer, active, revision, true, "capacity-exhausted");
        }
        reconcileProjectedItems(level, data, materializer, active, target, revision);
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
        recordConflict(level, materializer, blocked, data.snapshot().stateRevision(), true, reason);
        return changed;
    }

    private static void recordConflict(ServerLevel level, SourceGrayboxMaterializer materializer, SourceGrayboxCargoLedger.Binding binding,
                                       String revision, boolean installed, String reason) {
        materializer.recordContainerConflict(level, "cargo-conflict:" + binding.id() + ":" + reason, binding.cargoId(), revision,
                position(binding), installed);
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

    private static boolean sameBinding(SourceGrayboxCargoLedger.Binding binding, ReferenceGrayboxSnapshot.Cargo cargo, BlockPos position) {
        return binding.cargoId().equals(cargo.id()) && binding.ownerKind().equals(cargo.ownerKind()) && binding.ownerId() == cargo.ownerId()
                && binding.resource() == resource(cargo) && binding.x() == position.getX() && binding.y() == position.getY()
                && binding.z() == position.getZ();
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

    private static BlockPos position(ReferenceGrayboxSnapshot.Cargo cargo) {
        return new BlockPos(cargo.rectangle().centreX(), ReferenceGrayboxLayout.GROUND_Y + 1, cargo.rectangle().centreZ());
    }
}
