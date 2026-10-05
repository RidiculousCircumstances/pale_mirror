package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

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

/** Confirms exact actor unload evidence only after vanilla's entity write and storage sync. */
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
        if (complete) confirmRecordedDepartures(level, runtime, synchronize);
    }

    /** Positive proof for actual recorded writes, not a claim that the whole world saved.
     * Natural chunk stores already ran final unload callbacks; they need not await autoSave.
     * Adoption/cleanup absence proofs must not consume this bounded positive batch.
     */
    static void confirmRecordedDepartures(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                          Supplier<CompletableFuture<Void>> synchronize) {
        var index = INDEXES.get(level);
        if (index == null || index.runtime != runtime) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var ticket = index.batch.complete(true, synchronize, ledger).orElse(null);
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
                selectedIndex.batch.acknowledgeBodies(ticket, currentLedger, receipt -> receipt.current(current)
                        && !FrontierV3ActorBodyController.departureReadPending(level, receipt)
                        && level.getEntity(receipt.identity().entityId()) == null,
                        () -> currentLedger.persist(level, current.bootstrap().worldId()));
                selectedIndex.batch.acknowledge(ticket, currentLedger, receipt -> {
                    var lease = current.sceneLeases().get(receipt.leaseId());
                    if (lease == null || lease.revision() != receipt.sceneRevision()
                            || level.getEntity(receipt.carrier().identity().entityId()) != null) return false;
                    SceneMember member = lease.members().stream().filter(value ->
                            value.actorId().equals(receipt.carrier().identity().actorId())
                            && value.entityId().equals(receipt.carrier().identity().entityId())).findFirst().orElse(null);
                    return member != null && FrontierV3SceneDepartureObserver.observedDeparture(current, lease, member, currentLedger)
                            .filter(receipt::equals).isPresent();
                }, receipt -> receipt.current(current)
                        && level.getEntity(receipt.carrier().identity().entityId()) == null,
                        () -> currentLedger.persist(level, current.bootstrap().worldId()));
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
            var ambient = new ArrayList<FrontierV3AmbientDeparture>();
            for (var receipt : ledger.ambientDepartures()) {
                if (ledger.savedAmbientDeparture(receipt)) continue;
                var appearances = saved.getOrDefault(receipt.carrier().identity().entityId(), List.of());
                if (appearances.size() == 1 && appearances.getFirst().matches(receipt)) ambient.add(receipt);
            }
            var bodies = new ArrayList<FrontierV3ActorBodyDeparture>();
            for (var receipt : ledger.bodyDepartures()) {
                if (ledger.savedBodyDeparture(receipt)) continue;
                var appearances = saved.getOrDefault(receipt.identity().entityId(), List.of());
                if (appearances.size() == 1 && appearances.getFirst().matches(receipt)) bodies.add(receipt);
            }
            return writes.completePass(complete, selected.isEmpty() && ambient.isEmpty() && bodies.isEmpty()
                    ? () -> CompletableFuture.completedFuture(null) : synchronize)
                    .map(write -> new Ticket(write, List.copyOf(selected), List.copyOf(ambient), List.copyOf(bodies)));
        }

        boolean acknowledgeBodies(Ticket ticket, FrontierV3AmbientCarrierLedger ledger,
                                   java.util.function.Predicate<FrontierV3ActorBodyDeparture> current, Runnable persist) {
            if (!writes.current(ticket.write()) || !ticket.saved().isDone()
                    || ticket.saved().isCompletedExceptionally()) return false;
            for (var receipt : ticket.bodies()) {
                if (current.test(receipt) && ledger.confirmSavedBodyDeparture(receipt)) persist.run();
            }
            return true;
        }

        boolean acknowledge(Ticket ticket, FrontierV3AmbientCarrierLedger ledger,
                            java.util.function.Predicate<FrontierV3SceneDeparture> currentOwner,
                            java.util.function.Predicate<FrontierV3AmbientDeparture> currentAmbientOwner, Runnable persist) {
            if (!writes.current(ticket.write()) || !ticket.saved().isDone()
                    || ticket.saved().isCompletedExceptionally()) return false;
            for (var receipt : ticket.departures()) {
                if (!currentOwner.test(receipt)) continue;
                if (ledger.confirmSavedDeparture(receipt)) persist.run();
            }
            for (var receipt : ticket.ambientDepartures()) {
                if (currentAmbientOwner.test(receipt) && ledger.confirmSavedAmbientDeparture(receipt)) persist.run();
            }
            if (!writes.accept(ticket.write())) return false;
            candidates.clear(); candidateCount = 0;
            return true;
        }
    }

    record Ticket(FrontierV3EntitySaveBatch.Ticket write, List<FrontierV3SceneDeparture> departures,
                  List<FrontierV3AmbientDeparture> ambientDepartures, List<FrontierV3ActorBodyDeparture> bodies) {
        Ticket { departures = List.copyOf(departures); ambientDepartures = List.copyOf(ambientDepartures); bodies = List.copyOf(bodies); }
        CompletableFuture<Void> saved() { return write.saved(); }
    }

    /** Only bounded primitive evidence is retained; arbitrary entity NBT is never cached. */
    record SavedBody(UUID id, String type, String actor, String kind, String owner, String representation,
                     long revision, long epoch, long residenceGeneration,
                     BodyPosition body, FixedScalar health,
                     Optional<FrontierV3ActorBodyDeparture.HandStack> offhand,
                     Optional<FrontierV3ActorBodyDeparture.HandStack> mainhand) {
        SavedBody(UUID id, String type, String actor, String kind, String owner, String representation,
                  long revision, long epoch, long residenceGeneration,
                  BodyPosition body, FixedScalar health, Optional<FrontierV3ActorBodyDeparture.HandStack> offhand) {
            this(id, type, actor, kind, owner, representation, revision, epoch, residenceGeneration,
                    body, health, offhand, Optional.empty());
        }
        SavedBody(UUID id, String type, String actor, String kind, String owner, String representation,
                  long revision, long epoch, long residenceGeneration,
                  BodyPosition body, FixedScalar health) {
            this(id, type, actor, kind, owner, representation, revision, epoch, residenceGeneration,
                    body, health, Optional.empty(), Optional.empty());
        }

        static Optional<SavedBody> from(CompoundTag entity) {
            if (!entity.hasUUID("UUID") || !entity.contains("id", Tag.TAG_STRING)
                    || !entity.contains("NeoForgeData", Tag.TAG_COMPOUND)
                    || !entity.contains("Pos", Tag.TAG_LIST) || !entity.contains("Health", Tag.TAG_FLOAT)) return Optional.empty();
            var tag = entity.getCompound("NeoForgeData");
            final FrontierV3ActorCarrierComposition.Owner declaredOwner;
            try { declaredOwner = FrontierV3ActorCarrierComposition.Owner.valueOf(
                    tag.getString(FrontierV3ActorCarrierComposition.OWNER_KEY)); }
            catch (IllegalArgumentException missingOrUnknown) { return Optional.empty(); }
            if (!tag.contains(FrontierV3ActorCarrierComposition.REVISION_KEY, Tag.TAG_LONG)
                    || !tag.contains(FrontierV3ActorCarrierComposition.EPOCH_KEY, Tag.TAG_LONG)
                    || !tag.contains(FrontierV3ActorBodyController.RESIDENCE_KEY, Tag.TAG_LONG)
                    || tag.getLong(FrontierV3ActorBodyController.RESIDENCE_KEY) < 1L
                    || tag.getLong(FrontierV3ActorCarrierComposition.REVISION_KEY) != 0L
                    || tag.getLong(FrontierV3ActorCarrierComposition.EPOCH_KEY) < 1L) return Optional.empty();
            ListTag position = entity.getList("Pos", Tag.TAG_DOUBLE);
            if (position.size() != 3 || !position.equals(entity.get("Pos"))) return Optional.empty();
            String type = entity.getString("id"), actor = tag.getString(FrontierV3ActorCarrierComposition.ACTOR_KEY);
            String kind = tag.getString(FrontierV3ActorCarrierComposition.KIND_KEY);
            String owner = tag.getString(FrontierV3ActorCarrierComposition.OWNER_KEY);
            String representation = tag.getString(FrontierV3ActorCarrierComposition.REPRESENTATION_KEY);
            if (type.length() > 64 || actor.isEmpty() || actor.length() > 128 || kind.length() > 32
                    || representation.length() > 32) return Optional.empty();
            double x = position.getDouble(0), y = position.getDouble(1), z = position.getDouble(2);
            float health = entity.getFloat("Health");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(health) || health <= 0.0F) return Optional.empty();
            var observedBody = FrontierV3BodyObservationSave.read(entity, x, y, z);
            if (observedBody.isEmpty()) return Optional.empty();
            Optional<FrontierV3ActorBodyDeparture.HandStack> offhand = Optional.empty();
            Optional<FrontierV3ActorBodyDeparture.HandStack> mainhand = Optional.empty();
            if (entity.contains("HandItems", Tag.TAG_LIST)) {
                ListTag hands = entity.getList("HandItems", Tag.TAG_COMPOUND);
                if (hands.size() != 2) return Optional.empty();
                CompoundTag held = hands.getCompound(1);
                CompoundTag main = hands.getCompound(0);
                if (main.contains("id", Tag.TAG_STRING) && main.contains("count", Tag.TAG_INT)
                        && !main.contains("components")) {
                    try {
                        mainhand = Optional.of(new FrontierV3ActorBodyDeparture.HandStack(main.getString("id"), main.getInt("count")));
                    } catch (IllegalArgumentException invalidHand) { return Optional.empty(); }
                }
                if (held.contains("id", Tag.TAG_STRING) && held.contains("count", Tag.TAG_INT)
                        && !held.contains("components")) {
                    try {
                        offhand = Optional.of(new FrontierV3ActorBodyDeparture.HandStack(
                                held.getString("id"), held.getInt("count")));
                    } catch (IllegalArgumentException invalidHand) { return Optional.empty(); }
                }
            }
            return Optional.of(new SavedBody(entity.getUUID("UUID"), type, actor, kind, owner, representation,
                    tag.getLong(FrontierV3ActorCarrierComposition.REVISION_KEY),
                    tag.getLong(FrontierV3ActorCarrierComposition.EPOCH_KEY),
                    tag.getLong(FrontierV3ActorBodyController.RESIDENCE_KEY),
                    observedBody.orElseThrow(),
                    new FixedScalar(Math.round((double) health * FixedScalar.SCALE)), offhand, mainhand));
        }

        boolean matches(FrontierV3SceneDeparture receipt) {
            var declaration = receipt.carrier().identity();
            return id.equals(declaration.entityId()) && actor.equals(declaration.actorId().value())
                    && kind.equals(declaration.kind().name()) && owner.equals(declaration.owner().name())
                    && representation.equals("LIVE_BODY") && revision == declaration.authorityRevision()
                    && epoch == declaration.epoch() && residenceGeneration == receipt.residenceGeneration()
                    && type.equals(declaration.kind() == ActorKind.RESIDENT
                        ? "minecraft:villager" : "minecraft:zombie")
                    && body.equals(receipt.observed().body()) && health.equals(receipt.observed().health())
                    && (receipt.offhand().isEmpty() || receipt.offhand().equals(offhand))
                    && (receipt.mainhand().isEmpty() || receipt.mainhand().equals(mainhand));
        }
        boolean matches(FrontierV3AmbientDeparture receipt) {
            var declaration = receipt.carrier().identity();
            return id.equals(declaration.entityId()) && actor.equals(declaration.actorId().value())
                    && kind.equals(declaration.kind().name()) && owner.equals(declaration.owner().name())
                    && representation.equals("LIVE_BODY") && revision == declaration.authorityRevision()
                    && epoch == declaration.epoch() && residenceGeneration == receipt.residenceGeneration()
                    && type.equals(declaration.kind() == ActorKind.RESIDENT ? "minecraft:villager" : "minecraft:zombie")
                    && body.equals(receipt.observed().body()) && health.equals(receipt.observed().health());
        }
        boolean matches(FrontierV3ActorBodyDeparture receipt) {
            var declaration = receipt.identity();
            return id.equals(declaration.entityId()) && actor.equals(declaration.actorId().value())
                    && kind.equals(declaration.kind().name()) && owner.equals(declaration.owner().name())
                    && representation.equals("LIVE_BODY") && revision == declaration.authorityRevision()
                    && epoch == declaration.epoch() && residenceGeneration == receipt.residenceGeneration()
                    && type.equals(declaration.kind() == ActorKind.RESIDENT ? "minecraft:villager" : "minecraft:zombie")
                    && body.equals(receipt.observed().body()) && health.equals(receipt.observed().health())
                    && offhand.equals(receipt.offhand()) && mainhand.equals(receipt.mainhand());
        }
    }

    private static final class Index {
        final FrontierV3ServerRuntime<FrontierWorldState, ?> runtime;
        final Batch batch = new Batch();
        Index(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { this.runtime = runtime; }
    }
}
