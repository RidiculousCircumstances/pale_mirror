package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Map;

/** Family-owned closure for an explicitly selected relationship, not discovery or live availability. */
final class DefenderEquipmentReturnVisitReferences {
    private DefenderEquipmentReturnVisitReferences() { }
    static void validate(StrategicPlanState plans, ExactInventory inventory, Map<SubjectId, ActorLocation> actors,
                         Map<PhysicalIntentId, PhysicalIntent> intents, io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState executions) {
        var selectedItems = new java.util.HashSet<SubjectId>();
        var selectedActors = new java.util.HashSet<SubjectId>();
        for (var assault : plans.settlementAssaults().values()) {
            var visit = assault.equipmentReturnVisit().orElse(null);
            if (visit == null) continue;
            visit.execution().ifPresent(executions::requireRetained);
            if (!actors.containsKey(visit.actorId()) || !inventory.containers().containsKey(visit.access().containerId())
                    || !selectedItems.add(visit.itemId()) || !selectedActors.add(visit.actorId()))
                throw new IllegalArgumentException("selected equipment return lost or duplicated its exact actor/container/item relationship");
            var intent = intents.get(visit.transferId());
            if (intent != null && (intent.kind() != PhysicalIntentKind.EQUIPMENT_RETURN
                    || !intent.roles().equals(visit.roles()) || !intent.targetSlot().equals(java.util.Optional.of(visit.targetSlot()))
                    || !intent.equipmentServiceRequest().equals(java.util.Optional.of(visit.request()))
                    || intent.lifecycleOwner() != PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT))
                throw new IllegalArgumentException("selected equipment return has a substituted transfer declaration");
            if (intent == null || intent.status() != PhysicalIntentStatus.CONFIRMED) {
                var item = inventory.items().get(visit.itemId());
                if (item == null || !item.economicOwnerId().equals(assault.settlementId())
                        || !item.custody().equals(new InventoryCustody.Actor(visit.actorId())))
                    throw new IllegalArgumentException("unsettled equipment return lost its exact owned actor-held stock");
            }
        }
        for (var execution : executions.current(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.EQUIPMENT_SERVICE).values()) requireOwner(plans, execution);
        for (var execution : executions.suspended())
            if (execution.activityKind() == io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.EQUIPMENT_SERVICE) requireOwner(plans, execution);
    }
    private static void requireOwner(StrategicPlanState plans, io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) {
        var parent = plans.settlementAssaults().get(execution.activityOwnerId());
        if (parent == null || parent.equipmentReturnVisit().flatMap(DefenderEquipmentReturnVisit::execution).filter(execution::equals).isEmpty())
            throw new IllegalArgumentException("equipment service execution lost its exact selected visit");
    }
}
