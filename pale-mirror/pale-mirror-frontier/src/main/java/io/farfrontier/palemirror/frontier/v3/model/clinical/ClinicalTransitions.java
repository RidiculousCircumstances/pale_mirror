package io.farfrontier.palemirror.frontier.v3.model.clinical;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.rest.*;
import java.util.*;

/** Atomic care transitions. Every native outcome retires its exact prepared effect once. */
public final class ClinicalTransitions {
    public static FrontierWorldState apply(FrontierWorldState s,SubjectId subject,ClinicalEvent event,long tick){
        var clinical=s.humanPopulation().clinical();var rules=s.bootstrap().ruleset().clinical();
        if(event instanceof ClinicalEvent.Vitality v){
            if(!subject.equals(v.actor())||v.atTick()!=tick||!Optional.ofNullable(clinical.wounds().get(v.actor())).equals(v.expected())
                    ||!s.humanPopulation().residents().containsKey(v.actor())||clinical.healing().containsKey(v.actor()))throw new IllegalArgumentException("stale vitality checkpoint");
            if(!ClinicalAuthority.alive(s,v.actor()))return ClinicalAuthority.update(s,clinical.withoutWound(v.actor()));
            var wound=ClinicalAuthority.observed(s,v.actor(),tick);
            return ClinicalAuthority.update(s,wound.observedHealth()==wound.maximumHealth()?clinical.withoutWound(v.actor()):clinical.with(wound));
        }
        if(event instanceof ClinicalEvent.Started started){
            var t=started.task();var resident=s.humanPopulation().resident(t.actor());
            if(!subject.equals(t.id())||!ClinicalAuthority.enabled(s)||resident==null||!resident.settlementId().equals(t.home())
                    ||!ClinicalAuthority.alive(s,t.actor())||!ClinicalAuthority.alive(s,t.patient())||t.phase()!=ClinicalTask.Phase.SELECT||t.revision()!=1||t.pending()||t.supply().isPresent()
                    ||t.startedAt()!=tick||!t.station().equals(s.actorLocations().get(t.actor()).supportingSurface())
                    ||clinical.tasks().containsKey(t.id())||clinical.taskFor(t.actor()).isPresent()
                    ||t.role()==ClinicalTask.Role.PATIENT&&!ClinicalAuthority.needsCare(s,t.patient())
                    ||t.role()==ClinicalTask.Role.MEDIC&&clinical.tasks().values().stream().noneMatch(patient->patient.role()==ClinicalTask.Role.PATIENT&&patient.patient().equals(t.patient())&&patient.home().equals(t.home()))
                    ||t.role()==ClinicalTask.Role.MEDIC&&(!HumanAssignmentProjection.compile(s).idle(t.actor())
                        ||!SettlementWorkPolicy.permissions(s,t.home()).permits(ResidentWorkKind.MEDICAL,t.actor())
                        ||!s.humanPopulation().resident(t.patient()).settlementId().equals(t.home())))throw new IllegalArgumentException("invalid clinical admission");
            var lifecycle=t.role()==ClinicalTask.Role.PATIENT?ActorExecutionComposition.LIFECYCLE.prepareBegin(s,t.execution(),tick):ActorExecutionComposition.LIFECYCLE.prepareVacant(s,t.execution());
            return lifecycle.commitFromPrepared(s,prepared->FrontierWorldStateUpdate.begin().humanPopulation(
                    prepared.humanPopulation().withClinical(prepared.humanPopulation().clinical().with(t))));
        }
        if(event instanceof ClinicalEvent.HealingPrepared prepared)return prepareHealing(s,subject,prepared.healing(),tick);
        if(event instanceof ClinicalEvent.Recovered recovered)return recover(s,subject,recovered,tick);
        SubjectId id=switch(event){
            case ClinicalEvent.Destination e->e.task();case ClinicalEvent.Ended e->e.task();case ClinicalEvent.SourceSelected e->e.task();
            case ClinicalEvent.SupplyPrepared e->e.task();case ClinicalEvent.Supplied e->e.task();case ClinicalEvent.SupplyAborted e->e.task();
            case ClinicalEvent.TendPrepared e->e.task();case ClinicalEvent.Tended e->e.task();default->throw new IllegalArgumentException("undeclared care event");};
        long revision=switch(event){
            case ClinicalEvent.Destination e->e.revision();case ClinicalEvent.Ended e->e.revision();case ClinicalEvent.SourceSelected e->e.revision();
            case ClinicalEvent.SupplyPrepared e->e.revision();case ClinicalEvent.Supplied e->e.revision();case ClinicalEvent.SupplyAborted e->e.revision();
            case ClinicalEvent.TendPrepared e->e.revision();case ClinicalEvent.Tended e->e.revision();default->throw new IllegalArgumentException("undeclared care event");};
        if(!subject.equals(id))throw new IllegalArgumentException("foreign care subject");var t=ClinicalAuthority.task(s,id,revision);
        if(event instanceof ClinicalEvent.Ended){
            var rest=s.humanPopulation().restPlaces().uses().get(t.actor());var injury=clinical.wounds().get(t.actor());
            if(ClinicalAuthority.alive(s,t.actor())&&rest!=null&&injury!=null&&rest.completed().filter(i->i.end()>injury.evaluatedAt()).isPresent())
                throw new IllegalArgumentException("rest interval remains unaccounted");
            if(ClinicalAuthority.pending(s,t)||s.actorMovements().containsKey(t.actor()))throw new IllegalArgumentException("cannot end unsettled care");
            var executions=s.actorExecutions();var retained=executions.actors().get(t.actor());
            if(retained!=null&&(retained.current().equals(Optional.of(t.execution()))||retained.continuations().contains(t.execution())))
                executions=ActorExecutionComposition.LIFECYCLE.retire(s,t.actor(),ActorActivityKind.CLINICAL_CARE,t.id());
            return s.withChanges(FrontierWorldStateUpdate.begin().actorExecutions(executions).humanPopulation(s.humanPopulation()
                    .withClinical(clinical.withoutTask(t.id())).withRestPlaces(s.humanPopulation().restPlaces().without(t.actor()))));
        }
        if(event instanceof ClinicalEvent.Destination destination){
            s.actorExecutions().requireCurrent(t.execution());
            var rest=s.humanPopulation().restPlaces().uses().get(t.actor());
            if(t.pending()||s.actorMovements().containsKey(t.actor())||destination.phase()==ClinicalTask.Phase.SUPPLY
                    ||rest!=null&&rest.phase()==RestUse.Phase.RESTING&&destination.phase()==ClinicalTask.Phase.APPROACH
                    ||destination.phase()==ClinicalTask.Phase.TEND&&!destination.station().equals(s.actorLocations().get(t.actor()).supportingSurface()))
                throw new IllegalArgumentException("unsettled clinical destination");
            return ClinicalAuthority.update(s,clinical.with(t.phase(destination.phase(),destination.station(),tick)));
        }
        if(event instanceof ClinicalEvent.SourceSelected selected){
            s.actorExecutions().requireCurrent(t.execution());var visit=selected.visit();var p=(ActorContainerItemOrder.Portion.Fungible)visit.order().portion();
            var rest=s.humanPopulation().restPlaces().uses().get(t.actor());
            if(t.pending()||t.supply().isPresent()||s.actorMovements().containsKey(t.actor())||!visit.execution().equals(t.execution())||!visit.economicOwner().equals(t.home())
                    ||rest!=null&&rest.phase()==RestUse.Phase.RESTING
                    ||visit.pending().isPresent()||!p.itemKind().equals(rules.bandage())||p.quantity()>rules.supplyLoad()
                    ||visit.order().goalRevision()!=t.revision()+1||visit.order().goalOrdinal()!=ClinicalTask.Phase.SUPPLY.wireTag()
                    ||ActorConsumables.available(s,t.actor(),t.home(),rules.bandage())>0||!ActorSupplyVisits.available(s,visit)
                    ||!ActorConsumables.freePocket(s,t.actor()).equals(Optional.of(visit.order().actorSlot())))throw new IllegalArgumentException("invalid clinical supply selection");
            ActorCarriedResources.requireNewAccountCapacity(s.inventory().fungibleResources(),t.actor(),p.destinationAccountId());
            return ClinicalAuthority.update(s,clinical.with(t.withSupply(visit)));
        }
        if(event instanceof ClinicalEvent.SupplyPrepared prepared){
            s.actorExecutions().requireCurrent(t.execution());var visit=t.supply().orElseThrow();var lease=Objects.requireNonNull(s.ambientLeases().get(t.actor()),"clinical supply scope");
            prepared.step().observation().require(s,t.execution(),lease.revision(),visit.order().station().standingBody());
            if(visit.pending().isPresent()||!ActorSupplyVisits.available(s,visit)||!serviceAvailable(s,visit)||s.actorMovements().containsKey(t.actor())
                    ||!MaterialSourceSelection.select(s.inventory().fungibleResources(),visit.order()).equals(prepared.step().source())
                    ||prepared.step().destinationEpoch()!=prepared.step().observation().actuation().body().physicalEpoch())throw new IllegalArgumentException("invalid clinical supply preimage");
            return ClinicalAuthority.update(s,clinical.with(t.withSupplyPrepared(visit.prepared(prepared.step()))));
        }
        if(event instanceof ClinicalEvent.Supplied loaded){
            var visit=t.supply().orElseThrow();FrontierWorldStateUpdate update;
            if(loaded.step().isEmpty()){
                if(visit.pending().isPresent()||!ActorExecutionCoordinator.coldAvailable(s,t.actor())||!ActorSupplyVisits.available(s,visit)||!serviceAvailable(s,visit)
                        ||!t.order().arrivedAt(s.actorLocations().get(t.actor()).supportingSurface()))throw new IllegalArgumentException("clinical cold supply lacks exact available arrival");
                update=ActorResourceTransferComposition.cold(s,visit.order());
            }else{
                if(!loaded.step().equals(visit.pending()))throw new IllegalArgumentException("foreign clinical supply receipt");var step=loaded.step().orElseThrow();
                update=ActorBodyAuthority.retainsPhysicalCustody(s,t.actor())?ActorResourceTransferComposition.observed(s,visit.order(),step.sourceEpoch(),step.destinationEpoch(),loaded.source(),loaded.destination())
                        :ActorResourceTransferComposition.retiredObserved(s,visit.order(),step.observation().actuation(),step.sourceEpoch(),step.destinationEpoch(),loaded.source(),loaded.destination());
            }
            return s.withChanges(update.humanPopulation(s.humanPopulation().withClinical((loaded.step().isPresent()?clinical.supplied(visit):clinical).with(t.settled(ClinicalTask.Phase.SELECT,tick)))));
        }
        if(event instanceof ClinicalEvent.SupplyAborted aborted){
            var visit=t.supply().orElseThrow();if(!aborted.step().equals(visit.pending())||aborted.step().isEmpty()&&s.actorMovements().containsKey(t.actor()))throw new IllegalArgumentException("foreign or moving clinical supply withdrawal");
            return ClinicalAuthority.update(s,clinical.with(t.settled(ClinicalTask.Phase.SELECT,tick)));
        }
        if(event instanceof ClinicalEvent.TendPrepared prepared){
            var tend=prepared.tend();validateTend(s,t,tend,tick);
            return ClinicalAuthority.update(s,clinical.with(t.withTend(tend)));
        }
        var outcome=(ClinicalEvent.Tended)event;var wound=clinical.wounds().get(t.patient());var update=FrontierWorldStateUpdate.begin();
        if(outcome.tend().isPresent()){
            var tend=outcome.tend().orElseThrow();if(!t.tend().equals(outcome.tend()))throw new IllegalArgumentException("foreign dressing receipt");
            if(outcome.applied()){
                update.inventory(s.inventory().withFungibleResources(ActorConsumableUse.observed(s,t.actor(),t.home(),rules.bandage(),tend.bandage(),tend.lot(),outcome.remaining())));
                if(ClinicalAuthority.alive(s,t.patient()))wound=tend.wound().dressed(tend.quality(),tend.coverage(),tend.harmRevision(),tick)
                        .observe(s.actorLocations().get(t.patient()).condition().health(),tick);
            }
        }else{
            s.actorExecutions().requireCurrent(t.execution());
            if(t.pending()||t.phase()!=ClinicalTask.Phase.TEND||!ClinicalAuthority.nearPatient(s,t)||!ClinicalAuthority.alive(s,t.patient())
                    ||!ActorExecutionCoordinator.coldAvailable(s,t.actor())||!ActorExecutionCoordinator.coldAvailable(s,t.patient())
                    ||tick<t.startedAt()+rules.procedureTicks(s.humanPopulation().resident(t.actor()).skill(ResidentSkill.MEDICINE))
                    ||wound==null||wound.observedHealth()!=s.actorLocations().get(t.patient()).condition().health().raw())throw new IllegalArgumentException("cold dressing lacks its finite safe procedure");
            update.inventory(s.inventory().withFungibleResources(ActorConsumableUse.cold(s,t.actor(),t.home(),rules.bandage(),new ActorConsumableUse.Portion(outcome.coldAccount().orElseThrow(),outcome.coldLot().orElseThrow()))));
            wound=wound.dressed(ClinicalAuthority.quality(s,t),rules.coverageUnits(),wound.harmRevision(),tick);
        }
        var next=clinical.with(t.settled(ClinicalTask.Phase.SELECT,tick));if(wound!=null)next=next.with(wound);
        return s.withChanges(update.humanPopulation(s.humanPopulation().withClinical(next)));
    }
    public static boolean serviceAvailable(FrontierWorldState s,ActorSupplyVisit visit){return ActorSupplyVisits.service(visit,ServiceAccessDemand.Kind.CLINICAL_SUPPLY).map(id->ServiceAccessCoordinator.available(s,id)).orElse(true);}
    private static void validateTend(FrontierWorldState s,ClinicalTask t,ClinicalTend tend,long tick){
        ActorBodyAuthority.requireActuation(s,tend.performer());ActorBodyAuthority.requireActuation(s,tend.patient());var rules=s.bootstrap().ruleset().clinical();
        var wound=s.humanPopulation().clinical().wounds().get(t.patient());
        if(t.pending()||ActorInventoryInteractionFences.pending(s,t.actor())||ActorInventoryInteractionFences.pending(s,t.patient())||t.phase()!=ClinicalTask.Phase.TEND||
            !tend.performer().execution().equals(t.execution())||!tend.patient().execution().actorId().equals(t.patient())
                ||!tend.wound().equals(wound)||wound.observedHealth()!=s.actorLocations().get(t.patient()).condition().health().raw()||!wound.needsDressing(tend.quality())
                ||tend.quality()!=ClinicalAuthority.quality(s,t)||tend.coverage()!=rules.coverageUnits()||tend.startedAt()!=tick||tend.attempt()!=tick+1
                ||tend.duration()!=rules.procedureTicks(s.humanPopulation().resident(t.actor()).skill(ResidentSkill.MEDICINE))
                ||tend.bandage().authorityEpoch()!=tend.performer().body().physicalEpoch()||!tend.bandage().itemKind().equals(rules.bandage())
                ||!tend.bandage().equals(s.inventory().fungibleResources().bindings().get(tend.bandage().id()))||!ClinicalAuthority.nearPatient(s,t)
                ||s.actorMovements().containsKey(t.actor())||s.actorMovements().containsKey(t.patient())
                ||s.humanPopulation().clinical().tasks().values().stream().anyMatch(other->!other.id().equals(t.id())&&other.patient().equals(t.patient())&&other.tend().isPresent()))
            throw new IllegalArgumentException("dressing lacks exact patient, skill, range, wound or bandage");
        ActorConsumableUse.require(s,t.actor(),t.home(),rules.bandage(),new ActorConsumableUse.Portion(tend.bandage().accountId(),tend.lot()));
    }
    private static FrontierWorldState prepareHealing(FrontierWorldState s,SubjectId subject,ClinicalHealing h,long tick){
        var actor=h.actor().execution().actorId();var clinical=s.humanPopulation().clinical();var wound=clinical.wounds().get(actor);
        ActorBodyAuthority.requireActuation(s,h.actor());
        if(!subject.equals(actor)||wound==null||ActorInventoryInteractionFences.pending(s,actor)||clinical.healing().containsKey(actor)||wound.observedHealth()!=h.before()
                ||h.before()!=s.actorLocations().get(actor).condition().health().raw()||h.harmRevision()!=wound.harmRevision()
                ||h.throughTick()!=tick||h.attempt()!=tick+1||h.amount()!=ClinicalAuthority.recovery(s,wound,tick)
                ||clinical.tasks().values().stream().anyMatch(t->t.patient().equals(actor)&&t.tend().isPresent()))throw new IllegalArgumentException("invalid exact healing preparation");
        return ClinicalAuthority.update(s,clinical.with(h));
    }
    private static FrontierWorldState recover(FrontierWorldState s,SubjectId subject,ClinicalEvent.Recovered e,long tick){
        var clinical=s.humanPopulation().clinical();var wound=clinical.wounds().get(e.actor());
        if(!subject.equals(e.actor())||e.throughTick()>tick||wound==null)throw new IllegalArgumentException("foreign or absent recovery subject");
        var update=FrontierWorldStateUpdate.begin();
        if(e.healing().isPresent()){
            var h=e.healing().orElseThrow();
            if(!e.healing().equals(Optional.ofNullable(clinical.healing().get(e.actor())))||h.amount()!=e.amount()||h.throughTick()!=e.throughTick())throw new IllegalArgumentException("foreign native healing receipt");
            clinical=clinical.withoutHealing(e.actor());
            // Native health always arrives through the common body observation. Never overwrite later damage.
            if(e.applied()&&ClinicalAuthority.alive(s,e.actor()))wound=wound.observe(s.actorLocations().get(e.actor()).condition().health(),tick);
        }else{
            if(clinical.healing().containsKey(e.actor())||!ClinicalAuthority.alive(s,e.actor())||wound.observedHealth()!=s.actorLocations().get(e.actor()).condition().health().raw()
                    ||e.amount()!=ClinicalAuthority.recovery(s,wound,e.throughTick())||e.amount()>0&&!ActorExecutionCoordinator.coldAvailable(s,e.actor())
                    ||!e.applied())throw new IllegalArgumentException("recovery conflicts with current vitality or native custody");
            var health=new FixedScalar(Math.addExact(wound.observedHealth(),e.amount()));
            if(e.amount()>0){var actor=s.actorLocations().get(e.actor());update.actorLocations(ImmutableLookupMap.withEntry(s.actorLocations(),e.actor(),
                    actor.withObserved(actor.body(),actor.condition().withHealth(health))));}
            wound=wound.observe(health,e.throughTick());
        }
        clinical=wound.observedHealth()==wound.maximumHealth()||!ClinicalAuthority.alive(s,e.actor())?clinical.withoutWound(e.actor()):clinical.with(wound);
        return s.withChanges(update.humanPopulation(s.humanPopulation().withClinical(clinical)));
    }
    private ClinicalTransitions(){}
}
