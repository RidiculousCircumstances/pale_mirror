package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Source-port contact consequences between the hive, people, trade and deliberate suppression. */
final class ReferenceInfectionInteraction {
    private static final double SPORE_JUMP_CHANCE = 0.10d;
    private static final double SPORE_JUMP_MIN_ROUTE_INFECTION = 0.30d;
    private static final double SETTLEMENT_BIOMASS_PER_PERSON = 0.075d;
    private static final double GENETIC_PER_PERSON = 0.022d;
    private static final double ASSIMILATION_EFFICIENCY = 0.68d;
    private static final double ATTACK_ILLNESS_PER_POWER = 0.00040d;
    private static final double POST_ASSAULT_MINIMUM_POPULATION_LOSS = 0.5d;
    private static final int POST_ASSAULT_MAXIMUM_AGE_DAYS = 18;
    private static final double POST_ASSAULT_MAXIMUM_DISTANCE = 20.0d;
    private static final double HOTSPOT_MINIMUM_LEVEL = 0.45d;
    private static final double INFECTED_THRESHOLD = 0.20d;
    private static final int HOTSPOT_COUNT = 5;
    private static final double HOTSPOT_MIN_DISTANCE = 6.0d;

    private ReferenceInfectionInteraction() { }

    static int afterTrade(ReferenceInfectionModel model, Map<Integer, ReferenceSettlement> settlements, List<ReferenceTradeRecord> trades) {
        int jumps = 0;
        for (ReferenceTradeRecord record : trades) {
            if (model.rng.random() > SPORE_JUMP_CHANCE * model.adaptationMultiplier("spore_jump_multiplier")) continue;
            ReferenceSettlement buyer = settlements.get(record.buyerId());
            ReferenceSettlement seller = settlements.get(record.sellerId());
            if (buyer == null || seller == null || !buyer.alive()) continue;
            if (model.routeInfection(seller.x(), seller.y(), buyer.x(), buyer.y()) < SPORE_JUMP_MIN_ROUTE_INFECTION) continue;
            model.addLatentColony(buyer.x(), buyer.y(), 7.0d, 0.18d, model.genome(), null);
            jumps++;
        }
        return jumps;
    }

    static void introduceRefugees(ReferenceInfectionModel model, int x, int y, double illnessBurden, double people) {
        if (illnessBurden <= 0.0d) return;
        double strength = Math.min(0.42d, illnessBurden * 0.35d * model.adaptationMultiplier("refugee_vector_multiplier"));
        model.addLatentColony(x, y, people * illnessBurden * 0.03d, strength, model.genome(), null);
        model.damageMemory.merge("illness", illnessBurden, Double::sum);
    }

    static void settlementDestroyed(ReferenceInfectionModel model, ReferenceSettlement settlement) {
        double mass = settlement.population() * SETTLEMENT_BIOMASS_PER_PERSON;
        double genetic = settlement.population() * GENETIC_PER_PERSON;
        model.ecosystem.addDetritus(settlement.x(), settlement.y(), mass);
        ReferenceHiveOrgan nearest = closest(model.organs.values(), settlement.x(), settlement.y());
        if (nearest != null) {
            nearest.biomass(Math.min(storage(nearest.kind()), nearest.biomass() + mass * ASSIMILATION_EFFICIENCY));
            nearest.samples(nearest.samples() + genetic);
        }
        model.harvestedBiomass += mass;
        model.harvestedGeneticMaterial += genetic;
    }

    static void recordAttackHarvest(ReferenceInfectionModel model, ReferenceAttackEvent attack, ReferenceSettlement settlement,
                                    double populationLoss, boolean destroyed, int day) {
        settlement.illnessBurden(Math.min(1.0d, settlement.illnessBurden() + attack.power() * ATTACK_ILLNESS_PER_POWER
                * model.adaptationMultiplier("illness_multiplier")));
        if (populationLoss <= 0.0d) return;
        double mass = populationLoss * SETTLEMENT_BIOMASS_PER_PERSON;
        double genes = populationLoss * GENETIC_PER_PERSON;
        model.ecosystem.addDetritus(settlement.x(), settlement.y(), mass);
        if (populationLoss >= POST_ASSAULT_MINIMUM_POPULATION_LOSS || destroyed) {
            model.pendingExploitation.add(new ReferenceExploitationSite(settlement.x(), settlement.y(), mass, genes, day,
                    attack.sourceOrganId() == null || attack.sourceOrganId() == 0 ? -1 : attack.sourceOrganId()));
        }
    }

    static ReferenceGridPosition exploitationTarget(ReferenceInfectionModel model, ReferenceHiveOrgan source, int day) {
        ReferenceExploitationSite result = null;
        double score = Double.NEGATIVE_INFINITY;
        for (ReferenceExploitationSite item : model.pendingExploitation) {
            if (day - item.day() > POST_ASSAULT_MAXIMUM_AGE_DAYS
                    || Math.hypot(item.x() - source.x(), item.y() - source.y()) > POST_ASSAULT_MAXIMUM_DISTANCE) continue;
            double candidate = item.mass() + item.genes() * 5.0d;
            if (candidate > score) {
                result = item;
                score = candidate;
            }
        }
        return result == null ? null : new ReferenceGridPosition(result.x(), result.y());
    }

    static void resolveExploitation(ReferenceInfectionModel model, int x, int y, ReferenceHiveOrgan source, ReferenceBioformKind kind) {
        for (ReferenceExploitationSite item : List.copyOf(model.pendingExploitation)) {
            if (item.x() != x || item.y() != y) continue;
            if (kind == ReferenceBioformKind.HARVESTER) model.ecosystem.addDetritus(x, y, item.mass() * 0.65d);
            model.pendingExploitation.remove(item);
            return;
        }
    }

    static double suppressArea(ReferenceInfectionModel model, int x, int y, double radius, double strength, boolean damageOrgans) {
        double removed = 0.0d;
        double resistance = 1.0d;
        if (strength > 0.4d) {
            resistance *= model.adaptationMultiplier("scorch_resistance");
            model.recordDamage("scorch", strength);
        } else {
            resistance *= model.adaptationMultiplier("containment_resistance");
            model.recordDamage("containment", strength);
        }
        for (int yy = Math.max(0, (int) (y - radius - 1.0d)); yy < Math.min(model.height, (int) (y + radius + 2.0d)); yy++) {
            for (int xx = Math.max(0, (int) (x - radius - 1.0d)); xx < Math.min(model.width, (int) (x + radius + 2.0d)); xx++) {
                double distance = Math.hypot(xx - x, yy - y);
                if (distance > radius) continue;
                double delta = Math.min(model.level[yy][xx], strength * resistance * Math.max(0.0d, 1.0d - distance / Math.max(radius, 0.001d)));
                model.level[yy][xx] -= delta;
                removed += delta;
            }
        }
        if (damageOrgans) {
            for (ReferenceHiveOrgan organ : model.organs.values()) {
                double distance = Math.hypot(organ.x() - x, organ.y() - y);
                if (distance > radius) continue;
                double focus = Math.max(0.0d, 1.0d - distance / Math.max(radius, 0.001d));
                double organDamage = strength * focus * 125.0d;
                organ.vitality(Math.max(0.0d, organ.vitality() - organDamage));
                organ.biomass(Math.max(0.0d, organ.biomass() - organDamage * 0.32d));
            }
            model.removeDestroyedOrgans();
        }
        return removed;
    }

    static List<ReferenceInfectionHotspot> hotspots(ReferenceInfectionModel model, int count, double minimumDistance) {
        List<ReferenceInfectionHotspot> candidates = new ArrayList<>();
        for (int y = 0; y < model.height; y++) for (int x = 0; x < model.width; x++) {
            if (model.level[y][x] > HOTSPOT_MINIMUM_LEVEL) candidates.add(new ReferenceInfectionHotspot(x, y, model.level[y][x]));
        }
        candidates.sort(Comparator.comparingDouble(ReferenceInfectionHotspot::level).reversed()
                .thenComparing(Comparator.comparingInt(ReferenceInfectionHotspot::x).reversed())
                .thenComparing(Comparator.comparingInt(ReferenceInfectionHotspot::y).reversed()));
        List<ReferenceInfectionHotspot> result = new ArrayList<>();
        for (ReferenceInfectionHotspot candidate : candidates) {
            boolean distant = result.stream().allMatch(existing -> Math.hypot(candidate.x() - existing.x(), candidate.y() - existing.y()) >= minimumDistance);
            if (!distant) continue;
            result.add(candidate);
            if (result.size() >= count) break;
        }
        return List.copyOf(result);
    }

    static double infectedFraction(ReferenceInfectionModel model, double threshold) {
        int infected = 0;
        for (double[] row : model.level) for (double value : row) if (value >= threshold) infected++;
        return (double) infected / (model.width * model.height);
    }

    static int hotspotCount() { return HOTSPOT_COUNT; }
    static double hotspotMinimumDistance() { return HOTSPOT_MIN_DISTANCE; }
    static double infectedThreshold() { return INFECTED_THRESHOLD; }

    private static ReferenceHiveOrgan closest(Iterable<ReferenceHiveOrgan> organs, int x, int y) {
        ReferenceHiveOrgan result = null;
        double distance = Double.POSITIVE_INFINITY;
        for (ReferenceHiveOrgan organ : organs) {
            double candidate = Math.hypot(organ.x() - x, organ.y() - y);
            if (candidate < distance) {
                result = organ;
                distance = candidate;
            }
        }
        return result;
    }

    private static double storage(ReferenceOrganKind kind) { return switch (kind) {
        case CORE -> 220.0d; case SYNAPSE -> 100.0d; case DIGESTIVE_POOL -> 360.0d; case BROOD_SAC -> 170.0d; case SPORULATOR -> 130.0d;
    }; }
}
