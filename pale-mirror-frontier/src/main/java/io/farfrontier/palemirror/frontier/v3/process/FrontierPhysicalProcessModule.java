package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
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
            return FrontierPhysicalIntentCommandProcess.plan(state, command);
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
            case PhysicalIntentPrepared prepared -> reducePrepared(state, event.subject(), prepared);
            case PhysicalIntentTransition transition -> reduceTransition(state, event.subject(), transition);
            case StructureDamaged damaged -> reduceStructureDamaged(state, event.subject(), damaged);
            case PhysicalDeltaObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case PhysicalDeltasObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case ResourceDeposited deposited -> FrontierWorldPhysicalObservationProcess.reduceResourceDeposit(state, event.subject(), deposited);
            case ExactItemCustodyChanged changed -> reduceCustodyChanged(state, event.subject(), changed);
            case ExactItemDestroyed destroyed -> reduceDestroyed(state, event.subject(), destroyed);
            case CargoCarrierReleased released -> reduceCarrierReleased(state, event.subject(), released);
            case InventoryConflictObserved observed -> reduceInventoryConflict(state, event.subject(), observed);
            case ContainerSurfaceTransition transition -> ContainerSurfaceProcess.reduce(state, event.subject(), transition);
            default -> throw new IllegalArgumentException("physical process does not own event: " + event.payload().type());
        };
    }

    private static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntentPrepared prepared) {
        PhysicalIntent intent = prepared.intent();
        if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR) return StructuralRepairProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION) return RouteConstructionProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE || intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING) {
            return RouteMaintenanceProcess.reducePrepared(state, subject, intent);
        }
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) {
            if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction material pickup must be prepared by the route network");
            RouteConstructionStateSupport.validateMaterialLoadingIntent(state, intent);
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) return DecontaminationProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) return ResourceSiteProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) return ResourceSiteHarvestProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
            if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("production transformation must be prepared by its settlement");
            ProductionTransformationStateSupport.validateIntent(state, intent);
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.CARGO_LOADING) return CargoLoadingStateSupport.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive nutrient endpoint intent must be prepared by the hive");
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE) {
            EquipmentIssueStateSupport.validateIntent(state, intent);
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment issue must be prepared by its settlement");
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN) {
            EquipmentReturnStateSupport.validateIntent(state, intent);
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment return must be prepared by its settlement");
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) {
            if (state.hiveColony().growthJobs().containsKey(intent.causeSubjectId())) return HiveGrowthProcess.reducePrepared(state, subject, intent);
            if (state.humanPopulation().birthJobs().containsKey(intent.causeSubjectId())) return PopulationBirthProcess.reducePrepared(state, subject, intent);
            if (state.humanPopulation().medicalOperations().containsKey(intent.causeSubjectId())) {
                MedicalTreatmentProcess.operationForIntent(state, intent);
                if (!subject.equals(state.humanPopulation().medicalOperations().get(intent.causeSubjectId()).settlementId())) {
                    throw new IllegalArgumentException("medical treatment must be prepared by its settlement");
                }
                return state.preparePhysicalIntent(intent);
            }
            if (state.humanPopulation().provisions().containsKey(intent.causeSubjectId())) return SettlementProvisionProcess.reducePrepared(state, subject, intent);
            throw new IllegalArgumentException("exact consumption has no supported owning process");
        }
        if (intent.kind() == PhysicalIntentKind.SCENE_STRIKE) {
            SceneStrikeStateSupport.validateIntent(state, intent);
            if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike must be prepared by its exact scene owner");
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.EXPLOSION) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion intent must be prepared by the hive");
            ExplosionStateSupport.validateIntent(state, intent);
            return state.preparePhysicalIntent(intent);
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || operation.stage() != OperationStage.ARRIVED || !subject.equals(operation.settlementId())) {
            throw new IllegalArgumentException("physical intent must be prepared by an arrived route operation owner");
        }
        if (intent.kind() != PhysicalIntentKind.CARGO_HANDOFF || !intent.subjectIds().contains(operation.cargoId())
                || !intent.subjectIds().contains(operation.id())) throw new IllegalArgumentException("physical intent does not own arrived cargo hand-off");
        return state.preparePhysicalIntent(intent);
    }

    private static FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, PhysicalIntentTransition transition) {
        PhysicalIntent intent = state.physicalIntents().get(transition.intentId());
        if (intent == null) throw new IllegalArgumentException("physical intent transition has no prepared intent");
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) {
            if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction material pickup transition lacks route-network ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE || intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING) {
            if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route maintenance transition lacks route-network ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE) {
            SettlementServiceInputIssueStateSupport.validateIntent(state, intent);
            SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
            if (work == null || !subject.equals(work.settlementId())) {
                throw new IllegalArgumentException("service input issue transition lacks its retained settlement work owner");
            }
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR || intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION || intent.kind() == PhysicalIntentKind.DECONTAMINATION) {
            SubjectId owner = intent.kind() == PhysicalIntentKind.DECONTAMINATION ? DecontaminationProcess.owner(state, intent.causeSubjectId()).id()
                    : FrontierWorldStateSupport.semanticOwner(state.bootstrap(), state.hiveColony(), intent.causeSubjectId());
            if (!subject.equals(owner)) throw new IllegalArgumentException("structural repair transition lacks its owning settlement");
            if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) DecontaminationProcess.taskForIntent(state, intent, StrategicTaskStatus.ACTIVE);
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.EXPLOSION) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion transition lacks hive ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.SCENE_STRIKE) {
            if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike transition lacks exact scene ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION || intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) {
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site transition lacks site ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
            if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("production transformation transition lacks settlement ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.CARGO_LOADING) return CargoLoadingStateSupport.reduceTransition(state, subject, intent, transition);
        if (intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive nutrient endpoint transition lacks hive ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE) {
            if (transition.status() == PhysicalIntentStatus.RUNNING) EquipmentIssueStateSupport.validateIntent(state, intent);
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment issue transition lacks settlement ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN) {
            if (transition.status() == PhysicalIntentStatus.RUNNING) EquipmentReturnStateSupport.validateIntent(state, intent);
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment return transition lacks settlement ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) {
            HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
            if (job != null) {
                if (!subject.equals(job.hiveId())) throw new IllegalArgumentException("hive growth consumption transition lacks hive ownership");
                return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
            }
            ResidentBirthJob birth = state.humanPopulation().birthJobs().get(intent.causeSubjectId());
            if (birth != null) {
                if (!subject.equals(birth.settlementId())) throw new IllegalArgumentException("resident birth consumption transition lacks settlement ownership");
                return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
            }
            MedicalEvacuationOperation medical = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
            if (medical != null) {
                MedicalTreatmentProcess.operationForIntent(state, intent);
                if (!subject.equals(medical.settlementId())) throw new IllegalArgumentException("medical treatment consumption transition lacks settlement ownership");
                return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
            }
            if (!state.humanPopulation().provisions().containsKey(intent.causeSubjectId()) || !subject.equals(intent.causeSubjectId())) {
                throw new IllegalArgumentException("settlement provision consumption transition lacks settlement ownership");
            }
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("physical intent transition subject does not own operation");
        return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
    }

    private static FrontierWorldState reduceStructureDamaged(FrontierWorldState state, SubjectId subject, StructureDamaged damage) {
        if (!subject.equals(FrontierWorldStateSupport.structureSettlement(state.bootstrap(), damage.structureId()))) {
            throw new IllegalArgumentException("structure damage lacks its owning settlement");
        }
        return state.recordStructureDamage(damage);
    }

    private static FrontierWorldState reduceCustodyChanged(FrontierWorldState state, SubjectId subject, ExactItemCustodyChanged changed) {
        if (!subject.equals(FrontierWorldStateSupport.itemOwner(state, changed))) throw new IllegalArgumentException("item custody observation lacks its canonical owner");
        return state.withInventory(state.inventory().moveObservedItem(changed.itemId(), changed.from(), changed.to()));
    }

    private static FrontierWorldState reduceDestroyed(FrontierWorldState state, SubjectId subject, ExactItemDestroyed destroyed) {
        ExactItemStack item = state.inventory().items().get(destroyed.itemId());
        if (item == null || !item.custody().equals(destroyed.source()) || !subject.equals(FrontierWorldStateSupport.itemOwner(state, destroyed))) {
            throw new IllegalArgumentException("item destruction lacks its canonical owner");
        }
        return state.withInventory(state.inventory().destroyObservedItem(destroyed.itemId(), destroyed.source()));
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
