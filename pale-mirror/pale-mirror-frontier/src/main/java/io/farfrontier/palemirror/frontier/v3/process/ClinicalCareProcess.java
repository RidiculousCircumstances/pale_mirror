package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import io.farfrontier.palemirror.frontier.v3.model.rest.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Short dressing procedures and independent patient stays. Shared owners execute every route and transfer. */
public final class ClinicalCareProcess {
    public static boolean availableWork(FrontierWorldState s,ResidentProfile r){
        if(ClinicalAuthority.needsCare(s,r.id()))return false;
        return s.humanPopulation().clinical().tasks().values().stream().filter(t->t.role()==ClinicalTask.Role.PATIENT&&t.home().equals(r.settlementId())&&!t.actor().equals(r.id()))
            .anyMatch(t->s.humanPopulation().clinical().wounds().get(t.patient())!=null&&s.humanPopulation().clinical().wounds().get(t.patient()).needsDressing(s.bootstrap().ruleset().clinical().quality(r.skill(ResidentSkill.MEDICINE),false))
                &&s.humanPopulation().clinical().tasks().values().stream().noneMatch(other->other.role()==ClinicalTask.Role.MEDIC&&other.patient().equals(t.patient())));
    }
    public static List<ProposedEvent> review(FrontierWorldState initial,ScheduledAction action,long tick){
        var events=new ArrayList<ProposedEvent>();var s=initial;var rules=s.bootstrap().ruleset().clinical();
        var residents=s.humanPopulation().residentsIn(action.subject()).stream().sorted(Comparator.comparing(ResidentProfile::id)).toList();
        for(var r:residents){
            var clinical=s.humanPopulation().clinical();var wound=clinical.wounds().get(r.id());var actor=s.actorLocations().get(r.id());
            if(!clinical.healing().containsKey(r.id())&&(wound!=null&&(!ClinicalAuthority.alive(s,r.id())||wound.observedHealth()!=actor.condition().health().raw())
                    ||wound==null&&ClinicalAuthority.alive(s,r.id())&&actor.condition().health().compareTo(ActorCondition.HEALTHY.health())<0)){
                var v=new ClinicalEvent.Vitality(r.id(),Optional.ofNullable(wound),tick);events.add(new ProposedEvent(r.id(),v));s=ClinicalTransitions.apply(s,r.id(),v,tick);
            }
            wound=s.humanPopulation().clinical().wounds().get(r.id());
            if(wound!=null&&ActorExecutionCoordinator.coldAvailable(s,r.id())&&!s.humanPopulation().clinical().healing().containsKey(r.id())
                    &&s.humanPopulation().clinical().tasks().values().stream().noneMatch(t->t.patient().equals(r.id())&&t.tend().isPresent())){
                var recovered=new ClinicalEvent.Recovered(r.id(),Optional.empty(),tick,ClinicalAuthority.recovery(s,wound,tick),true);
                events.add(new ProposedEvent(r.id(),recovered));s=ClinicalTransitions.apply(s,r.id(),recovered,tick);
            }
            if(ClinicalAuthority.needsCare(s,r.id())&&s.humanPopulation().clinical().taskFor(r.id()).isEmpty()
                    &&!s.humanPopulation().meals().containsKey(r.id())&&!s.combat().evasions().containsKey(r.id())&&!s.actorMovements().containsKey(r.id())&&ActorExecutionComposition.LIFECYCLE.mayBegin(s,r.id())){
                var id=new SubjectId("clinical:patient/"+r.id().value().replace(':','-'));
                var execution=s.actorExecutions().next(r.id(),ActorActivityKind.CLINICAL_CARE,id);
                var task=new ClinicalTask(id,r.settlementId(),r.id(),execution,ClinicalTask.Role.PATIENT,ClinicalTask.Phase.SELECT,1,
                        s.actorLocations().get(r.id()).supportingSurface(),Optional.empty(),Optional.empty(),tick);
                var started=new ClinicalEvent.Started(task);s=ClinicalTransitions.apply(s,id,started,tick);events.add(new ProposedEvent(id,started));events.add(ClinicalContinuation.wake(id,tick));
            }
        }
        for(var task:s.humanPopulation().clinical().tasks().values())if(task.home().equals(action.subject()))events.add(ClinicalContinuation.wake(task.id(),tick));
        var candidates=ResidentWorkComposition.SELECTION.eligible(s,action.subject(),ResidentWorkKind.MEDICAL,HumanCapability.MEDICINE,tick).stream()
                .sorted(Comparator.comparingInt((ResidentProfile r)->r.skill(ResidentSkill.MEDICINE)).reversed().thenComparing(ResidentProfile::id)).toList();
        for(var medic:candidates){
            var snapshot=s;
            var patient=s.humanPopulation().clinical().tasks().values().stream().filter(t->t.role()==ClinicalTask.Role.PATIENT&&t.home().equals(action.subject()))
                    .filter(t->ClinicalAuthority.alive(snapshot,t.patient())&&!t.actor().equals(medic.id())&&snapshot.humanPopulation().clinical().wounds().containsKey(t.patient()))
                    .filter(t->snapshot.humanPopulation().clinical().tasks().values().stream().noneMatch(other->other.role()==ClinicalTask.Role.MEDIC&&other.patient().equals(t.patient())))
                    .filter(t->snapshot.humanPopulation().clinical().wounds().get(t.patient()).needsDressing(rules.quality(medic.skill(ResidentSkill.MEDICINE),false)))
                    .sorted(Comparator.comparingLong((ClinicalTask t)->snapshot.actorLocations().get(t.patient()).condition().health().raw()).thenComparing(ClinicalTask::patient)).findFirst();
            if(patient.isEmpty())break;
            var id=new SubjectId("clinical:medic/"+medic.id().value().replace(':','-'));var execution=s.actorExecutions().next(medic.id(),ActorActivityKind.CLINICAL_CARE,id);
            var task=new ClinicalTask(id,medic.settlementId(),patient.orElseThrow().patient(),execution,ClinicalTask.Role.MEDIC,ClinicalTask.Phase.SELECT,1,
                    s.actorLocations().get(medic.id()).supportingSurface(),Optional.empty(),Optional.empty(),tick);
            var started=new ClinicalEvent.Started(task);s=ClinicalTransitions.apply(s,id,started,tick);events.add(new ProposedEvent(id,started));events.add(ClinicalContinuation.wake(id,tick));
        }
        events.add(new ProposedEvent(action.subject(),new ScheduleEffect.Rescheduled(action.id(),ClinicalContinuation.review(action.subject(),tick+rules.reviewTicks()))));
        return List.copyOf(events);
    }
    public static List<ProposedEvent> progress(FrontierWorldState s,ScheduledAction action,long tick){
        try{return progressKnown(s,action,tick);}
        catch(KnownPedestrianNavigation.RouteUnavailable waiting){return retry(s,action,tick);}
    }
    private static List<ProposedEvent> progressKnown(FrontierWorldState s,ScheduledAction action,long tick){
        var task=s.humanPopulation().clinical().tasks().get(action.subject());if(task==null)return cancelled(action);
        var rules=s.bootstrap().ruleset().clinical();var use=s.humanPopulation().restPlaces().uses().get(task.actor());
        if(task.pending()||ClinicalAuthority.pendingOwner(s,task.actor()).isPresent()||use!=null&&use.pendingBody().isPresent())return retry(s,action,tick);
        if(!ClinicalAuthority.alive(s,task.actor())||!ClinicalAuthority.alive(s,task.patient())){
            if(ClinicalAuthority.alive(s,task.actor())&&s.actorMovements().containsKey(task.actor())){
                var assessment=ActorMovementProcess.ownerTransitionCheckpoint(s,task.execution(),tick);
                if(assessment instanceof ActivityInterruptionPlanner.Waiting)return retry(s,action,tick);
                var ready=((ActivityInterruptionPlanner.Ready)assessment).validate(s,task.actor());
                var events=new ArrayList<>(ready.events());events.add(cancel(action));events.add(ClinicalContinuation.wake(task.id(),tick));return List.copyOf(events);
            }
            if(use!=null&&use.phase()==RestUse.Phase.RESTING&&ClinicalAuthority.alive(s,task.actor()))return leaveBed(s,task,action,use,tick);
            return ended(s,task,action,tick);
        }
        if(!ClinicalAuthority.current(s,task))return cancelled(action);
        if(task.role()==ClinicalTask.Role.MEDIC&&ClinicalAuthority.needsCare(s,task.actor())&&!s.actorMovements().containsKey(task.actor()))return ended(s,task,action,tick);
        if(s.actorMovements().containsKey(task.actor()))return cancelled(action);
        var wound=s.humanPopulation().clinical().wounds().get(task.patient());
        if(use!=null&&use.phase()==RestUse.Phase.RESTING){
            if(!RestPlaces.present(s,use)||s.humanPopulation().restPlaces().unavailable().contains(use.place().id())||!ClinicalAuthority.wantsRest(s,task.actor())||hungry(s,task.actor(),tick)||
                s.combat().evasions().containsKey(task.actor())||task.phase()==ClinicalTask.Phase.SUPPLY)
                return leaveBed(s,task,action,use,tick);
            boolean selfDressing=wound!=null&&wound.needsDressing(ClinicalAuthority.quality(s,task));
            if(!selfDressing||anotherProcedure(s,task)){
                if(task.phase()!=ClinicalTask.Phase.REST)return destination(task,action,ClinicalTask.Phase.REST,s.actorLocations().get(task.actor()).supportingSurface(),tick);
                return retry(s,action,tick);
            }
        }
        if(wound==null||task.role()==ClinicalTask.Role.MEDIC&&!wound.needsDressing(ClinicalAuthority.quality(s,task)))return ended(s,task,action,tick);
        if(task.phase()==ClinicalTask.Phase.SUPPLY){
            var visit=task.supply().orElseThrow();
            if(!ActorSupplyVisits.available(s,visit))return List.of(new ProposedEvent(task.id(),new ClinicalEvent.SupplyAborted(task.id(),task.revision(),Optional.empty())),cancel(action),ClinicalContinuation.wake(task.id(),tick));
            if(!task.order().arrivedAt(s.actorLocations().get(task.actor()).supportingSurface()))return move(task,action,tick);
            if(ActorSupplyVisits.coldAvailable(s,visit)&&ClinicalTransitions.serviceAvailable(s,visit)){
                return List.of(new ProposedEvent(task.id(),new ClinicalEvent.Supplied(task.id(),task.revision(),Optional.empty(),List.of(),List.of())),cancel(action),ClinicalContinuation.wake(task.id(),tick));
            }return retry(s,action,tick);
        }
        boolean needsDressing=wound.needsDressing(ClinicalAuthority.quality(s,task));
        if(needsDressing){
            if(ActorConsumables.available(s,task.actor(),task.home(),rules.bandage())==0){
                var selected=ActorSupplyVisits.select(s,task.execution(),task.home(),rules.bandage(),rules.supplyLoad(),ClinicalTask.Phase.SUPPLY.wireTag(),task.revision()+1);
                if(selected.isPresent())return List.of(new ProposedEvent(task.id(),new ClinicalEvent.SourceSelected(task.id(),task.revision(),selected.orElseThrow())),cancel(action),ClinicalContinuation.wake(task.id(),tick));
                if(task.role()==ClinicalTask.Role.MEDIC)return ended(s,task,action,tick);
            }else{
                if(!ClinicalAuthority.nearPatient(s,task)||s.actorMovements().containsKey(task.patient())){
                    if(task.role()==ClinicalTask.Role.PATIENT)return retry(s,action,tick);
                    var target=beside(s,task);if(target.isEmpty())return retry(s,action,tick);
                    if(task.phase()!=ClinicalTask.Phase.APPROACH||!task.station().equals(target.orElseThrow()))return destination(task,action,ClinicalTask.Phase.APPROACH,target.orElseThrow(),tick);
                    return move(task,action,tick);
                }
                if(anotherProcedure(s,task))return retry(s,action,tick);
                if(task.phase()!=ClinicalTask.Phase.TEND)return destination(task,action,ClinicalTask.Phase.TEND,s.actorLocations().get(task.actor()).supportingSurface(),tick);
                if(s.humanPopulation().clinical().tasks().values().stream().anyMatch(other->!other.id().equals(task.id())&&other.patient().equals(task.patient())&&other.tend().isPresent()))return retry(s,action,tick);
                if(ActorExecutionCoordinator.coldAvailable(s,task.actor())&&ActorExecutionCoordinator.coldAvailable(s,task.patient())
                        &&tick>=task.startedAt()+rules.procedureTicks(s.humanPopulation().resident(task.actor()).skill(ResidentSkill.MEDICINE))){
                    var portion=ActorConsumableUse.select(s,task.actor(),task.home(),rules.bandage());if(portion.isEmpty())return retry(s,action,tick);
                    return List.of(new ProposedEvent(task.id(),new ClinicalEvent.Tended(task.id(),task.revision(),Optional.empty(),Optional.of(portion.orElseThrow().account()),Optional.of(portion.orElseThrow().lot()),true,
                        List.of())),cancel(action),ClinicalContinuation.wake(task.id(),tick));
                }
                long complete=task.startedAt()+rules.procedureTicks(s.humanPopulation().resident(task.actor()).skill(ResidentSkill.MEDICINE));
                return tick<complete?List.of(new ProposedEvent(task.id(),new ScheduleEffect.Rescheduled(action.id(),ClinicalContinuation.progress(task.id(),complete)))):retry(s,action,tick);
            }
        }
        if(task.role()==ClinicalTask.Role.MEDIC)return ended(s,task,action,tick);
        if(ClinicalAuthority.wantsRest(s,task.actor())){
            if(use==null){
                var selected=RestPlaces.select(s,task.execution(),task.home(),RestPlaceProfile.Purpose.MEDICAL,tick);
                if(selected.isPresent())return List.of(new ProposedEvent(task.actor(),new RestEvent.Reserved(selected.orElseThrow())),cancel(action),ClinicalContinuation.wake(task.id(),tick));
                return retry(s,action,tick);
            }
            if(!RestPlaces.present(s,use)||s.humanPopulation().restPlaces().unavailable().contains(use.place().id())||!RestPlaces.approachClear(s,use)){
                if(!ActorExecutionComposition.CAPABILITIES.require(task.execution().activityKind()).permitsRestRelease(s,task.execution(),use))return retry(s,action,tick);
                return List.of(new ProposedEvent(task.actor(),new RestEvent.Released(task.actor(),use.revision())),cancel(action),ClinicalContinuation.wake(task.id(),tick));
            }
            if(!s.actorLocations().get(task.actor()).supportingSurface().equals(use.approach())){
                if(task.phase()!=ClinicalTask.Phase.APPROACH||!task.station().equals(use.approach()))return destination(task,action,ClinicalTask.Phase.APPROACH,use.approach(),tick);
                return move(task,action,tick);
            }
            if(task.phase()!=ClinicalTask.Phase.REST)return destination(task,action,ClinicalTask.Phase.REST,use.approach(),tick);
            if(ActorExecutionCoordinator.coldAvailable(s,task.actor()))return List.of(new ProposedEvent(task.actor(),new RestEvent.Changed(task.actor(),use.revision(),Optional.empty(),RestUse.Action.ENTER,true,new
                SurfaceAnchor(use.place().head()).standingBody())),cancel(action),ClinicalContinuation.wake(task.id(),tick));
            return retry(s,action,tick);
        }
        return ended(s,task,action,tick);
    }
    private static boolean anotherProcedure(FrontierWorldState s,ClinicalTask task){return s.humanPopulation().clinical().tasks().values().stream().anyMatch(other->!other.id().equals(task.id())&&
        other.patient().equals(task.patient())&&other.phase()==ClinicalTask.Phase.TEND);}
    private static Optional<SurfaceAnchor> beside(FrontierWorldState s,ClinicalTask task){
        var origin=s.actorLocations().get(task.actor()).supportingSurface();var target=s.actorLocations().get(task.patient()).supportingSurface();
        var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(s,task.home(),List.of()).forActor(s,task.actor());
        for(var station:PedestrianInteractionStations.within(knowledge.geometry(),target,3,Set.of()).stream().filter(p->ClinicalAuthority.procedureStation(s,task,p)).sorted(Comparator.comparingLong(p->p.manhattanDistance(origin))).toList()){
            try{knowledge.plannedBetween(origin,station);return Optional.of(station);}catch(KnownPedestrianNavigation.RouteUnavailable wait){if(wait.status()!=PedestrianRouteResult.Status.NO_PATH)throw wait;}
        }return Optional.empty();
    }
    public static boolean hungry(FrontierWorldState s,SubjectId actor,long tick){
        var r=s.humanPopulation().resident(actor);long at=Math.max(tick,s.humanPopulation().nutrition(actor).lastEvaluatedTick());
        return s.humanPopulation().nutrition(actor).accrueThrough(at,s.bootstrap().ruleset().residentLife(),r.characteristics().effectiveMetabolismPermille(at)).wantsFood(s.bootstrap().ruleset().residentLife())
                &&ResidentMealOpportunity.mayPlan(s,actor,at);
    }
    private static List<ProposedEvent> leaveBed(FrontierWorldState s,ClinicalTask task,ScheduledAction action,RestUse use,long tick){
        if(ActorExecutionCoordinator.coldAvailable(s,task.actor())){
            var exits=RestPlaces.exitStations(s,use);if(exits.isEmpty())return retry(s,action,tick);
            return List.of(new ProposedEvent(task.actor(),new RestEvent.Changed(task.actor(),use.revision(),Optional.empty(),RestUse.Action.EXIT,true,exits.getFirst().standingBody())),cancel(action),ClinicalContinuation.wake(task.id(),tick));
        }
        return retry(s,action,tick); // The native rest adapter owns waking a physical body.
    }
    private static List<ProposedEvent> destination(ClinicalTask task,ScheduledAction action,ClinicalTask.Phase phase,SurfaceAnchor station,long tick){return List.of(new ProposedEvent(task.id(),new
        ClinicalEvent.Destination(task.id(),task.revision(),phase,station)),cancel(action),ClinicalContinuation.wake(task.id(),tick));}
    private static List<ProposedEvent> move(ClinicalTask task,ScheduledAction action,long tick){var events=new ArrayList<>(ActorMovementProcess.planStart(new ActorMovement(task.order(),tick,new
        ActorMovementContext.ClinicalLeg(task.id(),task.revision()),task.execution())));events.add(cancel(action));return List.copyOf(events);}
    private static List<ProposedEvent> ended(FrontierWorldState s,ClinicalTask task,ScheduledAction action,long tick){
        var use=s.humanPopulation().restPlaces().uses().get(task.actor());var wound=s.humanPopulation().clinical().wounds().get(task.actor());
        var events=new ArrayList<ProposedEvent>();
        if(ClinicalAuthority.alive(s,task.actor())&&use!=null&&wound!=null&&use.completed().filter(i->i.end()>wound.evaluatedAt()).isPresent()){
            if(!ActorExecutionCoordinator.coldAvailable(s,task.actor()))return retry(s,action,tick);
            events.add(new ProposedEvent(task.actor(),new ClinicalEvent.Recovered(task.actor(),Optional.empty(),tick,ClinicalAuthority.recovery(s,wound,tick),true)));
        }
        events.add(new ProposedEvent(task.id(),new ClinicalEvent.Ended(task.id(),task.revision())));events.add(cancel(action));events.add(ResidentActivityProcess.wakeAfterActivity(task.actor(),tick));
        if(task.role()==ClinicalTask.Role.MEDIC)events.add(new ProposedEvent(task.home(),new ScheduleEffect.ReconsiderationRequested(ClinicalContinuation.review(task.home(),tick+1))));
        return List.copyOf(events);
    }
    private static ProposedEvent cancel(ScheduledAction action){return new ProposedEvent(action.subject(),new ScheduleEffect.Cancelled(action.id()));}
    private static List<ProposedEvent> cancelled(ScheduledAction action){return List.of(cancel(action));}
    private static List<ProposedEvent> retry(FrontierWorldState s,ScheduledAction action,long tick){return List.of(new ProposedEvent(action.subject(),new ScheduleEffect.Rescheduled(action.id(),
        ClinicalContinuation.progress(action.subject(),tick+s.bootstrap().ruleset().clinical().reviewTicks()))));}
    private ClinicalCareProcess(){}
}
