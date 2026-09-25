package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** No-demand release requires one complete loaded/saved scene set, including cargo. */
final class FrontierV3SceneStoredRecovery {
    private static final int MAX_ATTEMPTS = 64;
    private static final int MAX_SCANS_PER_LEASE = 2;
    private static final long MIN_REARM_TICKS = 20L;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, Attempt>> ATTEMPTS = new IdentityHashMap<>();
    /** A killed JVM has no unload callback, even when vanilla flushed its still-loaded body. */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, UnobservedAttempt>> UNOBSERVED = new IdentityHashMap<>();

    private FrontierV3SceneStoredRecovery() { }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        ATTEMPTS.remove(runtime);
        UNOBSERVED.remove(runtime);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, Attempt> attempts = ATTEMPTS.get(runtime);
        if (attempts != null) {
            attempts.remove(leaseId);
            if (attempts.isEmpty()) ATTEMPTS.remove(runtime);
        }
        Map<SceneLeaseId, UnobservedAttempt> unobserved = UNOBSERVED.get(runtime);
        if (unobserved != null) {
            unobserved.remove(leaseId);
            if (unobserved.isEmpty()) UNOBSERVED.remove(runtime);
        }
    }

    static void progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                         FrontierWorldState state, SceneLease lease) {
        if (lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART) return;
        Map<SceneLeaseId, Attempt> attempts = ATTEMPTS.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        attempts.keySet().removeIf(id -> {
            SceneLease current = state.sceneLeases().get(id);
            return current == null || current.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART;
        });
        Attempt attempt = attempts.get(lease.id());
        if (attempt != null && attempt.decided()) {
            Receipts current = currentReceipts(level, state, lease);
            if (current == null) {
                progressWithoutUnloadCallback(level, runtime, state, lease);
                return;
            }
            Optional<Map<ChunkPos, Long>> epochs = targetWriteEpochs(level, current);
            if (changedEvidenceAfterCooldown(attempt.receipts(), attempt.writeEpochs(), attempt.startedAtTick(),
                    current, epochs, level.getGameTime())) {
                attempts.put(lease.id(), new Attempt(lease, current, inspect(level, current), 1,
                        epochs, level.getGameTime()));
            }
            return;
        }
        if (attempt == null) {
            if (attempts.size() >= MAX_ATTEMPTS || attempts.values().stream().anyMatch(value -> !value.future().isDone())) return;
            Receipts receipts = currentReceipts(level, state, lease);
            if (receipts == null) {
                progressWithoutUnloadCallback(level, runtime, state, lease);
                return;
            }
            discardUnobserved(runtime, lease.id());
            attempt = new Attempt(lease, receipts, inspect(level, receipts), 1,
                    targetWriteEpochs(level, receipts), level.getGameTime());
            attempts.put(lease.id(), attempt);
            return;
        }
        if (!attempt.future().isDone() || attempt.decided()) return;
        Proof proof;
        try { proof = attempt.future().join(); }
        catch (CompletionException | IllegalStateException failure) {
            if (retry(level, attempts, attempt, state, lease)) return;
            attempt.decide();
            PaleMirrorMod.LOGGER.warn("PMV3_SCENE_STORED_RECOVERY unresolved lease={} reason={}", lease.id().value(), failure.toString());
            return;
        }
        FrontierWorldState current = runtime.decodedState().orElse(null);
        SceneLease retained = current == null ? null : current.sceneLeases().get(lease.id());
        if (retained != null && retained.equals(attempt.lease()) && !proof.current(level)
                && retry(level, attempts, attempt, current, retained)) return;
        attempt.decide();
        if (retained == null || !retained.equals(attempt.lease()) || !proof.current(level)
                || !proof.matches(attempt.receipts())
                || !attempt.receipts().equals(currentReceipts(level, current, retained))) {
            PaleMirrorMod.LOGGER.warn("PMV3_SCENE_STORED_RECOVERY unresolved lease={} reason=stale-or-incomplete-member-evidence",
                    lease.id().value());
            return;
        }
        // The exact synced entity-region census/snapshot is stronger than the ordinary
        // save-callback marker. A graceful stop can persist the real 19-HP body while the
        // callback's batch missed a late unload receipt. Only after this fresh whole-scene
        // disk proof may the source owners publish the actor and optional cargo saved markers
        // consumed by generic release. A receipt or elapsed time alone never grants permission.
        var ledger = FrontierV3AmbientCarrierLedger.get(level, current.bootstrap().worldId());
        if (attempt.receipts().actors().values().stream().anyMatch(receipt -> !ledger.noLoadProofCandidate(receipt))) return;
        var cargoLedger = FrontierV3CargoDepartureLedger.get(level, current.bootstrap().worldId());
        if (attempt.receipts().cargo().filter(receipt -> !cargoLedger.noLoadProofCandidate(receipt)).isPresent()) return;
        for (var receipt : attempt.receipts().actors().values()) {
            if (!ledger.confirmSavedDeparture(receipt)) return;
        }
        ledger.persist(level, current.bootstrap().worldId());
        if (attempt.receipts().cargo().isPresent()) {
            if (!cargoLedger.confirmSavedObservation(attempt.receipts().cargo().orElseThrow())) return;
            cargoLedger.persist(level, current.bootstrap().worldId());
        }
        // The proof, latest entity-write/read stamps and canonical owner are checked on the
        // server thread immediately before this WAL transition. Generic release then consumes
        // the same saved departures, preserves exact cargo and publishes physical actor health.
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "scene-stored-draining",
                lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
        if (result instanceof CommandResult.Accepted) FrontierV3SceneExecutor.release(level, runtime, lease);
    }

    /** A flushed HOT scene may lose every unload callback when its JVM is killed. */
    private static void progressWithoutUnloadCallback(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                       FrontierWorldState state, SceneLease lease) {
        Set<UUID> missing = missingCallbacks(level, state, lease);
        if (missing == null || missing.isEmpty()) {
            discardUnobserved(runtime, lease.id());
            return;
        }
        Map<SceneLeaseId, UnobservedAttempt> pending = UNOBSERVED.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        UnobservedAttempt attempt = pending.get(lease.id());
        var globalEpoch = FrontierV3EntityWriteEpochs.globalStamp(level);
        if (globalEpoch.isEmpty()) return;
        if (attempt != null && attempt.decided()) {
            if (attempt.scans() >= MAX_SCANS_PER_LEASE || level.getGameTime() - attempt.startedAtTick() < MIN_REARM_TICKS
                    || attempt.globalEpoch() == globalEpoch.orElseThrow() && attempt.lease().equals(lease)
                        && attempt.missing().equals(missing)) return;
            pending.put(lease.id(), new UnobservedAttempt(lease, state, missing, globalEpoch.orElseThrow(), level.getGameTime(),
                    inspectUnobserved(level, missing), attempt.scans() + 1));
            return;
        }
        if (attempt == null) {
            if (pending.size() >= MAX_ATTEMPTS || pending.values().stream().anyMatch(value -> !value.future().isDone())) return;
            pending.put(lease.id(), new UnobservedAttempt(lease, state, missing, globalEpoch.orElseThrow(), level.getGameTime(),
                    inspectUnobserved(level, missing), 1));
            return;
        }
        if (!attempt.future().isDone()) return;
        attempt.decide();
        UnobservedProof proof;
        try { proof = attempt.future().join(); }
        catch (CompletionException | IllegalStateException failure) {
            PaleMirrorMod.LOGGER.warn("PMV3_SCENE_STORED_RECOVERY unobserved lease={} reason={}", lease.id().value(), failure.toString());
            return;
        }
        var current = runtime.decodedState().orElse(null);
        var retained = current == null ? null : current.sceneLeases().get(lease.id());
        if (retained == null || !retained.equals(attempt.lease()) || demandExists(level, retained)
                || !proof.current(level) || !attempt.missing().equals(missingCallbacks(level, current, retained))) return;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, current.bootstrap().worldId());
        var cargoLedger = FrontierV3CargoDepartureLedger.get(level, current.bootstrap().worldId());
        Map<UUID, FrontierV3SceneDeparture> recoveredActors = new LinkedHashMap<>();
        for (SceneMember member : retained.members()) {
            if (!missing.contains(member.entityId())) continue;
            var actor = current.actorLocations().get(member.actorId());
            var earlier = attempt.state().actorLocations().get(member.actorId());
            var ambient = current.ambientLeases().get(member.actorId());
            if (actor == null || !actor.equals(earlier)
                    || !java.util.Objects.equals(ambient, attempt.state().ambientLeases().get(member.actorId()))) return;
            var saved = proof.body(member.entityId());
            if (saved == null || !saved.id().equals(member.entityId()) || !saved.actor().equals(member.actorId().value())) return;
            FrontierV3SceneDeparture receipt;
            try {
                var kind = FrontierV3ActorCarrierComposition.ActorKind.valueOf(saved.kind());
                var inactive = FrontierV3ActorCarrierComposition.fromCanonical(current, member.actorId(), kind,
                        FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                        FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, retained.revision(), saved.epoch());
                receipt = new FrontierV3SceneDeparture(new FrontierV3AmbientCarrierLedger.Carrier(inactive,
                        Math.max(1L, retained.revision()), ambient == null ? 0L : ambient.revision()),
                        retained.id(), retained.revision(), new SceneMemberPosition(member.actorId(), saved.body(), saved.health()),
                        actor.condition().health());
            } catch (IllegalArgumentException foreign) { return; }
            if (!saved.matches(receipt)) return;
            recoveredActors.put(member.entityId(), receipt);
        }
        FrontierV3CargoDeparture recoveredCargo = null;
        if (FrontierV3SceneBehaviorRegistry.hasCargoCarrier(retained)
                && missing.contains(FrontierV3CargoCarrierExecutor.id(retained))) {
            var saved = proof.cart(FrontierV3CargoCarrierExecutor.id(retained));
            if (saved == null) return;
            try {
                recoveredCargo = new FrontierV3CargoDeparture(saved.leaseId(), saved.cargoId(), saved.id(),
                        saved.revision(), saved.epoch(), saved.body(), saved.inventory());
            } catch (IllegalArgumentException foreign) { return; }
            if (!saved.matches(recoveredCargo) || cargoLedger.conflicted(recoveredCargo.entityId())
                    || !FrontierV3CargoCarrierExecutor.currentDeparture(current, retained, recoveredCargo,
                    level.registryAccess())) return;
        }
        if (recoveredActors.size() + (recoveredCargo == null ? 0 : 1) != missing.size() || !proof.current(level)) return;
        // Publish disk-derived receipts only after the entire missing subset is proven.
        // The ordinary whole-scene stored proof then rechecks the complete loaded/saved
        // partition before any canonical DRAINING transition or cargo custody change.
        if (recoveredCargo != null) {
            if (!cargoLedger.record(recoveredCargo) || !cargoLedger.noLoadProofCandidate(recoveredCargo)
                    || !cargoLedger.confirmSavedObservation(recoveredCargo)) return;
            cargoLedger.persist(level, current.bootstrap().worldId());
        }
        for (var receipt : recoveredActors.values()) {
            if (!ledger.recordDeparture(receipt) || !ledger.noLoadProofCandidate(receipt)
                    || !ledger.confirmSavedDeparture(receipt)) return;
        }
        if (!recoveredActors.isEmpty()) ledger.persist(level, current.bootstrap().worldId());
        PaleMirrorMod.LOGGER.info("PMV3_SCENE_STORED_RECOVERY missing-callback-proved lease={} actors={} cargo={}",
                retained.id().value(), recoveredActors.size(), recoveredCargo != null);
        // Retain the already-proven one-body fast path; other compositions run the
        // existing independent full-set proof on the next server tick.
        if (recoveredActors.size() == 1 && recoveredCargo == null && retained.members().size() == 1) {
            CommandResult result = FrontierV3CommandSubmission.submit(runtime, "scene-disk-recovered-draining",
                    lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            if (result instanceof CommandResult.Accepted) FrontierV3SceneExecutor.release(level, runtime, lease);
        }
    }

    /** Null is an invalid/inconsistent partition, empty means no missing callback. */
    private static Set<UUID> missingCallbacks(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var cargoLedger = FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId());
        Set<UUID> missing = new HashSet<>();
        for (SceneMember member : lease.members()) {
            var actor = state.actorLocations().get(member.actorId());
            if (actor == null) return null;
            if (actor.condition().status() == ActorLifeStatus.DEAD) continue;
            Entity live = level.getEntity(member.entityId());
            if (live != null) {
                if (ledger.departure(member.actorId()).isPresent()
                        || !FrontierV3SceneExecutor.owned(live, state, lease, member)
                        || !(live instanceof Mob body) || body.getHealth() <= 0.0F) return null;
                continue;
            }
            if (ledger.departure(member.actorId()).isPresent()) {
                var receipt = FrontierV3SceneDepartureObserver.observedDeparture(state, lease, member, ledger).orElse(null);
                if (receipt == null || !ledger.noLoadProofCandidate(receipt)) return null;
                continue;
            }
            var ambient = state.ambientLeases().get(member.actorId());
            if (ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED
                    || ledger.hasDepartureConflict(member.actorId()) || !missing.add(member.entityId())) return null;
        }
        if (FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease)) {
            UUID id = FrontierV3CargoCarrierExecutor.id(lease);
            if (level.getEntity(id) != null) {
                if (!FrontierV3CargoCarrierExecutor.intact(level, state, lease)) return null;
            } else if (cargoLedger.observation(id).isPresent()) {
                var receipt = cargoLedger.observation(id).orElseThrow();
                if (cargoLedger.conflicted(id) || !cargoLedger.noLoadProofCandidate(receipt)
                        || !FrontierV3CargoCarrierExecutor.currentDeparture(state, lease, receipt,
                        level.registryAccess())) return null;
            } else if (cargoLedger.conflicted(id) || !missing.add(id)) return null;
        }
        return missing.size() > 256 ? null : Set.copyOf(missing);
    }

    private static boolean demandExists(ServerLevel level, SceneLease lease) {
        return FrontierV3SceneExecutor.demandExists(level, lease.handoffPosition());
    }

    private static void discardUnobserved(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId id) {
        var pending = UNOBSERVED.get(runtime);
        if (pending != null) {
            pending.remove(id);
            if (pending.isEmpty()) UNOBSERVED.remove(runtime);
        }
    }

    private static CompletableFuture<UnobservedProof> inspectUnobserved(ServerLevel level, Set<UUID> ids) {
        return FrontierV3StoredEntityCensus.scan(level, ids).thenComposeAsync(census -> {
            Map<ChunkPos, Set<UUID>> columns = new HashMap<>();
            for (UUID id : ids) {
                var matches = census.sightings().getOrDefault(id, Set.of());
                if (matches.size() != 1) return CompletableFuture.completedFuture(new UnobservedProof(census, Map.of(), Map.of(), ids));
                columns.computeIfAbsent(matches.iterator().next(), ignored -> new HashSet<>()).add(id);
            }
            Map<ChunkPos, CompletableFuture<FrontierV3StoredEntityInspection.StampedSnapshot>> reads = new HashMap<>();
            columns.forEach((column, selected) -> reads.put(column,
                    FrontierV3StoredEntityInspection.inspect(level, column, selected)));
            return CompletableFuture.allOf(reads.values().toArray(CompletableFuture[]::new))
                    .thenApplyAsync(ignored -> {
                        Map<ChunkPos, FrontierV3StoredEntityInspection.StampedSnapshot> snapshots = new HashMap<>();
                        reads.forEach((column, future) -> snapshots.put(column, future.join()));
                        return new UnobservedProof(census, columns, snapshots, ids);
                    }, level.getServer());
        }, level.getServer());
    }

    static record UnobservedProof(FrontierV3StoredEntityCensus.Result census, Map<ChunkPos, Set<UUID>> columns,
                                  Map<ChunkPos, FrontierV3StoredEntityInspection.StampedSnapshot> snapshots,
                                  Set<UUID> ids) {
        UnobservedProof {
            Map<ChunkPos, Set<UUID>> copied = new HashMap<>();
            columns.forEach((column, members) -> copied.put(column, Set.copyOf(members)));
            columns = Map.copyOf(copied); snapshots = Map.copyOf(snapshots); ids = Set.copyOf(ids);
        }
        boolean complete() {
            if (columns.isEmpty() || !columns.keySet().equals(snapshots.keySet())) return false;
            Set<UUID> covered = new HashSet<>();
            for (var entry : columns.entrySet()) {
                for (UUID id : entry.getValue()) {
                    if (!covered.add(id) || !census.uniqueAt(id, entry.getKey())) return false;
                }
            }
            return covered.equals(ids);
        }
        boolean current(ServerLevel level) {
            return complete() && census.stillCurrent(level)
                    && snapshots.entrySet().stream().allMatch(entry -> entry.getValue().stillCurrent(level, entry.getKey()));
        }
        FrontierV3SceneDeparturePersistence.SavedBody body(UUID id) {
            ChunkPos column = census.sightings().getOrDefault(id, Set.of()).stream().findFirst().orElse(null);
            if (column == null) return null;
            var snapshot = snapshots.get(column);
            return census.uniqueAt(id, column) && snapshot != null ? snapshot.snapshot().actors().get(id) : null;
        }
        FrontierV3CargoDeparturePersistence.SavedCart cart(UUID id) {
            ChunkPos column = census.sightings().getOrDefault(id, Set.of()).stream().findFirst().orElse(null);
            if (column == null) return null;
            var snapshot = snapshots.get(column);
            return census.uniqueAt(id, column) && snapshot != null ? snapshot.snapshot().cargo().get(id) : null;
        }
    }

    private static final class UnobservedAttempt {
        private final SceneLease lease;
        private final FrontierWorldState state;
        private final Set<UUID> missing;
        private final long globalEpoch, startedAtTick;
        private final int scans;
        private final CompletableFuture<UnobservedProof> future;
        private boolean decided;
        UnobservedAttempt(SceneLease lease, FrontierWorldState state, Set<UUID> missing,
                          long globalEpoch, long startedAtTick, CompletableFuture<UnobservedProof> future, int scans) {
            this.lease = lease; this.state = state; this.missing = Set.copyOf(missing);
            this.globalEpoch = globalEpoch; this.startedAtTick = startedAtTick; this.future = future;
            this.scans = scans;
        }
        SceneLease lease() { return lease; }
        FrontierWorldState state() { return state; }
        Set<UUID> missing() { return missing; }
        long globalEpoch() { return globalEpoch; }
        long startedAtTick() { return startedAtTick; }
        int scans() { return scans; }
        CompletableFuture<UnobservedProof> future() { return future; }
        boolean decided() { return decided; }
        void decide() { decided = true; }
    }

    private static boolean retry(ServerLevel level, Map<SceneLeaseId, Attempt> attempts, Attempt prior,
                                 FrontierWorldState state, SceneLease lease) {
        if (prior.scans() >= MAX_SCANS_PER_LEASE || !lease.equals(prior.lease())) return false;
        Receipts current = currentReceipts(level, state, lease);
        if (!prior.receipts().equals(current)) return false;
        attempts.put(lease.id(), new Attempt(lease, current, inspect(level, current), prior.scans() + 1,
                targetWriteEpochs(level, current), level.getGameTime()));
        return true;
    }

    /** A decided scan rearms on a new partition or write, never on elapsed time alone. */
    static boolean changedEvidenceAfterCooldown(Receipts prior, Optional<Map<ChunkPos, Long>> priorEpochs,
                                                long startedAtTick, Receipts current,
                                                Optional<Map<ChunkPos, Long>> currentEpochs, long nowTick) {
        return current != null && currentEpochs.isPresent() && nowTick - startedAtTick >= MIN_REARM_TICKS
                && (!prior.equals(current) || !priorEpochs.equals(currentEpochs));
    }

    private static Optional<Map<ChunkPos, Long>> targetWriteEpochs(ServerLevel level, Receipts receipts) {
        Set<ChunkPos> columns = new HashSet<>();
        receipts.actors().values().forEach(receipt -> columns.add(new ChunkPos(
                Math.floorDiv(receipt.observed().body().x(), 16), Math.floorDiv(receipt.observed().body().z(), 16))));
        receipts.cargo().ifPresent(receipt -> columns.add(new ChunkPos(
                Math.floorDiv(receipt.body().x(), 16), Math.floorDiv(receipt.body().z(), 16))));
        Map<ChunkPos, Long> epochs = new HashMap<>();
        for (ChunkPos column : columns) {
            var stamp = FrontierV3EntityWriteEpochs.stamp(level, column);
            if (stamp.isEmpty()) return Optional.empty();
            epochs.put(column, stamp.orElseThrow());
        }
        return Optional.of(Map.copyOf(epochs));
    }

    /** Null means a partial/ambiguous set; it is never evidence that bodies are absent. */
    private static Receipts currentReceipts(ServerLevel level,
                                                                        FrontierWorldState state, SceneLease lease) {
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        Map<UUID, FrontierV3SceneDeparture> receipts = new LinkedHashMap<>();
        Set<UUID> loaded = new HashSet<>();
        Set<UUID> expected = new HashSet<>();
        for (var member : lease.members()) {
            var actor = state.actorLocations().get(member.actorId());
            if (actor == null) return null;
            if (actor.condition().status() == ActorLifeStatus.DEAD) continue;
            if (!expected.add(member.entityId())) return null;
            Entity live = level.getEntity(member.entityId());
            if (live != null) {
                if (ledger.departure(member.actorId()).isPresent()
                        || !FrontierV3SceneExecutor.owned(live, state, lease, member)
                        || !(live instanceof Mob body) || body.getHealth() <= 0.0F) return null;
                loaded.add(member.entityId());
                continue;
            }
            var receipt = FrontierV3SceneDepartureObserver.observedDeparture(state, lease, member, ledger).orElse(null);
            if (receipt == null || !ledger.noLoadProofCandidate(receipt)
                    || receipts.putIfAbsent(member.entityId(), receipt) != null) return null;
        }
        Optional<FrontierV3CargoDeparture> cargo = Optional.empty();
        if (FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease)) {
            UUID id = FrontierV3CargoCarrierExecutor.id(lease);
            if (!expected.add(id)) return null;
            if (level.getEntity(id) != null) {
                if (!FrontierV3CargoCarrierExecutor.intact(level, state, lease)) return null;
                loaded.add(id);
            } else {
                var cargoLedger = FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId());
                var receipt = cargoLedger.observation(id).orElse(null);
                if (receipt == null || !cargoLedger.noLoadProofCandidate(receipt)
                        || !FrontierV3CargoCarrierExecutor.currentDeparture(state, lease, receipt, level.registryAccess())) return null;
                cargo = Optional.of(receipt);
            }
        }
        if (receipts.isEmpty() && cargo.isEmpty()) return null; // all-loaded uses its existing owner path
        var whole = new Receipts(receipts, cargo, loaded);
        return whole.covers(expected) ? whole : null;
    }

    private static CompletableFuture<Proof> inspect(ServerLevel level, Receipts receipts) {
        Map<ChunkPos, Set<UUID>> byChunk = new HashMap<>();
        for (var entry : receipts.actors().entrySet()) {
            var body = entry.getValue().observed().body();
            byChunk.computeIfAbsent(new ChunkPos(Math.floorDiv(body.x(), 16), Math.floorDiv(body.z(), 16)),
                    ignored -> new HashSet<>()).add(entry.getKey());
        }
        receipts.cargo().ifPresent(cargo -> byChunk.computeIfAbsent(
                new ChunkPos(Math.floorDiv(cargo.body().x(), 16), Math.floorDiv(cargo.body().z(), 16)),
                ignored -> new HashSet<>()).add(cargo.entityId()));
        Set<UUID> targets = receipts.targets();
        CompletableFuture<FrontierV3StoredEntityCensus.Result> censusFuture;
        try { censusFuture = FrontierV3StoredEntityCensus.scan(level, targets); }
        catch (RuntimeException unavailable) { return CompletableFuture.failedFuture(unavailable); }
        return censusFuture.thenComposeAsync(census -> {
            if (byChunk.entrySet().stream().anyMatch(entry -> entry.getValue().stream()
                    .anyMatch(id -> !census.uniqueAt(id, entry.getKey()))))
                return CompletableFuture.completedFuture(new Proof(census, Map.of(), byChunk));
            Map<ChunkPos, CompletableFuture<FrontierV3StoredEntityInspection.StampedSnapshot>> reads = new HashMap<>();
            byChunk.forEach((chunk, ids) -> reads.put(chunk, FrontierV3StoredEntityInspection.inspect(level, chunk, ids)));
            return CompletableFuture.allOf(reads.values().toArray(CompletableFuture[]::new))
                    .thenApplyAsync(ignored -> {
                        Map<ChunkPos, FrontierV3StoredEntityInspection.StampedSnapshot> snapshots = new HashMap<>();
                        reads.forEach((chunk, future) -> snapshots.put(chunk, future.join()));
                        return new Proof(census, snapshots, byChunk);
                    }, level.getServer());
        }, level.getServer());
    }

    record Receipts(Map<UUID, FrontierV3SceneDeparture> actors, Optional<FrontierV3CargoDeparture> cargo,
                    Set<UUID> loaded) {
        Receipts { actors = Map.copyOf(actors); java.util.Objects.requireNonNull(cargo); loaded = Set.copyOf(loaded); }
        Receipts(Map<UUID, FrontierV3SceneDeparture> actors, Optional<FrontierV3CargoDeparture> cargo) {
            this(actors, cargo, Set.of());
        }

        Set<UUID> targets() {
            var result = new HashSet<>(actors.keySet());
            cargo.ifPresent(receipt -> result.add(receipt.entityId()));
            if (result.size() != actors.size() + (cargo.isPresent() ? 1 : 0))
                throw new IllegalArgumentException("duplicate scene actor/cargo identity");
            return Set.copyOf(result);
        }

        boolean covers(Set<UUID> expected) {
            var all = new HashSet<>(loaded);
            Set<UUID> stored = targets();
            if (!java.util.Collections.disjoint(all, stored)) return false;
            all.addAll(stored);
            return all.equals(expected);
        }
    }

    record Proof(FrontierV3StoredEntityCensus.Result census,
                         Map<ChunkPos, FrontierV3StoredEntityInspection.StampedSnapshot> snapshots,
                         Map<ChunkPos, Set<UUID>> byChunk) {
        Proof {
            snapshots = Map.copyOf(snapshots);
            byChunk = Map.copyOf(byChunk);
        }

        boolean current(ServerLevel level) {
            return census.stillCurrent(level) && snapshots.entrySet().stream()
                    .allMatch(entry -> entry.getValue().stillCurrent(level, entry.getKey()));
        }

        boolean matches(Map<UUID, FrontierV3SceneDeparture> actors) {
            return matches(new Receipts(actors, Optional.empty()));
        }

        boolean matches(Receipts receipts) {
            for (var entry : byChunk.entrySet()) {
                var snapshot = snapshots.get(entry.getKey());
                if (snapshot == null) return false;
                for (UUID id : entry.getValue()) {
                    if (!census.uniqueAt(id, entry.getKey())) return false;
                    var actor = receipts.actors().get(id);
                    if (actor != null) {
                        if (!snapshot.snapshot().matches(actor)) return false;
                    } else if (receipts.cargo().isEmpty() || !id.equals(receipts.cargo().orElseThrow().entityId())
                            || !snapshot.snapshot().matches(receipts.cargo().orElseThrow())) return false;
                }
            }
            return byChunk.values().stream().mapToInt(Set::size).sum() == receipts.targets().size();
        }
    }

    private static final class Attempt {
        private final SceneLease lease;
        private final Receipts receipts;
        private final CompletableFuture<Proof> future;
        private final int scans;
        private final Optional<Map<ChunkPos, Long>> writeEpochs;
        private final long startedAtTick;
        private boolean decided;

        Attempt(SceneLease lease, Receipts receipts, CompletableFuture<Proof> future, int scans,
                Optional<Map<ChunkPos, Long>> writeEpochs, long startedAtTick) {
            this.lease = lease;
            this.receipts = receipts;
            this.future = future;
            this.scans = scans;
            this.writeEpochs = writeEpochs;
            this.startedAtTick = startedAtTick;
        }
        SceneLease lease() { return lease; }
        Receipts receipts() { return receipts; }
        CompletableFuture<Proof> future() { return future; }
        int scans() { return scans; }
        Optional<Map<ChunkPos, Long>> writeEpochs() { return writeEpochs; }
        long startedAtTick() { return startedAtTick; }
        boolean decided() { return decided; }
        void decide() { decided = true; }
    }
}
