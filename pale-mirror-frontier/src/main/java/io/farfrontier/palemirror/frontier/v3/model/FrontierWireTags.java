package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stable, explicit persisted tags for every currently durable Frontier v3 enum.
 *
 * <p>The listed values deliberately preserve the historic byte assignments, but the mapping
 * is now a schema contract instead of an implication of Java declaration order.  New values
 * must reserve an explicit, never-reused tag here; removed tags remain reserved forever.</p>
 */
public final class FrontierWireTags {
    private static final Map<Class<?>, Map<Integer, ? extends Enum<?>>> BY_TYPE = Map.ofEntries(
            entry(ActorLifeStatus.class,
                    tag(0, ActorLifeStatus.ALIVE), tag(1, ActorLifeStatus.DEAD)),
            entry(AmbientGoalKind.class,
                    tag(0, AmbientGoalKind.WORK), tag(1, AmbientGoalKind.GUARD), tag(2, AmbientGoalKind.PATROL), tag(3, AmbientGoalKind.SCOUT_PATROL),
                    tag(4, AmbientGoalKind.TRANSIT), tag(5, AmbientGoalKind.OPERATION_ASSEMBLY)),
            entry(AmbientLeaseStatus.class,
                    tag(0, AmbientLeaseStatus.PREPARED), tag(1, AmbientLeaseStatus.HOT), tag(2, AmbientLeaseStatus.DRAINING), tag(3, AmbientLeaseStatus.CLOSED),
                    tag(4, AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)),
            entry(BioformRole.class,
                    tag(0, BioformRole.WORKER), tag(1, BioformRole.SCOUT), tag(2, BioformRole.GUARD), tag(3, BioformRole.BOMBER)),
            entry(CompanyPurpose.class,
                    tag(0, CompanyPurpose.WORKS)),
            entry(CompanyStatus.class,
                    tag(0, CompanyStatus.ACTIVE), tag(1, CompanyStatus.INSOLVENT), tag(2, CompanyStatus.DISSOLVED)),
            entry(ContainerSurfaceStatus.class,
                    tag(0, ContainerSurfaceStatus.UNMATERIALIZED), tag(1, ContainerSurfaceStatus.PREPARED), tag(2, ContainerSurfaceStatus.ACTIVE),
                    tag(3, ContainerSurfaceStatus.CONFLICT)),
            entry(ContractStatus.class,
                    tag(0, ContractStatus.ORDERED), tag(1, ContractStatus.LOADED), tag(2, ContractStatus.DELIVERED), tag(3, ContractStatus.INTERRUPTED)),
            entry(EconomicAccountStatus.class,
                    tag(0, EconomicAccountStatus.ACTIVE), tag(1, EconomicAccountStatus.INSOLVENT)),
            entry(EconomicOwnerKind.class,
                    tag(0, EconomicOwnerKind.SETTLEMENT_TREASURY), tag(1, EconomicOwnerKind.HIVE_COLLECTIVE), tag(2, EconomicOwnerKind.PUBLIC_INFRASTRUCTURE),
                    tag(3, EconomicOwnerKind.COMPANY), tag(4, EconomicOwnerKind.RESIDENT)),
            entry(EmploymentContractStatus.class,
                    tag(0, EmploymentContractStatus.ACTIVE), tag(1, EmploymentContractStatus.SUSPENDED), tag(2, EmploymentContractStatus.TERMINATED)),
            entry(EmploymentTerminationReason.class,
                    tag(0, EmploymentTerminationReason.DEATH)),
            entry(ExplosionItemImpact.Outcome.class,
                    tag(0, ExplosionItemImpact.Outcome.RETAINED), tag(1, ExplosionItemImpact.Outcome.TRANSFERRED), tag(2, ExplosionItemImpact.Outcome.DESTROYED),
                    tag(3, ExplosionItemImpact.Outcome.CONFLICT)),
            entry(GrayboxSemanticPart.class,
                    tag(0, GrayboxSemanticPart.FOUNDATION), tag(1, GrayboxSemanticPart.WALL), tag(2, GrayboxSemanticPart.ROOF), tag(3, GrayboxSemanticPart.HIVE_TISSUE),
                    tag(4, GrayboxSemanticPart.ROUTE_SURFACE), tag(5, GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE), tag(6, GrayboxSemanticPart.INFECTION_SURFACE)),
            entry(HiveDoctrine.class,
                    tag(0, HiveDoctrine.CONSOLIDATE), tag(1, HiveDoctrine.EXPAND), tag(2, HiveDoctrine.INTERDICT)),
            entry(HiveGrowthBlockReason.class,
                    tag(0, HiveGrowthBlockReason.BIOMASS_UNAVAILABLE), tag(1, HiveGrowthBlockReason.GROWTH_CAPACITY_UNAVAILABLE),
                    tag(2, HiveGrowthBlockReason.PHYSICAL_CONSUMPTION_UNKNOWN)),
            entry(HiveNutrientReceiptStatus.class,
                    tag(0, HiveNutrientReceiptStatus.STORED), tag(1, HiveNutrientReceiptStatus.CONSUMED)),
            entry(HiveNutrientTransferBlockReason.class,
                    tag(0, HiveNutrientTransferBlockReason.ENDPOINT_MATERIALIZED), tag(1, HiveNutrientTransferBlockReason.TARGET_SLOT_UNAVAILABLE),
                    tag(2, HiveNutrientTransferBlockReason.CARGO_CUSTODY_LOST)),
            entry(HiveNutrientTransferPhase.class,
                    tag(0, HiveNutrientTransferPhase.IN_TRANSIT), tag(1, HiveNutrientTransferPhase.BLOCKED), tag(2, HiveNutrientTransferPhase.DEPARTURE_PENDING),
                    tag(3, HiveNutrientTransferPhase.ARRIVAL_PENDING)),
            entry(HiveOrganKind.class,
                    tag(0, HiveOrganKind.HEART), tag(1, HiveOrganKind.BROOD), tag(2, HiveOrganKind.STORE)),
            entry(HumanCapability.class,
                    tag(0, HumanCapability.AGRICULTURE), tag(1, HumanCapability.EXTRACTION), tag(2, HumanCapability.INDUSTRY),
                    tag(3, HumanCapability.ENGINEERING), tag(4, HumanCapability.LOGISTICS), tag(5, HumanCapability.MEDICINE),
                    tag(6, HumanCapability.SECURITY), tag(7, HumanCapability.CIVIC)),
            entry(InventoryConflictKind.class,
                    tag(0, InventoryConflictKind.MISSING), tag(1, InventoryConflictKind.FOREIGN_OR_DUPLICATE)),
            entry(MarketDemandCancellationReason.class,
                    tag(0, MarketDemandCancellationReason.PRODUCTION_BLOCKED), tag(1, MarketDemandCancellationReason.TASK_NO_LONGER_PENDING)),
            entry(MarketDemandStatus.class,
                    tag(0, MarketDemandStatus.OPEN), tag(1, MarketDemandStatus.ORDERED), tag(2, MarketDemandStatus.FULFILLED), tag(3, MarketDemandStatus.CANCELLED),
                    tag(4, MarketDemandStatus.EXPIRED)),
            entry(MarketWorkOrderStatus.class,
                    tag(0, MarketWorkOrderStatus.ACCEPTED), tag(1, MarketWorkOrderStatus.FULFILLED), tag(2, MarketWorkOrderStatus.CANCELLED),
                    tag(3, MarketWorkOrderStatus.CONFLICT)),
            entry(OperationAssemblyDeferral.Reason.class,
                    tag(0, OperationAssemblyDeferral.Reason.LOADED_WORLD_OBSTRUCTION)),
            entry(PhysicalDeltaKind.class,
                    tag(0, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS), tag(1, PhysicalDeltaKind.UNKNOWN_SCAR)),
            entry(PhysicalIntentKind.class,
                    tag(0, PhysicalIntentKind.CARGO_HANDOFF), tag(1, PhysicalIntentKind.STRUCTURAL_REPAIR), tag(2, PhysicalIntentKind.ROUTE_CONSTRUCTION),
                    tag(3, PhysicalIntentKind.DECONTAMINATION), tag(4, PhysicalIntentKind.EXPLOSION), tag(5, PhysicalIntentKind.SCENE_STRIKE),
                    tag(6, PhysicalIntentKind.EXACT_ITEM_CONSUMPTION), tag(7, PhysicalIntentKind.RESOURCE_SITE_PREPARATION), tag(8, PhysicalIntentKind.RESOURCE_SITE_HARVEST),
                    tag(9, PhysicalIntentKind.PRODUCTION_TRANSFORMATION), tag(10, PhysicalIntentKind.CARGO_LOADING),
                    tag(11, PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING), tag(12, PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE),
                    tag(13, PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL), tag(14, PhysicalIntentKind.EQUIPMENT_ISSUE)),
            entry(PhysicalIntentStatus.class,
                    tag(0, PhysicalIntentStatus.PREPARED), tag(1, PhysicalIntentStatus.RUNNING), tag(2, PhysicalIntentStatus.CONFIRMED),
                    tag(3, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)),
            entry(PhysicalPostcondition.class,
                    tag(0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED), tag(1, PhysicalPostcondition.STRUCTURAL_REPAIR_OBSERVED),
                    tag(2, PhysicalPostcondition.ROUTE_CONSTRUCTION_OBSERVED), tag(3, PhysicalPostcondition.DECONTAMINATION_OBSERVED),
                    tag(4, PhysicalPostcondition.EXPLOSION_OBSERVED), tag(5, PhysicalPostcondition.SCENE_STRIKE_OBSERVED),
                    tag(6, PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED), tag(7, PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED),
                    tag(8, PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED), tag(9, PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED),
                    tag(10, PhysicalPostcondition.CARGO_LOADED_FROM_DEPOT_OBSERVED), tag(11, PhysicalPostcondition.ROUTE_CONSTRUCTION_MATERIAL_LOADED_OBSERVED),
                    tag(12, PhysicalPostcondition.HIVE_NUTRIENT_DEPARTED_OBSERVED), tag(13, PhysicalPostcondition.HIVE_NUTRIENT_ARRIVED_OBSERVED),
                    tag(14, PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED)),
            entry(ProductionBlockReason.class,
                    tag(0, ProductionBlockReason.INPUT_UNAVAILABLE), tag(1, ProductionBlockReason.OUTPUT_STORAGE_UNAVAILABLE), tag(2, ProductionBlockReason.FACILITY_UNAVAILABLE),
                    tag(3, ProductionBlockReason.WORKER_UNAVAILABLE), tag(4, ProductionBlockReason.FINANCE_UNAVAILABLE)),
            entry(ResidentHealthStatus.class,
                    tag(0, ResidentHealthStatus.HEALTHY), tag(1, ResidentHealthStatus.EXPOSED), tag(2, ResidentHealthStatus.INFECTED), tag(3, ResidentHealthStatus.RECOVERING)),
            entry(ResidentMigrationBlockReason.class,
                    tag(0, ResidentMigrationBlockReason.QUARANTINE), tag(1, ResidentMigrationBlockReason.DESTINATION_HOUSING_LOST),
                    tag(2, ResidentMigrationBlockReason.ROUTE_OBSTRUCTED)),
            entry(ResidentMigrationStatus.class,
                    tag(0, ResidentMigrationStatus.EN_ROUTE), tag(1, ResidentMigrationStatus.BLOCKED)),
            entry(ResidentNutritionStatus.class,
                    tag(0, ResidentNutritionStatus.NOURISHED), tag(1, ResidentNutritionStatus.HUNGRY), tag(2, ResidentNutritionStatus.STARVING)),
            entry(ResidentProfession.class,
                    tag(0, ResidentProfession.AGRICULTURAL_WORKER), tag(1, ResidentProfession.EXTRACTOR), tag(2, ResidentProfession.INDUSTRIAL_WORKER),
                    tag(3, ResidentProfession.ENGINEER), tag(4, ResidentProfession.LOGISTICIAN), tag(5, ResidentProfession.MEDICAL_WORKER),
                    tag(6, ResidentProfession.SECURITY_WORKER), tag(7, ResidentProfession.CIVIC_WORKER)),
            entry(ResidentRole.class,
                    tag(0, ResidentRole.FARMER), tag(1, ResidentRole.BUILDER), tag(2, ResidentRole.CRAFTER), tag(3, ResidentRole.GUARD), tag(4, ResidentRole.MEDIC),
                    tag(5, ResidentRole.HAULER)),
            entry(ResidentSkill.class,
                    tag(0, ResidentSkill.AGRICULTURE), tag(1, ResidentSkill.BUILDING), tag(2, ResidentSkill.CRAFTING), tag(3, ResidentSkill.SECURITY),
                    tag(4, ResidentSkill.MEDICINE), tag(5, ResidentSkill.LOGISTICS)),
            entry(ResourceSitePhase.class,
                    tag(0, ResourceSitePhase.UNPREPARED), tag(1, ResourceSitePhase.GROWING), tag(2, ResourceSitePhase.READY), tag(3, ResourceSitePhase.HARVESTING),
                    tag(4, ResourceSitePhase.CONFLICT), tag(5, ResourceSitePhase.DESTROYED)),
            entry(RouteConstructionStatus.class,
                    tag(0, RouteConstructionStatus.BUILDING), tag(1, RouteConstructionStatus.CONFLICT), tag(2, RouteConstructionStatus.READY)),
            entry(RouteEngagementOutcome.class,
                    tag(0, RouteEngagementOutcome.HIVE_VICTORY), tag(1, RouteEngagementOutcome.SETTLEMENT_VICTORY), tag(2, RouteEngagementOutcome.ABORTED)),
            entry(RouteEngagementStatus.class,
                    tag(0, RouteEngagementStatus.APPROACHING), tag(1, RouteEngagementStatus.WAITING_FOR_INTERCEPT), tag(2, RouteEngagementStatus.COLD_COMBAT),
                    tag(3, RouteEngagementStatus.HOT), tag(4, RouteEngagementStatus.RESOLVED), tag(5, RouteEngagementStatus.UNKNOWN_AFTER_RESTART),
                    tag(6, RouteEngagementStatus.CONFLICT)),
            entry(RoutePatrolStatus.class,
                    tag(0, RoutePatrolStatus.EN_ROUTE), tag(1, RoutePatrolStatus.ROUTE_CLEAR), tag(2, RoutePatrolStatus.OBSTRUCTION_CONFIRMED), tag(3, RoutePatrolStatus.FAILED)),
            entry(SceneLeaseStatus.class,
                    tag(0, SceneLeaseStatus.PREPARED), tag(1, SceneLeaseStatus.HOT), tag(2, SceneLeaseStatus.DRAINING), tag(3, SceneLeaseStatus.CLOSED),
                    tag(4, SceneLeaseStatus.UNKNOWN_AFTER_RESTART), tag(5, SceneLeaseStatus.CONFLICT)),
            entry(SettlementAssaultOutcome.class,
                    tag(0, SettlementAssaultOutcome.HIVE_VICTORY), tag(1, SettlementAssaultOutcome.SETTLEMENT_VICTORY), tag(2, SettlementAssaultOutcome.ABORTED)),
            entry(SettlementAssaultStatus.class,
                    tag(0, SettlementAssaultStatus.APPROACHING), tag(1, SettlementAssaultStatus.WAITING_FOR_BATTLE), tag(2, SettlementAssaultStatus.COLD_COMBAT),
                    tag(3, SettlementAssaultStatus.HOT), tag(4, SettlementAssaultStatus.RESOLVED), tag(5, SettlementAssaultStatus.UNKNOWN_AFTER_RESTART),
                    tag(6, SettlementAssaultStatus.CONFLICT)),
            entry(SettlementProvisionStatus.class,
                    tag(0, SettlementProvisionStatus.IDLE), tag(1, SettlementProvisionStatus.IN_PROGRESS), tag(2, SettlementProvisionStatus.SECURE),
                    tag(3, SettlementProvisionStatus.RATIONED), tag(4, SettlementProvisionStatus.SHORTAGE), tag(5, SettlementProvisionStatus.CONFLICT)),
            entry(SettlementQuarantineStatus.class,
                    tag(0, SettlementQuarantineStatus.NORMAL), tag(1, SettlementQuarantineStatus.QUARANTINED)),
            entry(StrategicObjectiveKind.class,
                    tag(0, StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION), tag(1, StrategicObjectiveKind.HIVE_EXPAND_INFECTION),
                    tag(2, StrategicObjectiveKind.HIVE_GROW_ORGANISM), tag(3, StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION),
                    tag(4, StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT), tag(5, StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD),
                    tag(6, StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE), tag(7, StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE),
                    tag(8, StrategicObjectiveKind.SETTLEMENT_CONSTRUCT_ROUTE_BYPASS), tag(9, StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE)),
            entry(StrategicObjectiveStatus.class,
                    tag(0, StrategicObjectiveStatus.ACTIVE), tag(1, StrategicObjectiveStatus.BLOCKED), tag(2, StrategicObjectiveStatus.COMPLETED)),
            entry(StrategicTaskKind.class,
                    tag(0, StrategicTaskKind.DECONTAMINATE_INFECTION_CELL), tag(1, StrategicTaskKind.SPREAD_INFECTION_CELL), tag(2, StrategicTaskKind.GROW_HIVE_ORGANISM),
                    tag(3, StrategicTaskKind.INTERCEPT_ROUTE_OPERATION), tag(4, StrategicTaskKind.ASSAULT_SETTLEMENT), tag(5, StrategicTaskKind.PRODUCE_BREAD),
                    tag(6, StrategicTaskKind.PREPARE_BREAD_CARGO), tag(7, StrategicTaskKind.DELIVER_BREAD_TO_HIVE), tag(8, StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE),
                    tag(9, StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS), tag(10, StrategicTaskKind.HARVEST_RESOURCE_SITE)),
            entry(StrategicTaskRequirement.class,
                    tag(0, StrategicTaskRequirement.ACTIVE_INFIRMARY), tag(1, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT),
                    tag(2, StrategicTaskRequirement.OPERATIONAL_HEART), tag(3, StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), tag(4, StrategicTaskRequirement.EXACT_HIVE_BIOMASS),
                    tag(5, StrategicTaskRequirement.ACTIVE_WORKSHOP), tag(6, StrategicTaskRequirement.EXACT_WHEAT_INPUT), tag(7, StrategicTaskRequirement.FREE_DEPOT_SLOT),
                    tag(8, StrategicTaskRequirement.EXACT_BREAD_CARGO), tag(9, StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE), tag(10, StrategicTaskRequirement.AVAILABLE_HAULER),
                    tag(11, StrategicTaskRequirement.AVAILABLE_GUARD), tag(12, StrategicTaskRequirement.CONFIRMED_ROUTE_OBSTRUCTION),
                    tag(13, StrategicTaskRequirement.EXACT_ROUTE_CONSTRUCTION_MATERIAL), tag(14, StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER),
                    tag(15, StrategicTaskRequirement.ACTIVE_FARM), tag(16, StrategicTaskRequirement.AVAILABLE_FARMER)),
            entry(StrategicTaskStatus.class,
                    tag(0, StrategicTaskStatus.PENDING), tag(1, StrategicTaskStatus.ACTIVE), tag(2, StrategicTaskStatus.BLOCKED), tag(3, StrategicTaskStatus.COMPLETED)),
            entry(StructureCondition.class,
                    tag(0, StructureCondition.INTACT), tag(1, StructureCondition.DAMAGED), tag(2, StructureCondition.DESTROYED)),
            entry(TerminalLogisticsReceipt.TerminalLogisticsOutcome.class,
                    tag(0, TerminalLogisticsReceipt.TerminalLogisticsOutcome.DELIVERED), tag(1, TerminalLogisticsReceipt.TerminalLogisticsOutcome.FAILED),
                    tag(2, TerminalLogisticsReceipt.TerminalLogisticsOutcome.INTERRUPTED))
);

    private FrontierWireTags() { }

    public static int tag(Enum<?> value) {
        Objects.requireNonNull(value, "persisted enum");
        Map<Integer, ? extends Enum<?>> byTag = BY_TYPE.get(value.getDeclaringClass());
        if (byTag == null) throw new IllegalArgumentException("unregistered persisted enum type: " + value.getDeclaringClass().getName());
        for (Map.Entry<Integer, ? extends Enum<?>> entry : byTag.entrySet()) {
            if (entry.getValue() == value) return entry.getKey();
        }
        throw new IllegalArgumentException("unregistered persisted enum value: " + value);
    }

    public static <E extends Enum<E>> E require(Class<E> type, int tag) {
        Objects.requireNonNull(type, "persisted enum type");
        Map<Integer, ? extends Enum<?>> byTag = BY_TYPE.get(type);
        if (byTag == null) throw new IllegalArgumentException("unregistered persisted enum type: " + type.getName());
        Enum<?> value = byTag.get(tag);
        if (value == null) throw new IllegalArgumentException("unknown " + type.getSimpleName() + " wire tag: " + tag);
        return type.cast(value);
    }

    public static java.util.Set<Class<?>> types() { return BY_TYPE.keySet(); }

    private record Tagged<E extends Enum<E>>(int value, E enumValue) { }

    private static <E extends Enum<E>> Tagged<E> tag(int value, E enumValue) {
        return new Tagged<>(value, Objects.requireNonNull(enumValue, "persisted enum value"));
    }

    @SafeVarargs
    private static <E extends Enum<E>> Map.Entry<Class<?>, Map<Integer, ? extends Enum<?>>> entry(Class<E> type, Tagged<E>... taggedValues) {
        Map<Integer, E> byTag = new LinkedHashMap<>();
        java.util.Set<E> values = new java.util.LinkedHashSet<>();
        for (Tagged<E> tagged : taggedValues) {
            E value = tagged.enumValue();
            if (tagged.value() < 0 || value.getDeclaringClass() != type || byTag.putIfAbsent(tagged.value(), value) != null || !values.add(value)) {
                throw new IllegalArgumentException("invalid stable wire tag registration for " + type.getName());
            }
        }
        return Map.entry(type, Map.copyOf(byTag));
    }
}
