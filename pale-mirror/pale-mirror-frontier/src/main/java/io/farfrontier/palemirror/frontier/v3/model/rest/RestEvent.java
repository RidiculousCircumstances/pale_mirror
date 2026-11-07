package io.farfrontier.palemirror.frontier.v3.model.rest;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
/** Generic rest interaction with an exact reservation, body and semantic action. */
public sealed interface RestEvent extends FrontierPayload {
    default SubjectId subject(){return switch(this){case Availability e->e.place().id().buildingId();case Reserved e->e.use().execution().actorId();case Prepared e->e.actor();case Changed e->e.actor();case Released e->e.actor();};}
    record Availability(RestPlace place,boolean available) implements RestEvent {public static final String TYPE="frontier.rest_availability";public String type(){return TYPE;}}
    record Reserved(RestUse use) implements RestEvent {public static final String TYPE="frontier.rest_reserved";public String type(){return TYPE;}}
    record Prepared(SubjectId actor,long revision,ActorActuationId body,RestUse.Action action) implements RestEvent {
        public static final String TYPE="frontier.rest_prepared";public String type(){return TYPE;}public boolean requiresDurableBeforeEffect(){return true;}
    }
    record Changed(SubjectId actor,long revision,Optional<ActorActuationId> body,RestUse.Action action,boolean applied,BodyPosition observed) implements RestEvent {
        public static final String TYPE="frontier.rest_changed";public String type(){return TYPE;}public boolean requiresDurableBeforeEffect(){return true;}
        public Changed{Objects.requireNonNull(body);Objects.requireNonNull(action);Objects.requireNonNull(observed);}
    }
    record Released(SubjectId actor,long revision) implements RestEvent {public static final String TYPE="frontier.rest_released";public String type(){return TYPE;}}
}
