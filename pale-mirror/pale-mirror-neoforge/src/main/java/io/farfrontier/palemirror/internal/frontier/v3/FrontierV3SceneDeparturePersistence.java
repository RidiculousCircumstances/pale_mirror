package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
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

/** Confirms a final scene unload only after vanilla's exact entity write and storage sync. */
final class FrontierV3SceneDeparturePersistence {
    private static final Map<ServerLevel, Index> INDEXES = new WeakHashMap<>();
    private static final int MAX_CANDIDATES = 4_096;
    private FrontierV3SceneDeparturePersistence() { }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        INDEXES.values().removeIf(index -> index.runtime == runtime);
    }

    static void observeWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written) {
        var index = INDEXES.get(level);
        if (index == null || index.runtime != runtime) {
            index = new Index(runtime);
            INDEXES.put(level, index);
        }
        index.batch.observe(chunk, data, written);
    }

    static void completeSavePass(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 boolean complete, Supplier<CompletableFuture<Void>> synchronize) {
        var index = INDEXES.get(level);
        if (index == null || index.runtime != runtime) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var ticket = index.batch.complete(complete, synchronize, ledger).orElse(null);
        if (ticket == null) return;
        var selectedIndex = index;
        ticket.saved().whenComplete((ignored, failure) -> level.getServer().execute(() -> {
            if (failure != null) {
                PaleMirrorMod.LOGGER.error("Scene departure entity save failed; release remains pending", failure);
                return;
            }
            if (INDEXES.get(level) != selectedIndex || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
            var current = runtime.decodedState().orElse(null);
            if (current == null) return;
            var currentLedger = FrontierV3AmbientCarrierLedger.get(level, current.bootstrap().worldId());
            try {
                selectedIndex.batch.acknowledge(ticket, currentLedger, receipt -> {
                    var lease = current.sceneLeases().get(receipt.leaseId());
                    if (lease == null || lease.revision() != receipt.sceneRevision()
                            || level.getEntity(receipt.carrier().identity().entityId()) != null) return false;
                    SceneMember member = lease.members().stream().filter(value ->
                            value.actorId().equals(receipt.carrier().identity().actorId())
                            && value.entityId().equals(receipt.carrier().identity().entityId())).findFirst().orElse(null);
                    return member != null && FrontierV3SceneDepartureObserver.observedDeparture(current, lease, member, currentLedger)
                            .filter(receipt::equals).isPresent();
                }, () -> currentLedger.persist(level, current.bootstrap().worldId()));
            } catch (RuntimeException failedPublication) {
                PaleMirrorMod.LOGGER.error("Scene departure save proof could not be published; release remains pending", failedPublication);
            }
        }));
    }

    /** Server-thread batch, selected at the write before vanilla calls setRemoved. */
    static final class Batch {
        private final FrontierV3EntitySaveBatch writes = new FrontierV3EntitySaveBatch();
        private final Map<Long, List<SavedBody>> candidates = new HashMap<>();
        private int candidateCount;
        private boolean overflowed;

        void observe(ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written) {
            writes.record(chunk.toLong(), written);
            var replaced = candidates.remove(chunk.toLong());
            if (replaced != null) candidateCount -= replaced.size();
            if (overflowed || writes.overflowed() || !FrontierV3CargoCleanupPersistence.matchesStoredChunk(data, chunk)) return;
            var entities = FrontierV3CargoCleanupPersistence.serializedEntities(data).orElse(null);
            if (entities == null) return;
            var selected = new ArrayList<SavedBody>();
            for (var entity : entities.values()) {
                SavedBody.from(entity).ifPresent(selected::add);
            }
            if (selected.size() + candidateCount > MAX_CANDIDATES) {
                overflowed = true;
                candidates.clear(); candidateCount = 0;
                return;
            }
            if (!selected.isEmpty()) {
                candidates.put(chunk.toLong(), List.copyOf(selected));
                candidateCount += selected.size();
            }
        }

        Optional<Ticket> complete(boolean complete, Supplier<CompletableFuture<Void>> synchronize,
                                  FrontierV3AmbientCarrierLedger ledger) {
            if (overflowed) return Optional.empty();
            var saved = new HashMap<UUID, List<SavedBody>>();
            candidates.values().stream().flatMap(List::stream)
                    .forEach(body -> saved.computeIfAbsent(body.id(), ignored -> new ArrayList<>()).add(body));
            var selected = new ArrayList<FrontierV3SceneDeparture>();
            for (var receipt : ledger.departures()) {
                if (ledger.savedDeparture(receipt)) continue;
                var appearances = saved.getOrDefault(receipt.carrier().identity().entityId(), List.of());
                if (appearances.size() == 1 && appearances.getFirst().matches(receipt)) selected.add(receipt);
            }
            return writes.completePass(complete, selected.isEmpty()
                    ? () -> CompletableFuture.completedFuture(null) : synchronize)
                    .map(write -> new Ticket(write, List.copyOf(selected)));
        }

        boolean acknowledge(Ticket ticket, FrontierV3AmbientCarrierLedger ledger,
                            java.util.function.Predicate<FrontierV3SceneDeparture> currentOwner, Runnable persist) {
            if (!writes.current(ticket.write()) || !ticket.saved().isDone()
                    || ticket.saved().isCompletedExceptionally()) return false;
            for (var receipt : ticket.departures()) {
                if (!currentOwner.test(receipt)) continue;
                if (ledger.confirmSavedDeparture(receipt)) persist.run();
            }
            if (!writes.accept(ticket.write())) return false;
            candidates.clear(); candidateCount = 0;
            return true;
        }
    }

    record Ticket(FrontierV3EntitySaveBatch.Ticket write, List<FrontierV3SceneDeparture> departures) {
        Ticket { departures = List.copyOf(departures); }
        CompletableFuture<Void> saved() { return write.saved(); }
    }

    /** Only bounded primitive evidence is retained; arbitrary entity NBT is never cached. */
    record SavedBody(UUID id, String type, String actor, String kind, String owner, String representation,
                     long revision, long epoch, String lease, long sceneRevision,
                     BodyPosition body, FixedScalar health,
                     Optional<FrontierV3SceneDeparture.HandStack> offhand) {
        SavedBody(UUID id, String type, String actor, String kind, String owner, String representation,
                  long revision, long epoch, String lease, long sceneRevision,
                  BodyPosition body, FixedScalar health) {
            this(id, type, actor, kind, owner, representation, revision, epoch, lease, sceneRevision,
                    body, health, Optional.empty());
        }

        static Optional<SavedBody> from(CompoundTag entity) {
            if (!entity.hasUUID("UUID") || !entity.contains("id", Tag.TAG_STRING)
                    || !entity.contains("NeoForgeData", Tag.TAG_COMPOUND)
                    || !entity.contains("Pos", Tag.TAG_LIST) || !entity.contains("Health", Tag.TAG_FLOAT)) return Optional.empty();
            var tag = entity.getCompound("NeoForgeData");
            if (!tag.getString(FrontierV3ActorCarrierComposition.OWNER_KEY).equals("SCENE_LEASE")
                    || !tag.contains(FrontierV3ActorCarrierComposition.REVISION_KEY, Tag.TAG_LONG)
                    || !tag.contains(FrontierV3ActorCarrierComposition.EPOCH_KEY, Tag.TAG_LONG)
                    || !tag.contains(FrontierV3SceneExecutor.REVISION_KEY, Tag.TAG_LONG)) return Optional.empty();
            ListTag position = entity.getList("Pos", Tag.TAG_DOUBLE);
            if (position.size() != 3 || !position.equals(entity.get("Pos"))) return Optional.empty();
            String type = entity.getString("id"), actor = tag.getString(FrontierV3ActorCarrierComposition.ACTOR_KEY);
            String kind = tag.getString(FrontierV3ActorCarrierComposition.KIND_KEY);
            String owner = tag.getString(FrontierV3ActorCarrierComposition.OWNER_KEY);
            String representation = tag.getString(FrontierV3ActorCarrierComposition.REPRESENTATION_KEY);
            String lease = tag.getString(FrontierV3SceneExecutor.LEASE_KEY);
            if (type.length() > 64 || actor.isEmpty() || actor.length() > 128 || kind.length() > 32
                    || representation.length() > 32 || lease.isEmpty() || lease.length() > 128) return Optional.empty();
            double x = position.getDouble(0), y = position.getDouble(1), z = position.getDouble(2);
            float health = entity.getFloat("Health");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(health) || health <= 0.0F) return Optional.empty();
            Optional<FrontierV3SceneDeparture.HandStack> offhand = Optional.empty();
            if (entity.contains("HandItems", Tag.TAG_LIST)) {
                ListTag hands = entity.getList("HandItems", Tag.TAG_COMPOUND);
                if (hands.size() != 2) return Optional.empty();
                CompoundTag held = hands.getCompound(1);
                if (held.contains("id", Tag.TAG_STRING) && held.contains("count", Tag.TAG_INT)
                        && !held.contains("components")) {
                    try {
                        offhand = Optional.of(new FrontierV3SceneDeparture.HandStack(
                                held.getString("id"), held.getInt("count")));
                    } catch (IllegalArgumentException invalidHand) { return Optional.empty(); }
                }
            }
            return Optional.of(new SavedBody(entity.getUUID("UUID"), type, actor, kind, owner, representation,
                    tag.getLong(FrontierV3ActorCarrierComposition.REVISION_KEY),
                    tag.getLong(FrontierV3ActorCarrierComposition.EPOCH_KEY), lease,
                    tag.getLong(FrontierV3SceneExecutor.REVISION_KEY),
                    new BodyPosition((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)),
                    new FixedScalar(Math.round((double) health * FixedScalar.SCALE)), offhand));
        }

        boolean matches(FrontierV3SceneDeparture receipt) {
            var declaration = receipt.carrier().identity();
            return id.equals(declaration.entityId()) && actor.equals(declaration.actorId().value())
                    && kind.equals(declaration.kind().name()) && owner.equals("SCENE_LEASE")
                    && representation.equals("LIVE_BODY") && revision == receipt.sceneRevision()
                    && epoch == declaration.epoch() && lease.equals(receipt.leaseId().value())
                    && sceneRevision == receipt.sceneRevision()
                    && type.equals(declaration.kind() == FrontierV3ActorCarrierComposition.ActorKind.RESIDENT
                        ? "minecraft:villager" : "minecraft:zombie")
                    && body.equals(receipt.observed().body()) && health.equals(receipt.observed().health())
                    && (receipt.offhand().isEmpty() || receipt.offhand().equals(offhand));
        }
    }

    private static final class Index {
        final FrontierV3ServerRuntime<FrontierWorldState, ?> runtime;
        final Batch batch = new Batch();
        Index(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { this.runtime = runtime; }
    }
}
