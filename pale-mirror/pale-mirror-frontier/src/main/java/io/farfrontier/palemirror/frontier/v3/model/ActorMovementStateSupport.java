package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;

import java.util.Map;

/** Bounded reference closure for the canonical movement aggregate. */
final class ActorMovementStateSupport {
    private ActorMovementStateSupport() { }

    static void validate(Map<SubjectId, ActorMovement> movements, Map<SubjectId, ActorLocation> actors,
                         HumanPopulation people, ExactInventory inventory, ActorExecutionState executions, ShipmentState shipments,
                         io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState groups) {
        if (movements.size() > 4_096)
            throw new IllegalArgumentException("actor movement retention bound exceeded");
        for (var id : executions.current(ActorActivityKind.SERVICE_EXIT).values()) {
            ActorMovement movement = movements.get(id.actorId());
            if (movement == null || !movement.executionId().equals(id))
                throw new IllegalArgumentException("service-exit execution lost its exact movement");
        }
        for (var entry : movements.entrySet()) {
            executions.requireCurrent(entry.getValue().executionId());
            if (!entry.getKey().equals(entry.getValue().order().actorId())
                    || !actors.containsKey(entry.getKey()) || people.meals().containsKey(entry.getKey()))
                throw new IllegalArgumentException("actor movement must bind one exact actor without a competing meal owner");
            switch (entry.getValue().context()) {
                case ActorMovementContext.ServiceExit exit -> {
                    ResidentProfile resident = people.resident(entry.getKey());
                    if (entry.getValue().executionId().activityKind() != ActorActivityKind.SERVICE_EXIT
                            || resident == null
                            || !FrontierWorldState.depotId(exit.settlementId()).equals(exit.depotId())
                            || entry.getValue().order().capability() != TraversalCapability.PEDESTRIAN
                            || !inventory.containers().containsKey(exit.depotId())
                            || !inventory.containers().get(exit.depotId()).ownerId().equals(exit.settlementId()))
                        throw new IllegalArgumentException("actor movement service exit lacks its declared resident, settlement or depot");
                }
                case ActorMovementContext.ShipmentLeg leg -> {
                    Shipment shipment = shipments.shipments().get(leg.shipmentId());
                    if (shipment == null || shipment.terminal() || shipment.revision() != leg.shipmentRevision()
                            || !shipment.execution().equals(entry.getValue().executionId())
                            || !shipment.movementOrder().equals(entry.getValue().order()))
                        throw new IllegalArgumentException("movement shipment leg lost its exact live declaration");
                }
                case ActorMovementContext.GroupLeg leg -> {
                    var group = groups.groups().get(leg.groupId()); var movement = entry.getValue();
                    if (group == null || group.phase() != io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Phase.TRAVELLING
                            || group.revision() != leg.groupRevision() || !movement.order().ownerId().equals(group.id())
                            || movement.order().goalRevision() != group.revision() || movement.order().goalOrdinal() != group.goalOrdinal()
                            || !movement.order().legalStations().equals(java.util.List.of(group.journey().orElseThrow().stations().get(entry.getKey()))))
                        throw new IllegalArgumentException("group movement lost its exact formation declaration");
                    group.member(entry.getKey());
                }
                case ActorMovementContext.ExpeditionSupply supply -> {
                    var mission = shipments.missions().get(supply.missionId()); var movement = entry.getValue();
                    if (mission == null || mission.stage() != TransportMission.Stage.LOADING || mission.supplies().isEmpty())
                        throw new IllegalArgumentException("supply movement lost its exact loading mission");
                    var load = mission.supplies().orElseThrow(); var allocation = load.allocations().stream()
                            .filter(a -> a.claimId().equals(supply.claimId())).findFirst().orElseThrow();
                    var group = groups.groups().get(mission.groupId());
                    if (!allocation.claimId().equals(supply.claimId()) || !allocation.actorId().equals(entry.getKey())
                            || allocation.loaded() || allocation.pending().isPresent() || !load.order(mission.id(), mission.sender(), allocation).movementOrder().equals(movement.order())
                            || group == null || !group.member(entry.getKey()).activityOwnerId().equals(movement.executionId().activityOwnerId()))
                        throw new IllegalArgumentException("supply movement lost its exact order or participant authority");
                }
                case ActorMovementContext.ExpeditionAssembly assembly -> {
                    var mission = shipments.missions().get(assembly.missionId()); var movement = entry.getValue();
                    if (mission == null || mission.stage() != TransportMission.Stage.LOADING || mission.supplies().isEmpty()
                            || !io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.assemblyOrder(mission, entry.getKey()).equals(movement.order())
                            || mission.supplies().orElseThrow().allocations().stream().anyMatch(a -> a.actorId().equals(entry.getKey()) && !a.loaded()))
                        throw new IllegalArgumentException("assembly movement lost its exact provisioned participant");
                    var group = groups.groups().get(mission.groupId());
                    if (group == null || !group.member(entry.getKey()).activityOwnerId().equals(movement.executionId().activityOwnerId()))
                        throw new IllegalArgumentException("assembly movement has foreign participant authority");
                }
            }
        }
    }
}
