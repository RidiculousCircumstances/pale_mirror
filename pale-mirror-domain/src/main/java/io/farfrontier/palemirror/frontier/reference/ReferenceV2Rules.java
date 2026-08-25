package io.farfrontier.palemirror.frontier.reference;

/** Source-pinned V2 constants used by the initial territorial cognition cut. */
final class ReferenceV2Rules {
    static final int SECTOR_SIZE = 4;
    static final double HUMAN_ACCESS_RADIUS = 7.0d;
    static final double HUMAN_OBSERVATION_RADIUS = 8.0d;
    static final double HIVE_OBSERVATION_THRESHOLD = 0.28d;
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
    static final int CHRYSALIS_DAYS = 18;
    static final double CHRYSALIS_MINIMUM_BIOMASS = 105.0d;
    static final double CHRYSALIS_MINIMUM_TISSUE = 0.46d;
    static final double CHRYSALIS_MINIMUM_VITALITY = 0.62d;
    static final double CHRYSALIS_DECAY_PER_DAY = 0.025d;
    static final double MATURE_CORE_BIOMASS = 115.0d;
    static final double MATURE_CORE_VITALITY = 68.0d;
    static final double FEEDING_BIOMASS_MULTIPLIER = 2.0d;
    static final double DECAY_TISSUE_FRACTION = 0.40d;

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
