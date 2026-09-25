package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Confirms an unloaded cargo cart only after its exact entity-region write and sync. */
final class FrontierV3CargoDeparturePersistence {
    private static final Map<ServerLevel, Index> INDEXES = new WeakHashMap<>();
    private static final int MAX_CANDIDATES = FrontierV3CargoDepartureLedger.MAX_ENTRIES;
    private FrontierV3CargoDeparturePersistence() { }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        INDEXES.values().removeIf(index -> index.runtime == runtime);
    }

    static void observeWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var index = INDEXES.get(level);
        if (index == null || index.runtime != runtime) {
            index = new Index(runtime);
            INDEXES.put(level, index);
        }
        index.batch.observe(chunk, data, written, state, level.registryAccess());
    }

    static void completeSavePass(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 boolean complete, Supplier<CompletableFuture<Void>> synchronize) {
        var index = INDEXES.get(level);
        if (index == null || index.runtime != runtime) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var ledger = FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId());
        var ticket = index.batch.complete(complete, synchronize, ledger).orElse(null);
        if (ticket == null) return;
        var selectedIndex = index;
        ticket.saved().whenComplete((ignored, failure) -> level.getServer().execute(() -> {
            if (failure != null) {
                PaleMirrorMod.LOGGER.error("Cargo departure entity save failed; release remains pending", failure);
                return;
            }
            if (INDEXES.get(level) != selectedIndex || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
            var current = runtime.decodedState().orElse(null);
            if (current == null) return;
            var currentLedger = FrontierV3CargoDepartureLedger.get(level, current.bootstrap().worldId());
            try {
                selectedIndex.batch.acknowledge(ticket, currentLedger, receipt -> {
                    var lease = current.sceneLeases().get(receipt.leaseId());
                    return lease != null && level.getEntity(receipt.entityId()) == null
                            && FrontierV3CargoCarrierExecutor.currentDeparture(current, lease, receipt, level.registryAccess());
                }, () -> currentLedger.persist(level, current.bootstrap().worldId()));
            } catch (RuntimeException failedPublication) {
                PaleMirrorMod.LOGGER.error("Cargo departure save proof could not be published; release remains pending", failedPublication);
            }
        }));
    }

    /** Selection is made before vanilla removes the entity on unload. */
    static final class Batch {
        private final FrontierV3EntitySaveBatch writes = new FrontierV3EntitySaveBatch();
        private final Map<Long, List<SavedCart>> candidates = new HashMap<>();
        private int count;
        private boolean overflowed;

        void observe(ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written,
                     FrontierWorldState state, HolderLookup.Provider registries) {
            writes.record(chunk.toLong(), written);
            var replaced = candidates.remove(chunk.toLong());
            if (replaced != null) count -= replaced.size();
            if (overflowed || writes.overflowed() || !FrontierV3CargoCleanupPersistence.matchesStoredChunk(data, chunk)) return;
            var entities = FrontierV3CargoCleanupPersistence.serializedEntities(data).orElse(null);
            if (entities == null) return;
            var selected = new ArrayList<SavedCart>();
            for (var entity : entities.values()) {
                var cart = SavedCart.from(entity, registries).orElse(null);
                if (cart == null) continue;
                var lease = state.sceneLeases().get(cart.leaseId());
                if (lease != null && FrontierSceneBehaviors.isLogistics(lease)
                        && lease.revision() == cart.revision()
                        && FrontierV3CargoCarrierExecutor.id(lease).equals(cart.id())) selected.add(cart);
            }
            if (selected.size() + count > MAX_CANDIDATES) {
                overflowed = true; candidates.clear(); count = 0; return;
            }
            if (!selected.isEmpty()) {
                candidates.put(chunk.toLong(), List.copyOf(selected)); count += selected.size();
            }
        }

        Optional<Ticket> complete(boolean complete, Supplier<CompletableFuture<Void>> synchronize,
                                  FrontierV3CargoDepartureLedger ledger) {
            if (overflowed) return Optional.empty();
            var saved = new HashMap<UUID, List<SavedCart>>();
            candidates.values().stream().flatMap(List::stream)
                    .forEach(cart -> saved.computeIfAbsent(cart.id(), ignored -> new ArrayList<>()).add(cart));
            var selected = new ArrayList<FrontierV3CargoDeparture>();
            for (var receipt : ledger.observations()) {
                if (ledger.savedObservation(receipt) || ledger.conflicted(receipt.entityId())) continue;
                var appearances = saved.getOrDefault(receipt.entityId(), List.of());
                if (appearances.size() == 1 && appearances.getFirst().matches(receipt)) selected.add(receipt);
            }
            return writes.completePass(complete, selected.isEmpty()
                    ? () -> CompletableFuture.completedFuture(null) : synchronize)
                    .map(write -> new Ticket(write, List.copyOf(selected)));
        }

        boolean acknowledge(Ticket ticket, FrontierV3CargoDepartureLedger ledger,
                            java.util.function.Predicate<FrontierV3CargoDeparture> currentOwner, Runnable persist) {
            if (!writes.current(ticket.write()) || !ticket.saved().isDone()
                    || ticket.saved().isCompletedExceptionally()) return false;
            for (var receipt : ticket.departures()) {
                if (!currentOwner.test(receipt)) continue;
                if (ledger.confirmSavedObservation(receipt)) persist.run();
            }
            if (!writes.accept(ticket.write())) return false;
            candidates.clear(); count = 0;
            return true;
        }
    }

    record Ticket(FrontierV3EntitySaveBatch.Ticket write, List<FrontierV3CargoDeparture> departures) {
        Ticket { departures = List.copyOf(departures); }
        CompletableFuture<Void> saved() { return write.saved(); }
    }

    /** Bounded, normalized inventory evidence; arbitrary entity NBT is never retained. */
    record SavedCart(UUID id, SceneLeaseId leaseId, SubjectId cargoId, long revision, long epoch,
                     BodyPosition body, List<CompoundTag> inventory) {
        SavedCart { inventory = inventory.stream().map(CompoundTag::copy).toList(); }

        static Optional<SavedCart> from(CompoundTag entity, HolderLookup.Provider registries) {
            if (!entity.hasUUID("UUID") || !entity.getString("id").equals("minecraft:chest_minecart")
                    || !entity.contains("NeoForgeData", Tag.TAG_COMPOUND)
                    || !entity.contains("Pos", Tag.TAG_LIST) || !entity.contains("Items", Tag.TAG_LIST)
                    || entity.contains("LootTable")) return Optional.empty();
            var owner = entity.getCompound("NeoForgeData");
            if (!owner.contains(FrontierV3CargoCarrierExecutor.LEASE_KEY, Tag.TAG_STRING)
                    || !owner.contains(FrontierV3CargoCarrierExecutor.CARGO_KEY, Tag.TAG_STRING)
                    || !owner.contains(FrontierV3CargoCarrierExecutor.REVISION_KEY, Tag.TAG_LONG)
                    || !owner.contains(FrontierV3CargoCarrierExecutor.EPOCH_KEY, Tag.TAG_LONG)) return Optional.empty();
            var lease = owner.getString(FrontierV3CargoCarrierExecutor.LEASE_KEY);
            var cargo = owner.getString(FrontierV3CargoCarrierExecutor.CARGO_KEY);
            if (lease.isEmpty() || lease.length() > 128 || cargo.isEmpty() || cargo.length() > 128) return Optional.empty();
            var position = entity.getList("Pos", Tag.TAG_DOUBLE);
            var items = entity.getList("Items", Tag.TAG_COMPOUND);
            if (position.size() != 3 || !position.equals(entity.get("Pos"))
                    || items.size() > FrontierV3CargoDeparture.SLOTS || !items.equals(entity.get("Items"))) return Optional.empty();
            double x = position.getDouble(0), y = position.getDouble(1), z = position.getDouble(2);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Optional.empty();
            var slots = new ArrayList<CompoundTag>(FrontierV3CargoDeparture.SLOTS);
            for (int slot = 0; slot < FrontierV3CargoDeparture.SLOTS; slot++) slots.add(new CompoundTag());
            var occupied = new boolean[FrontierV3CargoDeparture.SLOTS];
            try {
                for (var entry : items) {
                    var row = (CompoundTag) entry;
                    if (!row.contains("Slot", Tag.TAG_BYTE)) return Optional.empty();
                    int slot = row.getByte("Slot") & 255;
                    if (slot >= FrontierV3CargoDeparture.SLOTS || occupied[slot]) return Optional.empty();
                    occupied[slot] = true;
                    var stack = ItemStack.parseOptional(registries, row);
                    if (stack.isEmpty()) return Optional.empty();
                    slots.set(slot, (CompoundTag) stack.saveOptional(registries));
                }
                return Optional.of(new SavedCart(entity.getUUID("UUID"), new SceneLeaseId(lease), new SubjectId(cargo),
                        owner.getLong(FrontierV3CargoCarrierExecutor.REVISION_KEY),
                        owner.getLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY),
                        new BodyPosition((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)), slots));
            } catch (RuntimeException malformed) { return Optional.empty(); }
        }

        boolean matches(FrontierV3CargoDeparture receipt) {
            return id.equals(receipt.entityId()) && leaseId.equals(receipt.leaseId())
                    && cargoId.equals(receipt.cargoId()) && revision == receipt.sceneRevision()
                    && epoch == receipt.authorityEpoch() && body.equals(receipt.body())
                    && inventory.equals(receipt.inventory());
        }
    }

    private static final class Index {
        final FrontierV3ServerRuntime<FrontierWorldState, ?> runtime;
        final Batch batch = new Batch();
        Index(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { this.runtime = runtime; }
    }
}
