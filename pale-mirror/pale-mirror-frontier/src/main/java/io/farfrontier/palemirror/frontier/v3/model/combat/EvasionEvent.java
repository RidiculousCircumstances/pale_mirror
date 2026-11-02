package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
public sealed interface EvasionEvent extends FrontierPayload {
    record Threatened(SubjectId scope,ActorBodyId victim,CombatTarget threat,SurfaceAnchor victimPosition,Optional<SurfaceAnchor> threatPosition) implements EvasionEvent {
        public static final String TYPE="frontier.evasion.threatened";
        public Threatened{Objects.requireNonNull(scope);Objects.requireNonNull(victim);Objects.requireNonNull(threat);Objects.requireNonNull(victimPosition);Objects.requireNonNull(threatPosition);}
        public String type(){return TYPE;}
    }
    record Seen(ActorBodyId observer,SubjectId episode,long expectedObservation,SurfaceAnchor threatPosition,boolean dangerous) implements EvasionEvent {
        public static final String TYPE="frontier.evasion.seen";
        public Seen{Objects.requireNonNull(observer);Objects.requireNonNull(episode);Objects.requireNonNull(threatPosition);if(expectedObservation<0)throw new IllegalArgumentException("negative sight basis");}
        public String type(){return TYPE;}
    }
    record Started(SubjectId actor,SubjectId episode,ActorExecutionId execution) implements EvasionEvent {
        public static final String TYPE="frontier.evasion.started";
        public Started{Objects.requireNonNull(actor);Objects.requireNonNull(episode);Objects.requireNonNull(execution);}
        public String type(){return TYPE;}
    }
    record Destination(ActorExecutionId execution,long expectedRevision,SurfaceAnchor station) implements EvasionEvent {
        public static final String TYPE="frontier.evasion.destination";
        public Destination{Objects.requireNonNull(execution);Objects.requireNonNull(station);if(expectedRevision<1)throw new IllegalArgumentException("invalid escape revision");}
        public String type(){return TYPE;}
    }
    record Ended(SubjectId actor,SubjectId episode,long expectedRevision) implements EvasionEvent {
        public static final String TYPE="frontier.evasion.ended";
        public Ended{Objects.requireNonNull(actor);Objects.requireNonNull(episode);if(expectedRevision<1)throw new IllegalArgumentException("invalid escape completion");}
        public String type(){return TYPE;}
    }
}
