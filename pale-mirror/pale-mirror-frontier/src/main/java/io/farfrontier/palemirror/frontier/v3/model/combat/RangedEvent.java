package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
public sealed interface RangedEvent extends FrontierPayload permits RangedEvent.Observed,RangedEvent.Prepared,RangedEvent.Completed {
    record Observed(RangedStance stance) implements RangedEvent {
        public static final String TYPE="frontier.ranged_stance_observed";public String type(){return TYPE;}
        public Observed{Objects.requireNonNull(stance);}
    }
    record Prepared(RangedShot shot) implements RangedEvent {
        public static final String TYPE="frontier.ranged_shot_prepared";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
        public Prepared{Objects.requireNonNull(shot);}
    }
    record Completed(SubjectId owner,long attemptOrdinal,boolean emitted,List<FungiblePhysicalObservation.Stack> remaining) implements RangedEvent {
        public static final String TYPE="frontier.ranged_shot_completed";public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
        public Completed{Objects.requireNonNull(owner);remaining=List.copyOf(remaining);if(attemptOrdinal<1 || !emitted&&!remaining.isEmpty())throw new IllegalArgumentException("invalid ranged outcome");}
    }
}
