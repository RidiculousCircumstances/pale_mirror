package io.farfrontier.palemirror.frontier.v3.model.rest;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Reusable reservation, nearby interaction and actual rest semantics. No patient/doctor decisions. */
public final class RestPlaces {
    public static Optional<SubjectId> pendingOwner(FrontierWorldState s,SubjectId actor){return
        Optional.ofNullable(s.humanPopulation().restPlaces().uses().get(actor)).filter(use->use.pendingBody().isPresent()).map(use->use.execution().activityOwnerId());}
    public static boolean present(FrontierWorldState s,RestUse use){
        return SettlementBeds.places(s,FrontierWorldStateSupport.settlement(s.bootstrap(),use.place().home())).stream()
                .map(SettlementBeds.Place::restPlace).anyMatch(use.place()::equals);
    }
    public static Optional<RestUse> select(FrontierWorldState s,ActorExecutionId execution,SubjectId home,RestPlaceProfile.Purpose preferred,long tick){
        var origin=s.actorLocations().get(execution.actorId()).supportingSurface();
        var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(s,home,List.of()).forActor(s,execution.actorId());
        var places=SettlementBeds.places(s,FrontierWorldStateSupport.settlement(s.bootstrap(),home)).stream().map(SettlementBeds.Place::restPlace)
                .filter(RestPlace::present).filter(p->s.humanPopulation().restPlaces().free(p.id()))
                .sorted(Comparator.comparingInt((RestPlace p)->p.profile().purpose()==preferred?0:1)
                    .thenComparingLong(p->new SurfaceAnchor(p.head()).manhattanDistance(origin)).thenComparing(p->p.id().buildingId()).thenComparing(p->p.id().localId())).toList();
        for(var place:places){
            var stations=PedestrianInteractionStations.within(knowledge.geometry(),new SurfaceAnchor(place.head().offset(0,-1,0)),2,Set.of()).stream()
                    .filter(p->!p.support().equals(place.head())&&!p.support().equals(place.foot()))
                    .sorted(Comparator.comparingLong(p->p.manhattanDistance(origin))).toList();
            for(var station:stations){
                try{knowledge.plannedBetween(origin,station);return Optional.of(new RestUse(execution,place,station,RestUse.Phase.RESERVED,1,tick,Optional.empty(),Optional.empty()));}
                catch(KnownPedestrianNavigation.RouteUnavailable waiting){if(waiting.status()!=PedestrianRouteResult.Status.NO_PATH)throw waiting;}
            }
        }return Optional.empty();
    }
    public static FrontierWorldState apply(FrontierWorldState s,SubjectId subject,RestEvent event,long tick){
        var places=s.humanPopulation().restPlaces();
        if(event instanceof RestEvent.Availability observed){
            var place=observed.place();
            if(!subject.equals(place.id().buildingId())||SettlementBeds.places(s,FrontierWorldStateSupport.settlement(s.bootstrap(),place.home())).stream()
                    .map(SettlementBeds.Place::restPlace).noneMatch(place::equals))throw new IllegalArgumentException("foreign observed rest place");
            return update(s,places.availability(place.id(),observed.available()));
        }
        if(event instanceof RestEvent.Reserved reserved){
            var use=reserved.use();s.actorExecutions().requireCurrent(use.execution());
            if(!subject.equals(use.execution().actorId())||places.uses().containsKey(subject)||!places.free(use.place().id())||!use.place().present()
                    ||!approachClear(s,use)||use.phase()!=RestUse.Phase.RESERVED||use.revision()!=1||use.changedAt()!=tick||use.pendingBody().isPresent()||!present(s,use)
                    ||!ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind()).permitsRest(s,use.execution(),use))throw new IllegalArgumentException("invalid rest reservation");
            return update(s,places.with(use));
        }
        SubjectId actor=switch(event){case RestEvent.Prepared e->e.actor();case RestEvent.Changed e->e.actor();case RestEvent.Released e->e.actor();default->throw new IllegalArgumentException("unknown rest action");};
        long revision=switch(event){case RestEvent.Prepared e->e.revision();case RestEvent.Changed e->e.revision();case RestEvent.Released e->e.revision();default->throw new IllegalArgumentException("unknown rest action");};
        var use=Objects.requireNonNull(places.uses().get(actor),"rest use");if(!subject.equals(actor)||revision!=use.revision())throw new IllegalArgumentException("stale rest owner/revision");
        if(event instanceof RestEvent.Released){
            if(use.pendingBody().isPresent()||use.phase()!=RestUse.Phase.RESERVED&&s.actorLocations().get(actor).condition().status()==ActorLifeStatus.ALIVE
                    ||!ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind()).permitsRestRelease(s,use.execution(),use))
                throw new IllegalArgumentException("rest is still physically occupied");
            return update(s,places.without(actor));
        }
        if(event instanceof RestEvent.Prepared prepared){
            ActorBodyAuthority.requireActuation(s,prepared.body());
            if(!prepared.body().execution().equals(use.execution())||s.actorMovements().containsKey(actor)
                    ||ActorInventoryInteractionFences.pending(s,actor)||prepared.action()!=RestUse.Action.EXIT&&
                        !ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind()).permitsRest(s,use.execution(),use)
                    ||prepared.action()==RestUse.Action.ENTER&&(!present(s,use)||!s.actorLocations().get(actor).supportingSurface().equals(use.approach())))throw new IllegalArgumentException("rest needs exact current nearby body");
            return update(s,places.with(use.prepared(prepared.body(),prepared.action())));
        }
        var change=(RestEvent.Changed)event;
        if(change.body().isPresent()){
            if(!change.body().equals(use.pendingBody())||!Optional.of(change.action()).equals(use.pendingAction()))throw new IllegalArgumentException("foreign rest receipt");
        }else{
            s.actorExecutions().requireCurrent(use.execution());
            if(change.action()==RestUse.Action.RESTORE||change.action()==RestUse.Action.ENTER&&use.phase()!=RestUse.Phase.RESERVED
                    ||change.action()==RestUse.Action.EXIT&&(use.phase()!=RestUse.Phase.RESTING||!exitStations(s,use).contains(change.observed().supportingSurface()))||use.pendingBody().isPresent()||
                        !ActorExecutionCoordinator.coldAvailable(s,actor)||!change.applied()||s.actorMovements().containsKey(actor)
                    ||change.action()==RestUse.Action.ENTER&&(!present(s,use)||!s.actorLocations().get(actor).supportingSurface().equals(use.approach())))throw new IllegalArgumentException("rest cold interaction lacks sole authority/arrival");
        }
        var next=use.phase();var changes=FrontierWorldStateUpdate.begin();
        if(change.applied()){
            next=change.action()==RestUse.Action.EXIT?RestUse.Phase.RESERVED:RestUse.Phase.RESTING;
            if(change.body().isEmpty()){
                var expected=change.action()==RestUse.Action.ENTER?new SurfaceAnchor(use.place().head()).standingBody():change.observed().supportingSurface().standingBody();
                if(!expected.equals(change.observed()))throw new IllegalArgumentException("cold rest cannot relocate outside its retained nearby interaction");
                var location=s.actorLocations().get(actor);changes.actorLocations(ImmutableLookupMap.withEntry(s.actorLocations(),actor,location.withBody(expected)));
            }else if(!s.actorLocations().get(actor).body().equals(change.observed()))throw new IllegalArgumentException("rest outcome requires common physical body observation");
        }
        return s.withChanges(changes.humanPopulation(s.humanPopulation().withRestPlaces(places.with(use.changed(next,tick,change.applied()&&change.body().isEmpty()&&
            change.action()==RestUse.Action.EXIT?change.observed().supportingSurface():use.approach())))));
    }
    /** Waking is a bounded bed-side interaction, never permission to move through an unknown wall. */
    public static List<SurfaceAnchor> exitStations(FrontierWorldState s,RestUse use){
        var geometry=KnownPedestrianRouteKnowledge.forPublicJourney(s,use.place().home(),List.of()).forActor(s,use.execution().actorId()).geometry();
        var result=new LinkedHashSet<SurfaceAnchor>();
        for(var block:List.of(use.place().head(),use.place().foot()))
            result.addAll(PedestrianInteractionStations.within(geometry,new SurfaceAnchor(block.offset(0,-1,0)),1,Set.of()));
        result.remove(new SurfaceAnchor(use.place().head()));result.remove(new SurfaceAnchor(use.place().foot()));
        return result.stream().sorted(Comparator.comparingInt((SurfaceAnchor p)->p.equals(use.approach())?0:1).thenComparingLong(p->p.manhattanDistance(use.approach()))) .toList();
    }
    public static boolean approachClear(FrontierWorldState s,RestUse use){
        var geometry=KnownPedestrianRouteKnowledge.forPublicJourney(s,use.place().home(),List.of()).forActor(s,use.execution().actorId()).geometry();
        return geometry.clearanceKnown(use.approach())&&geometry.supportAvailable(use.approach())&&geometry.clearStation(use.approach());
    }
    private static FrontierWorldState update(FrontierWorldState s,RestPlaceState places){return s.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(s.humanPopulation().withRestPlaces(places)));}
    private RestPlaces(){}
}
