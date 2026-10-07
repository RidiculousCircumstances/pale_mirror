package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts;
import java.util.List;
import java.util.Optional;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;

/** Derived ownership edges and the publication/recovery barrier; no second persisted roster. */
final class UnitGroupRelationships {
    private UnitGroupRelationships() { }
    static void collect(FrontierWorldState state, List<Edge> edges) {
        for (var claim : state.inventory().fungibleResources().claims().values()) {
            if (claim.purpose() != ClaimPurpose.EXPEDITION_SUPPLY) continue;
            var mission = state.shipments().missions().get(claim.claimantId());
            if (mission == null || mission.supplies().stream().flatMap(load -> load.allocations().stream())
                    .noneMatch(a -> !a.loaded() && !a.withdrawn() && a.claimId().equals(claim.id())))
                throw new IllegalArgumentException("provisioning claim has no exact retained loading obligation");
        }
        for (var group : state.unitGroups().groups().values()) {
            var port = UnitGroupMissionPorts.require(group); port.validate(state, group);
            var owner = new SubjectEndpoint(EntityKind.UNIT_GROUP, group.id());
            var life = group.phase() == UnitGroup.Phase.CLOSED ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE;
            edges.add(declaredEdge(Kind.GROUP_MISSION, owner, owner,
                    new SubjectEndpoint(EntityKind.TRANSPORT_MISSION, group.mission().id()), life, "group:" + group.id().value()));
            for (var member : group.members()) edges.add(declaredEdge(Kind.GROUP_MEMBER, owner, owner,
                    new SubjectEndpoint(EntityKind.RESIDENT, member.actorId()), life, "group:" + group.id().value()));
        }
        for (var mission : state.shipments().missions().values()) {
            var group = state.unitGroups().groups().get(mission.groupId());
            if (group == null || !group.mission().equals(new UnitGroup.Mission(UnitGroup.MissionKind.TRANSPORT, mission.id()))
                    || mission.stage() == TransportMission.Stage.COMPLETE && group.phase() != UnitGroup.Phase.CLOSED
                    || group.phase() == UnitGroup.Phase.CLOSED && mission.stage() != TransportMission.Stage.RETURNING
                        && mission.stage() != TransportMission.Stage.COMPLETE)
                throw new IllegalArgumentException("transport mission lost its exact group or legal closing boundary");
            io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.validate(state, mission, group);
            var owner = new SubjectEndpoint(EntityKind.TRANSPORT_MISSION, mission.id());
            var life = mission.stage() == TransportMission.Stage.COMPLETE ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE;
            edges.add(declaredEdge(Kind.TRANSPORT_GROUP, owner, owner,
                    new SubjectEndpoint(EntityKind.UNIT_GROUP, group.id()), life, "transport:" + mission.id().value()));
            for (var id : mission.shipmentIds()) {
                var shipment = state.shipments().shipments().get(id);
                if (shipment == null || !shipment.transportMissionId().equals(Optional.of(mission.id())))
                    throw new IllegalArgumentException("mission lost its exact shipment reference");
                edges.add(declaredEdge(Kind.TRANSPORT_SHIPMENT, owner, owner,
                        new SubjectEndpoint(EntityKind.SHIPMENT, id), life, "transport:" + mission.id().value()));
            }
            mission.supplies().ifPresent(load -> {
                for (var a : load.allocations()) if (!a.loaded() && !a.withdrawn()) {
                    edges.add(declaredEdge(Kind.TRANSPORT_SUPPLY_CLAIM, owner, owner,
                            new SubjectEndpoint(EntityKind.RESOURCE_CLAIM, a.claimId()), Lifecycle.ACTIVE, "supply:" + a.claimId().value()));
                    edges.add(declaredEdge(Kind.TRANSPORT_SUPPLY_SOURCE, owner, owner,
                            new SubjectEndpoint(EntityKind.RESOURCE_ACCOUNT, a.sourceAccountId()), Lifecycle.ACTIVE, "supply:" + a.claimId().value()));
                }
            });
        }
        for (var shipment : state.shipments().shipments().values()) if (shipment.transportMissionId().isPresent()) {
            var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
            if (mission == null || !mission.shipmentIds().contains(shipment.id()))
                throw new IllegalArgumentException("shipment lost its exact owning transport mission");
            var owner = new SubjectEndpoint(EntityKind.SHIPMENT, shipment.id());
            edges.add(declaredEdge(Kind.SHIPMENT_MISSION, owner, owner,
                    new SubjectEndpoint(EntityKind.TRANSPORT_MISSION, mission.id()),
                    shipment.terminal() ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE, "shipment:" + shipment.id().value()));
        }
        for (var movement : state.actorMovements().values()) if (movement.context() instanceof ActorMovementContext.GroupLeg leg) {
            var group = state.unitGroups().groups().get(leg.groupId());
            if (group == null || !UnitGroupMissionPorts.require(group).execution(state, group, group.member(movement.order().actorId()))
                    .equals(Optional.of(movement.executionId())))
                throw new IllegalArgumentException("group movement lacks its nominal participant responsibility");
        }
    }
}
