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
            index.batch.acknowledge(ticket, ledger, declaration ->
                    FrontierV3ActorCarrierComposition.owns(level.getEntity(declaration.entityId()), declaration), binding -> {
                var body = level.getEntity(binding.declaration().entityId());
                return body != null && FrontierV3ActorOwnerBinding.from(body).filter(binding::equals).isPresent();
            });
        }));
    }

    static boolean matchesSaved(FrontierV3ActorAdoption adoption, CompoundTag entity) {
        return adoption.admittedBinding().matchesSaved(entity);
    }
    static boolean matchesSaved(FrontierV3ActorCarrierComposition.Declaration declaration, CompoundTag entity) {
        if (entity == null || !entity.hasUUID("UUID") || !declaration.entityId().equals(entity.getUUID("UUID"))
                || !entity.contains("NeoForgeData", Tag.TAG_COMPOUND)) return false;
        String expectedEntity = switch (declaration.kind()) {
            case RESIDENT -> "minecraft:villager";
            case BIOFORM -> "minecraft:zombie";
        };
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
        private final Map<Long, List<SaveCandidate>> candidates = new HashMap<>();
        private boolean overflowed;

        void observe(ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written,
                     FrontierV3AmbientCarrierLedger ledger) {
            writes.record(chunk.toLong(), written);
            candidates.remove(chunk.toLong());
            if (overflowed || writes.overflowed() || !FrontierV3CargoCleanupPersistence.matchesStoredChunk(data, chunk)) return;
            var entities = FrontierV3CargoCleanupPersistence.serializedEntities(data).orElse(null);
            if (entities == null) return;
            var transfers = java.util.stream.Stream.concat(
                    ledger.pendingAdoptions().stream().filter(value -> ledger.pendingHandoff(value.admitted().actorId()).isEmpty())
                        .map(AdoptionCandidate::new),
                    ledger.pendingHandoffs().stream().map(HandoffCandidate::new)).map(value -> (SaveCandidate) value);
            var births = ledger.firstAdmissions().stream()
                    .filter(value -> value.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING
                            && ledger.pendingHandoff(value.identity().actorId()).isEmpty())
                    .map(FirstCandidate::new);
            var selected = java.util.stream.Stream.concat(transfers, births)
                    .filter(value -> value.matchesSaved(entities.get(value.declaration().entityId()))).toList();
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
            selected.forEach(value -> counts.merge(value, 1, Integer::sum));
            var unique = selected.stream().filter(value -> counts.get(value) == 1).toList();
            return writes.completePass(complete, unique.isEmpty()
                    ? () -> CompletableFuture.completedFuture(null) : synchronize)
                    .map(ticket -> new Ticket(ticket, unique));
        }

        int acknowledge(Ticket ticket, FrontierV3AmbientCarrierLedger ledger,
                        Predicate<FrontierV3ActorCarrierComposition.Declaration> currentBody,
                        Predicate<FrontierV3ActorOwnerBinding> currentOwner) {
            if (!writes.accept(ticket.write())) return 0;
            candidates.clear();
            int count = 0;
            for (var candidate : ticket.candidates()) {
                if (currentBody.test(candidate.declaration()) && candidate.matchesCurrentOwner(currentOwner)
                        && candidate.acknowledge(ledger)) count++;
            }
            return count;
        }
    }

    private sealed interface SaveCandidate permits AdoptionCandidate, HandoffCandidate, FirstCandidate {
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
    private record HandoffCandidate(FrontierV3ActorHandoff handoff) implements SaveCandidate {
        public boolean matchesSaved(CompoundTag entity) { return handoff.currentBinding().matchesSaved(entity); }
        public boolean matchesCurrentOwner(Predicate<FrontierV3ActorOwnerBinding> currentOwner) { return currentOwner.test(handoff.currentBinding()); }
        public FrontierV3ActorCarrierComposition.Declaration declaration() { return handoff.current(); }
        public boolean acknowledge(FrontierV3AmbientCarrierLedger ledger) { return ledger.acknowledgeHandoff(handoff, declaration()); }
    }
    record Ticket(FrontierV3EntitySaveBatch.Ticket write, List<SaveCandidate> candidates) {
        Ticket { candidates = List.copyOf(candidates); }
        CompletableFuture<Void> saved() { return write.saved(); }
    }
    private static final class Index {
        final FrontierV3ServerRuntime<FrontierWorldState, ?> runtime;
        final Batch batch = new Batch();
        Index(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { this.runtime = runtime; }
    }
}
