package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts;
import java.util.List;
import java.util.Optional;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;

/** Derived ownership edges and the publication/recovery barrier; no second persisted roster. */
final class UnitGroupRelationships {
    private UnitGroupRelationships() { }
    static List<Object> validationInputs(FrontierWorldState state) {
        var inventory = state.inventory();
        return List.of(state.bootstrap(), state.unitGroups(), state.shipments(),
                ActorIdentityDeclarations.declaredKinds(state.bootstrap(), state.actorLocations()),
                ActorIdentityDeclarations.lifeStatuses(state.bootstrap(), state.actorLocations()),
                state.actorExecutions(), state.humanPopulation().residents(), inventory.fungibleResources(),
                inventory.containers(), inventory.surfaces(), inventory.economics().accounts(), inventory.economics().reservations(),
                state.companies().companies(), state.transportFleet(), state.extractionSites().deposits());
    }
    static void collect(FrontierWorldState state, List<Edge> edges) {
        // Full diagnostic/recovery entry points delegate to the same movement owner.
        ActorMovementStateSupport.validate(state);
        inspect(state, edges, true);
    }
    /** Publication needs the same owner checks, without constructing an unused diagnostic graph. */
    static void validate(FrontierWorldState state) { UnitGroupRelationshipChecks.validate(state); }
    private static void inspect(FrontierWorldState state, List<Edge> edges, boolean project) {
        for (var claim : state.inventory().fungibleResources().claims().values()) {
            validateClaim(state, claim);
        }
        for (var group : state.unitGroups().groups().values()) {
            validateGroup(state, group);
            if (project) {
                var owner = new SubjectEndpoint(EntityKind.UNIT_GROUP, group.id());
                var life = group.phase() == UnitGroup.Phase.CLOSED ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE;
                edges.add(declaredEdge(Kind.GROUP_MISSION, owner, owner,
                        new SubjectEndpoint(EntityKind.TRANSPORT_MISSION, group.mission().id()), life, "group:" + group.id().value()));
                for (var member : group.members()) edges.add(declaredEdge(Kind.GROUP_MEMBER, owner, owner,
                        new SubjectEndpoint(EntityKind.ACTOR, member.actorId()), life, "group:" + group.id().value()));
            }
        }
        for (var mission : state.shipments().missions().values()) {
            validateMission(state, mission);
            if (project) {
                var owner = new SubjectEndpoint(EntityKind.TRANSPORT_MISSION, mission.id());
                var life = mission.stage() == TransportMission.Stage.COMPLETE ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE;
                edges.add(declaredEdge(Kind.TRANSPORT_GROUP, owner, owner,
                        new SubjectEndpoint(EntityKind.UNIT_GROUP, mission.groupId()), life, "transport:" + mission.id().value()));
                mission.transportAssetId().ifPresent(actor -> edges.add(declaredEdge(Kind.TRANSPORT_ASSET, owner, owner,
                        new SubjectEndpoint(EntityKind.ACTOR, actor), life, "transport:" + mission.id().value())));
            }
            for (var id : mission.shipmentIds()) {
                if (project) {
                    var owner = new SubjectEndpoint(EntityKind.TRANSPORT_MISSION, mission.id());
                    var life = mission.stage() == TransportMission.Stage.COMPLETE ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE;
                    edges.add(declaredEdge(Kind.TRANSPORT_SHIPMENT, owner, owner,
                            new SubjectEndpoint(EntityKind.SHIPMENT, id), life, "transport:" + mission.id().value()));
                }
            }
            if (project) {
                var owner = new SubjectEndpoint(EntityKind.TRANSPORT_MISSION, mission.id());
                mission.supplies().ifPresent(load -> {
                    for (var a : load.allocations()) if (!a.loaded() && !a.withdrawn()) {
                        edges.add(declaredEdge(Kind.TRANSPORT_SUPPLY_CLAIM, owner, owner,
                                new SubjectEndpoint(EntityKind.RESOURCE_CLAIM, a.claimId()), Lifecycle.ACTIVE, "supply:" + a.claimId().value()));
                        edges.add(declaredEdge(Kind.TRANSPORT_SUPPLY_SOURCE, owner, owner,
                                new SubjectEndpoint(EntityKind.RESOURCE_ACCOUNT, a.sourceAccountId()), Lifecycle.ACTIVE, "supply:" + a.claimId().value()));
                    }
                });
                mission.replenishment().ifPresent(t -> {
                    edges.add(declaredEdge(Kind.TRANSPORT_SUPPLY_CLAIM, owner, owner,
                            new SubjectEndpoint(EntityKind.RESOURCE_CLAIM, t.claimId()), Lifecycle.ACTIVE, "refill:" + t.claimId().value()));
                    edges.add(declaredEdge(Kind.TRANSPORT_SUPPLY_SOURCE, owner, owner,
                            new SubjectEndpoint(EntityKind.RESOURCE_ACCOUNT, t.sourceAccountId()), Lifecycle.ACTIVE, "refill:" + t.claimId().value()));
                });
                mission.replenishmentPurchase().ifPresent(p -> edges.add(declaredEdge(Kind.TRANSPORT_PURCHASE, owner, owner,
                        new SubjectEndpoint(EntityKind.FINANCIAL_RESERVATION, p.payment().id()), Lifecycle.ACTIVE,
                        "purchase:" + p.payment().id().value())));
            }
        }
        for (var shipment : state.shipments().shipments().values()) if (shipment.transportMissionId().isPresent()) {
            validateShipment(state, shipment);
            if (project) {
                var owner = new SubjectEndpoint(EntityKind.SHIPMENT, shipment.id());
                edges.add(declaredEdge(Kind.SHIPMENT_MISSION, owner, owner,
                        new SubjectEndpoint(EntityKind.TRANSPORT_MISSION, shipment.transportMissionId().orElseThrow()),
                        shipment.terminal() ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE, "shipment:" + shipment.id().value()));
            }
        }
    }
    static void validateClaim(FrontierWorldState state, ClaimAllocation claim) {
            if (claim.purpose() != ClaimPurpose.EXPEDITION_SUPPLY) return;
            var mission = state.shipments().missions().get(claim.claimantId());
            if (mission == null || mission.supplies().stream().flatMap(load -> load.allocations().stream())
                    .noneMatch(a -> !a.loaded() && !a.withdrawn() && a.claimId().equals(claim.id()))
                        && mission.replenishment().filter(t -> t.claimId().equals(claim.id())).isEmpty())
                throw new IllegalArgumentException("provisioning claim has no exact retained loading obligation");
    }
    static void validateGroup(FrontierWorldState state, UnitGroup group) {
            var port = UnitGroupMissionPorts.require(group); port.validate(state, group);
            group.journey().ifPresent(journey -> {
                if (group.members().stream().anyMatch(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE
                        && !journey.stations().containsKey(member.actorId())))
                    throw new IllegalArgumentException("active group journey omits a surviving participant's declared station");
            });
    }
    static void validateMission(FrontierWorldState state, TransportMission mission) {
            var group = state.unitGroups().groups().get(mission.groupId());
            if (group == null || !group.mission().equals(new UnitGroup.Mission(UnitGroup.MissionKind.TRANSPORT, mission.id()))
                    || mission.stage() == TransportMission.Stage.COMPLETE && group.phase() != UnitGroup.Phase.CLOSED
                    || group.phase() == UnitGroup.Phase.CLOSED && mission.stage() != TransportMission.Stage.RETURNING
                        && mission.stage() != TransportMission.Stage.COMPLETE)
                throw new IllegalArgumentException("transport mission lost its exact group or legal closing boundary");
            io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.validate(state, mission, group);
        for (var id : mission.shipmentIds()) {
            var shipment = state.shipments().shipments().get(id);
            if (shipment == null || !shipment.transportMissionId().equals(Optional.of(mission.id())))
                throw new IllegalArgumentException("mission lost its exact shipment reference");
        }
    }
    static void validateShipment(FrontierWorldState state, Shipment shipment) {
        if (shipment.transportMissionId().isEmpty()) return;
            var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
            if (mission == null || !mission.shipmentIds().contains(shipment.id()))
                throw new IllegalArgumentException("shipment lost its exact owning transport mission");
    }

}
