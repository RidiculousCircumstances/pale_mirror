package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real care transitions over explicitly constructed unit preconditions; not native acceptance. */
class ClinicalCareTest {
    private static FrontierWorldState initial(){return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:clinical-test"),139,FrontierRulesets.installed("frontier-v3-clinical-care-r35")));}
    private record Patient(FrontierWorldState state,ClinicalTask task){}
    private static Patient patient(){
        var s=initial();var home=s.bootstrap().settlements().getFirst();var actor=s.humanPopulation().residents().values().stream().filter(r->r.settlementId().equals(home.id())).min(Comparator.comparing(ResidentProfile::id)).orElseThrow().id();
        var endpoint=ResourceStorageAccess.at(s,FrontierWorldState.depotId(home.id())).endpoint();
        var location=s.actorLocations().get(actor);s=s.withChanges(FrontierWorldStateUpdate.begin().actorLocations(ImmutableLookupMap.withEntry(s.actorLocations(),actor,
                location.withObserved(endpoint.station().standingBody(),location.condition().withHealth(FixedScalar.whole(12))))));
        s=ClinicalTransitions.apply(s,actor,new ClinicalEvent.Vitality(actor,Optional.empty(),1),1);
        var id=new SubjectId("clinical:unit-patient");var execution=s.actorExecutions().next(actor,ActorActivityKind.CLINICAL_CARE,id);
        var task=new ClinicalTask(id,home.id(),actor,execution,ClinicalTask.Role.PATIENT,ClinicalTask.Phase.SELECT,1,endpoint.station(),Optional.empty(),Optional.empty(),1);
        s=ClinicalTransitions.apply(s,id,new ClinicalEvent.Started(task),1);return new Patient(s,task);
    }
    @Test void bandagesHaveActualRecipeAndNeedButNoGenesisStock(){
        var s=initial();var rules=s.bootstrap().ruleset();var recipe=rules.production().require(new ProductionRecipe.Id("pale_mirror:bandages"));
        assertEquals(4,recipe.product().quantity());assertEquals(1,recipe.ingredients().getFirst().quantity());assertFalse(recipe.requiresCombustion());
        assertEquals(0,s.inventory().fungibleResources().totalQuantity(s.bootstrap().settlements().getFirst().id(),rules.clinical().bandage()));
        assertTrue(new ClinicalReserveSupply().read(s,s.bootstrap().settlements().getFirst().id()).needs().get(rules.clinical().bandage()).immediate()>0);
    }
    @Test void selfDressingMovesActualSupplyConsumesOneAndDoesNotInstantlyHeal(){
        var r=patient();var s=r.state();var t=r.task();var container=s.inventory().containers().get(FrontierWorldState.depotId(t.home()));
        s=AutomaticStationProductionTest.supply(s,container,s.bootstrap().ruleset().clinical().bandage(),4,"clinical-stock");
        var visit=ActorSupplyVisits.select(s,t.execution(),t.home(),s.bootstrap().ruleset().clinical().bandage(),4,ClinicalTask.Phase.SUPPLY.wireTag(),2).orElseThrow();
        s=ClinicalTransitions.apply(s,t.id(),new ClinicalEvent.SourceSelected(t.id(),t.revision(),visit),2);t=s.humanPopulation().clinical().tasks().get(t.id());
        s=s.withActorBody(t.actor(),t.station().standingBody());
        s=ClinicalTransitions.apply(s,t.id(),new ClinicalEvent.Supplied(t.id(),t.revision(),Optional.empty(),List.of(),List.of()),3);t=s.humanPopulation().clinical().tasks().get(t.id());
        s=ClinicalTransitions.apply(s,t.id(),new ClinicalEvent.Destination(t.id(),t.revision(),ClinicalTask.Phase.TEND,t.station()),4);t=s.humanPopulation().clinical().tasks().get(t.id());
        var portion=ActorConsumableUse.select(s,t.actor(),t.home(),s.bootstrap().ruleset().clinical().bandage()).orElseThrow();
        var tended=new ClinicalEvent.Tended(t.id(),t.revision(),Optional.empty(),Optional.of(portion.account()),Optional.of(portion.lot()),true,List.of());
        var early=s;var task=t;assertThrows(IllegalArgumentException.class,()->ClinicalTransitions.apply(early,task.id(),tended,5));
        var codec=new FrontierWorldStateCodec();s=codec.decode(codec.encode(s));
        s=ClinicalTransitions.apply(s,t.id(),tended,300);
        assertEquals(FixedScalar.whole(12),s.actorLocations().get(t.actor()).condition().health());
        assertEquals(3,ActorConsumables.available(s,t.actor(),t.home(),s.bootstrap().ruleset().clinical().bandage()));
        assertEquals(4*FixedScalar.SCALE,s.humanPopulation().clinical().wounds().get(t.actor()).dressed());
        assertEquals(1,s.actorExecutions().actors().get(t.actor()).generation());assertTrue(s.actorExecutions().actors().get(t.actor()).continuations().isEmpty());
        assertEquals(s,codec.decode(codec.encode(s)));var after=s;assertThrows(IllegalArgumentException.class,()->ClinicalTransitions.apply(after,task.id(),tended,301));
    }
    @Test void realKernelRunsSupplyDressingBedRestAndTerminalRelease(){
        var r=patient();var state=r.state();var t=r.task();
        state=AutomaticStationProductionTest.supply(state,state.inventory().containers().get(FrontierWorldState.depotId(t.home())),state.bootstrap().ruleset().clinical().bandage(),16,"clinical-kernel-stock");
        var base=io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var schedules=new ArrayList<>(base.initialSchedules());schedules.add(ClinicalContinuation.progress(t.id(),2));
        var configuration=new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(),state,new SimInstant(1),
                base.commandPlanner(),base.scheduledPlanner(),base.reducer(),base.stateCodec(),base.projectionMapper(),base.limits(),
                schedules,base.transactionCommitter(),base.stateValidator(),base.executionMetrics(),base.kernelQuarantineReporter());
        var engine=io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        boolean occupied=false,restarted=false,medicFinishedDuringStay=false;var medics=new HashSet<SubjectId>();
        for(long tick=2;tick<12000;tick+=20){
            var advanced=engine.advanceTo(new SimInstant(tick),new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1000,1000));
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE,advanced.status().kind(),advanced.status().toString());
            engine.compact(engine.executionView().revision());
            var current=engine.canonicalState().state();
            var use=current.humanPopulation().restPlaces().uses().get(t.actor());occupied|=use!=null&&use.phase()==io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Phase.RESTING;
            current.humanPopulation().clinical().tasks().values().stream().filter(task->task.role()==ClinicalTask.Role.MEDIC&&task.patient().equals(t.patient())).map(ClinicalTask::id).forEach(medics::add);
            medicFinishedDuringStay|=use!=null&&use.phase()==io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Phase.RESTING
                    &&medics.stream().anyMatch(id->!current.humanPopulation().clinical().tasks().containsKey(id))
                    &&current.humanPopulation().clinical().wounds().get(t.actor()).qualityPermille()>ClinicalAuthority.quality(current,t);
            if(use!=null&&use.phase()==io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Phase.RESTING&&!restarted){
                var image=new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(base.worldId(),Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(engine.checkpoint(),0)),List.of());
                engine=io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.recoverCanonicalStateAccess(configuration,image);
                assertEquals(current.humanPopulation().clinical(),engine.canonicalState().state().humanPopulation().clinical());
                assertEquals(use,engine.canonicalState().state().humanPopulation().restPlaces().uses().get(t.actor()));restarted=true;
            }
            if(!current.humanPopulation().clinical().tasks().containsKey(t.id())&&current.humanPopulation().clinical().tasks().values().stream().noneMatch(other->other.patient().equals(t.patient())))break;
        }
        var end=engine.canonicalState().state();
        assertTrue(restarted,"a resting patient must survive kernel checkpoint recovery");
        assertTrue(occupied,"a real scheduled patient must reach and occupy a bed through shared movement");
        assertTrue(medicFinishedDuringStay,"the skilled medic must finish a real independent dressing visit while the patient keeps resting");
        assertFalse(end.humanPopulation().clinical().tasks().containsKey(t.id()),"care must end rather than park forever: "+engine.checkpoint().schedules()+" task="+end.humanPopulation().clinical().tasks().get(t.id())+" move="+
            end.actorMovements().get(t.actor()));
        assertFalse(end.humanPopulation().restPlaces().uses().containsKey(t.actor()));
        assertTrue(end.actorLocations().get(t.actor()).condition().health().raw()>=19*FixedScalar.SCALE);
        assertTrue(end.inventory().fungibleResources().totalQuantity(t.home(),end.bootstrap().ruleset().clinical().bandage())<16);
        var codec=new FrontierWorldStateCodec();var decoded=codec.decode(codec.encode(end));
        assertEquals(end.humanPopulation(),decoded.humanPopulation());assertEquals(end.actorLocations(),decoded.actorLocations());assertEquals(end.actorExecutions(),decoded.actorExecutions());
        assertEquals(end.inventory(),decoded.inventory());
    }
    @Test void detachedBedSearchRetainsItsPendingDependencyInsteadOfQuarantiningTheTask(){
        var r=patient();var action=ClinicalContinuation.progress(r.task().id(),2);
        io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanner pending=(geometry,from,to)->
                new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,List.of(),0,0,"not yet requested");
        var prepared=io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.prepare(pending,
                ()->io.farfrontier.palemirror.frontier.v3.process.ClinicalCareProcess.progress(r.state(),action,2));
        assertFalse(prepared.observation().requests().isEmpty(),"the shared planning continuation must own the awaited bed route");
        assertTrue(prepared.observation().result().stream().anyMatch(e->e.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled));
        assertTrue(r.state().humanPopulation().restPlaces().uses().isEmpty(),"a pending path grants neither arrival nor a rest bonus");
    }
    @Test void coldHealingIsSettledBeforePhysicalAdmissionFencesTheActor(){
        var r=patient();var actor=r.task().actor();
        var state=ActorBodyAuthority.demand(r.state(),actor,1000);
        assertTrue(state.actorLocations().get(actor).condition().health().compareTo(r.state().actorLocations().get(actor).condition().health())>0);
        assertEquals(1000,state.humanPopulation().clinical().wounds().get(actor).evaluatedAt());
        assertFalse(ActorExecutionCoordinator.coldAvailable(state,actor));
        assertSame(state,ActorBodyAuthority.demand(state,actor,2000),"prepared/running custody cannot receive another COLD recovery writer");
    }
    @Test void recoveryIsPartitionIndependentAndDressingCannotRenewItsBudget(){
        var rules=ClinicalRules.initial();var actor=new SubjectId("resident:wound");
        var wound=ClinicalWound.observed(actor,FixedScalar.whole(9),FixedScalar.whole(20),0).dressed(700,rules.coverageUnits(),1,0);
        long whole=wound.recoveryUnits(12345,1000,rules);var split=wound;
        for(int tick=1;tick<=12345;tick++){long amount=split.recoveryUnits(tick,1000,rules);split=split.observe(new FixedScalar(split.observedHealth()+amount),tick);}
        assertEquals(wound.observedHealth()+whole,split.observedHealth());
        var tiny=new ClinicalWound(actor,19*FixedScalar.SCALE,20*FixedScalar.SCALE,FixedScalar.SCALE-1,1,700,1,0);
        assertTrue(tiny.recoveryUnits(1,1000,rules)>=1000,"a tiny dressing remainder cannot suppress ordinary healing");
        var fresh=wound.observe(FixedScalar.whole(8),1);assertEquals(wound.untreated()+FixedScalar.SCALE,fresh.untreated());assertEquals(wound.dressed(),fresh.dressed());
        assertThrows(IllegalArgumentException.class,()->fresh.dressed(700,rules.coverageUnits(),wound.harmRevision(),2));
        assertTrue(rules.quality(100,true)>rules.quality(0,false));assertTrue(rules.procedureTicks(100)<rules.procedureTicks(0));
    }
    @Test void dressingAndBedInteractionCannotPrepareOverEachOther(){
        var r=patient();var s=r.state();var t=r.task();var actor=t.actor();
        var use=io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaces.select(s,t.execution(),t.home(),
                io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaceProfile.Purpose.MEDICAL,2).orElseThrow();
        s=io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaces.apply(s,actor,new io.farfrontier.palemirror.frontier.v3.model.rest.RestEvent.Reserved(use),2);
        s=s.withActorBody(actor,use.approach().standingBody());
        s=ClinicalTransitions.apply(s,t.id(),new ClinicalEvent.Destination(t.id(),t.revision(),ClinicalTask.Phase.TEND,use.approach()),3);
        t=s.humanPopulation().clinical().tasks().get(t.id());
        var lot=new SubjectId("lot:clinical-fence");var account=new SubjectId("custody:clinical-fence");var pocket=new ActorItemSlot.Pocket(0);
        var ledger=s.inventory().fungibleResources().issue(new ResourceLot(lot,t.home(),s.bootstrap().ruleset().clinical().bandage(),2,"unit:clinical",List.of()),
                new CustodyAccount(account,new ResourceCustody.Actor(actor),Map.of(lot,2),Map.of(),Optional.of(pocket)));
        s=s.withInventory(s.inventory().withFungibleResources(ledger));s=ActorBodyAuthority.demand(s,actor);
        var body=ActorBodyAuthority.current(s,actor);s=ActorBodyAuthority.running(s,body);
        var stack=new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(actor,ActorBodyId.entityId(s.bootstrap().worldId(),actor),0),s.bootstrap().ruleset().clinical().bandage(),2);
        ledger=s.inventory().fungibleResources();ledger=ledger.rebind(account,body.physicalEpoch(),FungiblePhysicalObservation.bind(ledger,account,body.physicalEpoch(),List.of(stack)));
        s=s.withInventory(s.inventory().withFungibleResources(ledger));var permission=new ActorActuationId(body,t.execution());
        var tend=new ClinicalTend(permission,permission,ActorConsumables.spendable(s,actor,t.home(),s.bootstrap().ruleset().clinical().bandage()).orElseThrow(),lot,
                s.humanPopulation().clinical().wounds().get(actor),ClinicalAuthority.quality(s,t),s.bootstrap().ruleset().clinical().coverageUnits(),5,4,
                s.bootstrap().ruleset().clinical().procedureTicks(s.humanPopulation().resident(actor).skill(ResidentSkill.MEDICINE)));
        var prepared=new ClinicalEvent.TendPrepared(t.id(),t.revision(),tend);
        var dressing=ClinicalTransitions.apply(s,t.id(),prepared,4);
        var bed=new io.farfrontier.palemirror.frontier.v3.model.rest.RestEvent.Prepared(actor,use.revision(),permission,io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Action.ENTER);
        assertThrows(IllegalArgumentException.class,()->io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaces.apply(dressing,actor,bed,4));
        var resting=io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaces.apply(s,actor,bed,4);var taskId=t.id();
        assertThrows(IllegalArgumentException.class,()->ClinicalTransitions.apply(resting,taskId,prepared,4));
        assertEquals(Optional.of(t.id()),ActorInventoryInteractionFences.pendingOwner(resting,actor));
    }
    @Test void pendingNativeBodyFencesColdRecoveryAndDeadActorsCannotHeal(){
        var r=patient();var s=r.state();var actor=r.task().actor();long amount=ClinicalAuthority.recovery(s,s.humanPopulation().clinical().wounds().get(actor),1000);
        var e=new ClinicalEvent.Recovered(actor,Optional.empty(),1000,amount,true);var held=ActorBodyAuthority.demand(s,actor);
        assertThrows(IllegalArgumentException.class,()->ClinicalTransitions.apply(held,actor,e,1000));
        s=ClinicalTransitions.apply(s,actor,e,1000);assertTrue(s.actorLocations().get(actor).condition().health().compareTo(FixedScalar.whole(12))>0);
        var location=s.actorLocations().get(actor);var dead=s.withChanges(FrontierWorldStateUpdate.begin().actorLocations(ImmutableLookupMap.withEntry(s.actorLocations(),actor,location.deadAt(location.body()))));
        assertThrows(IllegalArgumentException.class,()->ClinicalTransitions.apply(dead,actor,new ClinicalEvent.Recovered(actor,Optional.empty(),1100,1,true),1100));
    }
}
