package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import java.util.*;

/** Shared local furniture interaction; the registered activity supplies purpose and duration. */
final class SeatProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of(SeatEvent.Reserved.TYPE, SeatEvent.Prepared.TYPE, SeatEvent.Checkpointed.TYPE, SeatEvent.ApplicationCheckpointed.TYPE, SeatEvent.Changed.TYPE, SeatEvent.Released.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("seat-contact",
            Set.of(SeatEvent.Prepared.TYPE, SeatEvent.Checkpointed.TYPE, SeatEvent.ApplicationCheckpointed.TYPE, SeatEvent.Changed.TYPE), Set.of(), TYPES, TYPES, TYPES);
    public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        try { SeatContacts.apply(state, ((SeatEvent) command.payload()).subject(), (SeatEvent) command.payload(), command.submittedAt().ticks()); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(((SeatEvent) command.payload()).subject(), command.payload())));
    }
    public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return SeatContacts.apply(state, event.subject(), (SeatEvent) event.payload(), event.instant().ticks());
    }
}
