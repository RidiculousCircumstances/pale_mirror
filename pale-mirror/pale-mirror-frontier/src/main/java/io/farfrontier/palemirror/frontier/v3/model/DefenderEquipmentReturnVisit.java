package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

/** The resolved parent owns this visit; UAE and common navigation own the actor and travel. */
public record DefenderEquipmentReturnVisit(PhysicalIntentId transferId, PhysicalIntentRoleBinding roles,
                                           ResourceStorageAccess access, PhysicalContainerSlot targetSlot,
                                           EquipmentServiceRequest request, Phase phase,
                                           Optional<ActorExecutionId> execution, Optional<MovementOrder> order) {
    public enum Phase { SELECTED, APPROACH, SERVICE, CLEARANCE }
    public DefenderEquipmentReturnVisit {
        Objects.requireNonNull(transferId); Objects.requireNonNull(roles); Objects.requireNonNull(access);
        Objects.requireNonNull(targetSlot); Objects.requireNonNull(request);
        Objects.requireNonNull(phase); execution = Objects.requireNonNull(execution); order = Objects.requireNonNull(order);
        if (roles.schema() != PhysicalIntentRoleSchema.ASSAULT_EQUIPMENT_RETURN)
            throw new IllegalArgumentException("defender return visit requires its complete nominal return schema");
        request.validateFor(PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT, PhysicalIntentKind.EQUIPMENT_RETURN, roles);
        if (!targetSlot.containerId().equals(access.containerId())
                || !request.identity().pointId().equals(access.containerId()))
            throw new IllegalArgumentException("defender return visit substituted its selected access or target slot");
        if (execution.isPresent() != (phase != Phase.SELECTED)
                || order.isPresent() != (phase == Phase.APPROACH || phase == Phase.CLEARANCE))
            throw new IllegalArgumentException("equipment visit phase lacks its declared activity/goal");
        if (execution.isPresent()) {
            var id = execution.orElseThrow();
            if (id.activityKind() != ActorActivityKind.EQUIPMENT_SERVICE
                    || !id.activityOwnerId().equals(roles.require(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT))
                    || !id.actorId().equals(roles.require(PhysicalIntentSubjectRole.ASSAULT_DEFENDER)))
                throw new IllegalArgumentException("equipment visit has a foreign activity declaration");
        }
        if (order.isPresent()) {
            var goal = order.orElseThrow();
            if (!goal.actorId().equals(execution.orElseThrow().actorId()) || !goal.ownerId().equals(execution.orElseThrow().activityOwnerId())
                    || goal.capability() != TraversalCapability.PEDESTRIAN
                    || phase == Phase.APPROACH && !access.stations().containsAll(goal.legalStations())
                    || phase == Phase.CLEARANCE && goal.legalStations().stream().anyMatch(station -> access.accessBoundary().occupied(station.standingBody())))
                throw new IllegalArgumentException("equipment visit substituted its semantic service destination");
        }
    }
    public DefenderEquipmentReturnVisit(PhysicalIntentId transferId, PhysicalIntentRoleBinding roles,
            ResourceStorageAccess access, PhysicalContainerSlot slot, EquipmentServiceRequest request) {
        this(transferId, roles, access, slot, request, Phase.SELECTED, Optional.empty(), Optional.empty());
    }
    public DefenderEquipmentReturnVisit withExecution(ActorExecutionId successor) {
        return new DefenderEquipmentReturnVisit(transferId, roles, access, targetSlot, request, phase, Optional.of(successor), order);
    }
    public SubjectId actorId() { return roles.require(PhysicalIntentSubjectRole.ASSAULT_DEFENDER); }
    public SubjectId itemId() { return roles.require(PhysicalIntentSubjectRole.EQUIPMENT); }
    public SubjectId ownerId() { return roles.require(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT); }
}
