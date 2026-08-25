package io.farfrontier.palemirror.frontier.reference;

import java.util.Map;

/** Source-pinned V2 constants used by the initial territorial cognition cut. */
final class ReferenceV2Rules {
    static final int SECTOR_SIZE = 4;
    static final double HUMAN_ACCESS_RADIUS = 7.0d;
    static final double HUMAN_OBSERVATION_RADIUS = 8.0d;
    static final double HIVE_OBSERVATION_THRESHOLD = 0.28d;
    static final double OBSERVATION_POST_RADIUS = 12.0d;
    static final double OTHER_POST_RADIUS = 8.0d;
    static final double POST_CONFIDENCE = 0.94d;
    static final double SCOUT_CONFIDENCE = 0.78d;
    static final double ROUTE_CONFIDENCE = 0.58d;
    static final double ROUTE_VALUE = 0.40d;
    static final double SCAR_ACCESS_PENALTY = 0.35d;
    static final double HUMAN_DECAY_PER_DAY = 0.035d;
    static final double HIVE_DECAY_PER_DAY = 0.055d;
    static final double MINIMUM_ACTION_CONFIDENCE = 0.42d;
    static final double EMERGENCY_RESERVE_DAYS = 10.0d;
    static final double MEDICINE_RESERVE_DAYS = 6.0d;
    static final double WATCH_THREAT = 0.16d;
    static final double EMERGENCY_THREAT = 0.36d;
    static final double SIEGE_THREAT = 0.64d;
    static final double RECOVERY_THREAT = 0.10d;
    static final double SIEGE_INTEGRITY = 42.0d;
    static final double MEDICAL_EMERGENCY_BURDEN = 0.24d;
    static final double MINIMUM_LEGITIMACY = 0.12d;
    static final int QUARANTINE_DAYS = 8;
    static final double QUARANTINE_MINIMUM_WILLINGNESS = 0.45d;
    static final double NORMAL_LEGITIMACY_RECOVERY = 0.004d;
    static final double WAR_BUDGET_BASE = 0.10d;
    static final double WAR_BUDGET_MILITANCY = 0.18d;
    static final double WAR_BUDGET_CRISIS = 0.20d;
    static final double REQUISITION_LEGITIMACY_COST = 0.035d;
    static final double EMERGENCY_WAGE_DECAY = 0.995d;
    static final double COMPANY_BASE_WAGE = 0.42d;
    static final double ROUTE_INSURANCE_RATE = 0.035d;
    static final double ROUTE_INSURANCE_RISK_MULTIPLIER = 3.0d;
    static final int ROUTE_INSURANCE_DAYS = 12;
    static final double CHARTER_BREACH_TRUST_LOSS = 0.12d;
    static final double COMPANY_STRESS_CASH_DAYS = 4.0d;
    static final double COMPANY_INSOLVENCY_CASH_DAYS = 10.0d;
    static final double PROCUREMENT_FRACTION = 0.30d;
    static final double EMERGENCY_PRICE_MULTIPLIER = 1.25d;
    static final double SIEGE_PRICE_MULTIPLIER = 1.50d;
    static final int SIEGE_RESERVE_DAYS = 16;
    static final int COMPENSATION_DELAY_DAYS = 20;
    static final double PROCUREMENT_MINIMUM_QUANTITY = 0.2d;
    static final int CIVIC_PROJECT_COOLDOWN_DAYS = 10;
    static final int CIVIC_CLAIM_DAYS = 8;
    static final double CIVIC_CLAIM_DISTANCE = 18.0d;
    static final int CHRYSALIS_DAYS = 18;
    static final double CHRYSALIS_MINIMUM_BIOMASS = 105.0d;
    static final double CHRYSALIS_MINIMUM_TISSUE = 0.46d;
    static final double CHRYSALIS_MINIMUM_VITALITY = 0.62d;
    static final double CHRYSALIS_DECAY_PER_DAY = 0.025d;
    static final double MATURE_CORE_BIOMASS = 115.0d;
    static final double MATURE_CORE_VITALITY = 68.0d;
    static final double FEEDING_BIOMASS_MULTIPLIER = 2.0d;
    static final double DECAY_TISSUE_FRACTION = 0.40d;
    static final int FRONTIER_PLANNING_INTERVAL = 4;
    static final int FRONTIER_MAXIMUM_CAMPAIGNS_PER_SETTLEMENT = 1;
    static final int FRONTIER_CAMPAIGN_COOLDOWN_DAYS = 14;
    static final double FRONTIER_MINIMUM_KNOWN_INFECTION = 0.18d;
    static final double FRONTIER_CLEAR_TARGET_INFECTION = 0.12d;
    static final double FRONTIER_HIVE_CONTROL_INFECTION = 0.42d;
    static final double FRONTIER_HUMAN_CONTROL_INFECTION = 0.18d;
    static final double FRONTIER_SCARRED_THRESHOLD = 0.58d;
    static final double FRONTIER_ABANDONED_ACCESS = 0.10d;
    static final double FRONTIER_CORDON_INITIAL_STRENGTH = 0.44d;
    static final double FRONTIER_CORDON_STRENGTH_PER_GARRISON = 0.016d;
    static final double FRONTIER_CORDON_DECAY = 0.035d;
    static final double FRONTIER_CORDON_BREAK_PER_BREAKER_POWER = 0.0055d;
    static final double FRONTIER_CLEAR_INFECTION_PER_ASSAULT = 0.008d;
    static final double FRONTIER_CLEAR_INFECTION_PER_ENGINEER = 0.005d;
    static final double FRONTIER_CLEAR_SPORE_FRACTION = 0.34d;
    static final double FRONTIER_CLEAR_AMMO_PER_PERSON = 0.16d;
    static final double FRONTIER_CLEAR_FOOD_PER_PERSON = 0.05d;
    static final int FRONTIER_HOLD_DAYS = 18;
    static final int FRONTIER_RESTORE_DAYS = 8;
    static final double FRONTIER_RECONTAMINATION_INFECTION = 0.14d;
    static final double FRONTIER_RECONTAMINATION_PER_DAY = 0.018d;
    static final double FRONTIER_MINIMUM_GARRISON = 8.0d;
    static final double FRONTIER_GARRISON_POPULATION_FRACTION = 0.018d;
    static final double FRONTIER_MINIMUM_SUPPLY_READINESS = 0.48d;
    static final double FRONTIER_SUPPLY_MAX_INFECTION = 0.58d;
    static final double FRONTIER_SUPPLY_HIVE_SECTOR_COST = 0.42d;
    static final double FRONTIER_SUPPLY_CONTESTED_SECTOR_COST = 0.12d;
    static final double FRONTIER_SUPPLY_ROUTE_SECTOR_BONUS = 0.12d;
    static final double FRONTIER_SUPPLY_PATH_MAX_COST = 3.0d;
    static final double FRONTIER_HIVE_COUNTERATTACK_INFECTION = 0.10d;
    static final double FRONTIER_HIVE_COUNTERATTACK_PERSONNEL_LOSS = 0.025d;
    static final double FRONTIER_ISOLATED_ORGAN_READINESS_LOSS = 0.12d;
    static final double FRONTIER_ISOLATED_ORGAN_BIOMASS_LOSS = 0.055d;
    static final Map<ReferenceHumanUnitKind, Double> FRONTIER_ROLE_MIX = Map.of(
            ReferenceHumanUnitKind.SCOUT, 0.12d,
            ReferenceHumanUnitKind.LINE, 0.34d,
            ReferenceHumanUnitKind.ASSAULT, 0.28d,
            ReferenceHumanUnitKind.ENGINEER, 0.16d,
            ReferenceHumanUnitKind.MEDIC, 0.05d,
            ReferenceHumanUnitKind.LOGISTICS, 0.05d
    );

    private ReferenceV2Rules() { }

    static double siteValue(ReferenceSiteKind kind) {
        return switch (kind) {
            case FARM -> 1.35d;
            case MINE -> 0.95d;
            case FOREST -> 0.75d;
            case POWER -> 1.10d;
        };
    }
}
