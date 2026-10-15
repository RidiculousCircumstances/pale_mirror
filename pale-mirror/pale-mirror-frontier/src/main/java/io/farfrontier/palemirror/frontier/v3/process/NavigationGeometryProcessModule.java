package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved;
import java.util.*;

/** Geometry observation owns knowledge only; it does not edit blocks, work or actor authority. */
final class NavigationGeometryProcessModule implements FrontierWorldProcessModule {
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("navigation-geometry",
            Set.of(NavigationGeometryObserved.TYPE), Set.of(), Set.of(NavigationGeometryObserved.TYPE),
            Set.of(NavigationGeometryObserved.TYPE), Set.of(NavigationGeometryObserved.TYPE));
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        var value = (NavigationGeometryObserved) command.payload();
        if (!command.actor().equals(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR))
            return FrontierWorldCommandPlanner.rejected("navigation geometry requires its physical observation producer");
        try { value.validated(state, NavigationGeometryObserved.OWNER); }
        catch (IllegalArgumentException rejected) { return FrontierWorldCommandPlanner.rejected(rejected.getMessage()); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(NavigationGeometryObserved.OWNER, value)));
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return ((NavigationGeometryObserved) event.payload()).apply(state, event.subject());
    }
}
