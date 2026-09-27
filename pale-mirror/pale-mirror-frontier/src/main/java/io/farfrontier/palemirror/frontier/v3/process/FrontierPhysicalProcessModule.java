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
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
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
            return FrontierWorldPhysicalObservationProcess.planFungibleHandoff(state, observed);
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
            try { return new CommandPlan.Accepted(ProductionProcess.planMaterializedInputDeparture(state, changed.itemId(), observation)); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ExactItemDestroyed destroyed) {
            ExactItemStack item = state.inventory().items().get(destroyed.itemId());
            if (item == null || !item.custody().equals(destroyed.source())) {
                return FrontierWorldCommandPlanner.rejected("destroyed item source differs from canonical custody");
            }
            ProposedEvent observation = new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, destroyed), destroyed);
            try { return new CommandPlan.Accepted(ProductionProcess.planMaterializedInputDeparture(state, destroyed.itemId(), observation)); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof CargoCarrierReleased released) {
            SceneLease lease = state.sceneLeases().get(released.leaseId());
            if (lease == null || !FrontierSceneBehaviors.isLogistics(lease)
                    || !FrontierSceneBehaviors.logistics(lease).cargoId().equals(released.cargoId())
                    || lease.status() != SceneLeaseStatus.HOT) {
                return FrontierWorldCommandPlanner.rejected("cargo carrier release lacks one HOT matching scene lease");
            }
            if (!CargoCarrierIdentity.id(lease).equals(released.carrierId())) {
                return FrontierWorldCommandPlanner.rejected("cargo carrier identity is not canonical for its scene");
            }
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
            if (operation == null) return FrontierWorldCommandPlanner.rejected("cargo carrier release has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released)));
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
            case PhysicalIntentPrepared prepared -> FrontierWorldProcessCatalog.physicalLifecycles()
                    .reducePrepared(state, event.subject(), prepared.intent());
            case PhysicalIntentTransition transition -> reduceTransitionByOwner(state, event.subject(), transition);
            case StructureDamaged damaged -> reduceStructureDamaged(state, event.subject(), damaged);
            case PhysicalDeltaObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case PhysicalDeltasObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case ResourceDeposited deposited -> FrontierWorldPhysicalObservationProcess.reduceResourceDeposit(state, event.subject(), deposited);
            case FungibleStackLayoutObserved observed -> FrontierWorldPhysicalObservationProcess.reduceFungibleLayout(state, event.subject(), observed);
            case FungibleResourceHandoffObserved observed -> FrontierWorldPhysicalObservationProcess.reduceFungibleHandoff(state, event.subject(), observed);
            case FungibleStackBindingsReleased released -> FrontierWorldPhysicalObservationProcess.reduceFungibleBindingRelease(state, event.subject(), released);
            case ExactItemCustodyChanged changed -> reduceCustodyChanged(state, event.subject(), changed);
            case ExactItemDestroyed destroyed -> reduceDestroyed(state, event.subject(), destroyed);
            case CargoCarrierReleased released -> reduceCarrierReleased(state, event.subject(), released);
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

    private static FrontierWorldState reduceCustodyChanged(FrontierWorldState state, SubjectId subject, ExactItemCustodyChanged changed) {
        if (!subject.equals(FrontierWorldStateSupport.itemOwner(state, changed))) throw new IllegalArgumentException("item custody observation lacks its canonical owner");
        return observedExactBakeryDeparture(state, changed.itemId(), changed.from(),
                state.inventory().moveObservedItem(changed.itemId(), changed.from(), changed.to()));
    }

    private static FrontierWorldState reduceDestroyed(FrontierWorldState state, SubjectId subject, ExactItemDestroyed destroyed) {
        ExactItemStack item = state.inventory().items().get(destroyed.itemId());
        if (item == null || !item.custody().equals(destroyed.source()) || !subject.equals(FrontierWorldStateSupport.itemOwner(state, destroyed))) {
            throw new IllegalArgumentException("item destruction lacks its canonical owner");
        }
        return observedExactBakeryDeparture(state, destroyed.itemId(), destroyed.source(),
                state.inventory().destroyObservedItem(destroyed.itemId(), destroyed.source()));
    }

    private static FrontierWorldState observedExactBakeryDeparture(FrontierWorldState state, SubjectId itemId,
                                                                     InventoryCustody source, ExactInventory inventory) {
        ProductionJob job = state.productionJobs().values().stream()
                .filter(value -> value.consumedItemId().equals(itemId) && value.bakeryWork().isPresent()
                        && value.inputHold() instanceof ProductionInputHold.Materialized
                        && value.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DEPOT_PICKUP)
                .findFirst().orElse(null);
        if (job == null) return state.withInventory(inventory);
        SubjectId depot = FrontierWorldState.depotId(job.settlementId());
        ExactItemStack current = inventory.items().get(itemId);
        if (current != null && current.custody() instanceof InventoryCustody.ContainerSlot slot
                && slot.containerId().equals(depot)) return state.withInventory(inventory);
        BakeryWorkBlock.Reason reason = job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent()
                ? BakeryWorkBlock.Reason.AMBIGUOUS_EFFECT : BakeryWorkBlock.Reason.SOURCE_CHANGED;
        BakeryWorkBlock block = new BakeryWorkBlock(reason, depot,
                source instanceof InventoryCustody.ContainerSlot slot ? slot.slot() : -1,
                "minecraft:wheat", current == null ? 0 : current.count());
        var jobs = new java.util.LinkedHashMap<>(state.productionJobs());
        jobs.put(job.id(), job.withBakeryWork(job.bakeryWork().orElseThrow().withBlock(java.util.Optional.of(block))));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).productionJobs(jobs));
    }

    private static FrontierWorldState reduceCarrierReleased(FrontierWorldState state, SubjectId subject, CargoCarrierReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        RouteOperation operation = lease == null || !FrontierSceneBehaviors.isLogistics(lease)
                ? null : state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("cargo carrier release lacks its owning settlement");
        return state.releaseCargoCarrier(released);
    }

    private static FrontierWorldState reduceInventoryConflict(FrontierWorldState state, SubjectId subject, InventoryConflictObserved observed) {
        InventoryConflict conflict = observed.conflict();
        ContainerRecord container = state.inventory().containers().get(conflict.containerId());
        if (container == null || !subject.equals(container.ownerId())) throw new IllegalArgumentException("inventory conflict lacks its container owner");
        return state.withInventory(state.inventory().recordConflict(conflict));
    }
}
