package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;

/** Validates the closed physical-receipt union independently of aggregate world-state validation. */
final class FrontierWorldPhysicalObservationValidation {
    private FrontierWorldPhysicalObservationValidation() { }

    static void validate(FrontierBootstrap bootstrap, ExactInventory inventory, Map<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> infection,
                         Map<PhysicalIntentId, PhysicalIntent> intents, Map<PhysicalObservationId, PhysicalEffectObservation> observations,
                         Map<SceneLeaseId, SceneLease> sceneLeases,
                         Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                         RouteTopology topology, Map<SubjectId, SettlementServiceWork> serviceWorks) {
        for (Map.Entry<PhysicalObservationId, PhysicalEffectObservation> entry : observations.entrySet()) {
            PhysicalEffectObservation observation = entry.getValue();
            if (!entry.getKey().equals(observation.id())) throw new IllegalArgumentException("physical observation map key must match observation identity");
            PhysicalIntent intent = intents.get(observation.intentId());
            if (intent == null || intent.status() != PhysicalIntentStatus.CONFIRMED
                    || !intent.postconditionObservationId().equals(java.util.Optional.of(observation.id()))) {
                throw new IllegalArgumentException("physical observation must be the confirmed intent receipt");
            }
            if (observation instanceof FungibleCargoHandoffObservation cargo) {
                if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL
                        || !intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.CARGO).equals(cargo.cargoId())) {
                    throw new IllegalArgumentException("fungible nutrient arrival receipt has foreign cargo");
                }
            } else if (observation instanceof FungibleResourceConsumedObservation consumed) {
                if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXACT_ITEM_CONSUMPTION
                        || !intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ITEM).equals(consumed.lotId())) throw new IllegalArgumentException("fungible consumption receipt has foreign intent subjects");
                if (!consumed.remainingStacks().isEmpty()) throw new IllegalArgumentException("completed hive biomass receipt must consume its whole claimed layout");
            } else if (observation instanceof ExplosionObservation explosion) {
                ExplosionStateSupport.validateReceipt(intent, explosion);
            } else if (observation instanceof SceneStrikeObservation strike) {
                SceneStrikeStateSupport.validateObservation(sceneLeases, intent, strike);
            } else if (observation instanceof DecontaminationObservation decontamination) {
                if (serviceWorks.containsKey(intent.causeSubjectId())) {
                    SettlementServiceDecontaminationStateSupport.validateIntentForRecoveredReceipt(intent, decontamination, serviceWorks);
                } else DecontaminationStateSupport.validateReceipt(bootstrap, infection, intent, decontamination);
            } else if (observation instanceof StructuralRepairObservation repair) {
                StructuralRepairStateSupport.validateReceipt(intent, repair);
            } else if (observation instanceof ExactItemConsumedObservation consumed) {
                ExactItemConsumptionStateSupport.validateReceipt(intent, consumed);
            } else if (observation instanceof RouteConstructionObservation construction) {
                RouteConstructionStateSupport.validateReceipt(bootstrap, topology, constructions, intent, construction);
            } else if (observation instanceof RouteConstructionMaterialLoadObservation loading) {
                RouteConstructionStateSupport.validateMaterialLoadingReceiptForRecovery(inventory, constructions, intents, observations, intent, loading);
            } else if (observation instanceof RouteMaintenanceObservation maintenance) {
                RouteMaintenanceStateSupport.validateReceipt(bootstrap, topology, maintenances, intent, maintenance);
            } else if (observation instanceof RouteMaintenanceMaterialLoadObservation loading) {
                RouteMaintenanceStateSupport.validateMaterialLoadingReceiptForRecovery(inventory, maintenances, intents, observations, intent, loading);
            } else if (observation instanceof ResourceSiteHarvestObservation harvest) {
                ResourceSitePhysicalIntentStateSupport.validateHarvestReceipt(bootstrap, intent, harvest);
            } else if (observation instanceof ResourceSiteHarvestDeliveryObservation harvest) {
                ResourceSitePhysicalIntentStateSupport.validateHarvestDeliveryReceipt(bootstrap, intent, harvest);
            } else if (observation instanceof ResourceSiteHarvestDeferredObservation harvest) {
                ResourceSitePhysicalIntentStateSupport.validateDeferredHarvestReceipt(bootstrap, intent, harvest);
            } else if (observation instanceof ResourceSitePreparationObservation preparation) {
                ResourceSitePhysicalIntentStateSupport.validateReceipt(intent, preparation);
                ResourceSite initial = FrontierResourceSitePlan.compile(bootstrap).get(preparation.siteId());
                if (initial == null || preparation.preparedSoilSlots() != initial.soilSlots().size()
                        || preparation.preparedCropSlots() != initial.cropSlots().size())
                    throw new IllegalArgumentException("retained field preparation receipt has a foreign bootstrap layout");
            } else if (observation instanceof ProductionTransformationObservation production) {
                ProductionTransformationStateSupport.validateReceipt(intent, production);
            } else if (observation instanceof FungibleProductionObservation production) {
                FungibleProductionStateSupport.validateReceipt(intent, production);
            }  else if (observation instanceof HiveNutrientDepartureObservation departure) {
                if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE
                        || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.nutrientDeparture(departure.transferId(), departure.cargoId(), departure.itemId()))) {
                    throw new IllegalArgumentException("hive nutrient departure receipt has foreign exact subjects");
                }
            } else if (observation instanceof FungibleNutrientDepartureObservation departure) {
                if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE
                        || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.nutrientDeparture(departure.transferId(), departure.cargoId(), departure.lotId()))) {
                    throw new IllegalArgumentException("fungible nutrient departure receipt has foreign exact subjects");
                }
            } else if (observation instanceof HiveNutrientArrivalObservation arrival) {
                if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL
                        || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.nutrientArrival(arrival.transferId(), arrival.cargoId(), arrival.itemId()))) {
                    throw new IllegalArgumentException("hive nutrient arrival receipt has foreign exact subjects");
                }
            } else if (observation instanceof EquipmentIssueObservation issue) {
                EquipmentIssueStateSupport.validateReceiptForRecovery(inventory, intent, issue);
            } else if (observation instanceof EquipmentReturnObservation returned) {
                EquipmentReturnStateSupport.validateReceiptForRecovery(inventory, intent, returned);
            } else if (observation instanceof SettlementServiceInputIssueObservation issue) {
                SettlementServiceInputIssueStateSupport.validateReceiptForRecovery(intent, issue);
            } else throw new IllegalArgumentException("physical observation has an unknown effect kind");
        }
    }
}
