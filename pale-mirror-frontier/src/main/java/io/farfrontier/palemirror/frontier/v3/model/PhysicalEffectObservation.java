package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

/** Immutable postcondition evidence for one durable physical intent. */
public sealed interface PhysicalEffectObservation permits CargoHandoffObservation, DecontaminationObservation, ExactItemConsumedObservation,
        ExplosionObservation, ResourceSiteHarvestObservation, ResourceSitePreparationObservation, RouteConstructionObservation, SceneStrikeObservation, StructuralRepairObservation {
    PhysicalObservationId id();
    PhysicalIntentId intentId();
}
