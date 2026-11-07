package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Reusable source-neutral personal resupply. Activities decide quantities and when to visit. */
public final class ActorSupplyVisits {
    private static final Set<ResourceStorageRole> ROLES=Set.of(ResourceStorageRole.WAREHOUSE,ResourceStorageRole.LOCAL_BUFFER);
    public static Optional<ActorSupplyVisit> select(FrontierWorldState s,ActorExecutionId execution,SubjectId home,String kind,int maximum,long ordinal,long revision){
        var pocket=ActorConsumables.freePocket(s,execution.actorId());if(pocket.isEmpty())return Optional.empty();
        var ledger=s.inventory().fungibleResources();var origin=s.actorLocations().get(execution.actorId()).supportingSurface();
        var candidates=new ArrayList<ResourceSourceEndpoint>();
        for(var source:ResourceStorageSelection.withdrawalSources(s,ledger,home,kind,origin,ROLES))
            for(var station:ResourceStorageAccess.at(s,source.endpoint().containerId()).stations())candidates.add(source.endpoint().withStation(station));
        candidates.addAll(ResourceSourceDiscovery.groundSources(s,home,kind));
        candidates.sort(Comparator.comparingLong((ResourceSourceEndpoint source)->source.station().manhattanDistance(origin))
                .thenComparing(source->source.custody().toString()));
        for(var source:candidates){
            var accounts=ledger.accounts().values().stream().filter(a->a.custody().equals(source.custody())).toList();if(accounts.size()!=1)continue;
            var account=accounts.getFirst();int quantity=Math.min(maximum,ledger.unclaimedQuantity(account.id(),home,kind));if(quantity<1)continue;
            var lots=FungibleResourceCustodySupport.selectAtAccount(ledger,account.id(),home,kind,quantity);
            if(lots.isEmpty()||!FungibleResourceCustodySupport.canUseUnclaimed(s,lots.orElseThrow())
                    ||source.sourceKind()==ResourceSourceEndpoint.Kind.CONTAINER&&!FungibleResourceCustodySupport.canReserve(s,lots.orElseThrow()))continue;
            ResourceSourceComposition.admit(s,source);
            var destination=new SubjectId("custody:personal-supply/"+UUID.nameUUIDFromBytes((execution+"|"+revision).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if(!ActorCarriedResources.canAddAccount(ledger,execution.actorId(),destination))return Optional.empty();
            var portion=new ActorContainerItemOrder.Portion.Fungible(account.id(),account.custody(),destination,new ResourceCustody.Actor(execution.actorId()),Optional.empty(),kind,lots.orElseThrow().lotQuantities());
            var order=ResourceSourceComposition.pickup(source,execution.activityOwnerId(),execution.actorId(),portion,pocket.orElseThrow(),ordinal,revision);
            try{knowledge(s,home,order).forActor(s,execution.actorId()).plannedBetween(origin,order.station());}
            catch(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable waiting){
                if(waiting.status()!=io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)throw waiting;continue;}
            return Optional.of(new ActorSupplyVisit(execution,home,order,Optional.empty()));
        }
        return Optional.empty();
    }
    public static Optional<ServiceAccessDemand.Identity> service(ActorSupplyVisit visit,ServiceAccessDemand.Kind kind){
        return visit.order() instanceof ActorContainerItemOrder order?Optional.of(new ServiceAccessDemand.Identity(kind,order.ownerId(),order.actorId(),order.containerEndpoint().containerId())):Optional.empty();
    }
    public static KnownPedestrianRouteKnowledge knowledge(FrontierWorldState s,SubjectId home,ActorResourceItemOrder order){
        return order instanceof ActorContainerItemOrder container?ResourceStorageNavigation.forEndpoint(s,ResourceStorageAccess.at(s,container.containerEndpoint().containerId()).endpoint().withStation(container.station()))
                :KnownPedestrianRouteKnowledge.forPublicJourney(s,home,List.of());
    }
    /** Cheap eligibility only: no path search, source ranking or stock reservation. */
    public static boolean stockPresent(FrontierWorldState s,SubjectId actor,SubjectId home,String kind){
        if(ActorConsumables.available(s,actor,home,kind)>0)return true;
        var ledger=s.inventory().fungibleResources();var origin=s.actorLocations().get(actor).supportingSurface();
        if(!ResourceStorageSelection.withdrawalSources(s,ledger,home,kind,origin,ROLES).isEmpty())return true;
        return FungibleResourceReadIndex.groundAccounts(ledger).stream().anyMatch(a->ledger.unclaimedQuantity(a.id(),home,kind)>0);
    }
    public static boolean coldAvailable(FrontierWorldState s,ActorSupplyVisit visit){return ActorExecutionCoordinator.coldAvailable(s,visit.execution().actorId())
        &&visit.order() instanceof ActorContainerItemOrder order&&!ReferenceContainerCustody.hasLiveCustody(s,order.containerEndpoint().containerId());}
    public static boolean available(FrontierWorldState s,ActorSupplyVisit visit){
        var order=visit.order();var p=(ActorContainerItemOrder.Portion.Fungible)order.portion();
        var quantity=new FungibleResourceCustodySupport.LotSelection(p.sourceAccountId(),p.lotQuantities());
        if(!FungibleResourceCustodySupport.canUseUnclaimed(s,quantity)||order.endpointKind()==ActorResourceItemOrder.EndpointKind.CONTAINER&&!FungibleResourceCustodySupport.canReserve(s,quantity))return false;
        if(order instanceof ActorContainerItemOrder container && (!ResourceStorageSelection.permitsWithdrawal(s.inventory(),container.containerEndpoint().containerId(),visit.economicOwner(),ROLES)
                ||ReferenceContainerCustody.blocksCanonicalUse(s,container.containerEndpoint().containerId())
                ||!ResourceStorageAccess.at(s,container.containerEndpoint().containerId()).stations().contains(order.station())))return false;
        try{order.requireSourceEvidence(s.inventory().fungibleResources());return true;}catch(IllegalArgumentException changed){return false;}
    }
    private ActorSupplyVisits(){}
}
