package io.farfrontier.palemirror.frontier;

/**
 * Explicit graybox translation of the named constants in pale_mirror_ai's balance profile.
 * Fractions of a 1:40 physical inventory round up, because an absent Minecraft item may not be
 * represented by a fractional, invisible cost. Deliberate Java-only scenario cadence remains in
 * the relevant simulation and is not presented as Python conformance.
 */
final class FrontierBalance {
    static final double HUMAN_SPEED_OFFROAD_CELLS_PER_DAY = 1.55;
    static final double HIVE_SPEED_CELLS_PER_DAY = 0.65;
    static final double DEFEND_PERSONNEL_RATIO = 0.018;
    static final int DEFEND_PERSONNEL_MINIMUM = 12;
    static final double DEFEND_FOOD = 24.0;
    static final double DEFEND_MEDICINE = 0.6;
    static final double DEFEND_WEAPONS = 7.0;
    static final double DEFEND_AMMO = 32.0;
    static final long ON_STATION_GUARD_DEFENCE = 4;
    /*
     * The reference frontier plans every four days.  A graybox cannot use the
     * reference's perception gate verbatim (there is no invisible, aggregate
     * population to discover a hive), so it deliberately waits through one
     * readable approach window before making the first decision.  After that
     * window it retains the reference planning cadence; it is not a once per
     * month scripted raid.
     */
    static final int CAMPAIGN_FIRST_ELIGIBLE_DAY = 36;
    static final int CAMPAIGN_PLANNING_INTERVAL_DAYS = 4;
    static final int CAMPAIGN_MINIMUM_READINESS_PERMILLE = 480;
    static final int CAMPAIGN_HOLD_DAYS = 18;
    static final int CAMPAIGN_RESTORE_DAYS = 8;

    private FrontierBalance() { }

    static int defendPersonnel(FrontierProfile profile, int physicalPopulation) {
        if (physicalPopulation < 1) return 0;
        double sourcePopulation = Math.multiplyExact(physicalPopulation, profile.populationScale());
        return scaledCeiling(profile, Math.max(DEFEND_PERSONNEL_MINIMUM, sourcePopulation * DEFEND_PERSONNEL_RATIO));
    }
    static long defendFood(FrontierProfile profile) { return scaledCeiling(profile, DEFEND_FOOD); }
    static long defendMedicine(FrontierProfile profile) { return scaledCeiling(profile, DEFEND_MEDICINE); }
    static long defendWeapons(FrontierProfile profile) { return scaledCeiling(profile, DEFEND_WEAPONS); }
    static long defendAmmo(FrontierProfile profile) { return scaledCeiling(profile, DEFEND_AMMO); }
    /** 1:40 ceiling of pale_mirror_ai.strategy.operations.personnel.raid_nest.minimum. */
    static int campaignPersonnel(FrontierProfile profile) { return scaledCeiling(profile, 18.0); }
    /** 1:40 ceilings of pale_mirror_ai.strategy.operations.requirements.raid_nest. */
    static long campaignFood(FrontierProfile profile) { return scaledCeiling(profile, 42.0); }
    static long campaignMedicine(FrontierProfile profile) { return scaledCeiling(profile, 1.2); }
    static long campaignWeapons(FrontierProfile profile) { return scaledCeiling(profile, 14.0); }
    static long campaignAmmo(FrontierProfile profile) { return scaledCeiling(profile, 54.0); }
    /** One graybox food unit feeds eight individually materialized residents for one day. */
    static long civilianFoodNeed(int physicalPopulation) {
        if (physicalPopulation < 0) throw new IllegalArgumentException("physical population must not be negative");
        return Math.max(1, physicalPopulation / 8L);
    }
    static int humanTravelDays(FrontierPoint origin, FrontierPoint target) {
        return travelDays(origin, target, HUMAN_SPEED_OFFROAD_CELLS_PER_DAY);
    }
    static int hiveTravelDays(FrontierPoint origin, FrontierPoint target) {
        return travelDays(origin, target, HIVE_SPEED_CELLS_PER_DAY);
    }

    private static int scaledCeiling(FrontierProfile profile, double sourceAmount) {
        return Math.max(1, (int) Math.ceil(sourceAmount / profile.populationScale()));
    }
    private static int travelDays(FrontierPoint origin, FrontierPoint target, double cellsPerDay) {
        int cells = Math.max(Math.abs(origin.x() - target.x()), Math.abs(origin.z() - target.z()));
        return Math.max(1, (int) Math.ceil(cells / cellsPerDay));
    }
}
