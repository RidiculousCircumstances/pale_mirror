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
    }

    private static final Set<String> KERNEL = types(
            "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled");
    private static final Set<String> PHYSICAL = types(
            "frontier.physical_delta_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition",
            "frontier.structure_damaged", "frontier.resource_deposited", "frontier.exact_item_custody_changed",
            "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed", "frontier.container_surface_transition",
            "frontier.cargo_carrier_released");
    private static final Set<String> AMBIENT = types(
            "frontier.ambient_actor_died", "frontier.ambient_actor_observed", "frontier.ambient_lease_prepared",
            "frontier.ambient_lease_released", "frontier.ambient_lease_transition", "frontier.ambient_lease_restart_absence_observed");
    private static final Set<String> LOGISTICS = types(
            "frontier.supply_contract_created", "frontier.supply_contract_abandoned", "frontier.cargo_loaded",
            "frontier.cargo_delivered", "frontier.operation_created", "frontier.operation_advanced",
            "frontier.operation_assembly_advanced", "frontier.operation_assembly_deferred", "frontier.operation_travel_started",
            "frontier.operation_travel_advanced", "frontier.operation_travel_segment_completed", "frontier.operation_cold_suspended",
            "frontier.operation_failed", "frontier.terminal_logistics_compacted", "frontier.scene_lease_prepared",
            "frontier.scene_lease_handoff", "frontier.scene_lease_transition", "frontier.scene_lease_released_v2",
            "frontier.scene_lease_recovery_unresolved", "frontier.actor_died", "frontier.settlement_assault_scene_lease_prepared",
            "frontier.settlement_assault_scene_lease_handoff", "frontier.engineering_work_scene_lease_prepared",
            "frontier.engineering_work_scene_lease_handoff");
    private static final Set<String> POPULATION = types(
            "frontier.resident_born", "frontier.resident_migrated", "frontier.resident_migration_started",
            "frontier.resident_migration_advanced", "frontier.resident_transit_advanced", "frontier.resident_migration_blocked",
            "frontier.resident_migration_resumed", "frontier.resident_birth_started", "frontier.resident_birth_cancelled",
            "frontier.settlement_provision_started", "frontier.settlement_provision_started_v2",
            "frontier.settlement_provision_consumed", "frontier.settlement_provision_resolved",
            "frontier.resident_health_transition", "frontier.settlement_quarantine_transition",
            "frontier.medical_treatment_started", "frontier.medical_treatment_transition",
            "frontier.medical_treatment_scene_lease_prepared", "frontier.medical_treatment_scene_lease_handoff");
    private static final Set<String> ECONOMY = types(
            "frontier.company_registered", "frontier.employment_contract_opened", "frontier.employment_contract_terminated",
            "frontier.market_demand_opened", "frontier.market_quote_published", "frontier.market_work_order_accepted",
            "frontier.market_work_order_cancelled", "frontier.market_demand_expired", "frontier.market_demand_cancelled",
            "frontier.production_started", "frontier.production_completed", "frontier.production_blocked", "frontier.production_interrupted");
    private static final Set<String> RESOURCE_SITES = types(
            "frontier.resource_site_growth_advanced", "frontier.resource_site_preparation_started",
            "frontier.resource_site_prepared", "frontier.resource_site_harvest_started", "frontier.resource_site_harvested",
            "frontier.resource_site_conflict_observed");
    private static final Set<String> HIVE = types(
            "frontier.infection_changed", "frontier.hive_growth_started", "frontier.hive_growth_biomass_consumed",
            "frontier.hive_growth_completed", "frontier.hive_growth_blocked", "frontier.hive_nutrient_transfer_started",
            "frontier.hive_nutrient_transfer_advanced", "frontier.hive_nutrient_transfer_completed",
            "frontier.hive_nutrient_transfer_blocked", "frontier.hive_nutrient_transfer_endpoint_prepared",
            "frontier.hive_operation_observed", "frontier.hive_territory_observed", "frontier.hive_settlement_observed",
            "frontier.hive_doctrine_selected", "frontier.hot_scout_operation_observed", "frontier.scout_patrol_advanced", "frontier.scout_patrol_lease_recovered",
            "frontier.route_engagement_started", "frontier.route_engagement_attacker_advanced",
            "frontier.route_engagement_transition", "frontier.route_engagement_strike", "frontier.route_engagement_resolved",
            "frontier.settlement_assault_started", "frontier.settlement_assault_attacker_advanced",
            "frontier.settlement_assault_transition", "frontier.settlement_assault_strike", "frontier.settlement_assault_resolved");
    private static final Set<String> INFRASTRUCTURE = types(
            "frontier.route_construction_started", "frontier.route_construction_material_loaded",
            "frontier.route_construction_assembly_started", "frontier.route_construction_assembly_advanced",
            "frontier.route_maintenance_started", "frontier.route_maintenance_material_loaded",
            "frontier.route_maintenance_assembly_started", "frontier.route_maintenance_assembly_advanced",
            "frontier.route_maintenance_closed",
            "frontier.route_topology_cutover", "frontier.route_patrol_started", "frontier.route_patrol_advanced",
            "frontier.route_patrol_obstruction_confirmed", "frontier.route_patrol_failed");
    private static final Set<String> STRATEGY = types(
            "frontier.settlement_infection_observed", "frontier.strategic_objective_selected",
            "frontier.strategic_task_planned", "frontier.strategic_task_transition");
    private static final Set<String> ALL_WORLD = union(PHYSICAL, AMBIENT, LOGISTICS, POPULATION, ECONOMY, RESOURCE_SITES,
            HIVE, INFRASTRUCTURE, STRATEGY);
    private static final Map<String, FrontierWorldProcessModule> MODULES = Map.of(
            "physical-observation", new FrontierPhysicalProcessModule(),
            "ambient-actors", new FrontierAmbientProcessModule(),
            "logistics-scenes", new FrontierLogisticsProcessModule(),
            "population", new FrontierPopulationProcessModule(),
            "economy", new FrontierEconomyProcessModule(),
            "resource-sites", new FrontierResourceSiteProcessModule(),
            "hive", new FrontierHiveProcessModule(),
            "infrastructure", new FrontierInfrastructureProcessModule(),
            "strategy", new FrontierStrategyProcessModule());
    private static final Map<String, ScheduledPlanner> SCHEDULED_PLANNERS = Map.ofEntries(
            Map.entry("frontier.hive.infection.task", (state, action, autonomous) -> HiveInfectionProcess.plan(state, action)),
            Map.entry("frontier.settlement.production.task.start", (state, action, autonomous) -> ProductionProcess.planStart(state, action)),
            Map.entry("frontier.settlement.production.task.complete", (state, action, autonomous) -> ProductionProcess.planCompletion(state, action)),
            Map.entry("frontier.supply.task.start", (state, action, autonomous) -> SupplyOperationProcess.planStart(state, action)),
            Map.entry("frontier.supply.cargo.load", SupplyOperationProcess::planCargoLoad),
            Map.entry("frontier.operation.assembly", (state, action, autonomous) -> SupplyOperationProcess.planAssembly(state, action)),
            Map.entry("frontier.operation.progress", (state, action, autonomous) -> SupplyOperationProcess.planProgress(state, action)),
            Map.entry("frontier.terminal_logistics.retention", (state, action, autonomous) -> TerminalLogisticsProcess.plan(state, action)),
            Map.entry("frontier.hive.growth.task.start", (state, action, autonomous) -> HiveGrowthProcess.planStart(state, action)),
            Map.entry("frontier.hive.growth.task.complete", (state, action, autonomous) -> HiveGrowthProcess.planCompletion(state, action)),
            Map.entry("frontier.hive.nutrient.transfer.progress", (state, action, autonomous) -> HiveNutrientTransferProcess.plan(state, action)),
            Map.entry("frontier.population.birth.review", (state, action, autonomous) -> PopulationBirthProcess.planReview(state, action)),
            Map.entry("frontier.population.birth.complete", (state, action, autonomous) -> PopulationBirthProcess.planCompletion(state, action)),
            Map.entry("frontier.population.migration.review", (state, action, autonomous) -> PopulationMigrationProcess.planReview(state, action)),
            Map.entry("frontier.population.migration.progress", (state, action, autonomous) -> PopulationMigrationProcess.planProgress(state, action)),
            Map.entry(DefenderEquipmentProcess.REVIEW_ACTION, (state, action, autonomous) -> DefenderEquipmentProcess.plan(state, action)),
            Map.entry(DefenderEquipmentReturnProcess.REVIEW_ACTION, (state, action, autonomous) -> DefenderEquipmentReturnProcess.plan(state, action)),
            Map.entry("frontier.settlement.provision.review", (state, action, autonomous) -> SettlementProvisionProcess.planReview(state, action)),
            Map.entry("frontier.settlement.provision.progress", (state, action, autonomous) -> SettlementProvisionProcess.planProgress(state, action)),
            Map.entry("frontier.company.foundation.review", (state, action, autonomous) -> CompanyFoundationProcess.plan(state, action)),
            Map.entry("frontier.market.clear", (state, action, autonomous) -> MarketClearingProcess.plan(state, action)),
            Map.entry("frontier.resource_site.growth", (state, action, autonomous) -> ResourceSiteProcess.planGrowth(state, action)),
            Map.entry("frontier.resource_site.prepare", (state, action, autonomous) -> ResourceSiteProcess.planPreparation(state, action)),
            Map.entry("frontier.resource_site.harvest", (state, action, autonomous) -> ResourceSiteHarvestProcess.plan(state, action)),
            Map.entry("frontier.objective.resource_harvest", (state, action, autonomous) -> StrategicObjectiveProcess.planResourceHarvestOpportunity(state, action)),
            Map.entry("frontier.structural_repair.scan", (state, action, autonomous) -> StructuralRepairProcess.plan(state, action)),
            Map.entry("frontier.route_construction.scan", (state, action, autonomous) -> RouteConstructionProcess.plan(state, action)),
            Map.entry("frontier.route_construction.start", (state, action, autonomous) -> RouteConstructionProcess.planStart(state, action)),
            Map.entry("frontier.route_construction.assembly_progress", (state, action, autonomous) -> RouteConstructionProcess.planAssemblyProgress(state, action)),
            Map.entry("frontier.route_maintenance.scan", (state, action, autonomous) -> RouteMaintenanceProcess.plan(state, action)),
            Map.entry("frontier.route_maintenance.assembly_progress", (state, action, autonomous) -> RouteMaintenanceProcess.planAssemblyProgress(state, action)),
            Map.entry("frontier.route_patrol.start", (state, action, autonomous) -> RoutePatrolProcess.planStart(state, action)),
            Map.entry("frontier.route_patrol.progress", (state, action, autonomous) -> RoutePatrolProcess.planProgress(state, action)),
            Map.entry("frontier.hive_route_engagement.start", (state, action, autonomous) -> HiveRouteEngagementProcess.planStart(state, action)),
            Map.entry("frontier.hive_route_engagement.progress", (state, action, autonomous) -> HiveRouteEngagementProcess.planProgress(state, action)),
            Map.entry("frontier.hive_route_engagement.readiness", (state, action, autonomous) -> HiveRouteEngagementProcess.planReadiness(state, action)),
            Map.entry("frontier.hive_route_engagement.combat", (state, action, autonomous) -> HiveRouteEngagementProcess.planCombat(state, action)),
            Map.entry("frontier.hive.scout.patrol", (state, action, autonomous) -> HiveScoutPatrolProcess.plan(state, action)),
            Map.entry("frontier.decontamination.scan", (state, action, autonomous) -> DecontaminationProcess.plan(state, action)),
            Map.entry("frontier.objective.review", StrategicObjectiveProcess::plan),
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
                descriptor("kernel-schedule", Set.of(), Set.of(), Set.of(), emissions("kernel-schedule"), KERNEL),
                descriptor("physical-observation", physicalCommands(), Set.of(), PHYSICAL, emissions("physical-observation"), PHYSICAL),
                descriptor("ambient-actors", ambientCommands(), Set.of(), AMBIENT, emissions("ambient-actors"), AMBIENT),
                descriptor("logistics-scenes", logisticsCommands(), logisticsSchedules(), LOGISTICS, emissions("logistics-scenes"), LOGISTICS),
                descriptor("population", populationCommands(), populationSchedules(), POPULATION, emissions("population"), POPULATION),
                descriptor("economy", Set.of(), economySchedules(), ECONOMY, emissions("economy"), ECONOMY),
                descriptor("resource-sites", resourceCommands(), resourceSchedules(), RESOURCE_SITES, emissions("resource-sites"), RESOURCE_SITES),
                descriptor("hive", hiveCommands(), hiveSchedules(), HIVE, emissions("hive"), HIVE),
                descriptor("infrastructure", Set.of(), infrastructureSchedules(), INFRASTRUCTURE, emissions("infrastructure"), INFRASTRUCTURE),
                descriptor("strategy", strategyCommands(), strategySchedules(), STRATEGY, emissions("strategy"), STRATEGY));
    }

    public static Set<String> allWorldPayloadTypes() { return ALL_WORLD; }

    /** Exposed only for deterministic composition tests; this is not a plugin registry. */
    public static Set<String> executableModuleIds() { return MODULES.keySet(); }

    public static Set<String> scheduledKinds() { return SCHEDULED_PLANNERS.keySet(); }

    /** Routes only through the module selected by the registry's exact command-type owner. */
    public static CommandPlan planCommand(String processId, FrontierWorldState state, FrontierCommand command) {
        return module(processId).planCommand(state, command);
    }

    /** Routes only through the module selected by the registry's exact event-type owner. */
    public static FrontierWorldState reduce(String processId, FrontierWorldState state, FrontierEvent event) {
        return module(processId).reduce(state, event);
    }

    /** Bootstrap is data-only; this catalog owns the finite initial process schedule. */
    public static List<ScheduledAction> initialSchedule(FrontierBootstrap bootstrap) {
        FrontierRuleset.Cadence cadence = bootstrap.ruleset().cadence();
        List<ScheduledAction> actions = new java.util.ArrayList<>(List.of(StructuralRepairProcess.scan(1, cadence.structuralRepairInitialScanTick()),
                RouteConstructionProcess.scan(1, cadence.routeConstructionInitialScanTick()), RouteMaintenanceProcess.scan(1, cadence.routeConstructionInitialScanTick()),
                DecontaminationProcess.scan(1, cadence.decontaminationInitialScanTick())));
        for (int index = 0; index < bootstrap.settlements().size(); index++) {
            actions.add(StrategicObjectiveProcess.review(bootstrap.settlements().get(index).id(), 1,
                    cadence.settlementStrategicInitialReviewTick() + index * cadence.settlementInitialStagger()));
            actions.add(PopulationBirthProcess.review(bootstrap.settlements().get(index).id(), 1,
                    cadence.populationBirthInitialReviewTick() + index * cadence.settlementInitialStagger()));
            actions.add(SettlementProvisionProcess.review(bootstrap.settlements().get(index).id(), 1,
                    cadence.provisionInitialReviewTick() + index * cadence.settlementInitialStagger()));
            actions.add(CompanyFoundationProcess.review(bootstrap.settlements().get(index).id(), 1,
                    cadence.companyFoundationInitialReviewTick() + index * cadence.settlementInitialStagger()));
        }
        actions.add(PopulationMigrationProcess.review(1, cadence.populationMigrationInitialReviewTick()));
        actions.add(TerminalLogisticsProcess.review(1, cadence.terminalLogisticsInitialReviewTick()));
        FrontierResourceSitePlan.compile(bootstrap).keySet().stream().sorted()
                .forEach(site -> actions.add(ResourceSiteProcess.preparation(site, cadence.resourceInitialPreparationTick())));
        bootstrap.hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).sorted(java.util.Comparator.comparing(Bioform::id))
                .forEach(scout -> actions.add(HiveScoutPatrolProcess.patrol(scout.id(), 1,
                        cadence.hiveScoutInitialPatrolTick() + actions.size() * cadence.hiveScoutInitialStagger())));
        actions.add(StrategicObjectiveProcess.review(bootstrap.hive().id(), 1, cadence.hiveStrategicInitialReviewTick()));
        return List.copyOf(actions);
    }

    public static List<ProposedEvent> planScheduled(DeterministicProcessRegistry registry, FrontierWorldState state,
                                                     ScheduledAction action, boolean autonomousInterception) {
        String processId = registry.requireScheduledOwner(action.kind());
        ScheduledPlanner planner = SCHEDULED_PLANNERS.get(action.kind());
        if (planner == null) throw new IllegalStateException("registered scheduled kind has no planner: " + action.kind());
        List<ProposedEvent> planned = planner.plan(state, action, autonomousInterception);
        List<ProposedEvent> result = planned.isEmpty()
                ? List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())))
                : planned;
        return registry.validateEmissions(processId, result);
    }

    private static DeterministicProcessDescriptor descriptor(String id, Set<String> commands, Set<String> schedules,
                                                              Set<String> events, Set<String> emissions, Set<String> codecs) {
        return new DeterministicProcessDescriptor(id, commands, schedules, events, emissions, codecs);
    }

    private static Set<String> physicalCommands() { return types(
            "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
            "frontier.physical_delta_observed", "frontier.resource_deposited", "frontier.exact_item_custody_changed",
            "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed", "frontier.container_surface_transition",
            "frontier.cargo_carrier_released"); }
    private static Set<String> ambientCommands() { return types(
            "frontier.ambient_actor_died", "frontier.ambient_actor_observed", "frontier.ambient_lease_prepared",
            "frontier.ambient_lease_released", "frontier.ambient_lease_transition", "frontier.ambient_lease_restart_absence_observed"); }
    private static Set<String> logisticsCommands() { return types(
            "frontier.operation_assembly_advanced", "frontier.operation_assembly_deferred",
            "frontier.operation_travel_segment_completed", "frontier.operation_travel_advanced", "frontier.operation_travel_started",
            "frontier.scene_lease_prepared", "frontier.scene_lease_handoff", "frontier.scene_lease_transition",
            "frontier.scene_lease_released_v2", "frontier.scene_lease_recovery_unresolved", "frontier.actor_died",
            "frontier.settlement_assault_scene_lease_prepared", "frontier.settlement_assault_scene_lease_handoff",
            "frontier.engineering_work_scene_lease_prepared", "frontier.engineering_work_scene_lease_handoff"); }
    private static Set<String> populationCommands() { return types(
            "frontier.resident_born", "frontier.resident_migrated", "frontier.resident_transit_advanced",
            "frontier.medical_treatment_scene_lease_prepared", "frontier.medical_treatment_scene_lease_handoff"); }
    private static Set<String> resourceCommands() { return Set.of("frontier.resource_site_conflict_observed"); }
    private static Set<String> hiveCommands() { return types("frontier.hot_scout_operation_observed", "frontier.scout_patrol_advanced", "frontier.scout_patrol_lease_recovered"); }
    private static Set<String> strategyCommands() { return Set.of(); }

    private static Set<String> logisticsSchedules() { return types(
            "frontier.supply.task.start", "frontier.supply.cargo.load", "frontier.operation.assembly",
            "frontier.operation.progress", "frontier.terminal_logistics.retention", "frontier.hive_route_engagement.start",
            "frontier.hive_route_engagement.progress", "frontier.hive_route_engagement.readiness",
            "frontier.hive_route_engagement.combat", "frontier.settlement_assault.start",
            "frontier.settlement_assault.progress", "frontier.settlement_assault.combat"); }
    private static Set<String> populationSchedules() { return types(
            "frontier.population.birth.review", "frontier.population.birth.complete", "frontier.population.migration.review",
            "frontier.population.migration.progress", DefenderEquipmentProcess.REVIEW_ACTION, DefenderEquipmentReturnProcess.REVIEW_ACTION,
            "frontier.settlement.provision.review", "frontier.settlement.provision.progress"); }
    private static Set<String> economySchedules() { return types(
            "frontier.settlement.production.task.start", "frontier.settlement.production.task.complete",
            "frontier.company.foundation.review", "frontier.market.clear"); }
    private static Set<String> resourceSchedules() { return types(
            "frontier.resource_site.growth", "frontier.resource_site.prepare", "frontier.resource_site.harvest",
            "frontier.objective.resource_harvest"); }
    private static Set<String> hiveSchedules() { return types(
            "frontier.hive.infection.task", "frontier.hive.growth.task.start", "frontier.hive.growth.task.complete",
            "frontier.hive.nutrient.transfer.progress", "frontier.hive.scout.patrol"); }
    private static Set<String> infrastructureSchedules() { return types(
            "frontier.structural_repair.scan", "frontier.route_construction.scan", "frontier.route_construction.start",
            "frontier.route_construction.assembly_progress", "frontier.route_maintenance.scan", "frontier.route_maintenance.assembly_progress",
            "frontier.route_patrol.start", "frontier.route_patrol.progress", "frontier.decontamination.scan"); }
    private static Set<String> strategySchedules() { return types(
            "frontier.objective.review", "frontier.objective.reconsider", "frontier.objective.interrupt", "frontier.objective.assault"); }

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
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.physical_delta_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
                    "frontier.resource_deposited", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed",
                    "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.route_construction_material_loaded",
                    "frontier.resident_born", "frontier.resident_migrated",
                    "frontier.resident_migration_started", "frontier.resident_migration_advanced", "frontier.resident_transit_advanced", "frontier.resident_migration_blocked",
                    "frontier.resident_migration_resumed", "frontier.resident_birth_started", "frontier.resident_birth_cancelled", "frontier.settlement_provision_started",
                    "frontier.settlement_provision_started_v2", "frontier.settlement_provision_consumed", "frontier.settlement_provision_resolved",
                    "frontier.resident_health_transition", "frontier.settlement_quarantine_transition", "frontier.medical_treatment_started",
                    "frontier.medical_treatment_transition", "frontier.company_registered", "frontier.employment_contract_opened",
                    "frontier.employment_contract_terminated", "frontier.market_demand_opened", "frontier.market_quote_published", "frontier.market_work_order_accepted",
                    "frontier.market_work_order_cancelled", "frontier.market_demand_expired", "frontier.market_demand_cancelled", "frontier.production_started",
                    "frontier.production_completed", "frontier.production_blocked", "frontier.settlement_infection_observed", "frontier.strategic_objective_selected",
                    "frontier.strategic_task_planned", "frontier.strategic_task_transition");
            case "ambient-actors" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.ambient_actor_died", "frontier.ambient_actor_observed", "frontier.ambient_lease_prepared", "frontier.ambient_lease_released",
                    "frontier.ambient_lease_transition", "frontier.ambient_lease_restart_absence_observed", "frontier.company_registered", "frontier.employment_contract_opened", "frontier.employment_contract_terminated",
                    "frontier.market_demand_opened", "frontier.market_quote_published", "frontier.market_work_order_accepted", "frontier.market_work_order_cancelled",
                    "frontier.market_demand_expired", "frontier.market_demand_cancelled", "frontier.production_started", "frontier.production_completed", "frontier.production_blocked");
            case "logistics-scenes" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.supply_contract_created", "frontier.supply_contract_abandoned", "frontier.cargo_loaded", "frontier.cargo_delivered", "frontier.operation_created",
                    "frontier.operation_advanced", "frontier.operation_assembly_advanced", "frontier.operation_assembly_deferred", "frontier.operation_travel_started",
                    "frontier.operation_travel_advanced", "frontier.operation_travel_segment_completed", "frontier.operation_cold_suspended", "frontier.operation_failed",
                    "frontier.terminal_logistics_compacted", "frontier.scene_lease_prepared", "frontier.scene_lease_handoff", "frontier.scene_lease_transition",
                    "frontier.scene_lease_released_v2", "frontier.scene_lease_recovery_unresolved", "frontier.actor_died", "frontier.settlement_assault_scene_lease_prepared",
                    "frontier.settlement_assault_scene_lease_handoff", "frontier.engineering_work_scene_lease_prepared", "frontier.engineering_work_scene_lease_handoff",
                    "frontier.physical_delta_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition",
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
                    "frontier.route_engagement_resolved", "frontier.settlement_assault_started", "frontier.settlement_assault_attacker_advanced",
                    "frontier.settlement_assault_transition", "frontier.settlement_assault_strike", "frontier.settlement_assault_resolved");
            case "population" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.resident_born", "frontier.resident_migrated", "frontier.resident_migration_started", "frontier.resident_migration_advanced",
                    "frontier.resident_transit_advanced", "frontier.resident_migration_blocked", "frontier.resident_migration_resumed", "frontier.resident_birth_started",
                    "frontier.resident_birth_cancelled", "frontier.settlement_provision_started", "frontier.settlement_provision_started_v2", "frontier.settlement_provision_consumed",
                    "frontier.settlement_provision_resolved", "frontier.resident_health_transition", "frontier.settlement_quarantine_transition",
                    "frontier.medical_treatment_started", "frontier.medical_treatment_transition",
                    "frontier.medical_treatment_scene_lease_prepared", "frontier.medical_treatment_scene_lease_handoff", "frontier.physical_delta_observed",
                    "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged", "frontier.resource_deposited",
                    "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed", "frontier.container_surface_transition",
                    "frontier.cargo_carrier_released");
            case "economy" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.company_registered", "frontier.employment_contract_opened", "frontier.employment_contract_terminated", "frontier.market_demand_opened",
                    "frontier.market_quote_published", "frontier.market_work_order_accepted", "frontier.market_work_order_cancelled", "frontier.market_demand_expired",
                    "frontier.market_demand_cancelled", "frontier.production_started", "frontier.production_completed", "frontier.production_blocked", "frontier.production_interrupted",
                    "frontier.physical_delta_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
                    "frontier.resource_deposited", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed",
                    "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.settlement_infection_observed", "frontier.strategic_objective_selected",
                    "frontier.strategic_task_planned", "frontier.strategic_task_transition");
            case "resource-sites" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.resource_site_growth_advanced", "frontier.resource_site_preparation_started", "frontier.resource_site_prepared",
                    "frontier.resource_site_harvest_started", "frontier.resource_site_harvested", "frontier.resource_site_conflict_observed",
                    "frontier.physical_delta_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
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
                    "frontier.route_engagement_transition", "frontier.route_engagement_strike", "frontier.route_engagement_resolved", "frontier.settlement_assault_started",
                    "frontier.settlement_assault_attacker_advanced", "frontier.settlement_assault_transition", "frontier.settlement_assault_strike", "frontier.settlement_assault_resolved",
                    "frontier.production_interrupted",
                    "frontier.physical_delta_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
                    "frontier.resource_deposited", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed",
                    "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.settlement_infection_observed", "frontier.strategic_objective_selected",
                    "frontier.strategic_task_planned", "frontier.strategic_task_transition");
            case "infrastructure" -> types(
                    "kernel.schedule_created", "kernel.schedule_cancelled", "kernel.schedule_consumed", "kernel.schedule_rescheduled",
                    "frontier.route_construction_started", "frontier.route_construction_material_loaded", "frontier.route_topology_cutover",
                    "frontier.route_construction_assembly_started", "frontier.route_construction_assembly_advanced",
                    "frontier.route_maintenance_started", "frontier.route_maintenance_material_loaded",
                    "frontier.route_maintenance_assembly_started", "frontier.route_maintenance_assembly_advanced",
                    "frontier.route_patrol_started", "frontier.route_patrol_advanced", "frontier.route_patrol_obstruction_confirmed", "frontier.route_patrol_failed",
                    "frontier.physical_delta_observed", "frontier.physical_intent_prepared", "frontier.physical_intent_transition", "frontier.structure_damaged",
                    "frontier.resource_deposited", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed", "frontier.inventory_conflict_observed",
                    "frontier.container_surface_transition", "frontier.cargo_carrier_released", "frontier.resident_born", "frontier.resident_migrated",
                    "frontier.resident_migration_started", "frontier.resident_migration_advanced", "frontier.resident_transit_advanced", "frontier.resident_migration_blocked",
                    "frontier.resident_migration_resumed", "frontier.resident_birth_started", "frontier.resident_birth_cancelled", "frontier.settlement_provision_started",
                    "frontier.settlement_provision_started_v2", "frontier.settlement_provision_consumed", "frontier.settlement_provision_resolved",
                    "frontier.resident_health_transition", "frontier.settlement_quarantine_transition", "frontier.medical_treatment_started", "frontier.medical_treatment_transition", "frontier.settlement_infection_observed",
                    "frontier.strategic_objective_selected", "frontier.strategic_task_planned", "frontier.strategic_task_transition");
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
                    "frontier.route_engagement_transition", "frontier.route_engagement_strike", "frontier.route_engagement_resolved", "frontier.settlement_assault_started",
                    "frontier.settlement_assault_attacker_advanced", "frontier.settlement_assault_transition", "frontier.settlement_assault_strike", "frontier.settlement_assault_resolved",
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
    @SafeVarargs private static Set<String> union(Set<String>... values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Set<String> value : values) result.addAll(value);
        return Set.copyOf(result);
    }
}
