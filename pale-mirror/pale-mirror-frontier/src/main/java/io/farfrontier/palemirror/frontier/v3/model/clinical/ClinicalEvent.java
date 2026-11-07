package io.farfrontier.palemirror.frontier.v3.model.clinical;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Exact care-owner observations; prepared physical effects retain their complete preimages. */
public sealed interface ClinicalEvent extends FrontierPayload {
    default SubjectId subject(){return switch(this){
        case Vitality e->e.actor();case Started e->e.task().id();case Destination e->e.task();case Ended e->e.task();
        case SourceSelected e->e.task();case SupplyPrepared e->e.task();case Supplied e->e.task();case SupplyAborted e->e.task();
        case TendPrepared e->e.task();case Tended e->e.task();case HealingPrepared e->e.healing().actor().execution().actorId();case Recovered e->e.actor();};}

    record Vitality(SubjectId actor,Optional<ClinicalWound> expected,long atTick) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_vitality";public String type(){return TYPE;}
        public Vitality{Objects.requireNonNull(actor);Objects.requireNonNull(expected);if(atTick<0)throw new IllegalArgumentException("negative clinical instant");}
    }
    record Started(ClinicalTask task) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_started";public String type(){return TYPE;}
        public Started{Objects.requireNonNull(task);}
    }
    record Destination(SubjectId task,long revision,ClinicalTask.Phase phase,SurfaceAnchor station) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_destination";public String type(){return TYPE;}
        public Destination{Objects.requireNonNull(task);Objects.requireNonNull(phase);Objects.requireNonNull(station);if(revision<1)throw new IllegalArgumentException("invalid clinical revision");}
    }
    record Ended(SubjectId task,long revision) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_ended";public String type(){return TYPE;}
    }
    record SourceSelected(SubjectId task,long revision,ActorSupplyVisit visit) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_source_selected";public String type(){return TYPE;}
        public SourceSelected{Objects.requireNonNull(task);Objects.requireNonNull(visit);}
    }
    record SupplyPrepared(SubjectId task,long revision,ActorItemTransferStep step) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_supply_prepared";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
    record Supplied(SubjectId task,long revision,Optional<ActorItemTransferStep> step,List<FungiblePhysicalObservation.Stack> source,List<FungiblePhysicalObservation.Stack> destination) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_supplied";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
        public Supplied{Objects.requireNonNull(step);source=List.copyOf(source);destination=List.copyOf(destination);if(step.isEmpty()&&(!source.isEmpty()||!destination.isEmpty()))throw new
            IllegalArgumentException("cold supply has no native outcome");}
    }
    record SupplyAborted(SubjectId task,long revision,Optional<ActorItemTransferStep> step) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_supply_aborted";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
    record TendPrepared(SubjectId task,long revision,ClinicalTend tend) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_tend_prepared";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
    record Tended(SubjectId task,long revision,Optional<ClinicalTend> tend,Optional<SubjectId> coldAccount,Optional<SubjectId> coldLot,
            boolean applied,List<FungiblePhysicalObservation.Stack> remaining) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_tended";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
        public Tended{Objects.requireNonNull(tend);Objects.requireNonNull(coldAccount);Objects.requireNonNull(coldLot);remaining=List.copyOf(remaining);
            if(tend.isPresent()==coldAccount.isPresent()||coldAccount.isPresent()!=coldLot.isPresent()||tend.isEmpty()&&(!applied||!remaining.isEmpty()))throw new
                IllegalArgumentException("treatment must declare exactly one physical or cold preimage");}
    }
    record HealingPrepared(ClinicalHealing healing) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_healing_prepared";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
    record Recovered(SubjectId actor,Optional<ClinicalHealing> healing,long throughTick,long amount,boolean applied) implements ClinicalEvent {
        public static final String TYPE="frontier.clinical_recovered";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
        public Recovered{Objects.requireNonNull(actor);Objects.requireNonNull(healing);if(throughTick<0||amount<0)throw new IllegalArgumentException("invalid recovery delta");}
    }
}
