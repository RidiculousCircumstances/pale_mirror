package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure tactical-readiness view of the exact people retained by one active assault. */
public record SettlementDefenderReadinessProjection(
        SubjectId assaultId,
        SubjectId unitId,
        List<SubjectId> livingAssignedMembers,
        List<SubjectId> armedLivingAssignedMembers,
        boolean leaderOperational,
        SettlementDefenderReadinessStatus status
) {
    public SettlementDefenderReadinessProjection {
        Objects.requireNonNull(assaultId, "readiness assault");
        Objects.requireNonNull(unitId, "readiness unit");
        livingAssignedMembers = List.copyOf(livingAssignedMembers);
        armedLivingAssignedMembers = List.copyOf(armedLivingAssignedMembers);
        Objects.requireNonNull(status, "readiness status");
        if (livingAssignedMembers.stream().distinct().count() != livingAssignedMembers.size()
                || armedLivingAssignedMembers.stream().distinct().count() != armedLivingAssignedMembers.size()
                || !livingAssignedMembers.containsAll(armedLivingAssignedMembers)
                || status == SettlementDefenderReadinessStatus.UNAVAILABLE != livingAssignedMembers.isEmpty()
                || status == SettlementDefenderReadinessStatus.IMPROVISED != (!livingAssignedMembers.isEmpty() && armedLivingAssignedMembers.isEmpty())
                || status == SettlementDefenderReadinessStatus.DEGRADED != (!livingAssignedMembers.isEmpty()
                && !armedLivingAssignedMembers.isEmpty() && !leaderOperational)
                || status == SettlementDefenderReadinessStatus.READY != (!livingAssignedMembers.isEmpty()
                && !armedLivingAssignedMembers.isEmpty() && leaderOperational)) {
            throw new IllegalArgumentException("defender readiness must match exact members, equipment and leader state");
        }
    }

    public static SettlementDefenderReadinessProjection derive(FrontierWorldState state, SettlementAssault assault) {
        Objects.requireNonNull(state, "readiness state");
        Objects.requireNonNull(assault, "readiness assault");
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        List<SubjectId> livingAssigned = assault.defenderIds().stream().filter(id -> assignedTo(state, assignments, id, assault.id()))
                .filter(id -> RouteEngagementCombatRules.alive(state, id)).toList();
        List<SubjectId> armed = livingAssigned.stream().filter(id -> HumanTacticalFunctionProjection.hasWeapon(state, id)).toList();
        boolean leaderOperational = livingAssigned.contains(assault.defenderUnit().leaderId());
        SettlementDefenderReadinessStatus status = livingAssigned.isEmpty() ? SettlementDefenderReadinessStatus.UNAVAILABLE
                : armed.isEmpty() ? SettlementDefenderReadinessStatus.IMPROVISED
                : leaderOperational ? SettlementDefenderReadinessStatus.READY : SettlementDefenderReadinessStatus.DEGRADED;
        return new SettlementDefenderReadinessProjection(assault.id(), assault.defenderUnit().id(), livingAssigned, armed, leaderOperational, status);
    }

    /** The active assault that owns this resident's exclusive defence assignment, if any. */
    public static Optional<SettlementAssault> owningActiveAssault(FrontierWorldState state, SubjectId residentId) {
        Objects.requireNonNull(state, "readiness state");
        Objects.requireNonNull(residentId, "readiness resident");
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(residentId);
        if (assignment.kind() != HumanAssignmentKind.SETTLEMENT_DEFENCE) return Optional.empty();
        return assignment.ownerId().map(state.strategicPlans().settlementAssaults()::get)
                .filter(assault -> assault.status() != SettlementAssaultStatus.RESOLVED && assault.defenderIds().contains(residentId));
    }

    private static boolean assignedTo(FrontierWorldState state, HumanAssignmentProjection assignments, SubjectId residentId, SubjectId assaultId) {
        HumanAssignment assignment = assignments.assignment(residentId);
        return assignment.kind() == HumanAssignmentKind.SETTLEMENT_DEFENCE
                && assignment.ownerId().filter(assaultId::equals).isPresent()
                && state.humanPopulation().resident(residentId) != null;
    }
}
