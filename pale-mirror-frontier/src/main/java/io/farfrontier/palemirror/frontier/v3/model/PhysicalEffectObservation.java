package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

/** Immutable postcondition evidence for one durable physical intent. */
public sealed interface PhysicalEffectObservation permits CargoHandoffObservation, CargoLoadObservation, DecontaminationObservation, ExactItemConsumedObservation,
        ExplosionObservation, ProductionTransformationObservation, ResourceSiteHarvestObservation, ResourceSitePreparationObservation,
        RouteConstructionMaterialLoadObservation, RouteConstructionObservation, SceneStrikeObservation, StructuralRepairObservation,
        HiveNutrientDepartureObservation, HiveNutrientArrivalObservation, EquipmentIssueObservation, EquipmentReturnObservation,
        RouteMaintenanceObservation, RouteMaintenanceMaterialLoadObservation {
    PhysicalObservationId id();
    PhysicalIntentId intentId();
}
