package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.List;
import java.util.Set;

/** The one explicit v3 physical execution composition. */
final class FrontierV3PhysicalExecutors {
    private static final FrontierV3PhysicalExecutorRegistry REGISTRY = new FrontierV3PhysicalExecutorRegistry(List.of(
            executor("physical-observation", FrontierV3PhysicalExecutorRegistry.Stage.OBSERVATION, Set.of(), "physical-delta", FrontierV3PhysicalObservationExecutor::tick),
            executor("resource-site-explosion-observation", FrontierV3PhysicalExecutorRegistry.Stage.OBSERVATION, Set.of("physical-observation"), "resource-site-explosion", FrontierV3ResourceSiteExplosionExecutor::tick),
            executor("explosion-observation", FrontierV3PhysicalExecutorRegistry.Stage.OBSERVATION, Set.of("resource-site-explosion-observation"), "explosion-observation", FrontierV3ExplosionExecutor::tick),
            executor("cargo-carrier-impact-observation", FrontierV3PhysicalExecutorRegistry.Stage.OBSERVATION, Set.of("explosion-observation"), "cargo-carrier-impact", FrontierV3CargoCarrierImpactExecutor::tick),

            executor("graybox-projection", FrontierV3PhysicalExecutorRegistry.Stage.PROJECTION, Set.of("physical-observation"), "graybox-projection", FrontierV3GrayboxExecutor::tick),
            executor("resource-site-projection", FrontierV3PhysicalExecutorRegistry.Stage.PROJECTION, Set.of("graybox-projection"), "resource-site-projection", FrontierV3ResourceSiteExecutor::tick),
            executor("decontamination-projection", FrontierV3PhysicalExecutorRegistry.Stage.PROJECTION, Set.of("resource-site-projection"), "decontamination-projection", FrontierV3DecontaminationExecutor::tick),
            executor("infection-overlay-projection", FrontierV3PhysicalExecutorRegistry.Stage.PROJECTION, Set.of("decontamination-projection"), "infection-overlay-projection", FrontierV3InfectionOverlayExecutor::tick),
            executor("object-boards", FrontierV3PhysicalExecutorRegistry.Stage.PROJECTION, Set.of("infection-overlay-projection"), "object-board-projection", FrontierV3ObjectBoardExecutor::tick),

            // Opening a claimed cocoon is a durable, non-replayable physical effect.  It must
            // not masquerade as desired-state projection merely because the object began as a
            // graybox cell.
            executor("hive-cocoon-mobilization", FrontierV3PhysicalExecutorRegistry.Stage.RELEASE, Set.of("object-boards"), "hive-cocoon-mobilization", FrontierV3HiveMobilizationExecutor::tick),

            executor("ambient-actors", FrontierV3PhysicalExecutorRegistry.Stage.ACTOR, Set.of("hive-cocoon-mobilization"), "ambient-actor-leases", FrontierV3AmbientActorExecutor::tick),

            executor("inventory-observation", FrontierV3PhysicalExecutorRegistry.Stage.CUSTODY, Set.of("ambient-actors"), "inventory-custody-observation", FrontierV3InventoryObservationExecutor::tick),
            executor("cargo-carrier-observation", FrontierV3PhysicalExecutorRegistry.Stage.CUSTODY, Set.of("inventory-observation"), "cargo-carrier-custody-observation", FrontierV3CargoCarrierObservationExecutor::tick),

            executor("cargo-loading", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("cargo-carrier-observation"), "cargo-loading-effect", FrontierV3CargoLoadingExecutor::tick),
            // A RUNNING production transformation may have changed one exact owned chest slot
            // before a restart persisted its canonical receipt.  Reconcile that durable
            // physical effect before the generic surface drift audit: otherwise the audit
            // mistakes its own known recovery window for player/world tampering.
            executor("production-transformation", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("cargo-loading"), "production-transformation-effect", FrontierV3ProductionTransformationExecutor::tick),
            executor("container-surfaces", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("production-transformation"), "container-surface-effect", FrontierV3ContainerSurfaceExecutor::tick),
            // Harvest changes both one PM-owned field and its exact chest slot, so it is an
            // effect after the container owner has established ACTIVE provenance, not projection.
            executor("resource-site-harvest", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("container-surfaces"), "resource-site-harvest-effect", FrontierV3ResourceSiteHarvestExecutor::tick),
            executor("hive-nutrient-endpoints", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("resource-site-harvest"), "hive-nutrient-endpoint-effect", FrontierV3HiveNutrientEndpointExecutor::tick),
            executor("defender-equipment-issue", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("hive-nutrient-endpoints"), "defender-equipment-issue-effect", FrontierV3EquipmentIssueExecutor::tick),
            executor("defender-equipment-return", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("defender-equipment-issue"), "defender-equipment-return-effect", FrontierV3EquipmentReturnExecutor::tick),
            executor("exact-item-consumption", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("production-transformation", "hive-nutrient-endpoints"), "exact-item-consumption-effect", FrontierV3ExactItemConsumptionExecutor::tick),
            executor("cargo-handoff", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("exact-item-consumption"), "cargo-handoff-effect", FrontierV3CargoHandoffExecutor::tick),
            executor("structural-repair", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("cargo-handoff"), "structural-repair-effect", FrontierV3StructuralRepairExecutor::tick),
            executor("route-construction", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("structural-repair"), "route-construction-effect", FrontierV3RouteConstructionExecutor::tick),
            executor("route-maintenance", FrontierV3PhysicalExecutorRegistry.Stage.EFFECT, Set.of("route-construction"), "route-maintenance-effect", FrontierV3RouteMaintenanceExecutor::tick),

            executor("scenes", FrontierV3PhysicalExecutorRegistry.Stage.SCENE, Set.of("route-maintenance", "object-boards"), "scene-leases", FrontierV3SceneExecutor::tick)
    ));

    private FrontierV3PhysicalExecutors() { }

    static FrontierV3PhysicalExecutorRegistry registry() { return REGISTRY; }

    private static FrontierV3PhysicalExecutorRegistry.Definition executor(String id, FrontierV3PhysicalExecutorRegistry.Stage stage,
                                                                            Set<String> dependencies, String writeKind,
                                                                            FrontierV3PhysicalExecutorRegistry.Tick tick) {
        return new FrontierV3PhysicalExecutorRegistry.Definition(id, stage, dependencies, Set.of(writeKind), 1, tick);
    }
}
