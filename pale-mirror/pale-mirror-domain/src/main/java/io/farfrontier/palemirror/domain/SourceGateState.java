package io.farfrontier.palemirror.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical progression for a source-provided gate. PM stores only its
 * neutral plan and cleared logical parts; physical forms stay outside domain.
 */
public final class SourceGateState {
    private SourceGateStatus status;
    private GatePlanRef plan;
    private int currentPhaseIndex;
    private final Set<String> destroyedPartIds;

    public SourceGateState() { this(SourceGateStatus.INACTIVE, null, 0, Set.of()); }

    public SourceGateState(SourceGateStatus status, GatePlanRef plan, int currentPhaseIndex,
                           Set<String> destroyedPartIds) {
        this.status = Objects.requireNonNull(status, "status");
        this.plan = plan;
        this.currentPhaseIndex = currentPhaseIndex;
        this.destroyedPartIds = new LinkedHashSet<>(destroyedPartIds == null ? Set.of() : destroyedPartIds);
        if (status == SourceGateStatus.ACTIVE) validateActiveState();
        if (status != SourceGateStatus.ACTIVE && plan == null && currentPhaseIndex != 0) {
            throw new IllegalArgumentException("Inactive gate cannot have a phase index");
        }
    }

    public SourceGateStatus status() { return status; }
    public Optional<GatePlanRef> plan() { return Optional.ofNullable(plan); }
    public int currentPhaseIndex() { return currentPhaseIndex; }
    public Set<String> destroyedPartIds() { return Set.copyOf(destroyedPartIds); }
    public Optional<GatePhaseRef> currentPhase() {
        return status == SourceGateStatus.ACTIVE ? Optional.of(plan.phases().get(currentPhaseIndex)) : Optional.empty();
    }
    public boolean controllerVulnerable() { return !status.protectsController(); }

    public boolean pending() {
        if (status != SourceGateStatus.INACTIVE) return false;
        status = SourceGateStatus.PENDING;
        return true;
    }

    public boolean activate(GatePlanRef value) {
        if (status != SourceGateStatus.PENDING) return false;
        plan = Objects.requireNonNull(value, "value");
        currentPhaseIndex = 0;
        destroyedPartIds.clear();
        status = SourceGateStatus.ACTIVE;
        return true;
    }

    public boolean bypass() {
        if (status != SourceGateStatus.PENDING) return false;
        status = SourceGateStatus.BYPASSED;
        return true;
    }

    public boolean partDestroyed(String partId) {
        Objects.requireNonNull(partId, "partId");
        if (status != SourceGateStatus.ACTIVE) return false;
        GatePhaseRef phase = plan.phases().get(currentPhaseIndex);
        if (!phase.requiredPartIds().contains(partId) || !destroyedPartIds.add(partId)) return false;
        if (destroyedPartIds.containsAll(phase.requiredPartIds())) {
            if (currentPhaseIndex + 1 == plan.phases().size()) status = SourceGateStatus.UNSEALED;
            else currentPhaseIndex++;
        }
        return true;
    }

    public void reset() {
        status = SourceGateStatus.INACTIVE;
        plan = null;
        currentPhaseIndex = 0;
        destroyedPartIds.clear();
    }

    private void validateActiveState() {
        if (plan == null || currentPhaseIndex < 0 || currentPhaseIndex >= plan.phases().size()) {
            throw new IllegalArgumentException("Active gate requires a valid pinned plan and phase index");
        }
        Set<String> allParts = new LinkedHashSet<>();
        plan.phases().forEach(phase -> allParts.addAll(phase.requiredPartIds()));
        if (!allParts.containsAll(destroyedPartIds)) {
            throw new IllegalArgumentException("Gate contains destroyed part outside its pinned plan");
        }
    }
}
