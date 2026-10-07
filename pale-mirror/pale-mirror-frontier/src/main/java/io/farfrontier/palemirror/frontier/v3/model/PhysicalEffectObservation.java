package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

/** Immutable postcondition evidence for one durable physical intent. */
public sealed interface PhysicalEffectObservation permits FungibleCargoHandoffObservation,
        FungibleResourceConsumedObservation, FungibleNutrientDepartureObservation, DecontaminationObservation, ExactItemConsumedObservation,
        ExplosionObservation, ProductionTransformationObservation, FungibleProductionObservation, ResourceSiteHarvestObservation, ResourceSiteHarvestDeliveryObservation, ResourceSiteHarvestDeferredObservation, ResourceSitePreparationObservation,
        RouteConstructionMaterialLoadObservation, RouteConstructionObservation, SceneStrikeObservation, StructuralRepairObservation,
        HiveNutrientDepartureObservation, HiveNutrientArrivalObservation, EquipmentIssueObservation, EquipmentReturnObservation,
        RouteMaintenanceObservation, RouteMaintenanceMaterialLoadObservation, SettlementServiceInputIssueObservation {
    PhysicalObservationId id();
    PhysicalIntentId intentId();
}
