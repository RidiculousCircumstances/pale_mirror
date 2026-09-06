package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact pure tactical port of Python's {@code HivePlanner}. */
public final class ReferenceHivePlanner {
    private static final int PLANNING_INTERVAL_DAYS = 6;
    private static final int ORDER_COMMITMENT_DAYS = 7;
    private static final double SYNAPSE_SPACING = 2.0d;
    private static final double OTHER_ORGAN_SPACING = 3.0d;
    private static final double SYNAPSE_MINIMUM_TISSUE = 0.42d;
    private static final double DIGESTIVE_POOL_MINIMUM_TISSUE = 0.35d;
    private static final double BROOD_SAC_MINIMUM_TISSUE = 0.34d;
    private static final double SPORULATOR_MINIMUM_TISSUE = 0.38d;
    private static final double HARVESTER_MINIMUM_DISTANCE = 2.0d;
    private static final double HARVESTER_SEARCH_RADIUS = 12.0d;
    private static final double HARVESTER_MINIMUM_ORGANIC_TARGET = 52.0d;
    private static final double HARVESTER_INFECTED_CELL_PENALTY = 0.82d;
    private static final double HARVESTER_DISTANCE_COST = 2.2d;
    private static final double HARVESTER_MINIMUM_TARGET_SCORE = 14.0d;
    private static final int HARVESTER_MAXIMUM_ACTIVE_PER_BROOD = 2;
    private static final double HARVESTER_DISPATCH_BIOMASS_CEILING = 80.0d;
    private static final double BROOD_LAUNCH_MINIMUM = 0.42d;
    private static final double SPORULATOR_LAUNCH_MINIMUM = 0.48d;
    private static final int MAXIMUM_ACTIVE_SPORE_CARRIERS = 1;
    private static final double FERAL_ATTACK_RADIUS = 4.0d;
    private static final double SETTLEMENT_BIOMASS_PER_PERSON = 0.075d;
    private static final double GENETIC_PER_PERSON = 0.022d;
    private static final double PREY_WOUNDED_PRIORITY = 4.0d;
    private static final double ARMOURED_TARGET_DEFENCE = 130.0d;
    private static final double SPORE_CORE_MIN_DISTANCE = 20.0d;
    private static final double HIVE_COUNTERATTACK_MIN_BIOMASS = 78.0d;
    private static final double HIVE_COUNTERATTACK_MIN_CORDON = 0.18d;

    /** Return only immutable orders. This method does not retain or mutate its view. */
    public List<ReferenceHiveOrder> plan(ReferenceHiveWorldView view) {
        if (view.day() % PLANNING_INTERVAL_DAYS != 0) return List.of();
        List<ReferenceHiveOrder> orders = new ArrayList<>();
        for (ReferenceHiveWorldView.Nest source : view.nests()) {
            if (view.day() - source.lastProjectDay() < ORDER_COMMITMENT_DAYS) continue;
            List<ReferenceHiveWorldView.Nest> local = localOrgans(view, source);
            boolean hasSynapse = hasKind(local, ReferenceOrganKind.SYNAPSE);
            boolean hasDigestive = hasKind(local, ReferenceOrganKind.DIGESTIVE_POOL);
            boolean hasSporulator = hasKind(local, ReferenceOrganKind.SPORULATOR);
            if (source.feral() && strategicOrgan(source.kind())) continue;
            switch (source.kind()) {
                case CORE -> planCore(view, source, hasSynapse, hasDigestive, hasSporulator, orders);
                case DIGESTIVE_POOL -> planDigestivePool(view, source, local, orders);
                case SYNAPSE -> planSynapse(view, source, hasDigestive, orders);
                case BROOD_SAC -> planBroodSac(view, source, orders);
                case SPORULATOR -> planSporulator(view, source, orders);
            }
        }
        return List.copyOf(orders);
    }

    private static void planCore(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source, boolean hasSynapse,
                                 boolean hasDigestive, boolean hasSporulator, List<ReferenceHiveOrder> orders) {
        ReferenceOrganKind needed = !hasSynapse ? ReferenceOrganKind.SYNAPSE
                : !hasDigestive ? ReferenceOrganKind.DIGESTIVE_POOL : !hasSporulator ? ReferenceOrganKind.SPORULATOR : null;
        Candidate target = needed == null ? null : organTarget(view, source, needed);
        if (target != null) orders.add(ReferenceHiveOrder.morph(source.id(), target.x, target.y, needed, "complete local biological complex"));
    }

    private static void planDigestivePool(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source,
                                          List<ReferenceHiveWorldView.Nest> local, List<ReferenceHiveOrder> orders) {
        if (hasKind(local, ReferenceOrganKind.BROOD_SAC)) return;
        Candidate target = organTarget(view, source, ReferenceOrganKind.BROOD_SAC);
        if (target != null) orders.add(ReferenceHiveOrder.morph(source.id(), target.x, target.y, ReferenceOrganKind.BROOD_SAC, "feed a brood organ"));
    }

    private static void planSynapse(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source, boolean hasDigestive,
                                    List<ReferenceHiveOrder> orders) {
        if (hasDigestive) return;
        Candidate target = organTarget(view, source, ReferenceOrganKind.DIGESTIVE_POOL);
        if (target != null) orders.add(ReferenceHiveOrder.morph(source.id(), target.x, target.y, ReferenceOrganKind.DIGESTIVE_POOL, "extend nutrient routing"));
    }

    private static void planBroodSac(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source, List<ReferenceHiveOrder> orders) {
        long active = view.swarms().stream().filter(item -> item.kind() == ReferenceBioformKind.HARVESTER
                && Integer.valueOf(source.id()).equals(item.sourceNestId())).count();
        Candidate harvest = harvestTarget(view, source);
        Candidate frontier = frontierTarget(view, source);
        if (frontier != null && source.biomass() >= HIVE_COUNTERATTACK_MIN_BIOMASS && canLaunch(view, source, ReferenceBioformKind.RAIDER)) {
            orders.add(ReferenceHiveOrder.launch(source.id(), frontier.x, frontier.y, ReferenceBioformKind.RAIDER,
                    composition(ReferenceBioformKind.RAIDER, 5.0d, ReferenceBioformKind.BREAKER, 2.0d), -1,
                    "break a biologically observed human cordon and sever its supply"));
            return;
        }
        if (harvest != null && active < HARVESTER_MAXIMUM_ACTIVE_PER_BROOD && source.biomass() <= HARVESTER_DISPATCH_BIOMASS_CEILING
                && canLaunch(view, source, ReferenceBioformKind.HARVESTER)) {
            orders.add(ReferenceHiveOrder.launch(source.id(), harvest.x, harvest.y, ReferenceBioformKind.HARVESTER, Map.of(), -1,
                    "forage organic frontier"));
            return;
        }
        Prey prey = prey(view, source);
        if (prey == null) return;
        boolean armoured = prey.defence > ARMOURED_TARGET_DEFENCE;
        Map<ReferenceBioformKind, Double> composition = armoured
                ? composition(ReferenceBioformKind.RAIDER, 1.0d, ReferenceBioformKind.BREAKER, 1.0d)
                : composition(ReferenceBioformKind.RAIDER, 1.0d);
        orders.add(ReferenceHiveOrder.launch(source.id(), prey.x, prey.y, ReferenceBioformKind.RAIDER, composition, prey.id,
                armoured ? "screen and breach an armoured human settlement" : "screen and consume vulnerable human settlement"));
    }

    private static void planSporulator(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source, List<ReferenceHiveOrder> orders) {
        if (source.feral()) return;
        Candidate target = sporeTarget(view, source);
        if (target != null) orders.add(ReferenceHiveOrder.launch(source.id(), target.x, target.y, ReferenceBioformKind.SPORE_CARRIER,
                Map.of(), -1, "seed distant organic foothold"));
    }

    private static List<ReferenceHiveWorldView.Nest> localOrgans(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source) {
        return view.nests().stream().filter(item -> distance(item.x(), item.y(), source.x(), source.y()) <= 20.0d).toList();
    }

    private static Candidate organTarget(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source, ReferenceOrganKind kind) {
        double minimum = kind == ReferenceOrganKind.SYNAPSE ? SYNAPSE_SPACING : OTHER_ORGAN_SPACING;
        double minimumTissue = minimumTissue(kind);
        Candidate result = null;
        for (ReferenceHiveWorldView.Cell cell : view.cells()) {
            if (cell.x() == 0 || cell.x() == view.width() - 1 || cell.y() == 0 || cell.y() == view.height() - 1 || cell.infection() < minimumTissue) continue;
            double distance = distance(cell.x(), cell.y(), source.x(), source.y());
            if (distance < minimum || view.nests().stream().anyMatch(organ -> distance(cell.x(), cell.y(), organ.x(), organ.y()) < minimum)) continue;
            double settlementDistance = view.settlements().stream().filter(ReferenceHiveWorldView.Settlement::alive)
                    .mapToDouble(item -> distance(cell.x(), cell.y(), item.x(), item.y())).min().orElse(20.0d);
            double score = cell.organicMass() * 0.10d + Math.min(10.0d, settlementDistance) * (kind == ReferenceOrganKind.SYNAPSE ? 0.3d : 0.05d)
                    - distance * 0.12d;
            result = better(result, new Candidate(score, cell.x(), cell.y()));
        }
        return result;
    }

    private static Candidate harvestTarget(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source) {
        Candidate result = null;
        for (ReferenceHiveWorldView.Cell cell : view.cells()) {
            double distance = distance(cell.x(), cell.y(), source.x(), source.y());
            if (distance < HARVESTER_MINIMUM_DISTANCE || distance > HARVESTER_SEARCH_RADIUS || cell.organicMass() < HARVESTER_MINIMUM_ORGANIC_TARGET) continue;
            double frontierFactor = 1.0d - Math.min(1.0d, cell.infection()) * HARVESTER_INFECTED_CELL_PENALTY;
            double score = cell.organicMass() * frontierFactor - distance * HARVESTER_DISTANCE_COST;
            if (score >= HARVESTER_MINIMUM_TARGET_SCORE) result = better(result, new Candidate(score, cell.x(), cell.y()));
        }
        return result;
    }

    private static Prey prey(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source) {
        Prey result = null;
        for (ReferenceHiveWorldView.Settlement settlement : view.settlements()) {
            if (!settlement.alive()) continue;
            double distance = distance(settlement.x(), settlement.y(), source.x(), source.y());
            if (source.feral() && distance > FERAL_ATTACK_RADIUS) continue;
            double edible = settlement.population() * SETTLEMENT_BIOMASS_PER_PERSON;
            double genetic = settlement.population() * GENETIC_PER_PERSON;
            double wounded = 1.0d + Math.max(0.0d, 1.0d - settlement.integrity() / 100.0d) * PREY_WOUNDED_PRIORITY;
            double score = wounded * (edible + genetic * 8.0d) / (1.0d + distance * 0.15d + settlement.defence() * 0.012d);
            result = better(result, new Prey(score, settlement.id(), settlement.x(), settlement.y(), settlement.defence()));
        }
        return result;
    }

    private static Candidate sporeTarget(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source) {
        Candidate result = null;
        for (ReferenceHiveWorldView.Cell cell : view.cells()) {
            if (cell.x() == 0 || cell.x() == view.width() - 1 || cell.y() == 0 || cell.y() == view.height() - 1) continue;
            double distance = distance(cell.x(), cell.y(), source.x(), source.y());
            if (distance < SPORE_CORE_MIN_DISTANCE || view.nests().stream().anyMatch(organ -> distance(cell.x(), cell.y(), organ.x(), organ.y()) < SPORE_CORE_MIN_DISTANCE)) continue;
            double nearestPrey = view.settlements().stream().filter(ReferenceHiveWorldView.Settlement::alive)
                    .mapToDouble(item -> distance(cell.x(), cell.y(), item.x(), item.y())).min().orElse(20.0d);
            double score = cell.organicMass() * cell.moisture() - distance * 0.35d + Math.min(8.0d, nearestPrey) * 0.45d;
            result = better(result, new Candidate(score, cell.x(), cell.y()));
        }
        return result;
    }

    private static Candidate frontierTarget(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source) {
        Candidate result = null;
        for (ReferenceHiveWorldView.Sector sector : view.sectors()) {
            if ((!sector.control().equals("human") && !sector.control().equals("contested"))
                    || (sector.cordonStrength() < HIVE_COUNTERATTACK_MIN_CORDON && !sector.supplied())) continue;
            int x = sector.x() * 4 + 2;
            int y = sector.y() * 4 + 2;
            double distance = distance(x, y, source.x(), source.y());
            if (distance > HARVESTER_SEARCH_RADIUS) continue;
            double score = sector.cordonStrength() * 2.2d + Math.min(1.0d, sector.garrison() / 20.0d)
                    + sector.infrastructureValue() * 0.06d + (sector.supplied() ? 0.35d : 0.0d) - distance * 0.045d;
            result = better(result, new Candidate(score, x, y));
        }
        return result;
    }

    private static boolean canLaunch(ReferenceHiveWorldView view, ReferenceHiveWorldView.Nest source, ReferenceBioformKind kind) {
        double readiness = Math.max(0.0d, Math.min(1.0d, source.vitality() / 100.0d));
        double minimum = source.kind() == ReferenceOrganKind.BROOD_SAC ? BROOD_LAUNCH_MINIMUM
                : source.kind() == ReferenceOrganKind.SPORULATOR ? SPORULATOR_LAUNCH_MINIMUM : 0.0d;
        if (readiness < minimum || source.biomass() < biomass(kind) || view.swarms().size() >= view.maximumSwarms()) return false;
        return kind != ReferenceBioformKind.SPORE_CARRIER
                || view.swarms().stream().filter(item -> item.kind() == ReferenceBioformKind.SPORE_CARRIER).count() < MAXIMUM_ACTIVE_SPORE_CARRIERS;
    }

    private static boolean strategicOrgan(ReferenceOrganKind kind) {
        return kind == ReferenceOrganKind.CORE || kind == ReferenceOrganKind.SYNAPSE || kind == ReferenceOrganKind.DIGESTIVE_POOL || kind == ReferenceOrganKind.SPORULATOR;
    }
    private static boolean hasKind(List<ReferenceHiveWorldView.Nest> nests, ReferenceOrganKind kind) { return nests.stream().anyMatch(item -> item.kind() == kind); }
    private static double minimumTissue(ReferenceOrganKind kind) { return switch (kind) {
        case CORE -> 0.72d; case SYNAPSE -> SYNAPSE_MINIMUM_TISSUE; case DIGESTIVE_POOL -> DIGESTIVE_POOL_MINIMUM_TISSUE;
        case BROOD_SAC -> BROOD_SAC_MINIMUM_TISSUE; case SPORULATOR -> SPORULATOR_MINIMUM_TISSUE;
    }; }
    private static double biomass(ReferenceBioformKind kind) { return switch (kind) {
        case HARVESTER, RAIDER -> 18.0d; case BREAKER -> 32.0d; case SPORE_CARRIER -> 30.0d;
    }; }
    private static double distance(double x1, double y1, double x2, double y2) { return Math.hypot(x1 - x2, y1 - y2); }
    private static Candidate better(Candidate current, Candidate candidate) {
        return current == null || candidate.score > current.score || candidate.score == current.score
                && (candidate.x > current.x || candidate.x == current.x && candidate.y > current.y) ? candidate : current;
    }
    private static Prey better(Prey current, Prey candidate) {
        return current == null || candidate.score > current.score || candidate.score == current.score
                && (candidate.id > current.id || candidate.id == current.id && (candidate.x > current.x || candidate.x == current.x && candidate.y > current.y)) ? candidate : current;
    }
    private static Map<ReferenceBioformKind, Double> composition(ReferenceBioformKind first, double firstCount,
                                                                  ReferenceBioformKind second, double secondCount) {
        Map<ReferenceBioformKind, Double> result = new LinkedHashMap<>(); result.put(first, firstCount); result.put(second, secondCount); return result;
    }
    private static Map<ReferenceBioformKind, Double> composition(ReferenceBioformKind kind, double count) { return Map.of(kind, count); }
    private record Candidate(double score, int x, int y) { }
    private record Prey(double score, int id, int x, int y, double defence) { }
}
