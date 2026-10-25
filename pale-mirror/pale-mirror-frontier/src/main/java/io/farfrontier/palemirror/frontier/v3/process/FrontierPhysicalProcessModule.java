package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;

/** Exact owner for durable physical observations, intents and surface state. */
final class FrontierPhysicalProcessModule implements FrontierWorldProcessModule {
    @Override public List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> retiredSchedules(
            FrontierWorldState previous, FrontierWorldState next, FrontierEvent event,
            java.util.function.Supplier<List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction>> pending) {
        return ExactItemDepartureReactions.retiredSchedules(previous, next, pending);
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof PhysicalInventoryTransfersObserved batch)
            return PhysicalInventoryTransfersProcess.plan(state, batch, command);
        if (command.payload() instanceof ContainerPlayerEditObserved edit)
            return ContainerPlayerEditProcess.plan(state, edit, command);
        if (command.payload() instanceof GroundStockExit destroyed) {
            try { destroyed.apply(state, destroyed.economicOwner()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            var events = new java.util.ArrayList<ProposedEvent>();
            events.add(new ProposedEvent(destroyed.economicOwner(), destroyed));
            for (var activity : SourceStockClaimLoss.released(state, destroyed.forfeitedClaims())) {
                activity.cancelledProgress().ifPresent(id -> events.add(new ProposedEvent(activity.actor(),
                        new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Cancelled(id))));
                events.add(ResidentActivityProcess.wakeAfterActivity(activity.actor(), command.submittedAt().ticks()));
            }
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof ResourceStorageAdopted adopted) {
            try { adopted.apply(state, adopted.declaration().container().ownerId()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(adopted.declaration().container().ownerId(), adopted)));
        }
        if (command.payload() instanceof ResourceStorageDeclared declared) {
            try { declared.apply(state, declared.container().ownerId()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(declared.container().ownerId(), declared)));
        }
        if (command.payload() instanceof ResourceStoragePolicyChanged changed) {
            try { changed.apply(state, changed.expected().endpoint().settlementId()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(changed.expected().endpoint().settlementId(), changed)));
        }
        if (command.payload() instanceof PhysicalIntentTransition || command.payload() instanceof PhysicalIntentPrepared) {
            if (command.payload() instanceof PhysicalIntentPrepared prepared) {
                return FrontierWorldProcessCatalog.physicalLifecycles().planPrepared(state, command, prepared);
            }
            PhysicalIntentTransition transition = (PhysicalIntentTransition) command.payload();
            PhysicalIntent intent = state.physicalIntents().get(transition.intentId());
            if (intent == null) return FrontierWorldCommandPlanner.rejected("physical intent is unknown");
            return FrontierWorldProcessCatalog.physicalLifecycles().planTransition(state, command, intent, transition);
        }
        if (command.payload() instanceof StructureDamaged damage) {
            try { state.recordStructureDamage(damage); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(
                    FrontierWorldStateSupport.structureSettlement(state.bootstrap(), damage.structureId()), damage)));
        }
        if (command.payload() instanceof PhysicalDeltaObserved observed) {
            return FrontierWorldPhysicalObservationProcess.plan(state, observed, command.submittedAt().ticks());
        }
        if (command.payload() instanceof PhysicalDeltasObserved observed) {
            return FrontierWorldPhysicalObservationProcess.plan(state, observed, command.submittedAt().ticks());
        }
        if (command.payload() instanceof ResourceDeposited deposited) {
            return FrontierWorldPhysicalObservationProcess.planResourceDeposit(state, deposited);
        }
        if (command.payload() instanceof FungibleStackLayoutObserved observed) {
            return FrontierWorldPhysicalObservationProcess.planFungibleLayout(state, observed);
        }
        if (command.payload() instanceof FungibleResourceHandoffObserved observed) {
            return FrontierWorldPhysicalObservationProcess.planFungibleHandoff(state, observed, command.submittedAt().ticks());
        }
        if (command.payload() instanceof FungibleStockDepartureObserved observed) {
            return FrontierWorldPhysicalObservationProcess.planFungibleStockDeparture(state, observed,
                    command.submittedAt().ticks());
        }
        if (command.payload() instanceof FungibleStockContributionObserved observed) {
            return FrontierWorldPhysicalObservationProcess.planFungibleStockContribution(state, observed,
                    command.submittedAt().ticks());
        }
        if (command.payload() instanceof FungibleStackBindingsReleased released) {
            return FrontierWorldPhysicalObservationProcess.planFungibleBindingRelease(state, released, command.submittedAt().ticks());
        }
        if (command.payload() instanceof ExactItemCustodyChanged changed) {
            ExactItemStack item = state.inventory().items().get(changed.itemId());
            if (item == null || !item.custody().equals(changed.from())) {
                return FrontierWorldCommandPlanner.rejected("observed item source differs from canonical custody");
            }
            ProposedEvent observation = new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, changed), changed);
            try { return new CommandPlan.Accepted(ExactItemDepartureComposition.plan(state, command, changed.itemId(), observation)); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ExactItemDestroyed destroyed) {
            ExactItemStack item = state.inventory().items().get(destroyed.itemId());
            if (item == null || !item.custody().equals(destroyed.source())) {
                return FrontierWorldCommandPlanner.rejected("destroyed item source differs from canonical custody");
            }
            ProposedEvent observation = new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, destroyed), destroyed);
            try { return new CommandPlan.Accepted(ExactItemDepartureComposition.plan(state, command, destroyed.itemId(), observation)); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof InventoryConflictObserved observed) {
            InventoryConflict conflict = observed.conflict();
            ContainerRecord container = state.inventory().containers().get(conflict.containerId());
            if (container == null || conflict.slot() >= container.slotCount()
                    || (!state.inventory().items().containsKey(conflict.subjectId())
                    && !state.inventory().containers().containsKey(conflict.subjectId()))) {
                return FrontierWorldCommandPlanner.rejected("inventory conflict references an unknown exact surface");
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(container.ownerId(), observed)));
        }
        if (command.payload() instanceof ContainerSurfaceTransition transition) {
            return ContainerSurfaceProcess.plan(state, transition);
        }
        return FrontierWorldCommandPlanner.rejected("physical process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case GroundStockExit destroyed -> destroyed.apply(state, event.subject());
            case ResourceStorageDeclared declared -> declared.apply(state, event.subject());
            case ResourceStorageAdopted adopted -> adopted.apply(state, event.subject());
            case ResourceStoragePolicyChanged changed -> changed.apply(state, event.subject());
            case PhysicalIntentPrepared prepared -> FrontierWorldProcessCatalog.physicalLifecycles()
                    .reducePrepared(state, event.subject(), prepared.intent());
            case PhysicalIntentTransition transition -> reduceTransitionByOwner(state, event.subject(), transition);
            case StructureDamaged damaged -> reduceStructureDamaged(state, event.subject(), damaged);
            case PhysicalDeltaObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case PhysicalDeltasObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case ResourceDeposited deposited -> FrontierWorldPhysicalObservationProcess.reduceResourceDeposit(state, event.subject(), deposited);
            case ContainerPlayerEditObserved observed -> ContainerPlayerEditProcess.reduce(state, event.subject(), observed, event.instant().ticks());
            case FungibleStackLayoutObserved observed -> FrontierWorldPhysicalObservationProcess.reduceFungibleLayout(state, event.subject(), observed);
            case FungibleResourceHandoffObserved observed -> FrontierWorldPhysicalObservationProcess.reduceFungibleHandoff(state, event.subject(), observed);
            case FungibleStockDepartureObserved observed -> FrontierWorldPhysicalObservationProcess.reduceFungibleStockDeparture(state, event.subject(), observed);
            case FungibleStockContributionObserved observed -> FrontierWorldPhysicalObservationProcess.reduceFungibleStockContribution(state, event.subject(), observed);
            case FungibleStackBindingsReleased released -> FrontierWorldPhysicalObservationProcess.reduceFungibleBindingRelease(state, event.subject(), released);
            case ExactItemCustodyChanged changed -> reduceCustodyChanged(state, event.subject(), changed, event.instant().ticks());
            case ExactItemDestroyed destroyed -> reduceDestroyed(state, event.subject(), destroyed, event.instant().ticks());
            case InventoryConflictObserved observed -> reduceInventoryConflict(state, event.subject(), observed);
            case ContainerSurfaceTransition transition -> ContainerSurfaceProcess.reduce(state, event.subject(), transition);
            default -> throw new IllegalArgumentException("physical process does not own event: " + event.payload().type());
        };
    }

    private static FrontierWorldState reduceTransitionByOwner(FrontierWorldState state, SubjectId subject, PhysicalIntentTransition transition) {
        PhysicalIntent intent = state.physicalIntents().get(transition.intentId());
        if (intent == null) throw new IllegalArgumentException("physical intent transition has no prepared intent");
        return FrontierWorldProcessCatalog.physicalLifecycles().reduceTransition(state, subject, intent, transition);
    }

    private static FrontierWorldState reduceStructureDamaged(FrontierWorldState state, SubjectId subject, StructureDamaged damage) {
        if (!subject.equals(FrontierWorldStateSupport.structureSettlement(state.bootstrap(), damage.structureId()))) {
            throw new IllegalArgumentException("structure damage lacks its owning settlement");
        }
        return state.recordStructureDamage(damage);
    }

    static FrontierWorldState reduceCustodyChanged(FrontierWorldState state, SubjectId subject, ExactItemCustodyChanged changed, long tick) {
        if (!subject.equals(FrontierWorldStateSupport.itemOwner(state, changed))) throw new IllegalArgumentException("item custody observation lacks its canonical owner");
        return ExactItemDepartureComposition.observe(state, changed.itemId(), changed.from(),
                state.inventory().moveObservedItem(changed.itemId(), changed.from(), changed.to()), tick);
    }

    private static FrontierWorldState reduceDestroyed(FrontierWorldState state, SubjectId subject, ExactItemDestroyed destroyed, long tick) {
        ExactItemStack item = state.inventory().items().get(destroyed.itemId());
        if (item == null || !item.custody().equals(destroyed.source()) || !subject.equals(FrontierWorldStateSupport.itemOwner(state, destroyed))) {
            throw new IllegalArgumentException("item destruction lacks its canonical owner");
        }
        return ExactItemDepartureComposition.observe(state, destroyed.itemId(), destroyed.source(),
                state.inventory().destroyObservedItem(destroyed.itemId(), destroyed.source()), tick);
    }

    private static FrontierWorldState reduceInventoryConflict(FrontierWorldState state, SubjectId subject, InventoryConflictObserved observed) {
        InventoryConflict conflict = observed.conflict();
        ContainerRecord container = state.inventory().containers().get(conflict.containerId());
        if (container == null || !subject.equals(container.ownerId())) throw new IllegalArgumentException("inventory conflict lacks its container owner");
        return state.withInventory(state.inventory().recordConflict(conflict));
    }
}
