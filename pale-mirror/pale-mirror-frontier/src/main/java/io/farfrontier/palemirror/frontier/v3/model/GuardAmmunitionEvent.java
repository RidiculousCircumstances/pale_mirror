package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.RangedSupply;
import java.util.*;
/** Guard owner supplies its exact appointment; custody remains in the common ledger. */
public sealed interface GuardAmmunitionEvent extends FrontierPayload permits GuardAmmunitionEvent.Selected,GuardAmmunitionEvent.Prepared,GuardAmmunitionEvent.Loaded,GuardAmmunitionEvent.Cancelled,GuardAmmunitionEvent.Aborted {
    SubjectId duty();
    long revision();
    record Selected(SubjectId duty,long revision,RangedSupply supply) implements GuardAmmunitionEvent {
        public static final String TYPE="frontier.guard_ammunition_selected";public String type(){return TYPE;}
        public Selected{Objects.requireNonNull(duty);Objects.requireNonNull(supply);if(revision<1)throw new IllegalArgumentException("invalid supply owner revision");}
    }
    record Prepared(SubjectId duty,long revision,ActorItemTransferStep step) implements GuardAmmunitionEvent {
        public static final String TYPE="frontier.guard_ammunition_prepared";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
        public Prepared{Objects.requireNonNull(duty);Objects.requireNonNull(step);if(revision<1)throw new IllegalArgumentException("invalid supply owner revision");}
    }
    /** An absent preimage is a COLD transfer; native outcomes retain the exact prepared step. */
    record Loaded(SubjectId duty,long revision,Optional<ActorItemTransferStep> step,List<FungiblePhysicalObservation.Stack> source,List<FungiblePhysicalObservation.Stack> destination) implements GuardAmmunitionEvent {
        public static final String TYPE="frontier.guard_ammunition_loaded";public String type(){return TYPE;}
        public Loaded{Objects.requireNonNull(duty);Objects.requireNonNull(step);source=List.copyOf(source);destination=List.copyOf(destination);if(revision<1 ||
                step.isEmpty()&&(!source.isEmpty()||!destination.isEmpty()))throw new IllegalArgumentException("invalid supply outcome");}
    }
    /** Native protocol positively withdrew the exact unused effect; absence/timeout is never this evidence. */
    record Aborted(SubjectId duty,long revision,ActorItemTransferStep step) implements GuardAmmunitionEvent {
        public static final String TYPE="frontier.guard_ammunition_aborted";public String type(){return TYPE;}
        public Aborted{Objects.requireNonNull(duty);Objects.requireNonNull(step);if(revision<1)throw new IllegalArgumentException("invalid ammunition abort");}
    }
    record Cancelled(SubjectId duty,long revision) implements GuardAmmunitionEvent {
        public static final String TYPE="frontier.guard_ammunition_cancelled";public String type(){return TYPE;}
        public Cancelled{Objects.requireNonNull(duty);if(revision<1)throw new IllegalArgumentException("invalid supply cancellation");}
    }
}
