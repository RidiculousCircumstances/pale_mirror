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
                         Map<SubjectId, RouteOperation> operations, Map<SubjectId, SupplyContract> contracts, Map<SceneLeaseId, SceneLease> sceneLeases,
                         Map<SubjectId, RouteConstruction> constructions, RouteTopology topology) {
        for (Map.Entry<PhysicalObservationId, PhysicalEffectObservation> entry : observations.entrySet()) {
            PhysicalEffectObservation observation = entry.getValue();
            if (!entry.getKey().equals(observation.id())) throw new IllegalArgumentException("physical observation map key must match observation identity");
            PhysicalIntent intent = intents.get(observation.intentId());
            if (intent == null || intent.status() != PhysicalIntentStatus.CONFIRMED
                    || !intent.postconditionObservationId().equals(java.util.Optional.of(observation.id()))) {
                throw new IllegalArgumentException("physical observation must be the confirmed intent receipt");
            }
            if (observation instanceof CargoHandoffObservation cargo) {
                FrontierCargoValidation.validateObservation(bootstrap, operations, contracts, inventory, intent, cargo);
            } else if (observation instanceof ExplosionObservation explosion) {
                ExplosionStateSupport.validateReceipt(intent, explosion);
            } else if (observation instanceof SceneStrikeObservation strike) {
                SceneStrikeStateSupport.validateObservation(operations, sceneLeases, intent, strike);
            } else if (observation instanceof DecontaminationObservation decontamination) {
                DecontaminationStateSupport.validateReceipt(bootstrap, infection, intent, decontamination);
            } else if (observation instanceof StructuralRepairObservation repair) {
                StructuralRepairStateSupport.validateReceipt(intent, repair);
            } else if (observation instanceof ExactItemConsumedObservation consumed) {
                ExactItemConsumptionStateSupport.validateReceipt(intent, consumed);
            } else if (observation instanceof RouteConstructionObservation construction) {
                RouteConstructionStateSupport.validateReceipt(bootstrap, topology, constructions, intent, construction);
            } else if (observation instanceof ResourceSiteHarvestObservation harvest) {
                ResourceSitePhysicalIntentStateSupport.validateHarvestReceipt(bootstrap, intent, harvest);
            } else if (observation instanceof ResourceSitePreparationObservation preparation) {
                ResourceSitePhysicalIntentStateSupport.validateReceipt(intent, preparation);
            } else if (observation instanceof ProductionTransformationObservation production) {
                ProductionTransformationStateSupport.validateReceipt(intent, production);
            } else throw new IllegalArgumentException("physical observation has an unknown effect kind");
        }
    }
}
