package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Set;

/** Free portable inventory effects; no physiology, mission stage or movement policy. */
final class UnitInventoryProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.unit_inventory_disposition_observed", "frontier.unit_inventory_bound_observed");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor(
            "unit-inventory", TYPES, Set.of(), TYPES, TYPES, TYPES);
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof UnitInventoryBoundObserved receipt) {
            try { UnitInventoryBodyCustody.bind(state, receipt.body().actorId(), receipt); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(receipt.body().actorId(), receipt)));
        }
        if (!(command.payload() instanceof UnitInventoryDispositionObserved receipt))
            return FrontierWorldCommandPlanner.rejected("inventory rejects undeclared effect");
        try { UnitInventoryDisposition.apply(state, receipt.body().actorId(), receipt); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(receipt.body().actorId(), receipt)));
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof UnitInventoryBoundObserved receipt)
            return UnitInventoryBodyCustody.bind(state, event.subject(), receipt);
        if (!(event.payload() instanceof UnitInventoryDispositionObserved receipt))
            throw new IllegalArgumentException("inventory rejects undeclared event");
        return UnitInventoryDisposition.apply(state, event.subject(), receipt);
    }
}
