package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Closed ownership catalog for the installed Frontier world processes.
 *
 * <p>This is intentionally an explicit finite composition rather than classpath discovery.
 * Descriptor ownership, executable owner and scheduler routing are all checked at startup, so
 * a newly declared durable payload cannot become a dead catalog entry.</p>
 */
public final class FrontierWorldProcessCatalog {
    @FunctionalInterface
    private interface ScheduledPlanner {
        List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean autonomousInterception);
        default List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                         boolean autonomousInterception,
                                         io.farfrontier.palemirror.frontier.v3.api.SimInstant currentInstant) {
            return plan(state, action, autonomousInterception);
        }
        default boolean held(FrontierWorldState state, ScheduledAction action) { return false; }
    }

    private static final Set<String> KERNEL = types(
            "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
            "frontier.kernel_quarantine_observed");
    private static final Set<String> PHYSICAL = types(
            "frontier.physical_delta_observed", "frontier.physical_deltas_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition",
            "frontier.structure_damaged", "frontier.resource_deposited", "frontier.fungible_stack_layout_observed",
            "frontier.fungible_resource_handoff_observed", "frontier.fungible_stock_departure_observed",
            "frontier.fungible_stock_contribution_observed", "frontier.fungible_stack_bindings_released",
            "frontier.exact_item_custody_changed",
            "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed", "frontier.container_surface_transition",
            "frontier.cargo_carrier_released");
    private static final Set<String> REPLICA_CUSTODY = types(
            "frontier.physical_replica_declared", "frontier.physical_replica_emitted", "frontier.physical_replica_observed", "frontier.physical_replica_conflict_observed", "frontier.reference_mutation_closed", "frontier.physical_custody_acquired",
            "frontier.projection_custody_prepared", "frontier.projection_custody_confirmed",
            "frontier.reference_projection_prepared",
            "frontier.projection_conflict_observed",
            "frontier.physical_custody_checkpointed", "frontier.physical_custody_unresolved", "frontier.physical_custody_released",
            "frontier.fenced_recovery_prepared", "frontier.fenced_recovery_running", "frontier.fenced_recovery_observed", "frontier.fenced_recovery_confirmed",
            "frontier.fenced_recovery_revoked_to_cold", "frontier.fenced_recovery_ambiguous", "frontier.fenced_recovery_abandoned",
            "frontier.cargo_cleanup_saved");
    private static final Set<String> AMBIENT = types(
            "frontier.ambient_body_confirmed",
            "frontier.ambient_actor_died", "frontier.ambient_actor_observed", "frontier.ambient_lease_prepared",
            "frontier.ambient_lease_released", "frontier.ambient_lease_transition", "frontier.ambient_lease_restart_absence_observed");
    private static final Set<String> LOGISTICS = types(
            "frontier.supply_contract_created", "frontier.supply_contract_abandoned", "frontier.cargo_loaded",
            "frontier.cargo_delivered", "frontier.operation_created", "frontier.operation_advanced",
            "frontier.operation_assembly_advanced", "frontier.operation_assembly_deferred", "frontier.operation_travel_started",
            "frontier.operation_travel_advanced", "frontier.operation_travel_segment_completed", "frontier.operation_cold_suspended",
            "frontier.operation_failed", "frontier.terminal_logistics_compacted", "frontier.scene_lease_prepared",
            "frontier.scene_lease_handoff", "frontier.scene_lease_transition", "frontier.scene_lease_released_v2",
            "frontier.scene_lease_recovery_unresolved", "frontier.scene_lease_recovery_revoked", "frontier.actor_died", "frontier.settlement_assault_scene_lease_prepared",
            "frontier.settlement_assault_scene_lease_handoff", "frontier.engineering_work_scene_lease_prepared",
            "frontier.engineering_work_scene_lease_handoff");
    private static final Set<String> POPULATION = types(
            "frontier.resident_born", "frontier.resident_migrated", "frontier.resident_migration_started",
            "frontier.resident_migration_advanced", "frontier.resident_transit_advanced", "frontier.resident_migration_blocked",
            "frontier.resident_migration_resumed", "frontier.resident_birth_started", "frontier.resident_birth_cancelled",
            "frontier.settlement_provision_started", "frontier.settlement_provision_started_v2",
            "frontier.settlement_provision_consumed", "frontier.settlement_provision_resolved",
            "frontier.resident_starvation_integrated", "frontier.resident_need_integrated", "frontier.resident_metabolism_changed",
            "frontier.resident_meal_started",
            "frontier.resident_meal_cold_step", "frontier.resident_meal_hot_arrived",
            "frontier.resident_meal_hot_effect_prepared", "frontier.resident_meal_hot_effect_observed",
            "frontier.resident_meal_hot_hand_materialized", "frontier.resident_meal_hot_hand_released",
            "frontier.resident_meal_hot_access_cleared", "frontier.resident_meal_hot_returned",
            "frontier.resident_health_transition", "frontier.settlement_quarantine_transition",
            "frontier.medical_treatment_started", "frontier.medical_treatment_transition",
            "frontier.medical_treatment_scene_lease_prepared", "frontier.medical_treatment_scene_lease_handoff");
    private static final Set<String> ACTOR_MOVEMENT = types(
            "frontier.actor_movement_cold_advanced", "frontier.actor_movement_hot_observed", "frontier.actor_movement_interrupted", "frontier.actor_movement_started");
    private static final Set<String> ECONOMY = types(
            "frontier.company_registered", "frontier.employment_contract_opened", "frontier.employment_contract_terminated",
            "frontier.market_demand_opened", "frontier.market_quote_published", "frontier.market_work_order_accepted",
            "frontier.market_work_order_cancelled", "frontier.market_relationship_incident_recorded", "frontier.market_demand_expired", "frontier.market_demand_cancelled",
            "frontier.production_started", "frontier.production_completed", "frontier.fungible_production_completed",
            "frontier.production_work_progressed", "frontier.production_work_traversal_advanced",
            "frontier.production_cold_work_advanced", "frontier.bakery_cold_step", "frontier.bakery_input_reallocated", "frontier.bakery_hot_goal_arrived", "frontier.bakery_hot_access_cleared",
            "frontier.bakery_hot_effect_prepared", "frontier.bakery_hot_effect_observed", "frontier.bakery_hot_work_tick",
            "frontier.bakery_hot_hand_release", "frontier.bakery_hot_hand_materialized", "frontier.bakery_hot_block_changed", "frontier.production_work_traversal_blocked",
            "frontier.production_work_scene_lease_prepared", "frontier.production_work_scene_lease_handoff", "frontier.production_work_scene_preparation_aborted", "frontier.production_work_scene_finalized",
            "frontier.production_blocked", "frontier.production_interrupted");
    private static final Set<String> RESOURCE_SITES = types(
            "frontier.resource_site_growth_advanced", "frontier.resource_site_preparation_started",
            "frontier.resource_site_prepared", "frontier.resource_site_harvest_started",
            "frontier.resource_site_harvest_crop_prepared",
            "frontier.resource_site_harvest_progressed",
            "frontier.resource_site_harvest_scene_reconciled",
            "frontier.resource_site_harvest_hand_projected",
            "frontier.resource_site_harvest_hand_release",
            "frontier.resource_site_harvest_cold_traversal_advanced", "frontier.resource_site_harvest_cold_goal_advanced", "frontier.resource_site_harvest_cold_goal_held", "frontier.resource_site_harvest_returned",
            "frontier.resource_site_harvest_segment_renewed", "frontier.resource_site_harvest_blocked_cell_skipped",
            "frontier.resource_site_harvest_target_retargeted", "frontier.resource_site_harvest_route_blocked",
            "frontier.resource_site_harvest_route_cleared", "frontier.resource_site_harvest_hot_traversal_advanced", "frontier.resource_site_harvest_hot_goal_arrived",
                    "frontier.resource_site_harvest_hot_transit_observed",
            "frontier.resource_site_harvest_batch_prepared", "frontier.resource_site_harvest_batch_delivered",
            "frontier.resource_site_harvest_scene_lease_prepared",
            "frontier.resource_site_harvest_scene_preparation_aborted",
            "frontier.resource_site_harvest_scene_lease_handoff",
            "frontier.resource_site_conflict_observed", "frontier.resource_field_cell_observed", "frontier.resource_field_work_access_observed",
            "frontier.resource_field_world_change_held", "frontier.resource_field_world_change_acknowledged",
            "frontier.resource_field_foreign_change_held", "frontier.resource_field_foreign_cell_observed",
            "frontier.resource_field_foreign_change_acknowledged",
            "frontier.resource_field_player_break_prepared");
    private static final Set<String> HIVE = types(
            "frontier.infection_changed", "frontier.hive_growth_started", "frontier.hive_growth_biomass_consumed",
            "frontier.hive_growth_completed", "frontier.hive_growth_blocked", "frontier.hive_nutrient_transfer_started",
            "frontier.hive_nutrient_transfer_advanced", "frontier.hive_nutrient_transfer_completed",
            "frontier.hive_nutrient_transfer_blocked", "frontier.hive_nutrient_transfer_endpoint_prepared",
            "frontier.hive_operation_observed", "frontier.hive_territory_observed", "frontier.hive_settlement_observed",
            "frontier.hive_doctrine_selected", "frontier.hot_scout_operation_observed", "frontier.scout_patrol_advanced", "frontier.scout_patrol_lease_recovered",
            "frontier.route_engagement_started", "frontier.route_engagement_attacker_advanced",
            "frontier.route_engagement_transition", "frontier.route_engagement_strike", "frontier.route_engagement_resolved", "frontier.route_engagement_command_authority_changed",
            "frontier.settlement_assault_started", "frontier.settlement_assault_attacker_advanced", "frontier.settlement_assault_formation_observed", "frontier.settlement_assault_march_issue_observed",
            "frontier.settlement_assault_transition", "frontier.settlement_assault_strike", "frontier.settlement_assault_resolved", "frontier.deferred_aftermath_prepared", "frontier.deferred_aftermath_resolved",
            "frontier.hive_mobilization_started", "frontier.hive_mobilization_release_started",
            "frontier.hive_mobilization_cocoon_released", "frontier.hive_mobilization_assembly_advanced", "frontier.hive_mobilization_return_advanced", "frontier.hive_mobilization_departed", "frontier.hive_mobilization_conflicted");
    private static final Set<String> INFRASTRUCTURE = types(
            "frontier.route_construction_started", "frontier.route_construction_material_loaded",
            "frontier.route_construction_assembly_started", "frontier.route_construction_assembly_advanced",
            "frontier.route_maintenance_started", "frontier.route_maintenance_material_loaded",
            "frontier.route_maintenance_assembly_started", "frontier.route_maintenance_assembly_advanced",
            "frontier.route_maintenance_closed",
            "frontier.route_topology_cutover", "frontier.route_patrol_started", "frontier.route_patrol_formation_advanced", "frontier.route_patrol_formation_observed",
            "frontier.route_patrol_obstruction_confirmed", "frontier.route_patrol_failed", "frontier.route_patrol_blocked",
            "frontier.route_patrol_scene_lease_prepared", "frontier.route_patrol_scene_lease_handoff");
    private static final Set<String> SETTLEMENT_SERVICE_WORK = types(
            "frontier.settlement_service_work_started", "frontier.settlement_service_work_scene_lease_prepared",
            "frontier.settlement_service_work_scene_lease_handoff", "frontier.settlement_service_work_traversal_advanced",
            "frontier.settlement_service_work_traversal_blocked", "frontier.settlement_service_work_progressed");
    private static final Set<String> STRATEGY = types(
            "frontier.settlement_infection_observed", "frontier.strategic_objective_selected",
            "frontier.strategic_task_planned", "frontier.strategic_task_transition");
    private static final Set<String> ALL_WORLD = union(PHYSICAL, REPLICA_CUSTODY, AMBIENT, LOGISTICS, POPULATION, ACTOR_MOVEMENT, ECONOMY, RESOURCE_SITES,
            HIVE, INFRASTRUCTURE, SETTLEMENT_SERVICE_WORK, STRATEGY);
    private static final Map<String, FrontierWorldProcessModule> MODULES = Map.ofEntries(
            Map.entry("physical-observation", new FrontierPhysicalProcessModule()),
            Map.entry("replica-custody", new FrontierReplicaCustodyProcessModule()),
            Map.entry("ambient-actors", new FrontierAmbientProcessModule()),
            Map.entry("logistics-scenes", new FrontierLogisticsProcessModule()),
            Map.entry("population", new FrontierPopulationProcessModule()),
            Map.entry("actor-movement", new FrontierActorMovementProcessModule()),
            Map.entry("economy", new FrontierEconomyProcessModule()),
            Map.entry("resource-sites", new FrontierResourceSiteProcessModule()),
            Map.entry("hive", new FrontierHiveProcessModule()),
            Map.entry("infrastructure", new FrontierInfrastructureProcessModule()),
            Map.entry("settlement-service-work", new FrontierSettlementServiceWorkProcessModule()),
            Map.entry("strategy", new FrontierStrategyProcessModule()));
    private static final PhysicalIntentLifecycleCapabilities PHYSICAL_LIFECYCLES =
            PhysicalIntentLifecycleCapabilities.compose(MODULES.values());
    private static final Map<String, ScheduledPlanner> SCHEDULED_PLANNERS = Map.ofEntries(
            Map.entry("frontier.hive.infection.task", (state, action, autonomous) -> HiveInfectionProcess.plan(state, action)),
            Map.entry("frontier.settlement.production.task.start", (state, action, autonomous) -> ProductionProcess.planStart(state, action)),
            Map.entry("frontier.settlement.production.task.complete", new ScheduledPlanner() {
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean autonomous) {
                    return ProductionProcess.planCompletion(state, action);
                }
                @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                    return ProductionProcess.completionHeld(state, action);
                }
            }),
            Map.entry("frontier.supply.task.start", (state, action, autonomous) -> SupplyOperationProcess.planStart(state, action)),
            Map.entry("frontier.supply.cargo.load", SupplyOperationProcess::planCargoLoad),
            Map.entry("frontier.operation.assembly", (state, action, autonomous) -> SupplyOperationProcess.planAssembly(state, action)),
            Map.entry("frontier.operation.progress", (state, action, autonomous) -> SupplyOperationProcess.planProgress(state, action)),
            Map.entry("frontier.terminal_logistics.retention", (state, action, autonomous) -> TerminalLogisticsProcess.plan(state, action)),
            Map.entry("frontier.hive.growth.task.start", (state, action, autonomous) -> HiveGrowthProcess.planStart(state, action)),
            Map.entry("frontier.hive.growth.task.complete", (state, action, autonomous) -> HiveGrowthProcess.planCompletion(state, action)),
            Map.entry("frontier.hive.nutrient.transfer.progress", (state, action, autonomous) -> HiveNutrientTransferProcess.plan(state, action)),
            Map.entry("frontier.hive.mobilization.assembly_progress", (state, action, autonomous) -> HiveMobilizationProcess.planAssemblyProgress(state, action)),
            Map.entry("frontier.hive.mobilization.return_progress", (state, action, autonomous) -> HiveMobilizationProcess.planReturnProgress(state, action)),
            Map.entry("frontier.population.birth.review", (state, action, autonomous) -> PopulationBirthProcess.planReview(state, action)),
            Map.entry("frontier.population.birth.complete", (state, action, autonomous) -> PopulationBirthProcess.planCompletion(state, action)),
            Map.entry("frontier.population.migration.review", (state, action, autonomous) -> PopulationMigrationProcess.planReview(state, action)),
            Map.entry("frontier.population.migration.progress", (state, action, autonomous) -> PopulationMigrationProcess.planProgress(state, action)),
            Map.entry(DefenderEquipmentProcess.REVIEW_ACTION, (state, action, autonomous) -> DefenderEquipmentProcess.plan(state, action)),
            Map.entry(DefenderEquipmentReturnProcess.REVIEW_ACTION, (state, action, autonomous) -> DefenderEquipmentReturnProcess.plan(state, action)),
            Map.entry(ResidentNeedProcess.REVIEW, (state, action, autonomous) -> ResidentNeedProcess.plan(state, action)),
            Map.entry(ResidentActivityProcess.REVIEW, new ScheduledPlanner() {
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                                          boolean autonomous) {
                    return ResidentActivityProcess.plan(state, action);
                }
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                                          boolean autonomous,
                                                          io.farfrontier.palemirror.frontier.v3.api.SimInstant currentInstant) {
                    return ResidentActivityProcess.plan(state, action, currentInstant.ticks());
                }
                @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                    return ResidentActivityProcess.held(state, action);
                }
            }),
            Map.entry(ResidentMealProcess.PROGRESS, new ScheduledPlanner() {
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                                          boolean autonomous) {
                    return ResidentMealProcess.planProgress(state, action);
                }
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                                          boolean autonomous,
                                                          io.farfrontier.palemirror.frontier.v3.api.SimInstant currentInstant) {
                    return ResidentMealProcess.planProgress(state, action, currentInstant.ticks());
                }
                @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                    return ResidentMealProcess.held(state, action);
                }
            }),
            Map.entry(ActorMovementProcess.PROGRESS, new ScheduledPlanner() {
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                                          boolean autonomous) {
                    return ActorMovementProcess.plan(state, action, action.dueAt().ticks());
                }
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                                          boolean autonomous,
                                                          io.farfrontier.palemirror.frontier.v3.api.SimInstant currentInstant) {
                    return ActorMovementProcess.plan(state, action, currentInstant.ticks());
                }
                @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                    return ActorMovementProcess.held(state, action);
                }
            }),
            Map.entry("frontier.company.foundation.review", (state, action, autonomous) -> CompanyFoundationProcess.plan(state, action)),
            Map.entry("frontier.market.clear", (state, action, autonomous) -> MarketClearingProcess.plan(state, action)),
            Map.entry("frontier.resource_site.growth", new ScheduledPlanner() {
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean autonomous) {
                    return ResourceSiteProcess.planGrowth(state, action);
                }
                @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                    return state.resourceSites().hasPendingWorldChange(action.subject());
                }
            }),
            Map.entry("frontier.resource_site.prepare", (state, action, autonomous) -> ResourceSiteProcess.planPreparation(state, action)),
            Map.entry("frontier.resource_site.harvest", new ScheduledPlanner() {
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean autonomous) {
                    return ResourceSiteHarvestProcess.plan(state, action);
                }
                @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                    var task = state.strategicPlans().tasks().get(action.subject());
                    return task != null && task.resourceSiteTarget().isPresent()
                            && state.resourceSites().hasPendingWorldChange(task.resourceSiteTarget().orElseThrow());
                }
            }),
            Map.entry("frontier.resource_site.harvest.cold_progress", new ScheduledPlanner() {
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean autonomous) {
                    return ResourceSiteHarvestProcess.planColdProgress(state, action);
                }
                @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                                          boolean autonomous, io.farfrontier.palemirror.frontier.v3.api.SimInstant currentInstant) {
                    return ResourceSiteHarvestProcess.planColdProgress(state, action, currentInstant.ticks());
                }
                @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                    return ResourceSiteHarvestProcess.coldProgressHeld(state, action);
                }
            }),
            Map.entry("frontier.objective.resource_harvest", (state, action, autonomous) -> StrategicObjectiveProcess.planResourceHarvestOpportunity(state, action)),
            Map.entry("frontier.structural_repair.scan", (state, action, autonomous) -> StructuralRepairProcess.plan(state, action)),
            Map.entry("frontier.route_construction.scan", (state, action, autonomous) -> RouteConstructionProcess.plan(state, action)),
            Map.entry("frontier.route_construction.start", (state, action, autonomous) -> RouteConstructionProcess.planStart(state, action)),
            Map.entry("frontier.route_construction.assembly_progress", (state, action, autonomous) -> RouteConstructionProcess.planAssemblyProgress(state, action)),
            Map.entry("frontier.route_construction.progress", (state, action, autonomous) -> RouteConstructionProcess.planProgress(state, action)),
            Map.entry("frontier.route_construction.return_progress", (state, action, autonomous) -> RouteConstructionProcess.planReturnProgress(state, action)),
            Map.entry("frontier.route_maintenance.scan", (state, action, autonomous) -> RouteMaintenanceProcess.plan(state, action)),
            Map.entry("frontier.route_maintenance.assembly_progress", (state, action, autonomous) -> RouteMaintenanceProcess.planAssemblyProgress(state, action)),
            Map.entry("frontier.route_maintenance.progress", (state, action, autonomous) -> RouteMaintenanceProcess.planProgress(state, action)),
            Map.entry("frontier.route_maintenance.return_progress", (state, action, autonomous) -> RouteMaintenanceProcess.planReturnProgress(state, action)),
            Map.entry("frontier.route_patrol.start", (state, action, autonomous) -> RoutePatrolProcess.planStart(state, action)),
            Map.entry("frontier.route_patrol.progress", (state, action, autonomous) -> RoutePatrolProcess.planProgress(state, action)),
            Map.entry("frontier.hive_route_engagement.start", (state, action, autonomous) -> HiveRouteEngagementProcess.planStart(state, action)),
            Map.entry("frontier.hive_route_engagement.progress", (state, action, autonomous) -> HiveRouteEngagementProcess.planProgress(state, action)),
            Map.entry("frontier.hive_route_engagement.readiness", (state, action, autonomous) -> HiveRouteEngagementProcess.planReadiness(state, action)),
            Map.entry("frontier.hive_route_engagement.combat", (state, action, autonomous) -> HiveRouteEngagementProcess.planCombat(state, action)),
            Map.entry("frontier.hive_route_engagement.control", (state, action, autonomous) -> HiveRouteEngagementProcess.planCommandControl(state, action)),
            Map.entry("frontier.hive.scout.patrol", (state, action, autonomous) -> HiveScoutPatrolProcess.plan(state, action)),
            Map.entry("frontier.decontamination.scan", (state, action, autonomous) -> SettlementServiceWorkProcess.planDecontamination(state, action)),
            Map.entry("frontier.objective.review", StrategicObjectiveProcess::plan),
            Map.entry("frontier.objective.stock_reconsider", (state, action, autonomous) -> StrategicObjectiveProcess.planStockReconsideration(state, action)),
            Map.entry("frontier.objective.reconsider", (state, action, autonomous) -> StrategicObjectiveProcess.planReconsideration(state, action)),
            Map.entry("frontier.objective.interrupt", (state, action, autonomous) -> StrategicObjectiveProcess.planOpportunity(state, action)),
            Map.entry("frontier.objective.assault", (state, action, autonomous) -> StrategicObjectiveProcess.planAssaultOpportunity(state, action)),
            Map.entry("frontier.settlement_assault.start", (state, action, autonomous) -> HiveSettlementAssaultProcess.planStart(state, action)),
            Map.entry("frontier.settlement_assault.progress", (state, action, autonomous) -> HiveSettlementAssaultProcess.planProgress(state, action)),
            Map.entry("frontier.settlement_assault.combat", (state, action, autonomous) -> HiveSettlementAssaultProcess.planCombat(state, action)));

    static {
        Set<String> declared = descriptors().stream().map(DeterministicProcessDescriptor::id)
                .filter(id -> !id.equals("kernel-schedule")).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!declared.equals(MODULES.keySet())) {
            throw new IllegalStateException("Frontier executable modules differ from declared process ownership; declared="
                    + declared + " modules=" + MODULES.keySet());
        }
    }

    private FrontierWorldProcessCatalog() { }

    public static List<DeterministicProcessDescriptor> descriptors() {
        return List.of(
                descriptor("kernel-schedule", Set.of(), Set.of(), KERNEL, emissions("kernel-schedule"), KERNEL),
                descriptor("physical-observation", physicalCommands(), Set.of(), PHYSICAL, emissions("physical-observation"), PHYSICAL),
                descriptor("replica-custody", replicaCustodyCommands(), Set.of(), REPLICA_CUSTODY, emissions("replica-custody"), REPLICA_CUSTODY),
                descriptor("ambient-actors", ambientCommands(), Set.of(), AMBIENT, emissions("ambient-actors"), AMBIENT),
                descriptor("logistics-scenes", logisticsCommands(), logisticsSchedules(), LOGISTICS, emissions("logistics-scenes"), LOGISTICS),
                descriptor("population", populationCommands(), populationSchedules(), POPULATION, emissions("population"), POPULATION),
                descriptor("actor-movement", types("frontier.actor_movement_hot_observed"), types(ActorMovementProcess.PROGRESS),
                        ACTOR_MOVEMENT, emissions("actor-movement"), ACTOR_MOVEMENT),
                descriptor("economy", economyCommands(), economySchedules(), ECONOMY, emissions("economy"), ECONOMY),
                descriptor("resource-sites", resourceCommands(), resourceSchedules(), RESOURCE_SITES, emissions("resource-sites"), RESOURCE_SITES),
                descriptor("hive", hiveCommands(), hiveSchedules(), HIVE, emissions("hive"), HIVE),
                descriptor("infrastructure", infrastructureCommands(), infrastructureSchedules(), INFRASTRUCTURE, emissions("infrastructure"), INFRASTRUCTURE),
                descriptor("settlement-service-work", serviceWorkCommands(), serviceWorkSchedules(), SETTLEMENT_SERVICE_WORK,
                        emissions("settlement-service-work"), SETTLEMENT_SERVICE_WORK),
                descriptor("strategy", strategyCommands(), strategySchedules(), STRATEGY, emissions("strategy"), STRATEGY));
    }

    public static Set<String> allWorldPayloadTypes() { return ALL_WORLD; }

    /** Exposed only for deterministic composition tests; this is not a plugin registry. */
    public static Set<String> executableModuleIds() { return MODULES.keySet(); }

    public static Set<String> scheduledKinds() { return SCHEDULED_PLANNERS.keySet(); }

    /** Startup/recovery fence for the exact owner/kind/role-schema capability composition. */
    public static void requirePhysicalLifecycleComposition() {
        // Touching the closed composition is intentional: static construction has already
        // rejected absence, duplicates and declaration drift.  Keeping this explicit lets
        // hydration establish the same fence before any recovered record can be dispatched.
        PHYSICAL_LIFECYCLES.fingerprint();
    }

    /** Recovery validates each durable intent against the currently installed closed declaration. */
    public static void requirePhysicalLifecycleState(FrontierWorldState state) {
        PHYSICAL_LIFECYCLES.requireRetainedState(state);
    }

    /** Read-only composition/pressure account; it never influences admission or mutation. */
    public static PhysicalIntentLifecycleCompositionDiagnostic physicalLifecycleDiagnostic(FrontierWorldState state) {
        return PHYSICAL_LIFECYCLES.diagnostic(state);
    }

    public static String physicalLifecycleFingerprint() { return PHYSICAL_LIFECYCLES.fingerprint(); }

    /** Routes only through the module selected by the registry's exact command-type owner. */
    public static CommandPlan planCommand(String processId, FrontierWorldState state, FrontierCommand command) {
        return module(processId).planCommand(state, command);
    }

    /** Routes only through the module selected by the registry's exact event-type owner. */
    public static FrontierWorldState reduce(String processId, FrontierWorldState state, FrontierEvent event) {
        FrontierSceneLeaseAdmissionGuard.require(event.payload());
        return module(processId).reduce(state, event);
    }

    /** Exact registered reducer owner supplies same-transaction schedule retirement. */
    public static List<ScheduledAction> retiredSchedules(DeterministicProcessRegistry registry,
            FrontierWorldState previous, FrontierWorldState next, FrontierEvent event,
            java.util.function.Supplier<List<ScheduledAction>> pending) {
        return module(registry.requireReducedEventOwner(event.payload().type()))
                .retiredSchedules(previous, next, pending);
    }

    /** Bootstrap is data-only; this catalog owns the finite initial process schedule. */
    public static List<ScheduledAction> initialSchedule(FrontierBootstrap bootstrap) {
        FrontierRuleset.Cadence cadence = bootstrap.ruleset().cadence();
        List<ScheduledAction> actions = new java.util.ArrayList<>(List.of(StructuralRepairProcess.scan(1, cadence.structuralRepairInitialScanTick()),
                RouteConstructionProcess.scan(1, cadence.routeConstructionInitialScanTick()), RouteMaintenanceProcess.scan(1, cadence.routeConstructionInitialScanTick()),
                SettlementServiceWorkProcess.scan(1, cadence.decontaminationInitialScanTick())));
        for (int index = 0; index < bootstrap.settlements().size(); index++) {
            actions.add(StrategicObjectiveProcess.review(bootstrap.settlements().get(index).id(), 1,
                    cadence.settlementStrategicInitialReviewTick() + index * cadence.settlementInitialStagger()));
            actions.add(PopulationBirthProcess.review(bootstrap.settlements().get(index).id(), 1,
                    cadence.populationBirthInitialReviewTick() + index * cadence.settlementInitialStagger()));
            actions.add(CompanyFoundationProcess.review(bootstrap.settlements().get(index).id(), 1,
                    cadence.companyFoundationInitialReviewTick() + index * cadence.settlementInitialStagger()));
        }
        for (Settlement settlement : bootstrap.settlements()) {
            for (Resident resident : settlement.residents()) {
                int metabolism = ResidentCharacteristics.initial(bootstrap.ruleset().residentLife(),
                        resident.id()).effectiveMetabolismPermille(0L);
                actions.add(ResidentNeedProcess.review(resident.id(), ResidentNutrition.nourishedAtTick(0L)
                        .nextThresholdTick(bootstrap.ruleset().residentLife(), metabolism)));
                actions.add(ResidentActivityProcess.review(resident.id(), 1L));
            }
        }
        actions.add(PopulationMigrationProcess.review(1, cadence.populationMigrationInitialReviewTick()));
        actions.add(TerminalLogisticsProcess.review(1, cadence.terminalLogisticsInitialReviewTick()));
        FrontierResourceSitePlan.compile(bootstrap).keySet().stream().sorted()
                .forEach(site -> actions.add(ResourceSiteProcess.preparation(site, cadence.resourceInitialPreparationTick())));
        // Scout cadence must depend only on scout order. Unrelated resident timers must not
        // delay first contact or turn the hive's initial patrol into a population-size effect.
        List<Bioform> scouts = bootstrap.hive().bioforms().stream().filter(Bioform::isScout)
                .filter(scout -> HivePhysiologySupport.initiallyDeployed(bootstrap.hive(), scout))
                .sorted(java.util.Comparator.comparing(Bioform::id)).toList();
        for (int index = 0; index < scouts.size(); index++)
            actions.add(HiveScoutPatrolProcess.patrol(scouts.get(index).id(), 1,
                    cadence.hiveScoutInitialPatrolTick() + index * cadence.hiveScoutInitialStagger()));
        actions.add(StrategicObjectiveProcess.review(bootstrap.hive().id(), 1, cadence.hiveStrategicInitialReviewTick()));
        return List.copyOf(actions);
    }

    public static List<ProposedEvent> planScheduled(DeterministicProcessRegistry registry, FrontierWorldState state,
                                                     ScheduledAction action, boolean autonomousInterception) {
        return planScheduled(registry, state, action, autonomousInterception, action.dueAt());
    }

    public static List<ProposedEvent> planScheduled(DeterministicProcessRegistry registry, FrontierWorldState state,
                                                     ScheduledAction action, boolean autonomousInterception,
                                                     io.farfrontier.palemirror.frontier.v3.api.SimInstant currentInstant) {
        String processId = registry.requireScheduledOwner(action.kind());
        ScheduledPlanner planner = SCHEDULED_PLANNERS.get(action.kind());
        if (planner == null) throw new IllegalStateException("registered scheduled kind has no planner: " + action.kind());
        List<ProposedEvent> planned = planner.plan(state, action, autonomousInterception, currentInstant);
        List<ProposedEvent> result = planned.isEmpty()
                ? List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())))
                : planned;
        return registry.validateEmissions(processId, result);
    }

    public static boolean scheduledHeld(DeterministicProcessRegistry registry, FrontierWorldState state, ScheduledAction action) {
        registry.requireScheduledOwner(action.kind());
        ScheduledPlanner planner = SCHEDULED_PLANNERS.get(action.kind());
        if (planner == null) throw new IllegalStateException("registered scheduled kind has no planner: " + action.kind());
        return planner.held(state, action);
    }

    /** Park only waits whose source/service owner has an exact wake address. */
    public static Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> holdWakeKeys(
            FrontierWorldState state, ScheduledAction action) {
        if (!action.kind().equals(ResidentActivityProcess.REVIEW)
                && !action.kind().equals(ResidentMealProcess.PROGRESS)) return Set.of();
        ResidentProfile resident = state.humanPopulation().resident(action.subject());
        if (resident == null) return Set.of(action.subject());
        if (action.kind().equals(ResidentActivityProcess.REVIEW)) {
            return ResidentActivityProcess.wakeDependencies(state, action.subject());
        } else {
            ResidentMeal meal = state.humanPopulation().meals().get(action.subject());
            if (meal == null) return Set.of(action.subject());
            // Only the side-pocket wait is depot-addressed. Route/physical/HOT
            // holds have different causal owners and remain directly checked.
            if (meal.pendingPhysicalStep().isPresent()
                    || !FrontierSceneAdmission.available(state, List.of(meal.residentId()))
                    || state.sceneLeases().values().stream()
                        .anyMatch(lease -> lease.retainsMemberCustody(meal.residentId()))
                    || meal.phase() != ResidentMeal.Phase.MOVE || meal.coldTravel().isPresent()
                    || ServiceAccessCoordinator.depotAvailableForMeal(state, meal.depotId(), meal.residentId()))
                return Set.of();
        }
        return Set.of(action.subject(), FrontierWorldState.depotId(resident.settlementId()));
    }

    /** Derived invalidation; canonical facts and due order stay in the WAL-backed queue. */
    public static Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> wakeKeys(
            FrontierWorldState previous, FrontierWorldState next, FrontierEvent event) {
        java.util.HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId> keys = new java.util.HashSet<>();
        var resident = next.humanPopulation().resident(event.subject());
        if (resident == null) resident = previous.humanPopulation().resident(event.subject());
        if (resident != null) keys.add(resident.id());

        // Geometry affects clearance independently of stock/access. This bounded settlement
        // dependency also covers observed player edits whose event subject is the executor.
        if (previous.physicalDeltas() != next.physicalDeltas()
                || previous.structureDamage() != next.structureDamage()
                || previous.routeTopology() != next.routeTopology()
                || previous.hiveColony().addedOrgans() != next.hiveColony().addedOrgans())
            next.bootstrap().settlements().forEach(settlement -> keys.add(settlement.id()));

        boolean accessChanged = previous.actorLocations() != next.actorLocations()
                || previous.ambientLeases() != next.ambientLeases()
                || previous.humanPopulation().meals() != next.humanPopulation().meals()
                || previous.actorMovements() != next.actorMovements()
                || previous.productionJobs() != next.productionJobs()
                || previous.resourceSites() != next.resourceSites();
        boolean stockChanged = previous.inventory().fungibleResources() != next.inventory().fungibleResources();
        boolean custodyChanged = previous.replicaCustody() != next.replicaCustody();
        boolean sceneChanged = previous.sceneLeases() != next.sceneLeases();
        if (accessChanged || stockChanged || custodyChanged || sceneChanged) keys.add(event.subject());
        if (!accessChanged && !stockChanged && !custodyChanged && !sceneChanged)
            return Set.copyOf(keys);
        if (sceneChanged) {
            for (var entry : previous.sceneLeases().entrySet()) {
                SceneLease updated = next.sceneLeases().get(entry.getKey());
                if (entry.getValue().equals(updated)) continue;
                addResidentDepotKeys(previous, entry.getValue(), keys);
                if (updated != null) addResidentDepotKeys(next, updated, keys);
            }
            for (var entry : next.sceneLeases().entrySet()) {
                if (!previous.sceneLeases().containsKey(entry.getKey()))
                    addResidentDepotKeys(next, entry.getValue(), keys);
            }
        }
        io.farfrontier.palemirror.frontier.v3.api.SubjectId siteId = null;
        if (accessChanged) {
            if (next.resourceSites().sites().containsKey(event.subject())) siteId = event.subject();
            else {
                for (ResourceSiteLifecycle lifecycle : next.resourceSites().sites().values()) {
                    if (lifecycle.activeWork().filter(work -> work.id().equals(event.subject())).isPresent()) {
                        siteId = lifecycle.siteId();
                        break;
                    }
                }
            }
        }
        ResourceSite affectedSite = siteId == null ? null
                : FrontierResourceSitePlan.compile(next.bootstrap()).get(siteId);
        for (Settlement settlement : next.bootstrap().settlements()) {
            var depot = FrontierWorldState.depotId(settlement.id());
            boolean localAccess = accessChanged && (event.subject().equals(settlement.id())
                    || resident != null && resident.settlementId().equals(settlement.id())
                    || previous.productionJobs().containsKey(event.subject())
                        && previous.productionJobs().get(event.subject()).settlementId().equals(settlement.id())
                    || next.productionJobs().containsKey(event.subject())
                        && next.productionJobs().get(event.subject()).settlementId().equals(settlement.id())
                    || affectedSite != null && affectedSite.settlementId().equals(settlement.id()));
            boolean localStock = stockChanged && !java.util.Objects.equals(
                    FungibleResourceCustodySupport.accountAtContainer(previous, depot),
                    FungibleResourceCustodySupport.accountAtContainer(next, depot));
            boolean localCustody = custodyChanged && (ReferenceContainerCustody.blocksCanonicalUse(previous, depot)
                    != ReferenceContainerCustody.blocksCanonicalUse(next, depot)
                    || ReferenceContainerCustody.hasLiveCustody(previous, depot)
                    != ReferenceContainerCustody.hasLiveCustody(next, depot));
            if (localAccess || localStock || localCustody) keys.add(depot);
        }
        return Set.copyOf(keys);
    }

    private static void addResidentDepotKeys(FrontierWorldState state, SceneLease lease,
                                             Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> keys) {
        for (SceneMember member : lease.members()) {
            ResidentProfile resident = state.humanPopulation().resident(member.actorId());
            if (resident != null) {
                keys.add(resident.id());
                keys.add(FrontierWorldState.depotId(resident.settlementId()));
            }
        }
    }

    private static DeterministicProcessDescriptor descriptor(String id, Set<String> commands, Set<String> schedules,
                                                              Set<String> events, Set<String> emissions, Set<String> codecs) {
        return new DeterministicProcessDescriptor(id, commands, schedules, events, emissions, codecs);
    }

    private static Set<String> physicalCommands() { return types(
            "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
            "frontier.physical_delta_observed", "frontier.physical_deltas_observed", "frontier.resource_deposited",
            "frontier.fungible_stack_layout_observed", "frontier.fungible_resource_handoff_observed", "frontier.fungible_stock_departure_observed", "frontier.fungible_stock_contribution_observed",
            "frontier.fungible_stack_bindings_released", "frontier.exact_item_custody_changed",
            "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed", "frontier.container_surface_transition",
            "frontier.cargo_carrier_released"); }
    private static Set<String> replicaCustodyCommands() { return REPLICA_CUSTODY; }
    private static Set<String> ambientCommands() { return types(
            "frontier.ambient_actor_died", "frontier.ambient_actor_observed", "frontier.ambient_body_confirmed", "frontier.ambient_lease_prepared",
            "frontier.ambient_lease_released", "frontier.ambient_lease_transition", "frontier.ambient_lease_restart_absence_observed"); }
    private static Set<String> logisticsCommands() { return types(
            "frontier.operation_assembly_advanced", "frontier.operation_assembly_deferred",
            "frontier.operation_travel_segment_completed", "frontier.operation_travel_advanced", "frontier.operation_travel_started",
            "frontier.scene_lease_prepared", "frontier.scene_lease_handoff", "frontier.scene_lease_transition",
            "frontier.scene_lease_released_v2", "frontier.scene_lease_recovery_unresolved", "frontier.scene_lease_recovery_revoked", "frontier.actor_died",
            "frontier.settlement_assault_scene_lease_prepared", "frontier.settlement_assault_scene_lease_handoff",
            "frontier.engineering_work_scene_lease_prepared", "frontier.engineering_work_scene_lease_handoff"); }
    private static Set<String> populationCommands() { return types(
            "frontier.resident_born", "frontier.resident_migrated", "frontier.resident_transit_advanced",
            "frontier.resident_metabolism_changed", "frontier.resident_meal_hot_arrived",
            "frontier.resident_meal_hot_effect_prepared", "frontier.resident_meal_hot_effect_observed",
            "frontier.resident_meal_hot_hand_materialized", "frontier.resident_meal_hot_hand_released",
            "frontier.resident_meal_hot_access_cleared", "frontier.resident_meal_hot_returned",
            "frontier.medical_treatment_scene_lease_prepared", "frontier.medical_treatment_scene_lease_handoff"); }
    private static Set<String> economyCommands() { return types(
            "frontier.production_work_progressed", "frontier.production_work_traversal_advanced", "frontier.production_cold_work_advanced",
            "frontier.bakery_cold_step", "frontier.bakery_input_reallocated", "frontier.bakery_hot_goal_arrived", "frontier.bakery_hot_access_cleared", "frontier.bakery_hot_effect_prepared",
            "frontier.bakery_hot_effect_observed", "frontier.bakery_hot_work_tick", "frontier.bakery_hot_hand_release",
            "frontier.bakery_hot_hand_materialized", "frontier.bakery_hot_block_changed", "frontier.production_work_traversal_blocked",
            "frontier.production_work_scene_lease_prepared", "frontier.production_work_scene_lease_handoff"); }
    private static Set<String> resourceCommands() { return types(
            "frontier.resource_site_conflict_observed",
            "frontier.resource_field_cell_observed", "frontier.resource_field_work_access_observed",
            "frontier.resource_field_world_change_held", "frontier.resource_field_world_change_acknowledged",
            "frontier.resource_field_foreign_change_held", "frontier.resource_field_foreign_cell_observed",
            "frontier.resource_field_foreign_change_acknowledged",
            "frontier.resource_field_player_break_prepared",
            "frontier.resource_site_harvest_crop_prepared",
            "frontier.resource_site_harvest_progressed",
            "frontier.resource_site_harvest_scene_reconciled",
            "frontier.resource_site_harvest_hand_projected",
            "frontier.resource_site_harvest_hand_release",
            "frontier.resource_site_harvest_segment_renewed", "frontier.resource_site_harvest_blocked_cell_skipped",
            "frontier.resource_site_harvest_target_retargeted", "frontier.resource_site_harvest_route_blocked", "frontier.resource_site_harvest_route_cleared",
            "frontier.resource_site_harvest_batch_prepared", "frontier.resource_site_harvest_batch_delivered",
            "frontier.resource_site_harvest_hot_traversal_advanced", "frontier.resource_site_harvest_hot_goal_arrived", "frontier.resource_site_harvest_hot_transit_observed",
            "frontier.resource_site_harvest_scene_lease_prepared",
            "frontier.resource_site_harvest_scene_preparation_aborted",
            "frontier.resource_site_harvest_scene_lease_handoff"); }
    private static Set<String> hiveCommands() { return types("frontier.hot_scout_operation_observed", "frontier.scout_patrol_advanced", "frontier.scout_patrol_lease_recovered",
            "frontier.hive_mobilization_release_started", "frontier.hive_mobilization_cocoon_released", "frontier.hive_mobilization_assembly_advanced", "frontier.hive_mobilization_return_advanced",
            "frontier.hive_mobilization_conflicted", "frontier.deferred_aftermath_resolved",
            "frontier.settlement_assault_formation_observed", "frontier.settlement_assault_march_issue_observed"); }
    /**
     * These are the two physical-observation payloads that advance an already declared
     * engineering journey.  The infrastructure process verifies the exact HOT lease and
     * retained one-cell corridor transition before reducing either one.
     */
    private static Set<String> infrastructureCommands() { return types(
            "frontier.route_construction_assembly_advanced", "frontier.route_maintenance_assembly_advanced",
            "frontier.route_patrol_scene_lease_prepared", "frontier.route_patrol_scene_lease_handoff", "frontier.route_patrol_formation_observed",
            "frontier.route_patrol_blocked", "frontier.route_patrol_obstruction_confirmed"); }
    private static Set<String> serviceWorkCommands() { return types(
            "frontier.settlement_service_work_scene_lease_prepared", "frontier.settlement_service_work_scene_lease_handoff",
            "frontier.settlement_service_work_traversal_advanced", "frontier.settlement_service_work_traversal_blocked",
            "frontier.settlement_service_work_progressed"); }
    private static Set<String> strategyCommands() { return Set.of(); }

    private static Set<String> logisticsSchedules() { return types(
            "frontier.supply.task.start", "frontier.supply.cargo.load", "frontier.operation.assembly",
            "frontier.operation.progress", "frontier.terminal_logistics.retention", "frontier.hive_route_engagement.start",
            "frontier.hive_route_engagement.progress", "frontier.hive_route_engagement.readiness",
            "frontier.hive_route_engagement.combat", "frontier.hive_route_engagement.control"); }
    private static Set<String> populationSchedules() { return types(
            "frontier.population.birth.review", "frontier.population.birth.complete", "frontier.population.migration.review",
            "frontier.population.migration.progress", DefenderEquipmentProcess.REVIEW_ACTION, DefenderEquipmentReturnProcess.REVIEW_ACTION,
            ResidentNeedProcess.REVIEW, ResidentActivityProcess.REVIEW, ResidentMealProcess.PROGRESS); }
    private static Set<String> economySchedules() { return types(
            "frontier.settlement.production.task.start", "frontier.settlement.production.task.complete",
            "frontier.company.foundation.review", "frontier.market.clear"); }
    private static Set<String> resourceSchedules() { return types(
            "frontier.resource_site.growth", "frontier.resource_site.prepare", "frontier.resource_site.harvest",
            "frontier.resource_site.harvest.cold_progress", "frontier.objective.resource_harvest"); }
    private static Set<String> hiveSchedules() { return types(
            "frontier.hive.infection.task", "frontier.hive.growth.task.start", "frontier.hive.growth.task.complete",
            "frontier.hive.nutrient.transfer.progress", "frontier.hive.mobilization.assembly_progress", "frontier.hive.mobilization.return_progress", "frontier.hive.scout.patrol",
            "frontier.settlement_assault.start", "frontier.settlement_assault.progress", "frontier.settlement_assault.combat"); }
    private static Set<String> infrastructureSchedules() { return types(
            "frontier.structural_repair.scan", "frontier.route_construction.scan", "frontier.route_construction.start",
            "frontier.route_construction.assembly_progress", "frontier.route_construction.progress", "frontier.route_construction.return_progress",
            "frontier.route_maintenance.scan", "frontier.route_maintenance.assembly_progress",
            "frontier.route_maintenance.progress", "frontier.route_maintenance.return_progress",
            "frontier.route_patrol.start", "frontier.route_patrol.progress"); }
    private static Set<String> serviceWorkSchedules() { return types("frontier.decontamination.scan"); }
    private static Set<String> strategySchedules() { return types(
            "frontier.objective.review", "frontier.objective.stock_reconsider", "frontier.objective.reconsider", "frontier.objective.interrupt", "frontier.objective.assault"); }

    private static Set<String> types(String... values) { return Set.copyOf(List.of(values)); }

    /**
     * Explicit durable output contract for each owner. These are intentionally individual wire
     * type IDs, not domain-set unions: adding a payload to another reducer cannot silently make
     * it legal for an existing planner to emit it.
     */
    private static Set<String> emissions(String processId) {
        return switch (processId) {
            case "kernel-schedule" -> KERNEL;
            case "physical-observation" -> types(
                    "frontier.physical_custody_checkpointed", "frontier.physical_custody_released",
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    // A witnessed block change checkpoints only a resident whose retained COLD route crosses it.
                    "frontier.resident_meal_cold_step",
                    "frontier.actor_movement_cold_advanced",
                    "frontier.physical_delta_observed", "frontier.physical_deltas_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
                    "frontier.resource_deposited", "frontier.fungible_stack_layout_observed", "frontier.fungible_resource_handoff_observed", "frontier.fungible_stock_departure_observed", "frontier.fungible_stock_contribution_observed",
                    "frontier.fungible_stack_bindings_released", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed",
                    "frontier.inventory_conflict_observed",
                    "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.route_construction_material_loaded",
                    "frontier.route_maintenance_material_loaded",
                    // CARGO_LOADING is admitted by the generic physical boundary, but its
                    // exact owner atomically transfers the confirmed contract into cargo and
                    // creates the subsequent route operation.  These are deliberately named
                    // cross-owner outputs, not a broad logistics emission allowance.
                    "frontier.cargo_loaded", "frontier.operation_created",
                    "frontier.resident_born", "frontier.resident_migrated",
                    "frontier.resident_migration_started", "frontier.resident_migration_advanced", "frontier.resident_transit_advanced", "frontier.resident_migration_blocked",
                    "frontier.resident_migration_resumed", "frontier.resident_birth_started", "frontier.resident_birth_cancelled", "frontier.settlement_provision_started",
                    "frontier.settlement_provision_started_v2", "frontier.settlement_provision_consumed", "frontier.settlement_provision_resolved",
                    "frontier.resident_health_transition", "frontier.settlement_quarantine_transition", "frontier.medical_treatment_started",
                    "frontier.medical_treatment_transition", "frontier.company_registered", "frontier.employment_contract_opened",
                    "frontier.employment_contract_terminated", "frontier.market_demand_opened", "frontier.market_quote_published", "frontier.market_work_order_accepted",
                    "frontier.market_work_order_cancelled", "frontier.market_demand_expired", "frontier.market_demand_cancelled", "frontier.production_started",
                    "frontier.production_completed", "frontier.production_blocked", "frontier.settlement_infection_observed", "frontier.strategic_objective_selected",
                    "frontier.strategic_task_planned", "frontier.strategic_task_transition", "frontier.scene_lease_transition",
                    "frontier.production_work_scene_preparation_aborted", "frontier.production_work_scene_finalized");
            case "replica-custody" -> union(REPLICA_CUSTODY, types("kernel.schedule_created"));
            case "ambient-actors" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.ambient_actor_died", "frontier.ambient_actor_observed", "frontier.ambient_body_confirmed", "frontier.ambient_lease_prepared", "frontier.ambient_lease_released",
                    "frontier.ambient_lease_transition", "frontier.ambient_lease_restart_absence_observed", "frontier.company_registered", "frontier.employment_contract_opened", "frontier.employment_contract_terminated",
                    "frontier.market_demand_opened", "frontier.market_quote_published", "frontier.market_work_order_accepted", "frontier.market_work_order_cancelled",
                    "frontier.market_demand_expired", "frontier.market_demand_cancelled", "frontier.production_started", "frontier.production_completed", "frontier.production_blocked");
            case "logistics-scenes" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.supply_contract_created", "frontier.supply_contract_abandoned", "frontier.cargo_loaded", "frontier.cargo_delivered", "frontier.operation_created",
                    "frontier.operation_advanced", "frontier.operation_assembly_advanced", "frontier.operation_assembly_deferred", "frontier.operation_travel_started",
                    "frontier.operation_travel_advanced", "frontier.operation_travel_segment_completed", "frontier.operation_cold_suspended", "frontier.operation_failed",
                    "frontier.terminal_logistics_compacted", "frontier.scene_lease_prepared", "frontier.scene_lease_handoff", "frontier.scene_lease_transition",
                    "frontier.scene_lease_released_v2", "frontier.scene_lease_recovery_unresolved", "frontier.scene_lease_recovery_revoked", "frontier.actor_died", "frontier.settlement_assault_scene_lease_prepared",
                    "frontier.settlement_assault_scene_lease_handoff", "frontier.engineering_work_scene_lease_prepared", "frontier.engineering_work_scene_lease_handoff",
                    // The shared release executor owns the physical confirmation of every typed
                    // scene.  A blocked production scene therefore finalizes through this
                    // logistics-owned release command, while Economy remains the only reducer.
                    "frontier.production_work_scene_finalized",
                    "frontier.physical_delta_observed", "frontier.physical_deltas_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition",
                    "frontier.structure_damaged", "frontier.resource_deposited", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed",
                    "frontier.inventory_conflict_observed", "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.settlement_infection_observed",
                    "frontier.strategic_objective_selected", "frontier.strategic_task_planned", "frontier.strategic_task_transition", "frontier.company_registered",
                    "frontier.employment_contract_opened", "frontier.employment_contract_terminated", "frontier.market_demand_opened", "frontier.market_quote_published",
                    "frontier.market_work_order_accepted", "frontier.market_work_order_cancelled", "frontier.market_demand_expired", "frontier.market_demand_cancelled",
                    "frontier.production_started", "frontier.production_completed", "frontier.production_blocked", "frontier.infection_changed", "frontier.hive_growth_started",
                    "frontier.hive_growth_biomass_consumed", "frontier.hive_growth_completed", "frontier.hive_growth_blocked", "frontier.hive_nutrient_transfer_started",
                    "frontier.hive_nutrient_transfer_advanced", "frontier.hive_nutrient_transfer_completed", "frontier.hive_nutrient_transfer_blocked",
                    "frontier.hive_nutrient_transfer_endpoint_prepared", "frontier.hive_operation_observed", "frontier.hive_territory_observed",
                    "frontier.hive_settlement_observed", "frontier.hive_doctrine_selected", "frontier.hot_scout_operation_observed", "frontier.scout_patrol_advanced", "frontier.scout_patrol_lease_recovered",
                    "frontier.route_engagement_started", "frontier.route_engagement_attacker_advanced", "frontier.route_engagement_transition", "frontier.route_engagement_strike",
                    "frontier.route_engagement_resolved", "frontier.route_engagement_command_authority_changed", "frontier.settlement_assault_started",
                    "frontier.settlement_assault_attacker_advanced", "frontier.settlement_assault_formation_observed",
                    "frontier.settlement_assault_march_issue_observed",
                    "frontier.settlement_assault_transition", "frontier.settlement_assault_strike", "frontier.settlement_assault_resolved",
                    "frontier.hive_mobilization_started", "frontier.hive_mobilization_departed");
            case "population" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.actor_movement_interrupted", "frontier.actor_movement_started",
                    "frontier.resident_born", "frontier.resident_migrated", "frontier.resident_migration_started", "frontier.resident_migration_advanced",
                    "frontier.resident_transit_advanced", "frontier.resident_migration_blocked", "frontier.resident_migration_resumed", "frontier.resident_birth_started",
                    "frontier.resident_birth_cancelled", "frontier.settlement_provision_started", "frontier.settlement_provision_started_v2", "frontier.settlement_provision_consumed",
                    "frontier.settlement_provision_resolved", "frontier.resident_starvation_integrated", "frontier.resident_need_integrated", "frontier.resident_metabolism_changed",
                    "frontier.resident_meal_started", "frontier.resident_meal_cold_step", "frontier.resident_meal_hot_arrived",
                    "frontier.resident_meal_hot_effect_prepared", "frontier.resident_meal_hot_effect_observed",
                    "frontier.resident_meal_hot_hand_materialized", "frontier.resident_meal_hot_hand_released",
                    "frontier.resident_meal_hot_access_cleared", "frontier.resident_meal_hot_returned",
                    "frontier.resident_health_transition", "frontier.settlement_quarantine_transition",
                    "frontier.medical_treatment_started", "frontier.medical_treatment_transition",
                    "frontier.medical_treatment_scene_lease_prepared", "frontier.medical_treatment_scene_lease_handoff", "frontier.physical_delta_observed", "frontier.physical_deltas_observed",
                    "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged", "frontier.resource_deposited",
                    "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed", "frontier.container_surface_transition",
                    "frontier.cargo_carrier_released");
            case "actor-movement" -> types("frontier.actor_movement_cold_advanced", "frontier.actor_movement_hot_observed", "frontier.actor_movement_interrupted",
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled");
            case "economy" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.company_registered", "frontier.employment_contract_opened", "frontier.employment_contract_terminated", "frontier.market_demand_opened",
                    "frontier.market_quote_published", "frontier.market_work_order_accepted", "frontier.market_relationship_incident_recorded", "frontier.market_work_order_cancelled", "frontier.market_demand_expired",
                    "frontier.market_demand_cancelled", "frontier.production_started", "frontier.production_completed", "frontier.fungible_production_completed",
                    "frontier.production_work_progressed", "frontier.production_work_traversal_advanced", "frontier.production_cold_work_advanced",
                    "frontier.bakery_cold_step", "frontier.bakery_input_reallocated", "frontier.bakery_hot_goal_arrived", "frontier.bakery_hot_access_cleared", "frontier.bakery_hot_effect_prepared",
                    "frontier.bakery_hot_effect_observed", "frontier.bakery_hot_work_tick", "frontier.bakery_hot_hand_release",
                    "frontier.bakery_hot_hand_materialized", "frontier.bakery_hot_block_changed", "frontier.production_work_traversal_blocked",
                    "frontier.production_work_scene_lease_prepared", "frontier.production_work_scene_lease_handoff",
                    "frontier.production_work_scene_preparation_aborted", "frontier.production_work_scene_finalized", "frontier.production_blocked", "frontier.production_interrupted",
                    "frontier.scene_lease_transition",
                    "frontier.physical_delta_observed", "frontier.physical_deltas_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
                    "frontier.resource_deposited", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed",
                    "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.settlement_infection_observed", "frontier.strategic_objective_selected",
                    "frontier.strategic_task_planned", "frontier.strategic_task_transition");
            case "resource-sites" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.resource_site_growth_advanced", "frontier.resource_site_preparation_started", "frontier.resource_site_prepared",
                    "frontier.resource_site_harvest_started", "frontier.resource_site_harvest_crop_prepared",
                    "frontier.resource_site_harvest_progressed", "frontier.resource_site_harvest_cold_traversal_advanced", "frontier.resource_site_harvest_cold_goal_advanced",
                            "frontier.resource_site_harvest_cold_goal_held", "frontier.resource_site_harvest_returned",
                    "frontier.resource_site_harvest_segment_renewed", "frontier.resource_site_harvest_blocked_cell_skipped",
                    "frontier.resource_site_harvest_target_retargeted", "frontier.resource_site_harvest_route_blocked", "frontier.resource_site_harvest_route_cleared",
                    "frontier.resource_site_harvest_batch_prepared", "frontier.resource_site_harvest_batch_delivered",
                    "frontier.resource_site_harvest_scene_reconciled",
                    "frontier.resource_site_harvest_hand_projected",
                    "frontier.resource_site_harvest_hand_release",
                    "frontier.resource_site_harvest_hot_traversal_advanced", "frontier.resource_site_harvest_hot_goal_arrived", "frontier.resource_site_harvest_hot_transit_observed", "frontier.resource_site_harvest_scene_lease_prepared",
                    "frontier.resource_site_harvest_scene_preparation_aborted",
                    "frontier.resource_site_harvest_scene_lease_handoff",
                    "frontier.resource_site_conflict_observed", "frontier.resource_field_cell_observed", "frontier.resource_field_work_access_observed",
                    "frontier.resource_field_world_change_held", "frontier.resource_field_world_change_acknowledged",
                    "frontier.resource_field_foreign_change_held", "frontier.resource_field_foreign_cell_observed",
                    "frontier.resource_field_foreign_change_acknowledged",
                    "frontier.resource_field_player_break_prepared",
                    "frontier.physical_delta_observed", "frontier.physical_deltas_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
                    "frontier.resource_deposited", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed",
                    "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.settlement_infection_observed", "frontier.strategic_objective_selected",
                    "frontier.strategic_task_planned", "frontier.strategic_task_transition");
            case "hive" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.infection_changed", "frontier.hive_growth_started", "frontier.hive_growth_biomass_consumed", "frontier.hive_growth_completed",
                    "frontier.hive_growth_blocked", "frontier.hive_nutrient_transfer_started", "frontier.hive_nutrient_transfer_advanced",
                    "frontier.hive_nutrient_transfer_completed", "frontier.hive_nutrient_transfer_blocked", "frontier.hive_nutrient_transfer_endpoint_prepared",
                    "frontier.hive_operation_observed", "frontier.hive_territory_observed", "frontier.hive_settlement_observed", "frontier.hive_doctrine_selected",
                    "frontier.hot_scout_operation_observed", "frontier.scout_patrol_advanced", "frontier.scout_patrol_lease_recovered", "frontier.route_engagement_started", "frontier.route_engagement_attacker_advanced",
                    "frontier.route_engagement_transition", "frontier.route_engagement_strike", "frontier.route_engagement_resolved", "frontier.route_engagement_command_authority_changed", "frontier.settlement_assault_started",
                    "frontier.settlement_assault_attacker_advanced", "frontier.settlement_assault_formation_observed", "frontier.settlement_assault_march_issue_observed", "frontier.settlement_assault_transition", "frontier.settlement_assault_strike",
                    "frontier.settlement_assault_resolved", "frontier.deferred_aftermath_prepared", "frontier.deferred_aftermath_resolved",
                    "frontier.hive_mobilization_started", "frontier.hive_mobilization_release_started",
                    "frontier.hive_mobilization_cocoon_released", "frontier.hive_mobilization_assembly_advanced", "frontier.hive_mobilization_return_advanced", "frontier.hive_mobilization_departed", "frontier.hive_mobilization_conflicted",
                    "frontier.production_interrupted",
                    "frontier.physical_delta_observed", "frontier.physical_deltas_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
                    "frontier.resource_deposited", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed",
                    "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.settlement_infection_observed", "frontier.strategic_objective_selected",
                    "frontier.strategic_task_planned", "frontier.strategic_task_transition");
            case "infrastructure" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.route_construction_started", "frontier.route_construction_material_loaded", "frontier.route_topology_cutover",
                    "frontier.route_construction_assembly_started", "frontier.route_construction_assembly_advanced",
                    "frontier.route_maintenance_started", "frontier.route_maintenance_material_loaded",
                    "frontier.route_maintenance_assembly_started", "frontier.route_maintenance_assembly_advanced", "frontier.route_maintenance_closed",
                    "frontier.route_patrol_started", "frontier.route_patrol_formation_advanced",
                    "frontier.route_patrol_formation_observed", "frontier.route_patrol_obstruction_confirmed",
                    "frontier.route_patrol_failed", "frontier.route_patrol_blocked",
                    "frontier.route_patrol_scene_lease_prepared", "frontier.route_patrol_scene_lease_handoff",
                    "frontier.physical_delta_observed", "frontier.physical_deltas_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
                    "frontier.resource_deposited", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed",
                    "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.resident_born", "frontier.resident_migrated",
                    "frontier.resident_migration_started", "frontier.resident_migration_advanced", "frontier.resident_transit_advanced", "frontier.resident_migration_blocked",
                    "frontier.resident_migration_resumed", "frontier.resident_birth_started", "frontier.resident_birth_cancelled", "frontier.settlement_provision_started",
                    "frontier.settlement_provision_started_v2", "frontier.settlement_provision_consumed", "frontier.settlement_provision_resolved",
                    "frontier.resident_health_transition", "frontier.settlement_quarantine_transition", "frontier.medical_treatment_started", "frontier.medical_treatment_transition", "frontier.settlement_infection_observed",
                    "frontier.strategic_objective_selected", "frontier.strategic_task_planned", "frontier.strategic_task_transition");
            case "settlement-service-work" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.settlement_service_work_started", "frontier.settlement_service_work_scene_lease_prepared",
                    "frontier.settlement_service_work_scene_lease_handoff", "frontier.settlement_service_work_traversal_advanced",
                    "frontier.settlement_service_work_traversal_blocked", "frontier.settlement_service_work_progressed",
                    "frontier.scene_lease_transition", "frontier.strategic_task_transition");
            case "strategy" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.resident_born", "frontier.resident_migrated", "frontier.resident_migration_started", "frontier.resident_migration_advanced",
                    "frontier.resident_transit_advanced", "frontier.resident_migration_blocked", "frontier.resident_migration_resumed", "frontier.resident_birth_started",
                    "frontier.resident_birth_cancelled", "frontier.settlement_provision_started", "frontier.settlement_provision_started_v2", "frontier.settlement_provision_consumed",
                    "frontier.settlement_provision_resolved", "frontier.resident_health_transition", "frontier.settlement_quarantine_transition", "frontier.medical_treatment_started", "frontier.medical_treatment_transition",
                    "frontier.physical_intent_prepared",
                    "frontier.settlement_infection_observed", "frontier.strategic_objective_selected", "frontier.strategic_task_planned", "frontier.strategic_task_transition",
                    "frontier.infection_changed", "frontier.hive_growth_started", "frontier.hive_growth_biomass_consumed", "frontier.hive_growth_completed",
                    "frontier.hive_growth_blocked", "frontier.hive_nutrient_transfer_started", "frontier.hive_nutrient_transfer_advanced",
                    "frontier.hive_nutrient_transfer_completed", "frontier.hive_nutrient_transfer_blocked", "frontier.hive_nutrient_transfer_endpoint_prepared",
                    "frontier.hive_operation_observed", "frontier.hive_territory_observed", "frontier.hive_settlement_observed", "frontier.hive_doctrine_selected",
                    "frontier.hot_scout_operation_observed", "frontier.scout_patrol_advanced", "frontier.route_engagement_started", "frontier.route_engagement_attacker_advanced",
                    "frontier.route_engagement_transition", "frontier.route_engagement_strike", "frontier.route_engagement_resolved", "frontier.route_engagement_command_authority_changed", "frontier.settlement_assault_started",
                    "frontier.settlement_assault_attacker_advanced", "frontier.settlement_assault_formation_observed",
                    "frontier.settlement_assault_march_issue_observed", "frontier.settlement_assault_transition",
                    "frontier.settlement_assault_strike", "frontier.settlement_assault_resolved",
                    "frontier.company_registered", "frontier.employment_contract_opened", "frontier.employment_contract_terminated", "frontier.market_demand_opened",
                    "frontier.market_quote_published", "frontier.market_work_order_accepted", "frontier.market_work_order_cancelled", "frontier.market_demand_expired",
                    "frontier.market_demand_cancelled", "frontier.production_started", "frontier.production_completed", "frontier.production_blocked");
            default -> throw new IllegalArgumentException("unknown process emission contract: " + processId);
        };
    }
    private static FrontierWorldProcessModule module(String processId) {
        FrontierWorldProcessModule module = MODULES.get(processId);
        if (module == null) throw new IllegalArgumentException("no Frontier world process module for: " + processId);
        return module;
    }
    static PhysicalIntentLifecycleCapabilities physicalLifecycles() { return PHYSICAL_LIFECYCLES; }
    @SafeVarargs private static Set<String> union(Set<String>... values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Set<String> value : values) result.addAll(value);
        return Set.copyOf(result);
    }
}
