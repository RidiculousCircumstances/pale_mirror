package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

import java.util.List;

/** Routes trusted physical executor commands to the owner-specific durable process. */
public final class FrontierPhysicalIntentCommandProcess {
    private FrontierPhysicalIntentCommandProcess() { }

    static CommandPlan plan(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof PhysicalIntentTransition transition) return transition(state, command, transition);
        if (command.payload() instanceof PhysicalIntentPrepared prepared) return prepared(state, prepared);
        return rejected("physical executor command is not a physical intent");
    }

    private static CommandPlan transition(FrontierWorldState state, FrontierCommand command, PhysicalIntentTransition transition) {
        PhysicalIntent intent = state.physicalIntents().get(transition.intentId());
        if (intent == null) return rejected("physical intent is unknown");
        try {
            return switch (intent.kind()) {
                case STRUCTURAL_REPAIR -> new CommandPlan.Accepted(List.of(new ProposedEvent(
                        FrontierWorldStateSupport.semanticOwner(state.bootstrap(), state.hiveColony(), intent.causeSubjectId()), transition)));
                case ROUTE_CONSTRUCTION -> new CommandPlan.Accepted(RouteConstructionProcess.planTransition(state, intent, transition));
                case ROUTE_CONSTRUCTION_MATERIAL_LOADING -> new CommandPlan.Accepted(RouteConstructionProcess.planMaterialLoadingTransition(state, intent, transition));
                case DECONTAMINATION -> new CommandPlan.Accepted(DecontaminationProcess.planTransition(state, intent, transition));
                case RESOURCE_SITE_PREPARATION -> new CommandPlan.Accepted(
                        ResourceSiteProcess.planPreparationTransition(state, intent, transition, command.submittedAt().ticks()));
                case RESOURCE_SITE_HARVEST -> new CommandPlan.Accepted(
                        ResourceSiteHarvestProcess.planTransition(state, intent, transition, command.submittedAt().ticks()));
                case PRODUCTION_TRANSFORMATION -> productionTransition(state, intent, transition);
                case CARGO_LOADING -> new CommandPlan.Accepted(SupplyOperationProcess.planCargoLoadingTransition(
                        state, intent, transition, command.submittedAt().ticks()));
                case HIVE_NUTRIENT_DEPARTURE, HIVE_NUTRIENT_ARRIVAL -> new CommandPlan.Accepted(HiveNutrientTransferProcess.planTransition(
                        state, intent, transition, command.submittedAt().ticks()));
                case EQUIPMENT_ISSUE -> equipmentIssueTransition(state, intent, transition);
                case EQUIPMENT_RETURN -> equipmentReturnTransition(state, intent, transition);
                case CARGO_HANDOFF -> routeTransition(state, intent, transition, command.submittedAt().ticks());
                case EXPLOSION -> new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), transition)));
                case SCENE_STRIKE -> new CommandPlan.Accepted(List.of(new ProposedEvent(SceneStrikeStateSupport.owner(state, intent), transition)));
                case EXACT_ITEM_CONSUMPTION -> consumptionTransition(state, intent, transition, command);
                default -> routeTransition(state, intent, transition, command.submittedAt().ticks());
            };
        } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
    }

    private static CommandPlan routeTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null) return rejected("physical intent has no owning operation");
        if (intent.kind() == PhysicalIntentKind.CARGO_HANDOFF) return new CommandPlan.Accepted(SupplyOperationProcess.planTransition(state, intent, transition, now));
        return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), transition)));
    }

    /** A production transformation belongs to its settlement job, never to a route operation. */
    private static CommandPlan productionTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
        if (job == null) return rejected("production transformation has no active job");
        try {
            ProductionTransformationStateSupport.validateIntent(state, intent);
            return new CommandPlan.Accepted(List.of(new ProposedEvent(job.settlementId(), transition)));
        } catch (IllegalArgumentException invalid) {
            return rejected(invalid.getMessage());
        }
    }
    private static CommandPlan equipmentIssueTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING) EquipmentIssueStateSupport.validateIntent(state, intent);
        return new CommandPlan.Accepted(List.of(new ProposedEvent(intent.causeSubjectId(), transition)));
    }

    private static CommandPlan equipmentReturnTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING) EquipmentReturnStateSupport.validateIntent(state, intent);
        return new CommandPlan.Accepted(List.of(new ProposedEvent(intent.causeSubjectId(), transition)));
    }

    private static CommandPlan consumptionTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition,
                                                     FrontierCommand command) {
        if (state.hiveColony().growthJobs().containsKey(intent.causeSubjectId())) {
            return new CommandPlan.Accepted(HiveGrowthProcess.planTransition(state, intent, transition, command.submittedAt().ticks()));
        }
        if (state.humanPopulation().birthJobs().containsKey(intent.causeSubjectId())) {
            return new CommandPlan.Accepted(PopulationBirthProcess.planTransition(state, intent, transition, command.submittedAt().ticks()));
        }
        if (state.humanPopulation().medicalOperations().containsKey(intent.causeSubjectId())) {
            if ((transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING
                    || transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED)
                    && !FrontierMedicalTreatmentSceneSupport.permitsCurrentConsumptionIntent(state, intent)) {
                return rejected("medical treatment consumption requires its current HOT infirmary scene");
            }
            return new CommandPlan.Accepted(MedicalTreatmentProcess.planTransition(state, intent, transition, command.submittedAt().ticks()));
        }
        if (state.humanPopulation().provisions().containsKey(intent.causeSubjectId())) {
            return new CommandPlan.Accepted(SettlementProvisionProcess.planTransition(state, intent, transition, command.submittedAt().ticks()));
        }
        return rejected("exact consumption has no supported owning process");
    }

    private static CommandPlan prepared(FrontierWorldState state, PhysicalIntentPrepared prepared) {
        PhysicalIntent intent = prepared.intent();
        try {
            if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) {
                return new CommandPlan.Accepted(List.of(new ProposedEvent(intent.causeSubjectId(), prepared)));
            }
            if (intent.kind() == PhysicalIntentKind.EXPLOSION) {
                ExplosionStateSupport.validateIntent(state, intent);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), prepared)));
            }
            if (intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL) {
                boolean owns = state.bootstrap().hive().id().equals(intent.causeSubjectId())
                        && state.hiveColony().nutrientTransfers().values().stream().anyMatch(transfer -> intent.subjectIds().contains(transfer.id()));
                if (!owns) return rejected("hive nutrient endpoint has no retained transfer");
                return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), prepared)));
            }
            if (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE) {
                EquipmentIssueStateSupport.validateIntent(state, intent);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(intent.causeSubjectId(), prepared)));
            }
            if (intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN) {
                EquipmentReturnStateSupport.validateIntent(state, intent);
                return new CommandPlan.Accepted(List.of(new ProposedEvent(intent.causeSubjectId(), prepared)));
            }
            if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION) {
                RouteConstructionStateSupport.validateIntent(state, intent);
                if (!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(state, intent)) {
                    return rejected("route construction physical work requires its current HOT engineering scene");
                }
                return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, prepared)));
            }
            if (intent.kind() != PhysicalIntentKind.SCENE_STRIKE) return rejected("physical executor cannot prepare this intent kind");
            SceneStrikeStateSupport.validateIntent(state, intent);
            return new CommandPlan.Accepted(List.of(new ProposedEvent(SceneStrikeStateSupport.owner(state, intent), prepared)));
        } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
    }

    private static CommandPlan.Rejected rejected(String message) {
        return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, message));
    }
}
