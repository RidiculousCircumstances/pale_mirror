package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Read-only entity save observation; only acknowledged recovery metadata is retired. */
final class FrontierV3ActorAdoptionPersistence {
    private static final Map<ServerLevel, Index> INDEXES = new WeakHashMap<>();
    private FrontierV3ActorAdoptionPersistence() { }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        INDEXES.values().removeIf(index -> index.runtime == runtime);
    }

    static void observeWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var index = INDEXES.get(level);
        if (index == null || index.runtime != runtime) {
            index = new Index(runtime); INDEXES.put(level, index);
        }
        index.batch.observe(chunk, data, written, FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()));
    }

    static void completeSavePass(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 boolean complete, Supplier<CompletableFuture<Void>> synchronize) {
        var index = INDEXES.get(level);
        if (index == null || index.runtime != runtime) return;
        var ticket = index.batch.complete(complete, synchronize).orElse(null);
        if (ticket == null) return;
        ticket.saved().whenComplete((ignored, failure) -> level.getServer().execute(() -> {
            if (failure != null || INDEXES.get(level) != index || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
            var state = runtime.decodedState().orElse(null);
            if (state == null) return;
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            int acknowledged = index.batch.acknowledgeSaved(ticket, ledger, (declaration, residence) -> {
                if (!state.actorLocations().containsKey(declaration.actorId())) return false;
                var current = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, declaration.actorId());
                return current.physicalEpoch() == declaration.epoch()
                        && ledger.currentBodyResidence(declaration.actorId(), residence);
            }, binding -> {
                var body = level.getEntity(binding.declaration().entityId());
                // A positive write may finish after vanilla hides or unloads
                // this incarnation. A foreign indexed body still vetoes it;
                // absence alone grants neither reconstruction nor COLD custody.
                return body == null ? ledger.permitsRecordedOwner(binding)
                        : FrontierV3ActorOwnerBinding.from(body).filter(binding::equals).isPresent();
            });
            if (acknowledged > 0) ledger.persist(level, state.bootstrap().worldId());
        }));
    }

    static boolean matchesSaved(FrontierV3ActorAdoption adoption, CompoundTag entity) {
        return adoption.admittedBinding().matchesSaved(entity);
    }
    static boolean matchesSaved(FrontierV3ActorCarrierComposition.Declaration declaration, CompoundTag entity) {
        if (entity == null || !entity.hasUUID("UUID") || !declaration.entityId().equals(entity.getUUID("UUID"))
                || !entity.contains("NeoForgeData", Tag.TAG_COMPOUND)) return false;
        String expectedEntity = FrontierV3ActorCarrierFactory.entityType(declaration.kind());
        if (!expectedEntity.equals(entity.getString("id"))) return false;
        var tag = entity.getCompound("NeoForgeData");
        return declaration.actorId().value().equals(tag.getString(FrontierV3ActorCarrierComposition.ACTOR_KEY))
                && declaration.kind().name().equals(tag.getString(FrontierV3ActorCarrierComposition.KIND_KEY))
                && declaration.owner().name().equals(tag.getString(FrontierV3ActorCarrierComposition.OWNER_KEY))
                && declaration.representation().name().equals(tag.getString(FrontierV3ActorCarrierComposition.REPRESENTATION_KEY))
                && tag.contains(FrontierV3ActorCarrierComposition.REVISION_KEY, Tag.TAG_LONG)
                && tag.contains(FrontierV3ActorCarrierComposition.EPOCH_KEY, Tag.TAG_LONG)
                && declaration.authorityRevision() == tag.getLong(FrontierV3ActorCarrierComposition.REVISION_KEY)
                && declaration.epoch() == tag.getLong(FrontierV3ActorCarrierComposition.EPOCH_KEY);
    }

    /** Server-thread state, separately testable without pretending to save a Minecraft world. */
    static final class Batch {
        private static final int MAX_CANDIDATES = 4_096;
        private final FrontierV3EntitySaveBatch writes = new FrontierV3EntitySaveBatch();
        private final Map<Long, List<SavedCandidate>> candidates = new HashMap<>();
        private boolean overflowed;

        void observe(ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written,
                     FrontierV3AmbientCarrierLedger ledger) {
            writes.record(chunk.toLong(), written);
            candidates.remove(chunk.toLong());
            if (overflowed || writes.overflowed() || !FrontierV3StoredEntityInventory.matchesStoredChunk(data, chunk)) return;
            var entities = FrontierV3StoredEntityInventory.serializedEntities(data).orElse(null);
            if (entities == null) return;
            var transfers = ledger.pendingAdoptions().stream().map(AdoptionCandidate::new).map(value -> (SaveCandidate) value);
            var births = ledger.firstAdmissions().stream()
                    .filter(value -> value.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING)
                    .map(FirstCandidate::new);
            var selected = java.util.stream.Stream.concat(transfers, births)
                    .filter(value -> value.matchesSaved(entities.get(value.declaration().entityId())))
                    .filter(value -> {
                        var tags = entities.get(value.declaration().entityId()).getCompound("NeoForgeData");
                        return tags.contains(FrontierV3ActorBodyController.RESIDENCE_KEY, Tag.TAG_LONG)
                                && tags.getLong(FrontierV3ActorBodyController.RESIDENCE_KEY) > 0L;
                    })
                    .map(value -> new SavedCandidate(value, entities.get(value.declaration().entityId())
                            .getCompound("NeoForgeData").getLong(FrontierV3ActorBodyController.RESIDENCE_KEY))).toList();
            if (selected.size() + candidates.values().stream().mapToInt(List::size).sum() > MAX_CANDIDATES) {
                overflowed = true; candidates.clear(); return;
            }
            if (!selected.isEmpty()) candidates.put(chunk.toLong(), selected);
        }

        Optional<Ticket> complete(boolean complete, Supplier<CompletableFuture<Void>> synchronize) {
            if (overflowed) return Optional.empty();
            var selected = candidates.values().stream().flatMap(List::stream).toList();
            // Two stored appearances are ambiguity, never a successful adoption.
            var counts = new HashMap<SaveCandidate, Integer>();
            selected.forEach(value -> counts.merge(value.receipt(), 1, Integer::sum));
            var unique = selected.stream().filter(value -> counts.get(value.receipt()) == 1).toList();
            return writes.completePass(complete, unique.isEmpty()
                    ? () -> CompletableFuture.completedFuture(null) : synchronize)
                    .map(ticket -> new Ticket(ticket, unique));
        }

        int acknowledge(Ticket ticket, FrontierV3AmbientCarrierLedger ledger,
                        Predicate<FrontierV3ActorCarrierComposition.Declaration> currentBody,
                        Predicate<FrontierV3ActorOwnerBinding> currentOwner) {
            return acknowledgeSaved(ticket, ledger, (declaration, residence) -> currentBody.test(declaration), currentOwner);
        }

        int acknowledgeSaved(Ticket ticket, FrontierV3AmbientCarrierLedger ledger,
                java.util.function.BiPredicate<FrontierV3ActorCarrierComposition.Declaration, Long> currentIncarnation,
                Predicate<FrontierV3ActorOwnerBinding> currentOwner) {
            if (!writes.accept(ticket.write())) return 0;
            candidates.clear();
            int count = 0;
            for (var saved : ticket.candidates()) {
                var candidate = saved.receipt();
                if (currentIncarnation.test(candidate.declaration(), saved.residence()) && candidate.matchesCurrentOwner(currentOwner)
                        && candidate.acknowledge(ledger)) count++;
            }
            return count;
        }
    }

    private sealed interface SaveCandidate permits AdoptionCandidate, FirstCandidate {
        FrontierV3ActorCarrierComposition.Declaration declaration();
        boolean acknowledge(FrontierV3AmbientCarrierLedger ledger);
        default boolean matchesSaved(CompoundTag entity) { return FrontierV3ActorAdoptionPersistence.matchesSaved(declaration(), entity); }
        default boolean matchesCurrentOwner(Predicate<FrontierV3ActorOwnerBinding> currentOwner) { return true; }
    }
    private record FirstCandidate(FrontierV3ActorFirstAdmission first) implements SaveCandidate {
        private FrontierV3ActorOwnerBinding binding() { return first.attempt().orElseThrow(); }
        public FrontierV3ActorCarrierComposition.Declaration declaration() { return binding().declaration(); }
        public boolean matchesSaved(CompoundTag entity) { return binding().matchesSaved(entity); }
        public boolean matchesCurrentOwner(Predicate<FrontierV3ActorOwnerBinding> currentOwner) { return currentOwner.test(binding()); }
        public boolean acknowledge(FrontierV3AmbientCarrierLedger ledger) { return ledger.acknowledgeFirstAdmission(first, binding()); }
    }
    private record AdoptionCandidate(FrontierV3ActorAdoption adoption) implements SaveCandidate {
        public boolean matchesSaved(CompoundTag entity) { return adoption.admittedBinding().matchesSaved(entity); }
        public boolean matchesCurrentOwner(Predicate<FrontierV3ActorOwnerBinding> currentOwner) { return currentOwner.test(adoption.admittedBinding()); }
        public FrontierV3ActorCarrierComposition.Declaration declaration() { return adoption.admitted(); }
        public boolean acknowledge(FrontierV3AmbientCarrierLedger ledger) { return ledger.acknowledgeAdoption(adoption, adoption.admittedBinding()); }
    }
    private record SavedCandidate(SaveCandidate receipt, long residence) { }
    record Ticket(FrontierV3EntitySaveBatch.Ticket write, List<SavedCandidate> candidates) {
        Ticket { candidates = List.copyOf(candidates); }
        CompletableFuture<Void> saved() { return write.saved(); }
    }
    private static final class Index {
        final FrontierV3ServerRuntime<FrontierWorldState, ?> runtime;
        final Batch batch = new Batch();
        Index(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { this.runtime = runtime; }
    }
}
