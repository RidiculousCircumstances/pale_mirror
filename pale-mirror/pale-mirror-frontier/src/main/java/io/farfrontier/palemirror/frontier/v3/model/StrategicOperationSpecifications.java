package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Single closed requirement catalogue shared by planning explanations and durable task construction. */
public final class StrategicOperationSpecifications {
    private StrategicOperationSpecifications() { }
    public static List<StrategicTaskRequirement> requirements(StrategicObjectiveKind kind) {
        return switch (kind) {
            case SETTLEMENT_CONTAIN_LOCAL_INFECTION -> List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT);
            case HIVE_EXPAND_INFECTION -> List.of(StrategicTaskRequirement.OPERATIONAL_GANGLION);
            case HIVE_GROW_ORGANISM -> List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS);
            case HIVE_ASSAULT_SETTLEMENT -> List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD, StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER);
            case SETTLEMENT_PRODUCE_BREAD, SETTLEMENT_COMPANY_PRODUCTION, SETTLEMENT_MAINTAIN_EQUIPMENT -> List.of(StrategicTaskRequirement.ACTIVE_PRODUCTION_FACILITY, StrategicTaskRequirement.AVAILABLE_PRODUCTION_INPUT);
            case SETTLEMENT_PATROL_OBSTRUCTED_ROUTE -> List.of(StrategicTaskRequirement.AVAILABLE_GUARD);
            case SETTLEMENT_CONSTRUCT_ROUTE_BYPASS -> List.of(StrategicTaskRequirement.CONFIRMED_ROUTE_OBSTRUCTION, StrategicTaskRequirement.EXACT_ROUTE_CONSTRUCTION_MATERIAL);
            case SETTLEMENT_HARVEST_RESOURCE_SITE -> List.of(StrategicTaskRequirement.ACTIVE_FARM, StrategicTaskRequirement.AVAILABLE_FARMER,
                    StrategicTaskRequirement.FREE_DEPOT_SLOT);
        };
    }
}
