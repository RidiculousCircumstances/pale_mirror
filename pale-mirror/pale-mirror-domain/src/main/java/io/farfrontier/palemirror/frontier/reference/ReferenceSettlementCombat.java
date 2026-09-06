package io.farfrontier.palemirror.frontier.reference;

/** Exact pure combat policy from Python {@code Settlement}. */
final class ReferenceSettlementCombat {
    private static final double DEFENCE_WEAPON_MINIMUM = 5.0d;
    private static final double DEFENCE_WEAPONS_PER_PERSON = 0.03d;
    private static final double DEFENCE_AMMO_MINIMUM = 20.0d;
    private static final double DEFENCE_AMMO_PER_PERSON = 0.15d;
    private static final double DEFENCE_FORTIFICATION_STRENGTH = 50.0d;
    private static final double DEFENCE_WEAPON_STRENGTH = 2.7d;
    private static final double DEFENCE_AMMO_STRENGTH = 0.28d;
    private static final double COMBAT_WEAPONS_MINIMUM = 3.0d;
    private static final double COMBAT_WEAPONS_POWER_RATIO = 0.22d;
    private static final double COMBAT_WEAPONS_POPULATION_RATIO = 0.04d;
    private static final double COMBAT_AMMO_MINIMUM = 10.0d;
    private static final double COMBAT_AMMO_POWER_RATIO = 0.22d;
    private static final double COMBAT_WEAPON_STRENGTH = 3.0d;
    private static final double COMBAT_AMMO_STRENGTH = 0.58d;
    private static final double COMBAT_WEAPON_LOSS_FRACTION = 0.03d;
    private static final double COMBAT_DAMAGE_PER_OVERFLOW = 0.28d;
    private static final double COMBAT_POPULATION_LOSS_MAXIMUM = 0.14d;
    private static final double COMBAT_POPULATION_LOSS_SCALE = 1200.0d;
    private static final double COMBAT_STOCK_LOSS_MAXIMUM = 0.22d;
    private static final double COMBAT_STOCK_LOSS_SCALE = 900.0d;
    private static final double COMBAT_STOCK_LOSS_MULTIPLIER = 0.35d;
    private static final double COMBAT_CAPITAL_LOSS_MAXIMUM = 0.12d;
    private static final double COMBAT_CAPITAL_LOSS_SCALE = 1500.0d;
    private static final double COLLAPSE_POPULATION = 40.0d;

    private ReferenceSettlementCombat() { }

    static double defenceStrength(ReferenceSettlement settlement) {
        double weapons = Math.min(settlement.amount(ReferenceResource.WEAPONS), Math.max(
                DEFENCE_WEAPON_MINIMUM, settlement.population() * DEFENCE_WEAPONS_PER_PERSON));
        double ammunition = Math.min(settlement.amount(ReferenceResource.AMMO), Math.max(
                DEFENCE_AMMO_MINIMUM, settlement.population() * DEFENCE_AMMO_PER_PERSON));
        return settlement.facilities().fortification() * DEFENCE_FORTIFICATION_STRENGTH
                + weapons * DEFENCE_WEAPON_STRENGTH + ammunition * DEFENCE_AMMO_STRENGTH;
    }

    static double combatDefence(ReferenceSettlement settlement, double power) {
        if (!settlement.alive()) return 0.0d;
        return settlement.facilities().fortification() * DEFENCE_FORTIFICATION_STRENGTH
                + committedWeapons(settlement, power) * COMBAT_WEAPON_STRENGTH
                + committedAmmunition(settlement, power) * COMBAT_AMMO_STRENGTH;
    }

    static ReferenceSwarmAttackResolution resolve(
            ReferenceSettlement settlement,
            double power,
            double externalDefence,
            double structuralBreach,
            double personnelPressure,
            double medicProtection
    ) {
        if (!settlement.alive()) return new ReferenceSwarmAttackResolution(true, 0.0d, 0.0d, 0.0d, 0.0d);
        double weapons = committedWeapons(settlement, power);
        double ammunition = committedAmmunition(settlement, power);
        double defence = combatDefence(settlement, power) + nonNegative(externalDefence);
        settlement.remove(ReferenceResource.AMMO, ammunition);
        settlement.remove(ReferenceResource.WEAPONS, weapons * COMBAT_WEAPON_LOSS_FRACTION);
        double overflow = Math.max(0.0d, power - defence);
        double breach = nonNegative(structuralBreach);
        double damage = (overflow + breach) * COMBAT_DAMAGE_PER_OVERFLOW;
        settlement.integrity(Math.max(0.0d, settlement.integrity() - damage));
        double populationLoss = 0.0d;
        if (overflow > 0.0d) {
            populationLoss = settlement.population() * Math.min(COMBAT_POPULATION_LOSS_MAXIMUM,
                    (overflow + nonNegative(personnelPressure)) / COMBAT_POPULATION_LOSS_SCALE);
            populationLoss *= Math.max(0.35d, 1.0d - nonNegative(medicProtection));
            populationLoss = settlement.removePeople(populationLoss, "swarm_attack");
            double stockLoss = Math.min(COMBAT_STOCK_LOSS_MAXIMUM, overflow / COMBAT_STOCK_LOSS_SCALE);
            settlement.multiplyStock(1.0d - stockLoss * COMBAT_STOCK_LOSS_MULTIPLIER);
            double capitalLoss = Math.min(COMBAT_CAPITAL_LOSS_MAXIMUM, overflow / COMBAT_CAPITAL_LOSS_SCALE);
            settlement.facilities().workshop(settlement.facilities().workshop() * (1.0d - capitalLoss));
            settlement.facilities().armory(settlement.facilities().armory() * (1.0d - capitalLoss));
            settlement.facilities().clinic(settlement.facilities().clinic() * (1.0d - capitalLoss));
        }
        if (settlement.integrity() <= 0.0d
                || settlement.population() < settlement.profile().collapsePopulationFromSource(COLLAPSE_POPULATION)) {
            settlement.alive(false);
        }
        return new ReferenceSwarmAttackResolution(!settlement.alive(), damage, defence, breach, populationLoss);
    }

    private static double committedWeapons(ReferenceSettlement settlement, double power) {
        return Math.min(settlement.amount(ReferenceResource.WEAPONS), Math.max(
                COMBAT_WEAPONS_MINIMUM,
                Math.min(power * COMBAT_WEAPONS_POWER_RATIO,
                        settlement.population() * COMBAT_WEAPONS_POPULATION_RATIO)));
    }

    private static double committedAmmunition(ReferenceSettlement settlement, double power) {
        return Math.min(settlement.amount(ReferenceResource.AMMO),
                Math.max(COMBAT_AMMO_MINIMUM, power * COMBAT_AMMO_POWER_RATIO));
    }

    private static double nonNegative(double value) {
        return value > 0.0d ? value : 0.0d;
    }
}
