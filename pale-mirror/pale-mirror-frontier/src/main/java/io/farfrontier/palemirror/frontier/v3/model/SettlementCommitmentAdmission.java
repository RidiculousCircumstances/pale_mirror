package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** Shared operation admission. No second roster, resource ledger or mutable reservation cache. */
public final class SettlementCommitmentAdmission {
    public interface FacilityView { java.util.stream.Stream<SubjectId> committedFacilities(FrontierWorldState state); }
    private final List<FacilityView> owners;
    public SettlementCommitmentAdmission(List<FacilityView> owners) {
        this.owners = List.copyOf(owners);
        if (owners.isEmpty() || owners.size() > 32) throw new IllegalArgumentException("facility owners require bounded registration");
    }
    public record Request(SubjectId taskId, SubjectId settlementId, SubjectId facilityId, List<SubjectId> participants) {
        public Request {
            Objects.requireNonNull(taskId); Objects.requireNonNull(settlementId); Objects.requireNonNull(facilityId);
            participants = List.copyOf(participants);
        }
    }

    /** Read-only offer precondition. Refusal must not create a resident-owned waiting job. */
    public boolean facilityAvailable(FrontierWorldState state, SubjectId facilityId) {
        Objects.requireNonNull(state); Objects.requireNonNull(facilityId);
        return owners.stream().flatMap(owner -> owner.committedFacilities(state)).noneMatch(facilityId::equals);
    }
    public void require(FrontierWorldState state, Request request) {
        requireParticipants(state, request);
        if (!facilityAvailable(state, request.facilityId()))
            throw new IllegalArgumentException("operation facility already committed: " + request.facilityId().value());
    }

    /** Participant/decision admission is independent of a family's exact target capacity. */
    public void requireParticipants(FrontierWorldState state, Request request) {
        StrategicTask task = state.strategicPlans().tasks().get(request.taskId());
        if (task == null || !task.ownerId().equals(request.settlementId())
                || task.status() != StrategicTaskStatus.ACTIVE && task.status() != StrategicTaskStatus.PENDING)
            throw new IllegalArgumentException("operation admission has no live owner task");
        DecisionAuthority authority = state.strategicPlans().requireDecisionAuthority(request.settlementId());
        DecisionPolicyRegistry.require(authority);
        if (authority.kind() != DecisionAuthorityKind.SETTLEMENT || !task.authorityId().equals(authority.ownerId())
                || task.authorityEpoch() != authority.reconsiderationEpoch())
            throw new IllegalArgumentException("operation admission has a stale decision authority");
        if (state.bootstrap().settlements().stream().filter(settlement -> settlement.id().equals(request.settlementId()))
                .flatMap(settlement -> settlement.structures().stream()).noneMatch(facility -> facility.id().equals(request.facilityId())))
            throw new IllegalArgumentException("operation admission requests a foreign facility");
        SettlementWorkforce.requireAvailable(state, request.settlementId(), request.participants());
    }
}
