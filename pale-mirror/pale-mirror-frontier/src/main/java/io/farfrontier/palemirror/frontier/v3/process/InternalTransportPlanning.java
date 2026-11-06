package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
/** Manifest policy over the existing fleet/transport owners. Personal capacity is never multiplied. */
final class InternalTransportPlanning {
    static Optional<InternalTransportMissionStarted> offer(FrontierWorldState state,ResidentProfile worker,long tick){
        var asset=state.transportFleet().assetsAt(worker.settlementId()).stream()
                .filter(candidate->TransportAssetAdmission.available(state,candidate,worker.settlementId())).findFirst();
        if(asset.isEmpty())return Optional.empty();var animal=asset.orElseThrow();
        for(var discovered:ResourceSourceDiscovery.localHaulSources(state,worker.settlementId())) {
            if(!(discovered instanceof ShipmentEndpoint declaredSource))continue;
            var ledger=state.inventory().fungibleResources();var account=FungibleResourceCustodySupport.accountsAt(ledger,declaredSource.custody()).stream().findFirst();
            if(account.isEmpty())continue;
            var kinds=account.orElseThrow().lotQuantities().keySet().stream().map(ledger.lots()::get).filter(lot->lot.economicOwnerId().equals(worker.settlementId()))
                    .map(ResourceLot::itemKind).distinct().sorted().toList();if(kinds.isEmpty())continue;
            // Quantity is an upper bound only; exact source/capacity/claims are
            // still admitted below. A partial buffer cannot form this manifest,
            // so do not search every pickup/drop approach for every worker.
            long available = kinds.stream().mapToLong(kind -> Math.min(64,
                    ledger.unclaimedQuantity(account.orElseThrow().id(), worker.settlementId(), kind))).sum();
            if (available < 64) continue;
            for(var declaredReceiver:ResourceStorageSelection.candidates(state,worker.settlementId(),kinds.getFirst(),declaredSource.station(),Set.of(ResourceStorageRole.WAREHOUSE))) {
                if(declaredSource.containerId().equals(declaredReceiver.containerId()))continue;
                var amountsAvailable = new LinkedHashMap<String, Integer>();
                for (var kind : kinds) {
                    if (amountsAvailable.size() >= Math.max(1, animal.stackSlots() - 1)) break;
                    if (!ResourceStorageSelection.permits(state.inventory(), declaredReceiver.containerId(), worker.settlementId(),
                            kind, Set.of(ResourceStorageRole.WAREHOUSE))) continue;
                    var request = InternalHaulRequest.select(state, worker.settlementId(), declaredSource, declaredReceiver, kind, 64);
                    int count = request.map(InternalHaulRequest::quantity).orElse(0);
                    while (count > 0) {
                        amountsAvailable.put(kind, count);
                        if (ContainerStorageAdmission.receiveManifest(state, declaredReceiver.containerId(), amountsAvailable)) break;
                        count--;
                    }
                    if (count == 0) amountsAvailable.remove(kind);
                }
                if (amountsAvailable.values().stream().mapToInt(Integer::intValue).sum() < 64) continue;
                var journey=ShipmentEndpointComposition.knowledge(state,List.of(declaredSource,declaredReceiver));
                var pickup=ActorStorageApproach.select(state,declaredSource,animal.actorId(),state.actorLocations().get(animal.actorId()).supportingSurface(),journey);
                if(pickup.isEmpty())continue;
                var drop=ActorStorageApproach.select(state,declaredReceiver,animal.actorId(),pickup.orElseThrow().station(),journey);
                if(drop.isEmpty())continue;
                var source=pickup.orElseThrow();var receiver=drop.orElseThrow();
                long distance=source.station().manhattanDistance(receiver.station());
                if(distance<48)continue;
                var events=new ArrayList<InternalShipmentDispatched>();var amounts=new LinkedHashMap<String,Integer>();
                for(var kind:amountsAvailable.keySet()){
                    if(events.size()>=Math.max(1,animal.stackSlots()-1))break;
                    if(!ResourceStorageSelection.permits(state.inventory(),receiver.containerId(),worker.settlementId(),kind,Set.of(ResourceStorageRole.WAREHOUSE)))continue;
                    var prepared=InternalShipmentPlanning.prepare(state,worker,source,receiver,kind,amountsAvailable.get(kind),tick);
                    if(prepared.isEmpty())continue;var portion=prepared.orElseThrow();
                    var next=new LinkedHashMap<>(amounts);next.merge(kind,portion.shipment().quantity(),Math::addExact);
                    if(!ContainerStorageAdmission.receiveManifest(state,receiver.containerId(),next))continue;
                    events.add(portion);amounts=next;
                }
                int quantity=amounts.values().stream().mapToInt(Integer::intValue).sum();
                if(quantity<64 || events.isEmpty())continue;
                return assemble(state,worker,animal,source,receiver,events,tick);
            }
        }
        return Optional.empty();
    }
    private static Optional<InternalTransportMissionStarted> assemble(FrontierWorldState state,ResidentProfile worker,TransportAsset animal,
            ShipmentEndpoint source,ShipmentEndpoint receiver,List<InternalShipmentDispatched> portions,long tick){
        String token=UUID.nameUUIDFromBytes((source.containerId().value()+"\n"+receiver.containerId().value()+"\n"+worker.id().value()+"\n"+tick)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var missionId=new SubjectId("transport:internal-"+token);var groupId=new SubjectId("group:internal-"+token);
        var execution=state.actorExecutions().next(animal.actorId(),ActorActivityKind.GROUP_MEMBER,groupId);
        var carried=FungibleResourceCustodySupport.accountAtContainer(state,animal.containerId()).map(CustodyAccount::id)
                .orElseGet(()->ReferenceContainerCustody.scopeId(animal.containerId()));
        var shipments=portions.stream().map(portion->{var parcel=portion.shipment();var cargo=(ShipmentCargo.Fungible)parcel.cargo();
            return new Shipment(parcel.id(),parcel.authorization(),execution,source,receiver,
                    new ShipmentCargo.Fungible(cargo.sourceAccountId(),carried,cargo.receivingAccountId(),cargo.itemKind(),cargo.portions()),
                    parcel.status(),parcel.revision(),Optional.empty(),Optional.empty(),Optional.of(missionId),Optional.of(animal.containerId()));}).toList();
        var group=new UnitGroup(groupId,new UnitGroup.Mission(UnitGroup.MissionKind.TRANSPORT,missionId),List.of(
                new UnitGroup.Member(animal.actorId(),UnitGroup.Role.CARRIER,ActorActivityKind.GROUP_MEMBER,groupId),
                new UnitGroup.Member(worker.id(),UnitGroup.Role.GUIDE,ActorActivityKind.GROUP_MEMBER,groupId)),UnitGroup.Formation.COLUMN,
                UnitGroup.Phase.READY,1,0,Optional.empty());
        var profile=state.actorLocations().get(animal.actorId()).navigationProfile();
        var home=TransportGroupMissionPort.rendezvous(state,source,2,profile);
        var destination=TransportGroupMissionPort.rendezvous(state,receiver,2,profile);
        var mission=new TransportMission(missionId,groupId,shipments.stream().map(Shipment::id).toList(),source,receiver,
                new TransportOrganization(new EconomicIdentity(worker.settlementId(),EconomicOwnerKind.SETTLEMENT_TREASURY),source,TransportOrganization.Itinerary.SOURCE_DELIVERY),
                home,destination,TransportMission.Stage.LOADING,1).withTransportAsset(animal.actorId());
        var supplies=ExpeditionSupplyPlanning.load(state,mission,group,tick);if(supplies.isEmpty())return Optional.empty();
        mission=mission.withSupplies(supplies.orElseThrow());
        return Optional.of(new InternalTransportMissionStarted(new TransportMissionStarted(worker.settlementId(),EconomicOwnerKind.SETTLEMENT_TREASURY,mission,group,shipments),
                portions.stream().map(InternalShipmentDispatched::claim).toList()));
    }
    static FrontierWorldState admit(FrontierWorldState state,SubjectId subject,InternalTransportMissionStarted event,long tick){
        var transport=event.transport();if(!subject.equals(transport.mission().id()))throw new IllegalArgumentException("foreign internal manifest owner");
        var amounts=new LinkedHashMap<String,Integer>();
        for(var parcel:transport.shipments()) amounts.merge(parcel.itemKind(),parcel.quantity(),Math::addExact);
        if(!ContainerStorageAdmission.receiveManifest(state,transport.mission().receiver().containerId(),amounts))
            throw new IllegalArgumentException("internal manifest has no combined receiving capacity");
        var ledger=state.inventory().fungibleResources();
        var claims=event.claims().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ClaimAllocation::id,java.util.function.Function.identity()));
        for(var parcel:transport.shipments()) {
            var claim=Objects.requireNonNull(claims.get(parcel.authorization().claimId()),"manifest claim");
            if(parcel.authorization().kind()!=ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT || claim.purpose()!=ClaimPurpose.INTERNAL_LOGISTICS
                    || !claim.economicOwnerId().equals(transport.senderId()) || !claim.claimantId().equals(transport.senderId())
                    || !claim.itemKind().equals(parcel.itemKind()) || !claim.lotQuantities().equals(parcel.lotQuantities()))
                throw new IllegalArgumentException("internal manifest lacks its exact same-owner portions");
            var bindings=FungibleBindingReadIndex.forAccount(ledger,parcel.sourceAccountId());
            ledger=bindings.isEmpty()?ledger.reserve(claim,parcel.sourceAccountId()):ledger.reserveBound(claim,parcel.sourceAccountId(),bindings.getFirst().authorityEpoch());
        }
        var allocated=state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(ledger)));
        var admitted=TransportMissionProcess.admit(allocated,subject,transport,tick);
        var waiting=new LinkedHashMap<>(admitted.shipments().accumulation().firstAvailable());
        transport.shipments().forEach(parcel->waiting.remove(new HaulAccumulation.Key(transport.senderId(),parcel.sourceAccountId(),parcel.itemKind())));
        return admitted.withChanges(FrontierWorldStateUpdate.begin().shipments(admitted.shipments().withAccumulation(new HaulAccumulation(waiting))));
    }
    private InternalTransportPlanning(){}
}
