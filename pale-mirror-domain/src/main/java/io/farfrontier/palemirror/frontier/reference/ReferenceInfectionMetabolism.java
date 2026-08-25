package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact tissue propagation and ecology-only metabolism from {@code InfectionModel}. */
final class ReferenceInfectionMetabolism {
    private static final double SPREAD_NOISE = 0.002d;
    private static final double ZERO_THRESHOLD = 0.003d;
    private static final double NETWORK_THRESHOLD = 0.28d;
    private static final double SIGNAL_THRESHOLD = 0.32d;
    private static final double UNSIGNALED_GROWTH_FACTOR = 0.36d;
    private static final double UNSIGNALED_DECAY_FACTOR = 0.12d;
    private static final double FERAL_DECAY = 0.055d;
    private static final double METABOLIC_DEMAND_PER_TISSUE = 2.7d;
    private static final double NETWORK_CAPACITY = 18.0d;
    private static final double NETWORK_LOSS_PER_CELL = 0.018d;
    private static final double ASSIMILATION_EFFICIENCY = 0.68d;
    private static final double ORGAN_TRANSFER_CAPACITY = 20.0d;
    private static final double ORGAN_TRANSFER_RESERVE_FRACTION = 0.58d;
    private static final double CELL_MAINTENANCE = 0.021d;

    private ReferenceInfectionMetabolism() { }

    static void spread(ReferenceInfectionModel model) {
        double[][] old = model.level;
        double[][] next = new double[model.height][model.width];
        boolean active = !model.organs.isEmpty();
        List<List<Double>> signal = active ? model.signalMap() : zeroes(model.width, model.height);
        for (int y = 0; y < model.height; y++) for (int x = 0; x < model.width; x++) {
            double current = old[y][x];
            double nearby = 0.0d;
            int nearbyCount = 0;
            for (int[] offset : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                int nx = x + offset[0];
                int ny = y + offset[1];
                if (inside(model, nx, ny)) { nearby += old[ny][nx]; nearbyCount++; }
            }
            double susceptibility = susceptibility(model.biomes.get(y).get(x));
            if (model.biomes.get(y).get(x) == ReferenceBiome.MOUNTAINS) susceptibility *= model.adaptationMultiplier("mountain_spread");
            ReferenceEcosystemCell ecology = model.ecosystem.cell(x, y);
            double livingCover = Math.min(1.0d, ecology.organicMass() / 70.0d);
            double fertility = livingCover * Math.max(0.08d, 1.0d - ecology.scar() / model.adaptationMultiplier("scar_tolerance"));
            double value;
            if (active) {
                double command = UNSIGNALED_GROWTH_FACTOR + (1.0d - UNSIGNALED_GROWTH_FACTOR) * signal.get(y).get(x);
                double growth = model.growthRate() * current * (1.0d - current) * susceptibility * fertility * command;
                double diffusion = model.spreadRate() * (nearby / Math.max(1, nearbyCount)) * (1.0d - current) * susceptibility * fertility * command;
                if (signal.get(y).get(x) < SIGNAL_THRESHOLD) growth -= current * FERAL_DECAY * UNSIGNALED_DECAY_FACTOR;
                value = current + growth + diffusion + model.rng.uniform(-SPREAD_NOISE, SPREAD_NOISE);
            } else {
                value = current * (1.0d - FERAL_DECAY * model.adaptationMultiplier("feral_decay_multiplier"));
            }
            next[y][x] = value < ZERO_THRESHOLD ? 0.0d : Math.min(1.0d, Math.max(0.0d, value));
        }
        model.level = next;
    }

    static void digest(ReferenceInfectionModel model, int day) {
        model.networkFlows.clear();
        model.nestEconomy.clear();
        for (ReferenceHiveOrgan organ : model.organs.values()) model.nestEconomy.put(organ.id(), new ReferenceHiveEconomyEntry(day, organ));
        if (model.organs.isEmpty()) return;
        ReferenceInfectionModel.NetworkComponents components = model.networkComponents();
        Map<Integer, List<ReferenceHiveOrgan>> byComponent = new LinkedHashMap<>();
        for (ReferenceHiveOrgan organ : model.organs.values()) {
            Integer component = model.componentNear(components, organ.x(), organ.y());
            if (component != null) byComponent.computeIfAbsent(component, ignored -> new ArrayList<>()).add(organ);
        }
        for (Map.Entry<Integer, List<ReferenceHiveOrgan>> entry : byComponent.entrySet()) digestComponent(model, day, components, entry.getKey(), entry.getValue());
        double tissue = 0.0d;
        for (double[] row : model.level) for (double value : row) tissue += value;
        double perOrganTissue = tissue / Math.max(1, model.organs.size());
        for (ReferenceHiveOrgan organ : model.organs.values()) {
            double maintenance = maintenance(organ.kind()) + perOrganTissue * CELL_MAINTENANCE;
            organ.biomass(Math.max(0.0d, organ.biomass() - maintenance));
            model.nestEconomy.get(organ.id()).maintenance(maintenance);
        }
    }

    private static void digestComponent(ReferenceInfectionModel model, int day, ReferenceInfectionModel.NetworkComponents components,
                                        int component, List<ReferenceHiveOrgan> organs) {
        List<ReferenceHiveOrgan> receivers = organs.stream().filter(ReferenceInfectionMetabolism::isReceiver).toList();
        if (receivers.isEmpty()) receivers = organs;
        for (Map.Entry<ReferenceGridPosition, Integer> cell : components.byCell().entrySet()) {
            if (cell.getValue() != component) continue;
            int x = cell.getKey().x();
            int y = cell.getKey().y();
            double localLevel = model.level[y][x];
            if (localLevel < NETWORK_THRESHOLD) continue;
            double demand = localLevel * METABOLIC_DEMAND_PER_TISSUE * model.adaptationMultiplier("harvest_multiplier");
            ReferenceEcosystem.Consumption consumption = model.ecosystem.consume(x, y, demand);
            if (consumption.mass() <= 0.0d) continue;
            ReferenceHiveOrgan target = closest(receivers, x, y);
            double distance = Math.hypot(target.x() - x, target.y() - y);
            double moved = Math.min(consumption.mass(), NETWORK_CAPACITY / (1.0d + distance * 0.30d));
            double retained = moved * Math.max(0.12d, 1.0d - distance * NETWORK_LOSS_PER_CELL);
            double gain = retained * ASSIMILATION_EFFICIENCY * model.adaptationMultiplier("assimilation_multiplier");
            target.biomass(Math.min(storage(target.kind()), target.biomass() + gain));
            target.samples(target.samples() + consumption.geneticSignal());
            model.harvestedBiomass += retained;
            model.harvestedGeneticMaterial += consumption.geneticSignal();
            ReferenceHiveEconomyEntry ledger = model.nestEconomy.get(target.id());
            ledger.addSubstrateIn(moved); ledger.addBiomassIncome(gain); ledger.addSamplesIn(consumption.geneticSignal());
            model.networkFlows.add(new ReferenceNetworkFlow(day, "ecosystem", y * model.width + x, x, y, target.id(), moved, retained,
                    moved - retained, distance));
        }
        List<ReferenceHiveOrgan> donors = organs.stream().filter(ReferenceInfectionMetabolism::isReceiver).toList();
        List<ReferenceHiveOrgan> recipients = organs.stream().filter(organ -> !donors.contains(organ))
                .sorted(Comparator.comparingInt(organ -> priority(organ.kind()))).toList();
        for (ReferenceHiveOrgan recipient : recipients) {
            ReferenceHiveOrgan donor = richest(donors);
            if (donor == null) break;
            double available = Math.max(0.0d, donor.biomass() - storage(donor.kind()) * ORGAN_TRANSFER_RESERVE_FRACTION);
            if (available <= 0.0d) continue;
            double distance = Math.hypot(donor.x() - recipient.x(), donor.y() - recipient.y());
            double moved = Math.min(available, ORGAN_TRANSFER_CAPACITY / (1.0d + distance * 0.25d));
            double retained = moved * Math.max(0.15d, 1.0d - distance * NETWORK_LOSS_PER_CELL);
            donor.biomass(donor.biomass() - moved);
            recipient.biomass(Math.min(storage(recipient.kind()), recipient.biomass() + retained));
            ReferenceHiveEconomyEntry ledger = model.nestEconomy.get(recipient.id());
            ledger.addSubstrateIn(moved); ledger.addBiomassIncome(retained);
            model.networkFlows.add(new ReferenceNetworkFlow(day, "organ", donor.id(), donor.x(), donor.y(), recipient.id(), moved, retained,
                    moved - retained, distance));
        }
    }

    private static boolean inside(ReferenceInfectionModel model, int x, int y) { return x >= 0 && x < model.width && y >= 0 && y < model.height; }
    private static boolean isReceiver(ReferenceHiveOrgan organ) { return organ.kind() == ReferenceOrganKind.CORE || organ.kind() == ReferenceOrganKind.DIGESTIVE_POOL; }
    private static ReferenceHiveOrgan closest(List<ReferenceHiveOrgan> organs, int x, int y) {
        ReferenceHiveOrgan result = null; double distance = Double.POSITIVE_INFINITY;
        for (ReferenceHiveOrgan organ : organs) { double candidate = Math.hypot(organ.x() - x, organ.y() - y); if (candidate < distance) { result = organ; distance = candidate; } }
        return result;
    }
    private static ReferenceHiveOrgan richest(List<ReferenceHiveOrgan> organs) {
        ReferenceHiveOrgan result = null; double available = Double.NEGATIVE_INFINITY;
        for (ReferenceHiveOrgan organ : organs) { double candidate = organ.biomass() - storage(organ.kind()) * ORGAN_TRANSFER_RESERVE_FRACTION; if (candidate > available) { result = organ; available = candidate; } }
        return result;
    }
    private static int priority(ReferenceOrganKind kind) { return kind == ReferenceOrganKind.BROOD_SAC ? 0 : kind == ReferenceOrganKind.SPORULATOR ? 1 : kind == ReferenceOrganKind.SYNAPSE ? 2 : 3; }
    private static double maintenance(ReferenceOrganKind kind) { return switch (kind) { case CORE -> 0.55d; case SYNAPSE -> 0.32d; case DIGESTIVE_POOL -> 0.48d; case BROOD_SAC -> 0.62d; case SPORULATOR -> 0.44d; }; }
    private static double storage(ReferenceOrganKind kind) { return switch (kind) { case CORE -> 220.0d; case SYNAPSE -> 100.0d; case DIGESTIVE_POOL -> 360.0d; case BROOD_SAC -> 170.0d; case SPORULATOR -> 130.0d; }; }
    private static double susceptibility(ReferenceBiome biome) { return switch (biome) { case PLAINS -> 1.0d; case FOREST -> 1.10d; case MOUNTAINS -> 0.70d; case WETLAND -> 1.25d; case BARREN -> 0.82d; }; }
    private static List<List<Double>> zeroes(int width, int height) {
        List<List<Double>> result = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            List<Double> row = new ArrayList<>();
            for (int x = 0; x < width; x++) row.add(0.0d);
            result.add(row);
        }
        return result;
    }
}
