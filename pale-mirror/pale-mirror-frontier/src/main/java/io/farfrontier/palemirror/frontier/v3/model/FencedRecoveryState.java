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
                                  Map<SubjectId, FencedRecoveryTombstone> tombstones,
                                  CargoProjectionRetirements cargoRetirements) {
    public static final int MAX_BINDINGS = 4_096;

    public FencedRecoveryState {
        Objects.requireNonNull(cargoRetirements, "pending cargo retirements");
        current = Map.copyOf(Objects.requireNonNull(current, "current recovery bindings"));
        tombstones = Map.copyOf(Objects.requireNonNull(tombstones, "recovery tombstones"));
        long liveCargo = current.values().stream().filter(binding -> binding.asset() == FencedRecoveryAsset.CARGO).count();
        if (liveCargo + cargoRetirements.pending().size() > CargoProjectionRetirements.MAX_PENDING) {
            throw new IllegalArgumentException("live cargo lacks reserved cleanup capacity");
        }
        for (var pending : cargoRetirements.pending().values()) {
            var live = current.get(pending.authorization().bindingId());
            if (live != null && (live.asset() != FencedRecoveryAsset.CARGO
                    || live.ownerId().equals(pending.authorization().ownerId())
                    || live.authorityEpoch() <= pending.authorization().retiredEpoch())) {
                throw new IllegalArgumentException("live cargo overlaps a pending retired projection");
            }
        }
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

    public FencedRecoveryState(Map<SubjectId, FencedRecoveryBinding> current,
                               Map<SubjectId, FencedRecoveryTombstone> tombstones) {
        this(current, tombstones, CargoProjectionRetirements.empty());
    }

    /** Only the exact terminal owner can introduce a still-unacknowledged cleanup. */
    public FencedRecoveryState retainCargoRetirement(CargoProjectionRetirement obligation) {
        Objects.requireNonNull(obligation, "cargo retirement obligation");
        if (!obligation.authorization().equals(tombstones.get(obligation.authorization().bindingId()))) {
            throw new IllegalArgumentException("cargo retirement lacks current terminal authorization");
        }
        return new FencedRecoveryState(current, tombstones, cargoRetirements.retain(obligation));
    }

    /** Live cargo authority reserves space for its eventual cleanup before materialization. */
    public boolean canAdmitCargoProjection() {
        int liveCargo = (int) current.values().stream().filter(binding -> binding.asset() == FencedRecoveryAsset.CARGO).count();
        return cargoRetirements.canAdmit(liveCargo);
    }

    public FencedRecoveryState acknowledgeCargoCleanupSaved(CargoProjectionRetirement expected) {
        return new FencedRecoveryState(current, tombstones, cargoRetirements.acknowledgeSaved(expected));
    }

    /** Monotonic allocator for an exact binding identity; callers never infer an epoch from a scene revision. */
    public long nextEpoch(SubjectId bindingId) {
        Objects.requireNonNull(bindingId, "recovery binding id");
        FencedRecoveryBinding live = current.get(bindingId);
        FencedRecoveryTombstone retired = tombstones.get(bindingId);
        long prior = Math.max(live == null ? 0L : live.authorityEpoch(), retired == null ? 0L : retired.retiredEpoch());
        for (var pending : cargoRetirements.pending().values()) {
            if (pending.authorization().bindingId().equals(bindingId)) prior = Math.max(prior, pending.authorization().retiredEpoch());
        }
        if (prior == Long.MAX_VALUE) throw new IllegalArgumentException("recovery authority epoch is exhausted");
        return prior + 1L;
    }

    public FencedRecoveryState prepare(FencedRecoveryBinding binding) {
        Objects.requireNonNull(binding, "recovery binding"); binding.require(FencedRecoveryPhase.PREPARED);
        if (binding.asset() == FencedRecoveryAsset.CARGO && !canAdmitCargoProjection()) {
            throw new IllegalArgumentException("cargo projection admission waits for pending cleanup capacity");
        }
        if (current.containsKey(binding.bindingId())) throw new IllegalArgumentException("recovery binding is already current");
        if (binding.authorityEpoch() < nextEpoch(binding.bindingId())) throw new IllegalArgumentException("recovery binding reuses retained authority epoch");
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
        return new FencedRecoveryState(next, retained.tombstones, retained.cargoRetirements);
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
    public FencedRecoveryState inspectedObserved(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch); return replace(binding.inspectedObserved());
    }
    public FencedRecoveryState inspectedRunning(SubjectId bindingId, long epoch) {
        return replace(requireCurrent(bindingId, epoch).inspectedRunning());
    }
    public FencedRecoveryState confirm(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch).confirmed();
        return retire(binding, FencedRecoveryDisposition.REJECT_STALE, "confirmed");
    }
    /** Positive saved-body absence is not a rollback of a reversible effect checkpoint. */
    FencedRecoveryState retireObservedBodyAbsence(SubjectId bindingId, long epoch, SubjectId actor) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch);
        if (binding.asset() != FencedRecoveryAsset.BODY || !binding.ownerId().equals(actor)
                || binding.ownerRevision() != 0L
                || binding.phase() != FencedRecoveryPhase.RUNNING && binding.phase() != FencedRecoveryPhase.AMBIGUOUS)
            throw new IllegalArgumentException("saved absence requires one exact admitted actor body");
        return retire(binding, FencedRecoveryDisposition.RESUME_COLD, "observed-saved-body-absence");
    }
    /** Exact observed death is terminal even if work was unstarted or recovery ambiguous. */
    FencedRecoveryState retireObservedBodyDeath(SubjectId bindingId, long epoch, SubjectId owner, long revision) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch);
        if (binding.asset() != FencedRecoveryAsset.BODY || !binding.ownerId().equals(owner)
                || binding.ownerRevision() != revision) throw new IllegalArgumentException("death lacks exact body recovery authority");
        return retire(binding, FencedRecoveryDisposition.REJECT_STALE, "observed-body-death");
    }
    /**
     * Retires an unstarted effect because its exact canonical successor now owns the result.
     * This is neither a physical observation nor an ambiguity: a late projection of the old
     * effect is stale by construction and must not replay it.
     */
    public FencedRecoveryState compose(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = requireCurrent(bindingId, epoch);
        binding.require(FencedRecoveryPhase.PREPARED);
        return retire(binding, FencedRecoveryDisposition.REJECT_STALE, "canonical-composition");
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
        return new FencedRecoveryState(current, next, cargoRetirements);
    }

    /** Deterministic bounded retention policy; unknown late projections still reject by default. */
    private FencedRecoveryState compactForNewBinding(SubjectId incomingId) {
        if (current.size() + tombstones.size() < MAX_BINDINGS) return this;
        SubjectId retired = tombstones.keySet().stream().filter(id -> !id.equals(incomingId)).sorted().findFirst()
                .orElseGet(() -> tombstones.containsKey(incomingId) ? incomingId : null);
        if (retired == null) throw new IllegalArgumentException("recovery current-authority limit exceeded");
        Map<SubjectId, FencedRecoveryTombstone> next = new LinkedHashMap<>(tombstones); next.remove(retired);
        return new FencedRecoveryState(current, next, cargoRetirements);
    }

    private FencedRecoveryBinding requireCurrent(SubjectId bindingId, long epoch) {
        FencedRecoveryBinding binding = current.get(Objects.requireNonNull(bindingId, "recovery binding id"));
        if (binding == null || binding.authorityEpoch() != epoch) throw new IllegalArgumentException("recovery authority is stale or unavailable");
        return binding;
    }
    private FencedRecoveryState replace(FencedRecoveryBinding binding) {
        Map<SubjectId, FencedRecoveryBinding> next = new LinkedHashMap<>(current); next.put(binding.bindingId(), binding);
        return new FencedRecoveryState(next, tombstones, cargoRetirements);
    }
    private FencedRecoveryState retire(FencedRecoveryBinding binding, FencedRecoveryDisposition disposition, String reason) {
        Map<SubjectId, FencedRecoveryBinding> nextCurrent = new LinkedHashMap<>(current); nextCurrent.remove(binding.bindingId());
        Map<SubjectId, FencedRecoveryTombstone> nextTombstones = new LinkedHashMap<>(tombstones);
        nextTombstones.put(binding.bindingId(), new FencedRecoveryTombstone(binding.bindingId(), binding.asset(), binding.ownerId(),
                binding.ownerRevision(), binding.authorityEpoch(), disposition, reason));
        return new FencedRecoveryState(nextCurrent, nextTombstones, cargoRetirements);
    }
}
