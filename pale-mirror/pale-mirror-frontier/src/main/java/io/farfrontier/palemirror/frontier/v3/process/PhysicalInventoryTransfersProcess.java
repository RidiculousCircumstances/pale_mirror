package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Composition only; title, claim forfeiture and exact custody keep their existing owners. */
final class PhysicalInventoryTransfersProcess {
    private PhysicalInventoryTransfersProcess() { }
    static CommandPlan plan(FrontierWorldState state, PhysicalInventoryTransfersObserved batch, FrontierCommand command) {
        var consequences = new ArrayList<ProposedEvent>();
        try { apply(state, batch, command, consequences); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        return new CommandPlan.Accepted(List.copyOf(consequences));
    }
    private static FrontierWorldState apply(FrontierWorldState state, PhysicalInventoryTransfersObserved batch,
            FrontierCommand command, List<ProposedEvent> consequences) {
        var next = state;
        for (var transfer : batch.resources()) {
            var plan = FrontierWorldPhysicalObservationProcess.planFungibleHandoff(next, transfer, command.submittedAt().ticks());
            if (!(plan instanceof CommandPlan.Accepted accepted)) throw new IllegalArgumentException("invalid physical handoff in batch: " + plan);
            consequences.addAll(accepted.events());
            next = FungibleResourceHandoffStateSupport.apply(next, transfer);
        }
        for (var transfer : batch.exactItems()) {
            var item = next.inventory().items().get(transfer.itemId());
            if (item == null || !item.custody().equals(transfer.from()))
                throw new IllegalArgumentException("observed exact transfer source differs from canonical custody");
            var observation = new ProposedEvent(FrontierWorldStateSupport.itemOwner(next, transfer), transfer);
            consequences.addAll(ExactItemDepartureComposition.plan(next, command, transfer.itemId(), observation));
            next = FrontierPhysicalProcessModule.reduceCustodyChanged(next, observation.subject(), transfer);
        }
        return next;
    }
}
