package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.rest.*;
import java.util.*;
/** Generic place reservation/interaction owner. It knows neither injury nor a medic role. */
final class RestPlaceProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES=Set.of(RestEvent.Availability.TYPE,RestEvent.Reserved.TYPE,RestEvent.Prepared.TYPE,RestEvent.Changed.TYPE,RestEvent.Released.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR=new DeterministicProcessDescriptor("rest-place",Set.of(RestEvent.Availability.TYPE,RestEvent.Prepared.TYPE,RestEvent.Changed.TYPE),Set.of(),TYPES,TYPES,TYPES);
    public CommandPlan planCommand(FrontierWorldState s,FrontierCommand c){
        try{RestPlaces.apply(s,((RestEvent)c.payload()).subject(),(RestEvent)c.payload(),c.submittedAt().ticks());}
        catch(IllegalArgumentException invalid){return FrontierWorldCommandPlanner.rejected(invalid.getMessage());}
        return new CommandPlan.Accepted(List.of(new ProposedEvent(((RestEvent)c.payload()).subject(),c.payload())));
    }
    public FrontierWorldState reduce(FrontierWorldState s,FrontierEvent e){return RestPlaces.apply(s,e.subject(),(RestEvent)e.payload(),e.instant().ticks());}
}
