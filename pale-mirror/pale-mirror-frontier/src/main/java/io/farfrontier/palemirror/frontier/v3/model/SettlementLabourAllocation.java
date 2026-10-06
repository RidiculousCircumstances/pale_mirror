package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import java.util.List;
import java.util.Set;

/** Read-only allocation policy. Retained family jobs and groups remain the only assignment truth. */
public final class SettlementLabourAllocation {
    private SettlementLabourAllocation() { }

    public static boolean canCommitToMission(FrontierWorldState state, SubjectId settlementId,
                                             ResidentWorkKind kind, List<SubjectId> participants) {
        if (participants.isEmpty() || participants.size() > 64
                || participants.stream().distinct().count() != participants.size()) return false;
        var policy = SettlementWorkPolicy.permissions(state, settlementId);
        var assignments = HumanAssignmentProjection.compile(state);
        for (SubjectId actor : participants) {
            var resident = state.humanPopulation().resident(actor);
            if (resident == null || !resident.settlementId().equals(settlementId)
                    || !policy.permits(kind, actor) || !assignments.idle(actor)
                    || !SettlementWorkforce.availableForNewAssignment(state, resident)
                    || !ActorExecutionCoordinator.ordinaryWorkAdmission(state, actor).permitted()) return false;
        }
        Set<SubjectId> away = state.unitGroups().groups().values().stream()
                .filter(group -> group.phase() != UnitGroup.Phase.CLOSED)
                .flatMap(group -> group.members().stream()).map(UnitGroup.Member::actorId)
                .collect(java.util.stream.Collectors.toSet());
        state.shipments().shipments().values().stream().filter(shipment -> !shipment.terminal())
                .map(shipment -> shipment.execution().actorId()).forEach(away::add);
        for (var reserve : policy.minimumLocalStaff().entrySet()) {
            long before = policy.workers(reserve.getKey()).stream().filter(id -> !away.contains(id))
                    .filter(id -> state.actorLocations().containsKey(id)
                            && state.actorLocations().get(id).condition().status() == ActorLifeStatus.ALIVE).count();
            long after = policy.workers(reserve.getKey()).stream().filter(id -> !away.contains(id) && !participants.contains(id))
                    .filter(id -> state.actorLocations().containsKey(id)
                            && state.actorLocations().get(id).condition().status() == ActorLifeStatus.ALIVE).count();
            if (after < Math.min(before, reserve.getValue())) return false;
        }
        return true;
    }

    public static void requireMissionCommitment(FrontierWorldState state, SubjectId settlementId,
                                                ResidentWorkKind kind, List<SubjectId> participants) {
        if (!canCommitToMission(state, settlementId, kind, participants))
            throw new IllegalArgumentException("mission violates work permission, current assignment or local staffing reserve: "
                    + settlementId.value() + " / " + participants);
    }
}
