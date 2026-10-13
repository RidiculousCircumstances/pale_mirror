package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import java.util.*;

/** Mission-owned parcel scheduling. One declared group execution operates an asset, never one execution per stack. */
public final class TransportCargoExecution {
    private TransportCargoExecution() { }
    public static boolean shared(Shipment shipment) { return shipment.execution().activityKind() == ActorActivityKind.GROUP_MEMBER; }
    public static void require(ShipmentState state, Shipment shipment) {
        if (!shared(shipment)) throw new IllegalArgumentException("parcel has no declared group execution");
        var mission = state.missions().get(shipment.transportMissionId().orElseThrow());
        if (mission == null || !mission.groupId().equals(shipment.execution().activityOwnerId())
                || !mission.shipmentIds().contains(shipment.id()))
            throw new IllegalArgumentException("parcel lost its exact declared mission/group execution");
    }
    public static List<Shipment> parcels(FrontierWorldState state, UnitGroup group, ActorExecutionId execution) {
        if (execution.activityKind() != ActorActivityKind.GROUP_MEMBER || !execution.activityOwnerId().equals(group.id()))
            throw new IllegalArgumentException("cargo execution names a foreign group");
        group.member(execution.actorId());
        var mission = io.farfrontier.palemirror.frontier.v3.model.group.TransportGroupMissionPort.mission(state, group);
        return mission.shipmentIds().stream().map(state.shipments().shipments()::get)
                .filter(s -> shared(s) && s.execution().actorId().equals(execution.actorId())).toList();
    }
    public static Optional<Shipment> active(FrontierWorldState state, Shipment shipment) {
        if (!shared(shipment)) return Optional.of(shipment);
        require(state.shipments(), shipment);
        var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
        var group = state.unitGroups().groups().get(mission.groupId());
        return active(state, group, shipment.execution());
    }
    public static Optional<Shipment> active(FrontierWorldState state, UnitGroup group, ActorExecutionId execution) {
        var mission = io.farfrontier.palemirror.frontier.v3.model.group.TransportGroupMissionPort.mission(state, group);
        return parcels(state, group, execution).stream().filter(s -> !s.terminal() || s.reception().isPresent())
                .filter(s -> mission.stage() == TransportMission.Stage.LOADING ? s.status() == Shipment.Status.AWAITING_LOAD
                        : mission.stage() == TransportMission.Stage.UNLOADING)
                .findFirst(); // Explicit retained manifest order, not discovery by collection membership.
    }
    public static boolean selected(FrontierWorldState state, Shipment shipment) {
        return active(state, shipment).map(s -> s.id().equals(shipment.id())).orElse(false);
    }
    public static ActorExecutionState afterParcelClosed(FrontierWorldState state, Shipment shipment) {
        return afterParcelClosed(state.actorExecutions(), shipment);
    }
    public static ActorExecutionState afterParcelClosed(ActorExecutionState executions, Shipment shipment) {
        // The group retains travel/return purpose and owns its final release.
        return shared(shipment) ? executions : ActorExecutionComposition.LIFECYCLE.retire(executions,
                shipment.execution().actorId(), shipment.execution().activityKind(), shipment.id());
    }
}
