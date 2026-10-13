package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.*;

/** Logistics preflight. It receives exact commercial obligations; it cannot reserve a sale or invent an asset. */
final class GoodsTripAssembly {
    record Parcel(GoodsTradeContract contract, ClaimAllocation claim, SubjectId sourceAccount) { }
    static Optional<TransportMissionStarted> prepare(FrontierWorldState state, GoodsParticipant seller,
                                                    GoodsParticipant buyer, List<Parcel> parcels, long now) {
        if (parcels.isEmpty() || parcels.size() > 32) throw new IllegalArgumentException("unbounded trade manifest");
        for (var parcel : parcels) if (!parcel.contract().seller().equals(seller.party()) || !parcel.contract().buyer().equals(buyer.party())
                || !parcel.contract().sourceContainerId().equals(seller.endpoint().containerId())
                || !parcel.contract().receiverContainerId().equals(buyer.endpoint().containerId())
                || parcel.claim().quantity() > 64 || !parcel.contract().outstandingClaims().containsKey(parcel.claim().id()))
            throw new IllegalArgumentException("manifest mixes recipients or loses an exact accepted allocation");
        var people = ResidentWorkComposition.SELECTION.eligible(state, seller.endpoint().settlementId(),
                ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS, now);
        var available = new ArrayList<ResidentProfile>();
        for (var person : people) {
            var ids = new ArrayList<>(available.stream().map(ResidentProfile::id).toList()); ids.add(person.id());
            if (SettlementLabourAllocation.canCommitToMission(state, seller.endpoint().settlementId(), ResidentWorkKind.LOGISTICS, ids))
                available.add(person);
        }
        if (available.isEmpty()) return Optional.empty();
        // A person carries one stack. Do not add support people to a walking load.
        if (parcels.size() <= available.size()) {
            var walking = declaration(state, seller, buyer, parcels, available.subList(0, parcels.size()), Optional.empty(), now);
            if (walking.isPresent()) return walking;
        }
        for (var animal : state.transportFleet().assets().values().stream().filter(a ->
                TransportAssetAdmission.available(state, a, seller.endpoint().settlementId())).sorted(Comparator.comparing(TransportAsset::actorId)).toList()) {
            if (parcels.size() > Math.min(animal.stackSlots(), state.bootstrap().ruleset().expedition().packAnimalStackSlots())) continue;
            var packed = declaration(state, seller, buyer, parcels, List.of(available.getFirst()), Optional.of(animal), now);
            if (packed.isPresent()) return packed;
        }
        return Optional.empty();
    }
    private static Optional<TransportMissionStarted> declaration(FrontierWorldState state, GoodsParticipant seller,
            GoodsParticipant buyer, List<Parcel> parcels, List<ResidentProfile> people, Optional<TransportAsset> animal, long now) {
        String stem = WorkOpportunityIdentity.digest(parcels.stream().map(p -> p.claim().id().value()).collect(java.util.stream.Collectors.joining("|")));
        var missionId = new SubjectId("transport-mission:goods-trip/" + stem);
        var groupId = new SubjectId("unit-group:goods-trip/" + stem);
        var approachActor = animal.map(TransportAsset::actorId).orElse(people.getFirst().id());
        var journey = ShipmentEndpointComposition.knowledge(state, List.of(seller.endpoint(), buyer.endpoint()));
        var source = ActorStorageApproach.select(state, seller.endpoint(), approachActor,
                state.actorLocations().get(approachActor).supportingSurface(), journey);
        if (source.isEmpty()) return Optional.empty();
        var destinationPort = ActorStorageApproach.select(state, buyer.endpoint(), approachActor, source.orElseThrow().station(), journey);
        if (destinationPort.isEmpty()) return Optional.empty();
        var senderEndpoint = source.orElseThrow(); var receiverEndpoint = destinationPort.orElseThrow();
        var members = new ArrayList<UnitGroup.Member>(); var shipments = new ArrayList<Shipment>();
        var receiving = FungibleResourceCustodySupport.accountAtContainer(state, buyer.endpoint().containerId()).map(CustodyAccount::id)
                .orElseGet(() -> ReferenceContainerCustody.scopeId(buyer.endpoint().containerId()));
        for (int index = 0; index < parcels.size(); index++) {
            var parcel = parcels.get(index);
            var actor = animal.map(TransportAsset::actorId).orElse(people.get(Math.min(index, people.size() - 1)).id());
            var execution = state.actorExecutions().next(actor, ActorActivityKind.GROUP_MEMBER, groupId);
            var id = new SubjectId("shipment:goods/" + parcel.claim().id().value().replace(':', '-'));
            var carried = animal.map(a -> FungibleResourceCustodySupport.accountAtContainer(state, a.containerId()).map(CustodyAccount::id)
                    .orElseGet(() -> ReferenceContainerCustody.scopeId(a.containerId())))
                    .orElseGet(() -> new SubjectId("custody:" + id.value().replace(':', '-')));
            shipments.add(new Shipment(id, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT,
                    parcel.claim().id(), parcel.contract().id(), id, parcel.contract().revision()), execution,
                    senderEndpoint, receiverEndpoint, parcel.sourceAccount(), carried, receiving, parcel.contract().itemKind(),
                    parcel.claim().lotQuantities(), Shipment.Status.AWAITING_LOAD, 1, Optional.empty(), Optional.empty(),
                    Optional.of(missionId), animal.map(TransportAsset::containerId)));
            if (animal.isEmpty() || index == 0) members.add(new UnitGroup.Member(actor, UnitGroup.Role.CARRIER,
                    ActorActivityKind.GROUP_MEMBER, groupId));
        }
        if (animal.isPresent()) members.add(new UnitGroup.Member(people.getFirst().id(), UnitGroup.Role.GUIDE,
                ActorActivityKind.GROUP_MEMBER, groupId)); // This sole person actually accompanies the finite asset.
        var group = new UnitGroup(groupId, new UnitGroup.Mission(UnitGroup.MissionKind.TRANSPORT, missionId), members,
                UnitGroup.Formation.COLUMN, UnitGroup.Phase.READY, 1, 0, Optional.empty());
        try {
            var home = TransportGroupMissionPort.rendezvous(state, senderEndpoint, members.size(),
                    state.actorLocations().get(approachActor).navigationProfile());
            var destination = TransportGroupMissionPort.rendezvous(state, receiverEndpoint, members.size(),
                    state.actorLocations().get(approachActor).navigationProfile());
            var mission = new TransportMission(missionId, groupId, shipments.stream().map(Shipment::id).toList(),
                    senderEndpoint, receiverEndpoint, home, destination, TransportMission.Stage.LOADING, 1);
            if (animal.isPresent()) mission = mission.withTransportAsset(animal.orElseThrow().actorId());
            var load = ExpeditionSupplyPlanning.load(state, mission, group, now);
            if (load.isEmpty()) return Optional.empty();
            mission = ExpeditionFundingAuthority.declare(state, mission.withSupplies(load.orElseThrow()));
            var event = new TransportMissionStarted(seller.party().id(), seller.party().kind(), mission, group, shipments);
            TransportMissionProcess.admit(state, mission.id(), event, now);
            return Optional.of(event);
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return Optional.empty(); }
    }
    static List<ProposedEvent> events(TransportMissionStarted declaration, long now) {
        var events = new ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(declaration.mission().id(), declaration));
        declaration.shipments().forEach(s -> events.add(new ProposedEvent(s.id(),
                new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(ShipmentProcess.progress(s.id(), now + 1)))));
        events.add(new ProposedEvent(declaration.group().id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                UnitGroupProcess.progress(declaration.group().id(), now + 1))));
        events.add(new ProposedEvent(declaration.mission().id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                TransportMissionProcess.progress(declaration.mission().id(), now + 1))));
        return List.copyOf(events);
    }
    private GoodsTripAssembly() { }
}
