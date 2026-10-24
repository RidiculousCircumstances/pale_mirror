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
    sealed interface Stock permits Stock.Fungible, Stock.Individual {
        SubjectId claimId();
        int quantity();
        ShipmentCargo cargo(SubjectId carried, SubjectId receiving);
        record Fungible(ClaimAllocation claim, SubjectId sourceAccount) implements Stock {
            public SubjectId claimId() { return claim.id(); }
            public int quantity() { return claim.quantity(); }
            public ShipmentCargo cargo(SubjectId carried, SubjectId receiving) {
                return new ShipmentCargo.Fungible(sourceAccount, carried, receiving, claim.itemKind(), claim.lotQuantities());
            }
        }
        record Individual(IndividualResourceAllocation allocation) implements Stock {
            public SubjectId claimId() { return allocation.id(); }
            public int quantity() { return 1; }
            public ShipmentCargo cargo(SubjectId carried, SubjectId receiving) { return new ShipmentCargo.Individual(allocation); }
        }
    }
    record Parcel(GoodsTradeContract contract, Stock stock) {
        Parcel(GoodsTradeContract contract, ClaimAllocation claim, SubjectId sourceAccount) { this(contract, new Stock.Fungible(claim, sourceAccount)); }
        SubjectId claimId() { return stock.claimId(); }
        int quantity() { return stock.quantity(); }
    }
    static Optional<TransportMissionStarted> prepare(FrontierWorldState state, GoodsParticipant seller,
                                                    GoodsParticipant buyer, List<Parcel> parcels, GoodsTradeFulfillment.Mode mode, long now) {
        if (parcels.isEmpty()) throw new IllegalArgumentException("empty manifest");
        var contract = parcels.getFirst().contract();
        var source = GoodsParticipantStorage.endpoint(state, contract.sourceContainerId());
        var destination = GoodsParticipantStorage.endpoint(state, contract.receiverContainerId());
        var knowledge = ShipmentEndpointComposition.knowledge(state, List.of(source, destination));
        long outward = knowledge.estimateBetween(source.station(), destination.station()).estimatedEdges();
        long returning = knowledge.estimateBetween(destination.station(), source.station()).estimatedEdges();
        var timing = ExpeditionTiming.assess(state.bootstrap().ruleset().expedition(),
                mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? outward : returning,
                mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? returning : outward,
                mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? TransportOrganization.Itinerary.SOURCE_DELIVERY
                        : TransportOrganization.Itinerary.DESTINATION_COLLECTION);
        return prepare(state, seller, buyer, parcels, mode, now, timing.durationTicks());
    }
    static Optional<TransportMissionStarted> prepare(FrontierWorldState state, GoodsParticipant seller,
            GoodsParticipant buyer, List<Parcel> parcels, GoodsTradeFulfillment.Mode mode, long now, long forecastDuration) {
        var organizer = switch (mode) { case SELLER_DELIVERY -> seller; case BUYER_COLLECTION -> buyer; };
        if (parcels.isEmpty() || parcels.size() > 32) throw new IllegalArgumentException("unbounded trade manifest");
        for (var parcel : parcels) if (!parcel.contract().seller().equals(seller.party()) || !parcel.contract().buyer().equals(buyer.party())
                || parcel.contract().fulfillment().mode() != mode || !parcel.contract().fulfillment().organizer().equals(organizer.party())
                || !parcel.contract().sourceContainerId().equals(parcels.getFirst().contract().sourceContainerId())
                || !parcel.contract().receiverContainerId().equals(parcels.getFirst().contract().receiverContainerId())
                || parcel.quantity() > 64 || !parcel.contract().outstandingClaims().containsKey(parcel.claimId()))
            throw new IllegalArgumentException("manifest mixes recipients or loses an exact accepted allocation");
        for (var parcel : parcels) if (!GoodsParticipantStorage.eligible(state, seller, parcel.contract().sourceContainerId(), parcel.contract().itemKind())
                || !GoodsParticipantStorage.eligible(state, buyer, parcel.contract().receiverContainerId(), parcel.contract().itemKind()))
            return Optional.empty();
        var people = ResidentWorkComposition.SELECTION.eligible(state, organizer.settlementId(),
                ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS, now);
        var available = new ArrayList<ResidentProfile>();
        for (var person : people) {
            var ids = new ArrayList<>(available.stream().map(ResidentProfile::id).toList()); ids.add(person.id());
            if (SettlementLabourAllocation.canCommitToMission(state, organizer.settlementId(), ResidentWorkKind.LOGISTICS, ids))
                available.add(person);
        }
        if (available.isEmpty()) return Optional.empty();
        for (var option : GoodsTransportOptions.forLoad(state, organizer, forecastDuration, parcels.size(), available.size())) {
            var prepared = declaration(state, seller, buyer, parcels, mode,
                    available.subList(0, option.workers()), option.animal(), now);
            if (prepared.isPresent()) return prepared;
        }
        return Optional.empty();
    }
    private static Optional<TransportMissionStarted> declaration(FrontierWorldState state, GoodsParticipant seller,
            GoodsParticipant buyer, List<Parcel> parcels, GoodsTradeFulfillment.Mode mode, List<ResidentProfile> people, Optional<TransportAsset> animal, long now) {
        var sponsor = mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? seller : buyer;
        if (!ExpeditionCrewAdmission.canFeedBeforeTravel(state, sponsor.settlementId(),
                people.stream().map(ResidentProfile::id).toList(), animal, now)) return Optional.empty();
        String stem = mode.name().toLowerCase(java.util.Locale.ROOT) + "/" + WorkOpportunityIdentity.digest(parcels.stream().map(p -> p.claimId().value()).collect(java.util.stream.Collectors.joining("|")));
        var missionId = new SubjectId("transport-mission:goods-trip/" + stem);
        var groupId = new SubjectId("unit-group:goods-trip/" + stem);
        var approachActor = animal.map(TransportAsset::actorId).orElse(people.getFirst().id());
        var sourceEndpoint = GoodsParticipantStorage.endpoint(state, parcels.getFirst().contract().sourceContainerId());
        var destinationEndpoint = GoodsParticipantStorage.endpoint(state, parcels.getFirst().contract().receiverContainerId());
        var journey = ShipmentEndpointComposition.knowledge(state, List.of(sourceEndpoint, destinationEndpoint));
        var source = ActorStorageApproach.select(state, sourceEndpoint, approachActor,
                state.actorLocations().get(approachActor).supportingSurface(), journey);
        if (source.isEmpty()) return Optional.empty();
        var destinationPort = ActorStorageApproach.select(state, destinationEndpoint, approachActor, source.orElseThrow().station(), journey);
        if (destinationPort.isEmpty()) return Optional.empty();
        var senderEndpoint = source.orElseThrow(); var receiverEndpoint = destinationPort.orElseThrow();
        var members = new ArrayList<UnitGroup.Member>(); var shipments = new ArrayList<Shipment>();
        var receiving = FungibleResourceCustodySupport.accountAtContainer(state, destinationEndpoint.containerId()).map(CustodyAccount::id)
                .orElseGet(() -> ReferenceContainerCustody.scopeId(destinationEndpoint.containerId()));
        for (int index = 0; index < parcels.size(); index++) {
            var parcel = parcels.get(index);
            var actor = animal.map(TransportAsset::actorId).orElse(people.get(Math.min(index, people.size() - 1)).id());
            var execution = state.actorExecutions().next(actor, ActorActivityKind.GROUP_MEMBER, groupId);
            var id = new SubjectId("shipment:goods/" + parcel.claimId().value().replace(':', '-'));
            var carried = animal.map(a -> FungibleResourceCustodySupport.accountAtContainer(state, a.containerId()).map(CustodyAccount::id)
                    .orElseGet(() -> ReferenceContainerCustody.scopeId(a.containerId())))
                    .orElseGet(() -> new SubjectId("custody:" + id.value().replace(':', '-')));
            shipments.add(new Shipment(id, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT,
                    parcel.claimId(), parcel.contract().id(), id, parcel.contract().revision()), execution,
                    senderEndpoint, receiverEndpoint, parcel.stock().cargo(carried, receiving), Shipment.Status.AWAITING_LOAD, 1, Optional.empty(), Optional.empty(),
                    Optional.of(missionId), animal.map(TransportAsset::containerId)));
            if (animal.isEmpty() || index == 0) members.add(new UnitGroup.Member(actor, UnitGroup.Role.CARRIER,
                    ActorActivityKind.GROUP_MEMBER, groupId));
        }
        if (animal.isPresent()) members.add(new UnitGroup.Member(people.getFirst().id(), UnitGroup.Role.GUIDE,
                ActorActivityKind.GROUP_MEMBER, groupId)); // This sole person actually accompanies the finite asset.
        var group = new UnitGroup(groupId, new UnitGroup.Mission(UnitGroup.MissionKind.TRANSPORT, missionId), members,
                UnitGroup.Formation.COLUMN, UnitGroup.Phase.READY, 1, 0, Optional.empty());
        try {
            var homeEndpoint = mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? senderEndpoint : receiverEndpoint;
            var remoteEndpoint = mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? receiverEndpoint : senderEndpoint;
            var home = TransportGroupMissionPort.rendezvous(state, homeEndpoint, members.size(),
                    state.actorLocations().get(approachActor).navigationProfile());
            var destination = TransportGroupMissionPort.rendezvous(state, remoteEndpoint, members.size(),
                    state.actorLocations().get(approachActor).navigationProfile());
            var mission = new TransportMission(missionId, groupId, shipments.stream().map(Shipment::id).toList(),
                    senderEndpoint, receiverEndpoint, new TransportOrganization(new EconomicIdentity(homeEndpoint.settlementId(), EconomicOwnerKind.SETTLEMENT_TREASURY),
                            homeEndpoint, mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? TransportOrganization.Itinerary.SOURCE_DELIVERY : TransportOrganization.Itinerary.DESTINATION_COLLECTION),
                    home, destination, mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? TransportMission.Stage.LOADING : TransportMission.Stage.PREPARING, 1);
            if (animal.isPresent()) mission = mission.withTransportAsset(animal.orElseThrow().actorId());
            var load = ExpeditionSupplyPlanning.load(state, mission, group, now);
            if (load.isEmpty()) return Optional.empty();
            mission = ExpeditionFundingAuthority.declare(state, mission.withSupplies(load.orElseThrow()));
            var event = new TransportMissionStarted(seller.party().id(), seller.party().kind(), mission, group, shipments);
            TransportMissionProcess.admit(state, mission.id(), event, now);
            return Optional.of(event);
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            // Incomplete evidence must keep the selected candidate pending, not
            // launch the next crew/animal/quote as if this one were infeasible.
            if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                throw unavailable;
            return Optional.empty();
        }
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
