package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Exact family relationship lookup. A resolved combat owner never supplies current combat authority. */
public final class DefenderEquipmentReturnAuthority {
    private DefenderEquipmentReturnAuthority() { }
    public static DefenderEquipmentReturnVisit require(FrontierWorldState state, ActorExecutionId execution) {
        var parent = state.strategicPlans().settlementAssaults().get(execution.activityOwnerId());
        var visit = parent == null ? null : parent.equipmentReturnVisit().orElse(null);
        if (execution.activityKind() != ActorActivityKind.EQUIPMENT_SERVICE || parent == null
                || parent.status() != SettlementAssaultStatus.RESOLVED || visit == null
                || !visit.execution().equals(Optional.of(execution)))
            throw new IllegalArgumentException("equipment service lacks its exact resolved-parent visit/activity");
        state.actorExecutions().requireRetained(execution);
        return visit;
    }
    public static boolean pendingEffect(FrontierWorldState state, DefenderEquipmentReturnVisit visit) {
        var intent = state.physicalIntents().get(visit.transferId());
        return intent != null && intent.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED;
    }
}
