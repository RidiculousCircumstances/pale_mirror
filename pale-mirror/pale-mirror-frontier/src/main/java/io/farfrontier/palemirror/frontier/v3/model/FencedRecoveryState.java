package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Durable, exact recovery authority. This is deliberately not a second roster, inventory or
 * effect ledger: it only fences which physical projection may speak for an already-owned asset.
 */
public record FencedRecoveryState(Map<SubjectId, FencedRecoveryBinding> current,
                                  Map<SubjectId, FencedRecoveryTombstone> tombstones) {
    public static final int MAX_BINDINGS = 4_096;

    public FencedRecoveryState {
        current = Map.copyOf(Objects.requireNonNull(current, "current recovery bindings"));
        tombstones = Map.copyOf(Objects.requireNonNull(tombstones, "recovery tombstones"));
        if (current.size() + tombstones.size() > MAX_BINDINGS) throw new IllegalArgumentException("recovery retention limit exceeded");
        for (Map.Entry<SubjectId, FencedRecoveryBinding> entry : current.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().bindingId())) throw new IllegalArgumentException("recovery binding key is not exact");
            FencedRecoveryTombstone tombstone = tombstones.get(entry.getKey());
            if (tombstone != null && (tombstone.asset() != entry.getValue().asset()
                    || entry.getValue().authorityEpoch() <= tombstone.retiredEpoch())) {
                throw new IllegalArgumentException("current recovery authority does not supersede its tombstone");
            }
        }
        for (Map.Entry<SubjectId, FencedRecoveryTombstone> entry : tombstones.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().bindingId())) throw new IllegalArgumentException("recovery tombstone key is not exact");
        }
    }

    public static FencedRecoveryState empty() { return new FencedRecoveryState(Map.of(), Map.of()); }

    /** Monotonic allocator for an exact binding identity; callers never infer an epoch from a scene revision. */
    public long nextEpoch(SubjectId bindingId) {
        Objects.requireNonNull(bindingId, "recovery binding id");
        FencedRecoveryBinding live = current.get(bindingId);
        FencedRecoveryTombstone retired = tombstones.get(bindingId);
        long prior = Math.max(live == null ? 0L : live.authorityEpoch(), retired == null ? 0L : retired.retiredEpoch());
        if (prior == Long.MAX_VALUE) throw new IllegalArgumentException("recovery authority epoch is exhausted");
        return prior + 1L;
    }

    public FencedRecoveryState prepare(FencedRecoveryBinding binding) {
        Objects.requireNonNull(binding, "recovery binding"); binding.require(FencedRecoveryPhase.PREPARED);
        if (current.containsKey(binding.bindingId())) throw new IllegalArgumentException("recovery binding is already current");
        FencedRecoveryTombstone prior = tombstones.get(binding.bindingId());
        if (prior != null && (prior.asset() != binding.asset() || binding.authorityEpoch() <= prior.retiredEpoch())) {
            throw new IllegalArgumentException("recovery binding reuses a stale authority epoch or owner");
        }
        // A retained tombstone is a diagnostic exactness aid, not permission to accept an
        // otherwise unknown projection: lateLoad fails closed even when the bounded retention
        // tail has compacted an old entry.  Make room only as part of the same durable new
        // authority transition. A successor for the same identity itself fences all old epochs.
        FencedRecoveryState retained = compactForNewBinding(binding.bindingId());
        Map<SubjectId, FencedRecoveryBinding> next = new LinkedHashMap<>(retained.current); next.put(binding.bindingId(), binding);
        return new FencedRecoveryState(next, retained.tombstones);
    }

    /**
     * An attributed conflict-resolution boundary fences the unresolved projection before it
     * prepares its exact successor.  It is deliberately unavailable for running or observed
     * authority: only an ambiguous local consequence can be superseded without inventing its
     * postcondition.
     */
    FencedRecoveryState supersedeAmbiguous(FencedRecoveryBinding successor, String reason) {
        Objects.requireNonNull(successor, "recovery successor"); Objects.requireNonNull(reason, "recovery supersession reason");
        successor.require(FencedRecoveryPhase.PREPARED);
        FencedRecoveryBinding prior = current.get(successor.bindingId());
        if (prior == null || prior.phase() != FencedRecoveryPhase.AMBIGUOUS || prior.asset() != successor.asset()
                || !prior.ownerId().equals(successor.ownerId()) || prior.ownerRevision() != successor.ownerRevision()
                || successor.authorityEpoch() != prior.authorityEpoch() + 1L) {
            throw new IllegalArgumentException("recovery successor lacks exact ambiguous authority");
        }
        return retire(prior, FencedRecoveryDisposition.REJECT_STALE, reason).prepare(successor);
    }

    public FencedRecoveryState running(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch); return replace(binding.running());
    }
    public FencedRecoveryState observed(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch); return replace(binding.observed());
    }
    public FencedRecoveryState confirm(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch).confirmed();
        return retire(binding, FencedRecoveryDisposition.REJECT_STALE, "confirmed");
    }
    /** Terminal local owner disposition; it never becomes restart ambiguity or a new claim. */
    public FencedRecoveryState conflict(SubjectId bindingId, long epoch, String reason) {
        Objects.requireNonNull(reason, "recovery conflict reason");
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch);
        if (binding.phase() == FencedRecoveryPhase.CONFIRMED || binding.phase() == FencedRecoveryPhase.AMBIGUOUS) {
            throw new IllegalArgumentException("only a current physical attempt may be terminally conflicted");
        }
        return retire(binding, FencedRecoveryDisposition.ABANDON, reason);
    }
    /** Only a declared reversible checkpoint may return to COLD without inspecting an old projection. */
    public FencedRecoveryState revokeToCold(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch);
        if (!binding.reversibleCheckpoint() || (binding.phase() != FencedRecoveryPhase.PREPARED && binding.phase() != FencedRecoveryPhase.RUNNING)) {
            throw new IllegalArgumentException("only unconfirmed reversible checkpoint authority may resume cold");
        }
        return retire(binding, FencedRecoveryDisposition.RESUME_COLD, "revoked-to-cold");
    }
    public FencedRecoveryState ambiguous(SubjectId bindingId, long epoch, String reason, FencedRecoveryDisposition action) {
        Objects.requireNonNull(reason, "recovery ambiguity reason"); Objects.requireNonNull(action, "recovery ambiguity action");
        return replace(requireCurrent(bindingId, epoch).ambiguous(reason, action));
    }
    /** Bounded terminal policy: the owning process may abandon only after the local inspection budget. */
    public FencedRecoveryState abandon(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch);
        if (binding.phase() != FencedRecoveryPhase.AMBIGUOUS || binding.recoveryAttempts() < FencedRecoveryBinding.MAX_RECOVERY_ATTEMPTS) {
            throw new IllegalArgumentException("recovery ambiguity has not reached its abandonment policy");
        }
        return retire(binding, FencedRecoveryDisposition.ABANDON, binding.reason());
    }
    /** Read-only natural-load guard; it never adopts a projection or creates a domain event. */
    public FencedRecoveryDisposition lateLoad(SubjectId bindingId, FencedRecoveryAsset asset, SubjectId ownerId, long epoch) {
        Objects.requireNonNull(bindingId, "late binding id"); Objects.requireNonNull(asset, "late asset"); Objects.requireNonNull(ownerId, "late owner");
        FencedRecoveryBinding binding = current.get(bindingId);
        if (binding != null && binding.asset() == asset && binding.ownerId().equals(ownerId) && binding.authorityEpoch() == epoch) return FencedRecoveryDisposition.RECLAIM;
        FencedRecoveryTombstone tombstone = tombstones.get(bindingId);
        if (tombstone != null && tombstone.asset() == asset && tombstone.ownerId().equals(ownerId) && epoch <= tombstone.retiredEpoch()) {
            return FencedRecoveryDisposition.REJECT_STALE;
        }
        return FencedRecoveryDisposition.REJECT_STALE;
    }
    /** Explicit retention compaction cannot forget current authority and only drops supplied terminal tombstones. */
    public FencedRecoveryState compactTombstones(java.util.Set<SubjectId> ids) {
        Objects.requireNonNull(ids, "recovery tombstone ids");
        Map<SubjectId, FencedRecoveryTombstone> next = new LinkedHashMap<>(tombstones);
        for (SubjectId id : ids) {
            if (current.containsKey(id)) throw new IllegalArgumentException("cannot compact a current recovery authority");
            next.remove(id);
        }
        return new FencedRecoveryState(current, next);
    }

    /** Deterministic bounded retention policy; unknown late projections still reject by default. */
    private FencedRecoveryState compactForNewBinding(SubjectId incomingId) {
        if (current.size() + tombstones.size() < MAX_BINDINGS) return this;
        SubjectId retired = tombstones.keySet().stream().filter(id -> !id.equals(incomingId)).sorted().findFirst()
                .orElseGet(() -> tombstones.containsKey(incomingId) ? incomingId : null);
        if (retired == null) throw new IllegalArgumentException("recovery current-authority limit exceeded");
        Map<SubjectId, FencedRecoveryTombstone> next = new LinkedHashMap<>(tombstones); next.remove(retired);
        return new FencedRecoveryState(current, next);
    }

    private FencedRecoveryBinding requireCurrent(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = current.get(Objects.requireNonNull(bindingId, "recovery binding id"));
        if (binding == null || binding.authorityEpoch() != epoch) throw new IllegalArgumentException("recovery authority is stale or unavailable");
        return binding;
    }
    private FencedRecoveryState replace(FencedRecoveryBinding binding) {
        Map<SubjectId, FencedRecoveryBinding> next = new LinkedHashMap<>(current); next.put(binding.bindingId(), binding);
        return new FencedRecoveryState(next, tombstones);
    }
    private FencedRecoveryState retire(FencedRecoveryBinding binding, FencedRecoveryDisposition disposition, String reason) {
        Map<SubjectId, FencedRecoveryBinding> nextCurrent = new LinkedHashMap<>(current); nextCurrent.remove(binding.bindingId());
        Map<SubjectId, FencedRecoveryTombstone> nextTombstones = new LinkedHashMap<>(tombstones);
        nextTombstones.put(binding.bindingId(), new FencedRecoveryTombstone(binding.bindingId(), binding.asset(), binding.ownerId(),
                binding.ownerRevision(), binding.authorityEpoch(), disposition, reason));
        return new FencedRecoveryState(nextCurrent, nextTombstones);
    }
}
