package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Closed nominal contract for every persisted Frontier payload kind.
 *
 * <p>The contract deliberately has no class-name, status, identifier, payload-shape or
 * exception inference. Adding a codec kind without making an explicit declaration here makes
 * process composition fail before an engine can reduce it. A diagnostic declaration names its
 * only admitted reason(s); it never chooses one.</p>
 */
public final class DiagnosticProducerContract {
    private enum Admission { ORDINARY, REQUIRED_TUPLE, PAYLOAD_VALIDATES_CONDITIONAL_TUPLE, OWNER_STAMPS_TRANSITION }

    private record Entry(Admission admission, Set<DiagnosticReason> reasons) {
        private Entry {
            reasons = Set.copyOf(reasons);
            if (admission == Admission.ORDINARY && !reasons.isEmpty()) throw new IllegalArgumentException("ordinary payload cannot name diagnostic reasons");
            if (admission != Admission.ORDINARY && reasons.isEmpty()) throw new IllegalArgumentException("diagnostic payload must name its admitted reasons");
        }
    }

    private static final Map<String, Entry> DIAGNOSTIC = Map.ofEntries(
            diagnostic("frontier.resource_site_conflict_observed", resourceSiteReasons(), Admission.REQUIRED_TUPLE),
            diagnostic("frontier.hive_growth_blocked", DiagnosticReason.HIVE_GROWTH_BLOCKED, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.hive_mobilization_conflicted", DiagnosticReason.HIVE_MOBILIZATION_CONFLICT, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.hive_nutrient_transfer_blocked", DiagnosticReason.HIVE_NUTRIENT_BLOCKED, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.route_patrol_blocked", DiagnosticReason.ROUTE_PATROL_BLOCKED, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.route_patrol_failed", DiagnosticReason.ROUTE_PATROL_MEMBER_LOST, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.production_blocked", DiagnosticReason.PRODUCTION_BLOCKED, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.resident_migration_blocked", DiagnosticReason.RESIDENT_MIGRATION_BLOCKED, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.operation_failed", DiagnosticReason.OPERATION_FAILED, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.inventory_conflict_observed", DiagnosticReason.INVENTORY_CONFLICT, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.physical_replica_conflict_observed", DiagnosticReason.REPLICA_CUSTODY_CONFLICT, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.physical_custody_unresolved", DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.projection_conflict_observed", DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.fenced_recovery_ambiguous", DiagnosticReason.FENCED_RECOVERY_AMBIGUOUS, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.kernel_quarantine_observed", kernelReasons(), Admission.REQUIRED_TUPLE),
            diagnostic("frontier.scene_lease_recovery_unresolved", DiagnosticReason.SCENE_LEASE_RECOVERY_UNRESOLVED, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.ambient_lease_restart_absence_observed", DiagnosticReason.AMBIENT_LEASE_RESTART_ABSENCE, Admission.REQUIRED_TUPLE),
            diagnostic("frontier.settlement_provision_resolved", DiagnosticReason.SETTLEMENT_PROVISION_CONFLICT, Admission.PAYLOAD_VALIDATES_CONDITIONAL_TUPLE),
            diagnostic("frontier.fungible_resource_handoff_observed", DiagnosticReason.SETTLEMENT_PROVISION_CONFLICT, Admission.PAYLOAD_VALIDATES_CONDITIONAL_TUPLE),
            diagnostic("frontier.settlement_assault_transition", DiagnosticReason.SETTLEMENT_ASSAULT_CONFLICT, Admission.PAYLOAD_VALIDATES_CONDITIONAL_TUPLE),
            diagnostic("frontier.physical_intent_transition", DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED, Admission.OWNER_STAMPS_TRANSITION));

    private static final Set<String> ORDINARY = Set.of(
        "frontier.goods_order_placed", "frontier.goods_trade_reserved", "frontier.goods_trade_accepted", "frontier.goods_trade_cancelled",
        "frontier.goods_trade_claim_partitioned",
        "frontier.goods_trade_retired",
        "frontier.cargo_cleanup_saved",
        "frontier.ambient_body_confirmed", "frontier.ambient_lease_prepared",
        "frontier.ambient_lease_released", "frontier.ambient_lease_transition",
        "frontier.cargo_carrier_released", "frontier.cargo_delivered", "frontier.cargo_loaded", "frontier.company_registered",
        "frontier.container_surface_transition", "frontier.deferred_aftermath_prepared", "frontier.deferred_aftermath_resolved",
        "frontier.employment_contract_opened", "frontier.employment_contract_terminated", "frontier.engineering_work_scene_lease_handoff",
        "frontier.engineering_work_scene_lease_prepared", "frontier.exact_item_custody_changed", "frontier.exact_item_destroyed",
        "frontier.fenced_recovery_abandoned", "frontier.fenced_recovery_confirmed",
        "frontier.fenced_recovery_observed", "frontier.fenced_recovery_prepared", "frontier.fenced_recovery_revoked_to_cold",
        "frontier.fenced_recovery_running", "frontier.fungible_production_completed",
        "frontier.fungible_stack_bindings_released", "frontier.fungible_stack_layout_observed", "frontier.fungible_stock_departure_observed", "frontier.fungible_stock_contribution_observed", "frontier.hive_doctrine_selected",
        "frontier.hive_growth_biomass_consumed", "frontier.hive_growth_completed", "frontier.hive_growth_started",
        "frontier.hive_mobilization_assembly_advanced", "frontier.hive_mobilization_cocoon_released", "frontier.hive_mobilization_departed",
        "frontier.hive_mobilization_release_started", "frontier.hive_mobilization_return_advanced", "frontier.hive_mobilization_started",
        "frontier.hive_nutrient_transfer_advanced", "frontier.hive_nutrient_transfer_completed", "frontier.hive_nutrient_transfer_endpoint_prepared",
        "frontier.hive_nutrient_transfer_started", "frontier.hive_operation_observed", "frontier.hive_settlement_observed",
        "frontier.hive_territory_observed", "frontier.hot_scout_operation_observed", "frontier.infection_changed", "frontier.market_demand_cancelled",
        "frontier.market_demand_expired", "frontier.market_demand_opened", "frontier.market_quote_published",
        "frontier.market_relationship_incident_recorded", "frontier.market_work_order_accepted", "frontier.market_work_order_cancelled",
        "frontier.medical_treatment_scene_lease_handoff", "frontier.medical_treatment_scene_lease_prepared", "frontier.medical_treatment_started",
        "frontier.medical_treatment_transition", "frontier.operation_advanced", "frontier.operation_assembly_advanced",
        "frontier.operation_assembly_deferred", "frontier.operation_cold_suspended", "frontier.operation_created",
        "frontier.operation_travel_advanced", "frontier.operation_travel_segment_completed", "frontier.operation_travel_started",
        "frontier.physical_custody_acquired", "frontier.physical_custody_checkpointed", "frontier.physical_custody_released",
        "frontier.projection_custody_prepared", "frontier.projection_custody_confirmed",
        "frontier.reference_projection_prepared", "frontier.reference_surface_verified",
        "frontier.physical_delta_observed", "frontier.physical_deltas_observed", "frontier.physical_intent_prepared",
        "frontier.physical_replica_declared", "frontier.physical_replica_emitted", "frontier.physical_replica_observed", "frontier.reference_mutation_closed",
        "frontier.production_cold_work_advanced", "frontier.bakery_cold_step", "frontier.bakery_input_reallocated",
        "frontier.production_completed", "frontier.production_interrupted", "frontier.production_started",
        "frontier.production_work_progressed", "frontier.production_work_scene_finalized", "frontier.production_work_scene_lease_handoff",
        "frontier.production_work_scene_lease_prepared", "frontier.production_work_scene_preparation_aborted", "frontier.production_work_traversal_advanced",
        "frontier.production_work_traversal_blocked", "frontier.bakery_hot_effect_prepared",
        "frontier.bakery_hot_effect_observed", "frontier.bakery_hot_work_tick", "frontier.bakery_hot_hand_release",
        "frontier.bakery_hot_hand_materialized", "frontier.bakery_hot_block_changed", "frontier.bakery_scene_reconciled", "frontier.resident_birth_cancelled", "frontier.resident_birth_started", "frontier.resident_born",
        "frontier.resident_health_transition", "frontier.resident_starvation_integrated", "frontier.resident_need_integrated", "frontier.resident_metabolism_changed", "frontier.resident_work_modifiers_changed",
        "frontier.actor_movement_cold_advanced", "frontier.actor_movement_hot_observed", "frontier.actor_movement_interrupted", "frontier.actor_movement_started",
        "frontier.resident_meal_started", "frontier.resident_meal_cold_step", "frontier.resident_meal_hot_arrived",
        "frontier.resident_meal_hot_effect_prepared", "frontier.resident_meal_hot_effect_observed",
        "frontier.resident_meal_resource_effect_observed",
        "frontier.resident_meal_portion_disposition_observed",
        "frontier.resident_meal_hot_hand_materialized", "frontier.resident_meal_hot_hand_released",
        "frontier.resident_meal_hot_access_cleared", "frontier.resident_migrated", "frontier.resident_migration_advanced", "frontier.resident_migration_rejoin_advanced",
        "frontier.resident_migration_resumed", "frontier.resident_migration_started", "frontier.resident_transit_advanced", "frontier.resource_deposited",
        "frontier.resource_field_cell_observed", "frontier.resource_field_work_access_observed", "frontier.resource_field_world_change_held", "frontier.resource_field_world_change_acknowledged",
        "frontier.resource_field_foreign_change_held", "frontier.resource_field_foreign_cell_observed",
        "frontier.resource_field_foreign_change_acknowledged", "frontier.resource_field_player_break_prepared",
        "frontier.resource_site_growth_advanced", "frontier.resource_site_harvest_cold_traversal_advanced", "frontier.resource_site_harvest_cold_goal_advanced", "frontier.resource_site_harvest_cold_goal_held",
                "frontier.resource_site_harvest_returned", "frontier.resource_site_harvest_crop_prepared",
        "frontier.resource_site_harvest_hot_traversal_advanced", "frontier.resource_site_harvest_hot_goal_arrived", "frontier.resource_site_harvest_progressed", "frontier.resource_site_harvest_work_acknowledged",
                "frontier.resource_site_harvest_scene_reconciled", "frontier.resource_site_harvest_hand_projected",
                "frontier.resource_site_harvest_hand_release", "frontier.resource_site_harvest_segment_renewed",
                "frontier.resource_site_harvest_blocked_cell_skipped", "frontier.resource_site_harvest_immature_cell_skipped", "frontier.resource_site_harvest_work_changed",
                "frontier.resource_site_harvest_target_retargeted", "frontier.resource_site_harvest_route_blocked",
                "frontier.resource_site_harvest_route_cleared", "frontier.resource_site_harvest_batch_prepared", "frontier.resource_site_harvest_batch_delivered",
                "frontier.resource_site_harvest_scene_lease_handoff",
        "frontier.resource_site_harvest_scene_lease_prepared", "frontier.resource_site_harvest_scene_preparation_aborted", "frontier.resource_site_harvest_started", "frontier.resource_site_preparation_started",
        "frontier.resource_site_prepared", "frontier.route_construction_assembly_advanced", "frontier.route_construction_assembly_started",
        "frontier.route_construction_material_loaded", "frontier.route_construction_started", "frontier.route_engagement_attacker_advanced",
        "frontier.route_engagement_command_authority_changed", "frontier.route_engagement_resolved", "frontier.route_engagement_started",
        "frontier.route_engagement_strike", "frontier.route_engagement_transition", "frontier.route_maintenance_assembly_advanced",
        "frontier.route_maintenance_assembly_started", "frontier.route_maintenance_closed", "frontier.route_maintenance_material_loaded",
        "frontier.route_maintenance_started", "frontier.route_patrol_formation_advanced", "frontier.route_patrol_formation_observed",
        "frontier.route_patrol_obstruction_confirmed", "frontier.route_patrol_scene_lease_handoff", "frontier.route_patrol_scene_lease_prepared",
        "frontier.route_patrol_started", "frontier.route_topology_cutover", "frontier.scene_lease_handoff", "frontier.scene_lease_prepared",
        "frontier.scene_lease_recovery_revoked", "frontier.scene_lease_released_v2", "frontier.scene_lease_transition", "frontier.scout_patrol_advanced",
        "frontier.scout_patrol_started", "frontier.settlement_assault_attacker_advanced", "frontier.settlement_assault_formation_observed",
        "frontier.settlement_assault_march_issue_observed", "frontier.settlement_assault_resolved", "frontier.settlement_assault_scene_lease_handoff",
        "frontier.settlement_assault_scene_lease_prepared", "frontier.settlement_assault_started", "frontier.settlement_assault_strike",
        "frontier.settlement_infection_observed", "frontier.settlement_provision_consumed", "frontier.settlement_provision_started",
        "frontier.settlement_provision_started_v2", "frontier.settlement_quarantine_transition", "frontier.settlement_service_work_progressed",
        "frontier.settlement_service_work_scene_lease_handoff", "frontier.settlement_service_work_scene_lease_prepared",
        "frontier.settlement_service_work_started", "frontier.settlement_service_work_traversal_advanced", "frontier.settlement_service_work_traversal_blocked",
        "frontier.strategic_objective_selected", "frontier.strategic_task_planned", "frontier.strategic_task_transition", "frontier.structure_damaged",
        "frontier.supply_contract_abandoned", "frontier.supply_contract_created", "frontier.terminal_logistics_compacted",
        "frontier.actor_execution_resumed",
        "frontier.actor_presence_started",
        "frontier.actor_body_released",
        "frontier.actor_body_unloaded",
        "frontier.actor_body_present",
        "frontier.actor_body_inspected",
        "frontier.actor_body_died",
        "kernel.schedule_consumed", "kernel.schedule_rescheduled", "kernel.schedule_created", "kernel.schedule_cancelled");
    /** SavedData quarantine is a separately persisted producer, explicitly bridged rather than inferred from an adapter status. */
    private static final Set<DiagnosticReason> OUT_OF_BAND = Set.of(DiagnosticReason.FRONTIER_QUARANTINE);

    private DiagnosticProducerContract() { }

    public static void requireCompleteInventory(PayloadCodecs codecs) {
        Set<String> declared = new LinkedHashSet<>(ORDINARY);
        declared.addAll(DIAGNOSTIC.keySet());
        if (!declared.equals(codecs.types())) {
            Set<String> missing = new LinkedHashSet<>(codecs.types()); missing.removeAll(declared);
            Set<String> stale = new LinkedHashSet<>(declared); stale.removeAll(codecs.types());
            throw new IllegalArgumentException("diagnostic producer contract must declare every payload codec; missing=" + missing + " stale=" + stale);
        }
    }

    public static void requireAdmitted(FrontierPayload payload) {
        Entry entry = DIAGNOSTIC.get(payload.type());
        if (entry == null) {
            if (!ORDINARY.contains(payload.type())) throw new IllegalArgumentException("payload kind is absent from the diagnostic producer contract: " + payload.type());
            return;
        }
        var tuple = DiagnosticIncidentExtractor.tuple(payload);
        if (entry.admission == Admission.REQUIRED_TUPLE && tuple.isEmpty()) {
            throw new IllegalArgumentException("diagnostic producer omitted its required tuple: " + payload.type());
        }
        if (tuple.isPresent() && !entry.reasons.contains(tuple.orElseThrow().reason())) {
            throw new IllegalArgumentException("diagnostic producer supplied a foreign nominal reason: " + payload.type());
        }
    }

    public static Set<DiagnosticReason> retainedReasons() {
        EnumSet<DiagnosticReason> values = EnumSet.noneOf(DiagnosticReason.class);
        DIAGNOSTIC.values().forEach(entry -> values.addAll(entry.reasons));
        values.addAll(OUT_OF_BAND);
        return Set.copyOf(values);
    }

    private static Map.Entry<String, Entry> diagnostic(String type, DiagnosticReason reason, Admission admission) {
        return Map.entry(type, new Entry(admission, Set.of(reason)));
    }
    private static Map.Entry<String, Entry> diagnostic(String type, Set<DiagnosticReason> reasons, Admission admission) {
        return Map.entry(type, new Entry(admission, reasons));
    }
    private static Set<DiagnosticReason> resourceSiteReasons() {
        return java.util.Arrays.stream(ResourceSiteDiagnosticProducer.values()).map(ResourceSiteDiagnosticProducer::diagnosticReason)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static Set<DiagnosticReason> kernelReasons() {
        return java.util.Arrays.stream(KernelQuarantineObserved.Producer.values()).map(KernelQuarantineObserved.Producer::diagnosticReason)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
