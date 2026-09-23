package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Exact ordinary entity-write observer. Never requests chunks, writes entities or advances time. */
final class FrontierV3CargoCleanupPersistence {
    private static final Map<ServerLevel, Index> INDEXES = new WeakHashMap<>();
    private static final int MAX_SERIALIZED_ENTITIES = 16_384;
    static final int MAX_ACKNOWLEDGEMENTS_PER_PASS = 8;
    private FrontierV3CargoCleanupPersistence() { }

    static void witnessPublished(ServerLevel level) {
        var index = INDEXES.get(level);
        if (index != null) index.retained = null;
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        INDEXES.values().removeIf(index -> index.runtime == runtime);
    }

    static void observeWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var index = INDEXES.computeIfAbsent(level, ignored -> new Index(runtime));
        if (index.runtime != runtime) { index = new Index(runtime); INDEXES.put(level, index); }
        boolean overflowed = index.batch.overflowed();
        index.batch.record(chunk.toLong(), written);
        index.candidates.remove(chunk.toLong());
        index.coverage.invalidate(chunk.toLong());
        if (!overflowed && index.batch.overflowed()) {
            PaleMirrorMod.LOGGER.error("Cargo cleanup save batch exceeded bounded chunk inventory; acknowledgements retained");
        }
        try { index.refresh(level, state); }
        catch (IOException failure) {
            PaleMirrorMod.LOGGER.error("Cargo cleanup save index unavailable; acknowledgements retained", failure);
            return;
        }
        selectCandidates(level, state, chunk, data, index);
    }

    static CompletableFuture<Optional<CompoundTag>> observeRead(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ChunkPos chunk,
            CompletableFuture<Optional<CompoundTag>> read) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return read;
        var index = INDEXES.computeIfAbsent(level, ignored -> new Index(runtime));
        if (index.runtime != runtime) { index = new Index(runtime); INDEXES.put(level, index); }
        try { index.refresh(level, state); }
        catch (IOException failure) {
            PaleMirrorMod.LOGGER.error("Cargo cleanup read index unavailable; acknowledgements retained", failure);
            return read;
        }
        // A retirement may be committed after this ordinary read. Retain bounded
        // coverage for every observed column, just as for writes; an already-loaded
        // empty column need not be read again when that obligation appears.
        var observed = new CompletableFuture<Void>();
        var ticket = index.batch.recordRead(chunk.toLong(), observed);
        index.candidates.remove(chunk.toLong());
        index.coverage.invalidate(chunk.toLong());
        Index exactIndex = index;
        // Return this dependent stage so vanilla cannot upgrade/mutate the NBT before the copy.
        return preserveReadOutcome(read, observed, data -> {
            var snapshot = data.map(CompoundTag::copy);
            level.getServer().execute(() -> {
                try {
                    if (INDEXES.get(level) == exactIndex && exactIndex.batch.current(ticket)
                            && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
                        var current = runtime.decodedState().orElseThrow();
                        exactIndex.refresh(level, current);
                        selectCandidates(level, current, chunk, snapshot.orElse(null), exactIndex);
                    }
                    observed.complete(null);
                } catch (IOException | RuntimeException failure) {
                    observed.completeExceptionally(failure);
                    PaleMirrorMod.LOGGER.error("Cargo cleanup stored-read observation failed chunk={}", chunk, failure);
                }
            });
        });
    }

    /** Observer failure prevents cleanup, but must not corrupt vanilla's successful load. */
    static <T> CompletableFuture<T> preserveReadOutcome(CompletableFuture<T> read,
            CompletableFuture<Void> observed, Consumer<T> observe) {
        return read.thenApply(data -> {
            try {
                observe.accept(data);
            } catch (RuntimeException failure) {
                observed.completeExceptionally(failure);
                PaleMirrorMod.LOGGER.error("Cargo cleanup read observer failed; original entity read preserved", failure);
            }
            return data;
        }).whenComplete((ignored, failure) -> {
            if (failure != null) observed.completeExceptionally(failure);
        });
    }

    private static void selectCandidates(ServerLevel level, FrontierWorldState state, ChunkPos chunk, CompoundTag data, Index index) {
        index.candidates.remove(chunk.toLong());
        if (!matchesStoredChunk(data, chunk)) return;
        var receipts = index.byChunk.get(chunk.toLong());
        var entities = serializedEntities(data);
        if (entities.isEmpty()) return;
        // Retirements may appear after this observation. Filtering to today's tracked
        // UUIDs would later reinterpret an actually present entity as absent.
        index.coverage.observe(chunk.toLong(), entities.get().keySet());
        var ledger = FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId());
        var candidates = new ArrayList<Candidate>();
        for (var receipt : receipts == null ? List.<FrontierV3CargoDeparture>of() : receipts) {
            var retirement = FrontierV3CargoDepartureObserver.retirement(state, receipt);
            if (retirement != null && retirement.disposition() == CargoProjectionRetirement.Disposition.REMOVE_PROJECTION
                    && !entities.get().containsKey(receipt.entityId()) && level.getEntity(receipt.entityId()) == null
                    && !state.inventory().hasWorldCarrierCustody(receipt.entityId()) && !ledger.conflicted(receipt.entityId())) {
                candidates.add(new RemovedCandidate(retirement, receipt, List.copyOf(index.footprints.getOrDefault(retirement.entityId(), List.of()))));
            }
        }
        for (var entry : entities.get().entrySet()) {
            var retirement = state.fencedRecovery().cargoRetirements().pending().get(entry.getKey());
            if (retirement != null && retirement.disposition() == CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY
                    && savedWithoutSceneDeclaration(entry.getValue())) {
                candidates.add(new RetainedCandidate(retirement, chunk.toLong(),
                        List.copyOf(index.footprints.getOrDefault(retirement.entityId(), List.of()))));
            }
        }
        if (!candidates.isEmpty()) index.candidates.put(chunk.toLong(), List.copyOf(candidates));
    }

    static boolean matchesStoredChunk(CompoundTag data, ChunkPos chunk) {
        if (data == null) return true;
        if (!data.contains("Position", Tag.TAG_INT_ARRAY)) return false;
        int[] position = data.getIntArray("Position");
        return position.length == 2 && position[0] == chunk.x && position[1] == chunk.z;
    }

    static void completeSavePass(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 boolean complete, Supplier<CompletableFuture<Void>> synchronize) {
        var index = INDEXES.get(level);
        if (index == null || index.runtime != runtime) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        try {
            FrontierV3CargoFootprintObserver.flushRemovals(level);
            index.refresh(level, state);
        } catch (IOException failure) {
            PaleMirrorMod.LOGGER.error("Cargo footprint save index unavailable; obligations retained", failure); return;
        }
        compactRecoveredAcknowledgement(level, runtime, index);
        var candidates = new ArrayList<>(index.candidates.values().stream().flatMap(List::stream).toList());
        candidates.replaceAll(candidate -> candidate instanceof RemovedCandidate removed
                ? new RemovedCandidate(removed.retirement(), removed.receipt(), List.copyOf(index.footprints.getOrDefault(removed.retirement().entityId(), List.of())))
                : candidate instanceof RetainedCandidate alive
                ? new RetainedCandidate(alive.retirement(), alive.chunk(), List.copyOf(index.footprints.getOrDefault(alive.retirement().entityId(), List.of())))
                : candidate);
        // Prior observed removal is not durable deletion. A naturally returned cart may
        // supply a new clean surviving-cart proof; later column writes invalidate it normally.
        candidates.removeIf(candidate -> candidate instanceof RetainedCandidate alive
                && (!hasExactFootprint(alive.retirement(), alive.footprints())
                    || !otherColumnsAbsent(index.coverage, alive)));
        candidates.removeIf(candidate -> candidate instanceof RemovedCandidate removed
                && (!hasExactFootprint(removed.retirement(), removed.footprints())
                    || !index.coverage.absent(removed.retirement().entityId(), columns(removed.footprints()))));
        for (var retirement : state.fencedRecovery().cargoRetirements().pending().values()) {
            var footprints = index.footprints.getOrDefault(retirement.entityId(), List.of());
            if (footprints.stream().noneMatch(value -> value.matches(retirement) && value.removalChunk().isPresent())) continue;
            if (index.coverage.absent(retirement.entityId(), columns(footprints)) && level.getEntity(retirement.entityId()) == null) {
                candidates.add(new DestroyedRetainedCandidate(retirement, footprints));
            }
        }
        // No new flush is needed for an observation-only pass without cleanup candidates.
        var ticket = index.batch.completePass(complete, candidates.isEmpty() && !index.coverage.hasPending()
                ? () -> CompletableFuture.completedFuture(null) : synchronize).orElse(null);
        if (ticket == null || candidates.isEmpty()) {
            state.fencedRecovery().cargoRetirements().pending().values().stream()
                    .sorted(Comparator.comparing(CargoProjectionRetirement::entityId)).limit(MAX_ACKNOWLEDGEMENTS_PER_PASS)
                    .forEach(retirement -> PaleMirrorMod.LOGGER.info(
                            "PMV3 cargo cleanup waiting entity={} epoch={} passComplete={} ticket={} candidates={} loaded={} coverage=[{}] batch=[{}]",
                            retirement.entityId(), retirement.authorization().retiredEpoch(), complete, ticket != null, candidates.size(),
                            level.getEntity(retirement.entityId()) != null,
                            index.coverage.diagnostic(retirement.entityId(), columns(index.footprints.getOrDefault(retirement.entityId(), List.of()))),
                            index.batch.diagnostic()));
        }
        if (ticket == null) {
            if (complete && !index.awaitingReadBarrier) {
                index.batch.pendingReadBarrier().ifPresent(barrier -> {
                    index.awaitingReadBarrier = true;
                    barrier.whenComplete((ignored, failure) -> level.getServer().execute(() -> {
                        index.awaitingReadBarrier = false;
                        if (INDEXES.get(level) == index && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
                            // Resume this completed provider pass after its read observations,
                            // rebuilding candidates and fencing the current writes with sync.
                            // A failed read still fails the ordinary save-batch ticket.
                            completeSavePass(level, runtime, true, synchronize);
                        }
                    }));
                });
            }
            return;
        }
        ticket.saved().whenComplete((ignored, failure) -> level.getServer().execute(() -> {
            if (failure != null) {
                PaleMirrorMod.LOGGER.error("Cargo cleanup entity save pass failed; obligations retained", failure);
                return;
            }
            if (INDEXES.get(level) != index || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
            if (!index.batch.current(ticket)) {
                // New provider observations invalidate this proof, not the request to
                // finish the already-completed save pass. Rebuild; never accept stale IO.
                PaleMirrorMod.LOGGER.info("PMV3 cargo cleanup save proof superseded generation={} current=[{}]; rebuilding",
                        ticket.generation(), index.batch.diagnostic());
                completeSavePass(level, runtime, true, synchronize);
                return;
            }
            index.coverage.saved();
            var current = runtime.decodedState().orElseThrow().fencedRecovery().cargoRetirements().pending();
            var remaining = new HashMap<UUID, Candidate>();
            for (var candidate : candidates) {
                if (candidate.retirement().equals(current.get(candidate.retirement().entityId()))) {
                    remaining.put(candidate.retirement().entityId(), candidate);
                }
            }
            var selected = acknowledgementWindow(remaining.keySet(), index.lastAcknowledgementAttempt);
            boolean acknowledged = selected.size() == remaining.size();
            for (UUID id : selected) {
                index.lastAcknowledgementAttempt = id;
                acknowledged &= acknowledge(level, runtime, remaining.get(id));
            }
            // A failed marker write must remain retryable even if vanilla suppresses
            // subsequent empty-chunk writes. Do not drop the successful save evidence.
            if (acknowledged && index.batch.accept(ticket)) index.candidates.clear();
        }));
    }

    /** Deterministic round-robin; failed entries cannot monopolize the bounded IO allowance. */
    static List<UUID> acknowledgementWindow(Collection<UUID> candidates, UUID after) {
        Comparator<UUID> order = Comparator.naturalOrder();
        if (after != null) {
            order = Comparator.<UUID, Boolean>comparing(id -> id.compareTo(after) <= 0)
                    .thenComparing(Comparator.naturalOrder());
        }
        return candidates.stream().distinct().sorted(order).limit(MAX_ACKNOWLEDGEMENTS_PER_PASS).toList();
    }

    /** A later successful flush must never hide failure of this particular write. */
    static CompletableFuture<Void> afterSuccessfulWriteAndSync(CompletableFuture<Void> written,
                                                               Supplier<CompletableFuture<Void>> synchronize) {
        return Objects.requireNonNull(written).thenCompose(ignored -> Objects.requireNonNull(synchronize.get()));
    }

    private static boolean acknowledge(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Candidate candidate) {
        try { FrontierV3CargoFootprintObserver.flushRemovals(level); }
        catch (IOException failure) {
            PaleMirrorMod.LOGGER.error("Cargo removal evidence pending; cleanup acknowledgement deferred", failure);
            return false;
        }
        var state = runtime.decodedState().orElse(null);
        var retirement = candidate.retirement();
        if (state == null) return false;
        if (!retirement.equals(state.fencedRecovery().cargoRetirements().pending().get(retirement.entityId()))) return true;
        if (candidate instanceof DestroyedRetainedCandidate destroyed) {
            if (level.getEntity(retirement.entityId()) != null) return false;
            try {
                if (!markFootprintsSaved(level, retirement, destroyed.footprints())) return false;
                markRetainedPreparationSaved(level, retirement);
                FrontierV3CommandSubmission.submit(runtime, "cargo-cleanup-saved", retirement.entityId().toString(),
                        new FencedRecoveryPayloads.CargoCleanupSaved(retirement));
                var ledger = FrontierV3CargoDepartureLedger.get(level, retirement.worldId());
                ledger.observation(retirement.entityId()).ifPresent(ledger::resolveExact);
                forgetFootprints(level, destroyed.footprints());
                return true;
            } catch (IOException failure) {
                PaleMirrorMod.LOGGER.error("Destroyed cargo footprint changed/unreadable; acknowledgement retained", failure);
                return false;
            }
        }
        if (candidate instanceof RetainedCandidate alive) {
            var entity = level.getEntity(retirement.entityId());
            if (entity != null && (FrontierV3CargoCarrierExecutor.hasDeclaration(entity)
                    || entity.getPersistentData().contains(FrontierV3CargoFootprintObserver.KEY))) return false;
            try {
                if (!markFootprintsSaved(level, retirement, alive.footprints())) return false;
                markRetainedPreparationSaved(level, retirement);
            } catch (IOException failure) {
                PaleMirrorMod.LOGGER.error("Retained cargo footprint save marker failed; obligation retained", failure); return false;
            }
            FrontierV3CommandSubmission.submit(runtime, "cargo-cleanup-saved", retirement.entityId().toString(),
                    new FencedRecoveryPayloads.CargoCleanupSaved(retirement));
            forgetFootprints(level, alive.footprints());
            var ledger = FrontierV3CargoDepartureLedger.get(level, retirement.worldId());
            ledger.observation(retirement.entityId()).ifPresent(ledger::resolveExact);
            return true;
        }
        var removed = (RemovedCandidate) candidate;
        if (level.getEntity(retirement.entityId()) != null || state.inventory().hasWorldCarrierCustody(retirement.entityId())) return false;
        var archive = FrontierV3CargoCleanupArchive.at(level, retirement.worldId());
        try {
            if (!markFootprintsSaved(level, retirement, removed.footprints())) return false;
            archive.markSaved(removed.receipt());
        } catch (IOException failure) {
            PaleMirrorMod.LOGGER.error("Cargo cleanup save confirmation could not be retained entity={}", retirement.entityId(), failure);
            return false;
        }
        FrontierV3CommandSubmission.submit(runtime, "cargo-cleanup-saved", retirement.entityId().toString(),
                new FencedRecoveryPayloads.CargoCleanupSaved(retirement));
        forgetFootprints(level, removed.footprints());
        // submit only returns after its DURABLE_BEFORE_EFFECT WAL transaction has completed.
        try {
            archive.forgetAcknowledged(removed.receipt());
            FrontierV3CargoDepartureLedger.get(level, retirement.worldId()).resolveExact(removed.receipt());
        } catch (IOException failure) {
            var index = INDEXES.get(level);
            if (index != null && index.runtime == runtime && !index.savedRecovery.contains(removed.receipt())) {
                index.savedRecovery.addLast(removed.receipt());
            }
            PaleMirrorMod.LOGGER.error("Cargo cleanup acknowledged but witness compaction failed entity={}", retirement.entityId(), failure);
        }
        return true;
    }

    private static Set<Long> columns(List<FrontierV3CargoRetirementFootprint> footprints) {
        return footprints.stream().flatMap(value -> value.chunks().stream()).collect(java.util.stream.Collectors.toSet());
    }

    /** A rejected REMOVE release may have staged evidence before a later player handoff. */
    private static void markRetainedPreparationSaved(ServerLevel level, CargoProjectionRetirement retirement) throws IOException {
        var archive = FrontierV3CargoCleanupArchive.at(level, retirement.worldId());
        var prepared = archive.observation(retirement.entityId());
        if (prepared.isEmpty()) return;
        var receipt = prepared.orElseThrow();
        if (!receipt.leaseId().equals(retirement.leaseId()) || !receipt.cargoId().equals(retirement.cargoId())
                || receipt.sceneRevision() != retirement.authorization().ownerRevision()
                || receipt.authorityEpoch() > retirement.authorization().retiredEpoch()) {
            throw new IOException("retained cargo has foreign preparation evidence");
        }
        // This marks only obsolete metadata after physical save, never authority to remove
        // the player-owned cart or restore its former contents. Ack remains WAL-first.
        archive.markSaved(receipt);
        var index = INDEXES.get(level);
        if (index != null && !index.savedRecovery.contains(receipt)) index.savedRecovery.addLast(receipt);
    }

    private static boolean otherColumnsAbsent(FrontierV3CargoFootprintCoverage coverage, RetainedCandidate candidate) {
        var columns = columns(candidate.footprints()); columns.remove(candidate.chunk());
        return columns.isEmpty() || coverage.absent(candidate.retirement().entityId(), columns);
    }

    /** Missing birth history is unknown coverage, never evidence of an empty history. */
    static boolean hasExactFootprint(CargoProjectionRetirement retirement,
            List<FrontierV3CargoRetirementFootprint> footprints) {
        return footprints.stream().anyMatch(value -> value.matchesIdentity(retirement));
    }

    private static boolean markFootprintsSaved(ServerLevel level, CargoProjectionRetirement retirement,
            List<FrontierV3CargoRetirementFootprint> expected) throws IOException {
        if (!hasExactFootprint(retirement, expected)) return false;
        var archive = FrontierV3CargoFootprintArchive.at(level, retirement.worldId());
        var current = archive.inventory().stream().filter(value -> value.entity().equals(retirement.entityId())).toList();
        if (!current.equals(expected)) return false;
        for (var footprint : current) archive.markSaved(footprint);
        return true;
    }

    private static void forgetFootprints(ServerLevel level, List<FrontierV3CargoRetirementFootprint> footprints) {
        for (var footprint : footprints) {
            try { FrontierV3CargoFootprintArchive.at(level, footprint.world()).forgetExact(footprint); }
            catch (IOException failure) {
                var index = INDEXES.get(level);
                if (index != null && !index.savedFootprints.contains(footprint)) index.savedFootprints.addLast(footprint);
                PaleMirrorMod.LOGGER.error("Cargo footprint acknowledged but compaction failed entity={}", footprint.entity(), failure);
            }
        }
    }

    /** One metadata compaction per ordinary save pass, never an entity or canonical mutation. */
    private static void compactRecoveredAcknowledgement(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Index index) {
        if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        if (!index.savedFootprints.isEmpty()) {
            var footprint = index.savedFootprints.removeFirst();
            var lease = state.sceneLeases().get(footprint.lease());
            if (!state.fencedRecovery().cargoRetirements().pending().containsKey(footprint.entity())
                    && (lease == null || lease.status() == SceneLeaseStatus.CLOSED)) {
                try { FrontierV3CargoFootprintArchive.at(level, footprint.world()).forgetExact(footprint); }
                catch (IOException failure) {
                    index.savedFootprints.addLast(footprint);
                    PaleMirrorMod.LOGGER.error("Recovered cargo footprint compaction failed entity={}", footprint.entity(), failure);
                }
            }
            return;
        }
        if (index.savedRecovery.isEmpty()) return;
        var receipt = index.savedRecovery.removeFirst();
        // A crash before the durable acknowledgement leaves the exact obligation alive.
        // Its normal read/write proof path must finish it; this marker cannot waive that.
        if (state.fencedRecovery().cargoRetirements().pending().containsKey(receipt.entityId())) return;
        try {
            FrontierV3CargoCleanupArchive.at(level, state.bootstrap().worldId()).forgetAcknowledged(receipt);
            FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId()).resolveExact(receipt);
        } catch (IOException failure) {
            index.savedRecovery.addLast(receipt);
            PaleMirrorMod.LOGGER.error("Recovered cargo acknowledgement compaction failed entity={}", receipt.entityId(), failure);
        }
    }

    /** Includes nested passengers; malformed or over-budget data is unknown, never absence. */
    static Optional<Set<UUID>> serializedEntityIds(CompoundTag data) {
        return serializedEntities(data).map(Map::keySet);
    }

    static boolean savedWithoutSceneDeclaration(CompoundTag entity) {
        if (!entity.getString("id").equals("minecraft:chest_minecart")) return false;
        if (!entity.contains("NeoForgeData")) return true;
        if (!entity.contains("NeoForgeData", Tag.TAG_COMPOUND)) return false;
        var tag = entity.getCompound("NeoForgeData");
        return !tag.contains(FrontierV3CargoCarrierExecutor.LEASE_KEY)
                && !tag.contains(FrontierV3CargoCarrierExecutor.CARGO_KEY)
                && !tag.contains(FrontierV3CargoCarrierExecutor.REVISION_KEY)
                && !tag.contains(FrontierV3CargoCarrierExecutor.EPOCH_KEY)
                && !tag.contains(FrontierV3CargoFootprintObserver.KEY);
    }

    static Optional<Map<UUID, CompoundTag>> serializedEntities(CompoundTag data) {
        if (data == null) return Optional.of(Map.of());
        if (!data.contains("Entities", Tag.TAG_LIST)) return Optional.empty();
        var queue = new ArrayDeque<CompoundTag>();
        var root = data.getList("Entities", Tag.TAG_COMPOUND);
        if (!data.getList("Entities", Tag.TAG_COMPOUND).equals(data.get("Entities"))) return Optional.empty();
        if (root.size() > MAX_SERIALIZED_ENTITIES) return Optional.empty();
        root.forEach(value -> queue.add((CompoundTag) value));
        var entities = new HashMap<UUID, CompoundTag>(); int visited = 0;
        while (!queue.isEmpty()) {
            if (++visited > MAX_SERIALIZED_ENTITIES) return Optional.empty();
            var entity = queue.removeFirst();
            if (!entity.hasUUID("UUID")) return Optional.empty();
            if (entities.putIfAbsent(entity.getUUID("UUID"), entity) != null) return Optional.empty();
            if (entity.contains("Passengers")) {
                if (!entity.contains("Passengers", Tag.TAG_LIST)) return Optional.empty();
                var passengers = entity.getList("Passengers", Tag.TAG_COMPOUND);
                if (!passengers.equals(entity.get("Passengers")) || queue.size() + passengers.size() > MAX_SERIALIZED_ENTITIES) return Optional.empty();
                passengers.forEach(value -> queue.add((CompoundTag) value));
            }
        }
        return Optional.of(Map.copyOf(entities));
    }

    private sealed interface Candidate permits RemovedCandidate, RetainedCandidate, DestroyedRetainedCandidate {
        CargoProjectionRetirement retirement();
    }
    private record RemovedCandidate(CargoProjectionRetirement retirement, FrontierV3CargoDeparture receipt,
            List<FrontierV3CargoRetirementFootprint> footprints) implements Candidate { }
    private record RetainedCandidate(CargoProjectionRetirement retirement, long chunk,
            List<FrontierV3CargoRetirementFootprint> footprints) implements Candidate { }
    private record DestroyedRetainedCandidate(CargoProjectionRetirement retirement,
            List<FrontierV3CargoRetirementFootprint> footprints) implements Candidate { }

    private static final class Index {
        final FrontierV3ServerRuntime<FrontierWorldState, ?> runtime;
        CargoProjectionRetirements retained;
        final Map<UUID, FrontierV3CargoDeparture> known = new HashMap<>();
        final Map<Long, List<FrontierV3CargoDeparture>> byChunk = new HashMap<>();
        final FrontierV3EntitySaveBatch batch = new FrontierV3EntitySaveBatch();
        final Map<Long, List<Candidate>> candidates = new HashMap<>();
        final FrontierV3CargoFootprintCoverage coverage = new FrontierV3CargoFootprintCoverage();
        final Map<UUID, List<FrontierV3CargoRetirementFootprint>> footprints = new HashMap<>();
        final ArrayDeque<FrontierV3CargoDeparture> savedRecovery = new ArrayDeque<>();
        final ArrayDeque<FrontierV3CargoRetirementFootprint> savedFootprints = new ArrayDeque<>();
        boolean recoveryIndexed;
        boolean awaitingReadBarrier;
        UUID lastAcknowledgementAttempt;
        Index(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { this.runtime = runtime; }

        void refresh(ServerLevel level, FrontierWorldState state) throws IOException {
            var next = state.fencedRecovery().cargoRetirements();
            if (retained == next) return;
            footprints.clear();
            for (var footprint : FrontierV3CargoFootprintArchive.at(level, state.bootstrap().worldId()).inventory()) {
                if (next.pending().containsKey(footprint.entity())) {
                    footprints.computeIfAbsent(footprint.entity(), ignored -> new ArrayList<>()).add(footprint);
                }
            }
            known.keySet().retainAll(next.pending().keySet());
            var archive = FrontierV3CargoCleanupArchive.at(level, state.bootstrap().worldId());
            if (!recoveryIndexed) {
                savedRecovery.addAll(archive.savedWitnesses());
                savedFootprints.addAll(FrontierV3CargoFootprintArchive.at(level, state.bootstrap().worldId()).savedInventory());
                recoveryIndexed = true;
            }
            for (UUID id : next.pending().keySet()) {
                if (!known.containsKey(id)) archive.observation(id).ifPresent(receipt -> known.put(id, receipt));
            }
            byChunk.clear();
            for (var receipt : known.values()) {
                long chunk = ChunkPos.asLong(Math.floorDiv(receipt.body().x(), 16), Math.floorDiv(receipt.body().z(), 16));
                byChunk.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(receipt);
            }
            retained = next;
        }
    }
}
