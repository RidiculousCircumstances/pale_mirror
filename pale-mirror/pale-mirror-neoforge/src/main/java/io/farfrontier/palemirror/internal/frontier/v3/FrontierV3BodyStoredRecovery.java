package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Missing unload callbacks are a body-lifetime concern, independent of activity or scene. */
final class FrontierV3BodyStoredRecovery {
    private static final int MAX_BATCH = 256;
    private static final long MIN_REARM_TICKS = 20L;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Queue> QUEUES = new IdentityHashMap<>();
    private FrontierV3BodyStoredRecovery() { }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { QUEUES.remove(runtime); }

    /** Existing bounded actor probes supply candidates; no additional full-world actor scan. */
    static void offer(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                      FrontierWorldState state, SubjectId actor) {
        var binding = state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(actor));
        if (binding == null || binding.phase() != FencedRecoveryPhase.RUNNING
                && binding.phase() != FencedRecoveryPhase.AMBIGUOUS) return;
        var location = state.actorLocations().get(actor);
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var entity = ActorBodyId.entityId(state.bootstrap().worldId(), actor);
        if (location == null || location.condition().status() != ActorLifeStatus.ALIVE
                || ledger.hasBodyDeparture(actor) || ledger.hasDepartureConflict(actor)
                || level.getEntity(entity) != null || FrontierV3AmbientPendingAdmissions.get(runtime, entity) != null) return;
        var epoch = FrontierV3EntityWriteEpochs.globalStamp(level);
        if (epoch.isEmpty()) return;
        var candidate = new Candidate(new ActorBodyId(actor, binding.authorityEpoch()), location, entity);
        var queue = QUEUES.computeIfAbsent(runtime, ignored -> new Queue());
        if (queue.pending.containsKey(actor) || queue.active != null && queue.active.candidates().containsKey(actor)
                || new Attempt(candidate, epoch.orElseThrow()).equals(queue.attempted.get(actor))) return;
        if (queue.pending.size() < MAX_BATCH) queue.pending.put(actor, candidate);
    }

    /** One bounded asynchronous census per batch; unresolved evidence rearms only when storage changes. */
    static void progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var queue = QUEUES.get(runtime);
        if (queue == null) return;
        if (queue.active != null) {
            if (!queue.active.future().isDone()) return;
            var active = queue.active; queue.active = null;
            try {
                var proof = active.future().join();
                for (var candidate : active.candidates().values()) {
                    var columns = proof.census().sightings().getOrDefault(candidate.entity(), Set.of());
                    if (columns.size() != 1) {
                        unresolved(candidate, columns.isEmpty() ? "SAVED_BODY_ABSENT" : "DUPLICATE_SAVED_BODY");
                        continue;
                    }
                    var column = columns.iterator().next();
                    var snapshot = proof.snapshots().get(column);
                    if (snapshot == null || level.areEntitiesLoaded(column.toLong())) continue;
                    if (FrontierV3ActorBodyController.confirmStoredDeparture(level, runtime, candidate.body(),
                            candidate.location(), proof.census(), column, snapshot))
                        PaleMirrorMod.LOGGER.info("PMV3_BODY_STORED_RECOVERY actor={} epoch={} column={} result=SAVED_DEPARTURE_PROVED",
                                candidate.body().actorId().value(), candidate.body().physicalEpoch(), column);
                    else unresolved(candidate, "STALE_OR_INCOMPLETE_BODY_EVIDENCE");
                }
            } catch (CompletionException failure) {
                PaleMirrorMod.LOGGER.warn("PMV3_BODY_STORED_RECOVERY batch={} reason={}", active.candidates().size(), failure.toString());
            }
        }
        if (queue.pending.isEmpty() || level.getGameTime() - queue.lastStartedAt < MIN_REARM_TICKS
                || FrontierV3DepartureReturnReadFence.anyReadPending(level)) return;
        var epoch = FrontierV3EntityWriteEpochs.globalStamp(level);
        if (epoch.isEmpty()) return;
        var selected = Map.copyOf(queue.pending); queue.pending.clear();
        // Retain only current-lifetime attempts. This is volatile evidence memory, not a new authority.
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        queue.attempted.keySet().removeIf(actor -> !state.fencedRecovery().current().containsKey(ActorBodyId.recoveryBindingId(actor)));
        selected.forEach((actor, candidate) -> queue.attempted.put(actor, new Attempt(candidate, epoch.orElseThrow())));
        var ids = selected.values().stream().map(Candidate::entity).collect(java.util.stream.Collectors.toSet());
        queue.lastStartedAt = level.getGameTime();
        queue.active = new Batch(selected, FrontierV3StoredBodyEvidence.inspect(level, ids));
    }

    private static void unresolved(Candidate candidate, String reason) {
        PaleMirrorMod.LOGGER.warn("PMV3_BODY_STORED_RECOVERY actor={} epoch={} reason={}",
                candidate.body().actorId().value(), candidate.body().physicalEpoch(), reason);
    }
    record Candidate(ActorBodyId body, ActorLocation location, UUID entity) { }
    private record Attempt(Candidate candidate, long writeEpoch) { }
    private record Batch(Map<SubjectId, Candidate> candidates, CompletableFuture<FrontierV3StoredBodyEvidence.Proof> future) { }
    private static final class Queue {
        final Map<SubjectId, Candidate> pending = new LinkedHashMap<>();
        final Map<SubjectId, Attempt> attempted = new HashMap<>();
        Batch active;
        long lastStartedAt = -MIN_REARM_TICKS;
    }
}
