package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One settlement's current exact daily food cycle.  The amounts are allocations against named
 * item stacks, not a second food ledger: every fulfilled ration removes the same exact item
 * count from {@link ExactInventory}.
 */
public record SettlementProvision(SubjectId settlementId, int cycleOrdinal, long startedAtTick, int requiredRations,
                                  int fulfilledRations, List<SubjectId> recipientIds, List<SettlementRationAllocation> allocations, int nextAllocation,
                                  SettlementProvisionStatus status, Optional<PhysicalIntentId> activeIntentId) {
    public SettlementProvision {
        Objects.requireNonNull(settlementId, "provision settlement");
        if (cycleOrdinal < 0 || startedAtTick < 0 || requiredRations < 0 || fulfilledRations < 0 || fulfilledRations > requiredRations) {
            throw new IllegalArgumentException("invalid settlement provision counters");
        }
        recipientIds = List.copyOf(Objects.requireNonNull(recipientIds, "provision recipients")); allocations = List.copyOf(allocations);
        if (recipientIds.size() > HumanPopulation.MAX_RESIDENTS) throw new IllegalArgumentException("settlement provision recipient retention limit exceeded");
        if (recipientIds.size() != requiredRations || recipientIds.stream().distinct().count() != recipientIds.size()) {
            throw new IllegalArgumentException("settlement provision recipients must exactly name every required ration");
        }
        if (allocations.size() > HumanPopulation.MAX_RESIDENTS) {
            throw new IllegalArgumentException("settlement provision allocation retention limit exceeded");
        }
        if (nextAllocation < 0 || nextAllocation > allocations.size()) throw new IllegalArgumentException("invalid provision allocation cursor");
        if (allocations.stream().map(SettlementRationAllocation::itemId).distinct().count() != allocations.size()) {
            throw new IllegalArgumentException("one provision cycle may allocate each exact item once");
        }
        List<SubjectId> allocatedRecipients = allocations.stream().flatMap(allocation -> allocation.recipientIds().stream()).toList();
        if (allocatedRecipients.stream().distinct().count() != allocatedRecipients.size() || !recipientIds.containsAll(allocatedRecipients)) {
            throw new IllegalArgumentException("provision allocations must name distinct current-cycle recipients");
        }
        Objects.requireNonNull(status, "provision status"); Objects.requireNonNull(activeIntentId, "provision active intent");
        int allocated = allocations.stream().mapToInt(SettlementRationAllocation::count).sum();
        if (allocated > requiredRations || (status == SettlementProvisionStatus.IN_PROGRESS && fulfilledRations > allocated)) {
            throw new IllegalArgumentException("invalid settlement provision allocation total");
        }
        if (status == SettlementProvisionStatus.IN_PROGRESS && nextAllocation == allocations.size() && activeIntentId.isEmpty()) {
            throw new IllegalArgumentException("completed provision allocation may not remain in progress");
        }
        if (status != SettlementProvisionStatus.IN_PROGRESS && (activeIntentId.isPresent() || !allocations.isEmpty() || nextAllocation != 0)) {
            throw new IllegalArgumentException("terminal provision state may not retain an active physical intent");
        }
    }

    public static SettlementProvision idle(SubjectId settlementId) {
        return new SettlementProvision(settlementId, 0, 0L, 0, 0, List.of(), List.of(), 0, SettlementProvisionStatus.IDLE, Optional.empty());
    }

    public static SettlementProvision started(SubjectId settlementId, int cycleOrdinal, long tick, int required,
                                       List<SubjectId> recipientIds, List<SettlementRationAllocation> allocations) {
        if (allocations.isEmpty()) {
            return new SettlementProvision(settlementId, cycleOrdinal, tick, required, 0, recipientIds, List.of(), 0,
                    SettlementProvisionStatus.SHORTAGE, Optional.empty());
        }
        return new SettlementProvision(settlementId, cycleOrdinal, tick, required, 0, recipientIds, allocations, 0,
                SettlementProvisionStatus.IN_PROGRESS, Optional.empty());
    }

    public SettlementRationAllocation currentAllocation() {
        if (status != SettlementProvisionStatus.IN_PROGRESS || activeIntentId.isPresent() || nextAllocation >= allocations.size()) {
            throw new IllegalStateException("settlement provision has no ready allocation");
        }
        return allocations.get(nextAllocation);
    }

    public SettlementProvision beginPhysical(PhysicalIntentId intentId) {
        if (status != SettlementProvisionStatus.IN_PROGRESS || activeIntentId.isPresent()) throw new IllegalStateException("provision is not ready for physical consumption");
        return new SettlementProvision(settlementId, cycleOrdinal, startedAtTick, requiredRations, fulfilledRations, recipientIds, allocations,
                nextAllocation, status, Optional.of(Objects.requireNonNull(intentId, "provision intent")));
    }

    public SettlementProvision consumeCurrent(SubjectId itemId, int count) {
        SettlementRationAllocation allocation = currentOrActiveAllocation();
        if (!allocation.itemId().equals(itemId) || allocation.count() != count) throw new IllegalArgumentException("provision receipt does not match current allocation");
        int fulfilled = Math.addExact(fulfilledRations, count); int next = Math.addExact(nextAllocation, 1);
        if (next < allocations.size()) return new SettlementProvision(settlementId, cycleOrdinal, startedAtTick, requiredRations, fulfilled,
                recipientIds, allocations, next, SettlementProvisionStatus.IN_PROGRESS, Optional.empty());
        return new SettlementProvision(settlementId, cycleOrdinal, startedAtTick, requiredRations, fulfilled, recipientIds, List.of(), 0,
                fulfilled == requiredRations ? SettlementProvisionStatus.SECURE : SettlementProvisionStatus.RATIONED, Optional.empty());
    }

    public SettlementProvision shortage() {
        if (status != SettlementProvisionStatus.IN_PROGRESS || activeIntentId.isPresent()) throw new IllegalStateException("provision is not ready for shortage resolution");
        return new SettlementProvision(settlementId, cycleOrdinal, startedAtTick, requiredRations, fulfilledRations, recipientIds, List.of(), 0,
                fulfilledRations == 0 ? SettlementProvisionStatus.SHORTAGE : SettlementProvisionStatus.RATIONED, Optional.empty());
    }

    public SettlementProvision conflict() {
        if (status != SettlementProvisionStatus.IN_PROGRESS) throw new IllegalStateException("only current provision can conflict");
        return new SettlementProvision(settlementId, cycleOrdinal, startedAtTick, requiredRations, fulfilledRations, recipientIds, List.of(), 0,
                SettlementProvisionStatus.CONFLICT, Optional.empty());
    }

    public SettlementRationAllocation currentOrActiveAllocation() {
        if (status != SettlementProvisionStatus.IN_PROGRESS || nextAllocation >= allocations.size()) throw new IllegalStateException("provision has no active allocation");
        return allocations.get(nextAllocation);
    }
}
