package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict, lossless hydration of InfectionModel and its discrete-body extension. */
final class ReferenceGrayboxStateInfection {
    private ReferenceGrayboxStateInfection() { }

    static State read(Object encoded, int snapshotDay) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.infection.InfectionModel", "attributes");
        ReferenceGrayboxStateReader.exactKeys(fields, "infection attributes", "_next_nest_id", "_next_swarm_id", "biomes", "combat_scale",
                "damage_memory", "discrete_bioforms", "ecosystem", "genome", "growth_rate", "harvested_biomass", "harvested_genetic_material",
                "height", "intents", "latent_colonies", "level", "max_swarms", "nest_economy", "nest_economy_history", "nest_projects", "nests",
                "network_flows", "pending_exploitation", "project_history", "rng", "spawn_threshold", "spread_rate", "swarms", "width");
        int width = ReferenceGrayboxStateReader.integer(fields.get("width"), "infection width");
        int height = ReferenceGrayboxStateReader.integer(fields.get("height"), "infection height");
        if (width < 1 || height < 1 || snapshotDay < 0) throw new IllegalArgumentException("infection dimensions or day are invalid");
        List<List<ReferenceBiome>> biomes = ReferenceGrayboxStateInfectionRecords.biomes(fields.get("biomes"), width, height, "infection biomes");
        List<List<CellState>> cells = ecosystem(fields.get("ecosystem"), biomes, width, height);
        double[][] level = levels(fields.get("level"), width, height);
        LinkedHashMap<Integer, ReferenceHiveOrgan> organs = ReferenceGrayboxStateInfectionRecords.organs(fields.get("nests"), width, height);
        List<ReferenceSwarm> swarms = ReferenceGrayboxStateInfectionRecords.swarms(fields.get("swarms"), organs, width, height);
        int nextOrganId = ReferenceGrayboxStateReader.integer(fields.get("_next_nest_id"), "next organ id");
        int nextSwarmId = ReferenceGrayboxStateReader.integer(fields.get("_next_swarm_id"), "next swarm id");
        if (nextOrganId < 1 || nextSwarmId < 1 || organs.keySet().stream().anyMatch(id -> id >= nextOrganId)
                || swarms.stream().anyMatch(swarm -> swarm.id() >= nextSwarmId)) throw new IllegalArgumentException("infection sequence is stale");
        return new State(width, height, ReferenceGrayboxStateReader.number(fields.get("combat_scale"), "infection combat scale"),
                ReferenceGrayboxStateReader.bool(fields.get("discrete_bioforms"), "infection discrete bioforms"),
                ReferenceGrayboxStateReader.randomState(fields.get("rng"), "infection rng"), biomes, cells, level, organs,
                ReferenceGrayboxStateInfectionRecords.stringDoubles(fields.get("genome"), "infection genome"),
                ReferenceGrayboxStateInfectionRecords.stringDoubles(fields.get("damage_memory"), "infection damage memory"),
                ReferenceGrayboxStateInfectionRecords.latentColonies(fields.get("latent_colonies"), width, height),
                ReferenceGrayboxStateInfectionRecords.projects(fields.get("nest_projects"), organs, width, height),
                ReferenceGrayboxStateInfectionRecords.intents(fields.get("intents"), width, height),
                ReferenceGrayboxStateInfectionRecords.flows(fields.get("network_flows"), organs, width, height),
                ReferenceGrayboxStateInfectionRecords.nestEconomy(fields.get("nest_economy"), organs),
                ReferenceGrayboxStateInfectionRecords.economyHistory(fields.get("nest_economy_history")),
                ReferenceGrayboxStateInfectionRecords.history(fields.get("project_history"), width, height),
                ReferenceGrayboxStateInfectionRecords.exploitation(fields.get("pending_exploitation"), width, height), swarms,
                ReferenceGrayboxStateReader.number(fields.get("harvested_biomass"), "harvested biomass"),
                ReferenceGrayboxStateReader.number(fields.get("harvested_genetic_material"), "harvested genetic material"),
                ReferenceGrayboxStateReader.number(fields.get("growth_rate"), "infection growth rate"),
                ReferenceGrayboxStateReader.number(fields.get("spread_rate"), "infection spread rate"),
                ReferenceGrayboxStateReader.number(fields.get("spawn_threshold"), "infection spawn threshold"),
                ReferenceGrayboxStateReader.integer(fields.get("max_swarms"), "infection max swarms"), nextOrganId, nextSwarmId, snapshotDay);
    }

    static Map<Integer, Map<ReferenceBioformKind, List<String>>> bioformIdentities(Object encoded, List<ReferenceSwarm> swarms) {
        return ReferenceGrayboxStateInfectionRecords.bioformIdentities(encoded, swarms);
    }

    private static List<List<CellState>> ecosystem(Object encoded, List<List<ReferenceBiome>> biomes, int width, int height) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.ecology.Ecosystem", "attributes");
        ReferenceGrayboxStateReader.exactKeys(fields, "ecosystem attributes", "biomes", "cells", "height", "width");
        if (ReferenceGrayboxStateReader.integer(fields.get("width"), "ecosystem width") != width
                || ReferenceGrayboxStateReader.integer(fields.get("height"), "ecosystem height") != height
                || !biomes.equals(ReferenceGrayboxStateInfectionRecords.biomes(fields.get("biomes"), width, height, "ecosystem biomes"))) {
            throw new IllegalArgumentException("ecosystem topology differs from infection topology");
        }
        List<Object> rows = ReferenceGrayboxStateReader.sequence(fields.get("cells"), "list", "ecosystem cells");
        if (rows.size() != height) throw new IllegalArgumentException("ecosystem cell height differs from infection topology");
        List<List<CellState>> result = new ArrayList<>(height);
        for (int y = 0; y < height; y++) {
            List<Object> row = ReferenceGrayboxStateReader.sequence(rows.get(y), "list", "ecosystem row");
            if (row.size() != width) throw new IllegalArgumentException("ecosystem cell width differs from infection topology");
            List<CellState> cells = new ArrayList<>(width);
            for (Object cell : row) cells.add(CellState.read(cell));
            result.add(List.copyOf(cells));
        }
        return List.copyOf(result);
    }

    private static double[][] levels(Object encoded, int width, int height) {
        List<Object> rows = ReferenceGrayboxStateReader.sequence(encoded, "list", "infection level");
        if (rows.size() != height) throw new IllegalArgumentException("infection level height differs from topology");
        double[][] result = new double[height][width];
        for (int y = 0; y < height; y++) {
            List<Object> row = ReferenceGrayboxStateReader.sequence(rows.get(y), "list", "infection level row");
            if (row.size() != width) throw new IllegalArgumentException("infection level width differs from topology");
            for (int x = 0; x < width; x++) result[y][x] = ReferenceGrayboxStateReader.number(row.get(x), "infection level");
        }
        return result;
    }

    record State(
            int width, int height, double combatScale, boolean discreteBioforms, PythonRandom.State rng, List<List<ReferenceBiome>> biomes,
            List<List<CellState>> cells, double[][] level, LinkedHashMap<Integer, ReferenceHiveOrgan> organs, LinkedHashMap<String, Double> genome,
            LinkedHashMap<String, Double> damageMemory, List<ReferenceLatentColony> latentColonies, List<ReferenceNestProject> projects,
            List<ReferenceHiveIntent> intents, List<ReferenceNetworkFlow> flows, LinkedHashMap<Integer, ReferenceHiveEconomyEntry> nestEconomy,
            List<ReferenceHiveEconomySnapshot> economyHistory, List<ReferenceHiveHistoryEvent> history, List<ReferenceExploitationSite> exploitation,
            List<ReferenceSwarm> swarms, double harvestedBiomass, double harvestedGeneticMaterial, double growthRate, double spreadRate,
            double spawnThreshold, int maxSwarms, int nextOrganId, int nextSwarmId, int snapshotDay
    ) {
        State {
            biomes = biomes.stream().map(List::copyOf).toList(); cells = cells.stream().map(List::copyOf).toList();
            level = copy(level); organs = new LinkedHashMap<>(organs); genome = new LinkedHashMap<>(genome); damageMemory = new LinkedHashMap<>(damageMemory);
            latentColonies = List.copyOf(latentColonies); projects = List.copyOf(projects); intents = List.copyOf(intents); flows = List.copyOf(flows);
            nestEconomy = new LinkedHashMap<>(nestEconomy); economyHistory = List.copyOf(economyHistory); history = List.copyOf(history);
            exploitation = List.copyOf(exploitation); swarms = List.copyOf(swarms);
            if (maxSwarms < 0) throw new IllegalArgumentException("infection max swarms cannot be negative");
        }

        void applyTo(ReferenceInfectionModel model, Map<Integer, Map<ReferenceBioformKind, List<String>>> identities) {
            if (model.width != width || model.height != height || Double.doubleToLongBits(model.combatScale()) != Double.doubleToLongBits(combatScale)
                    || model.discreteBioforms() != discreteBioforms || !model.biomes.equals(biomes)) throw new IllegalArgumentException("infection target is incompatible");
            if (!identities.keySet().equals(swarms.stream().map(ReferenceSwarm::id).collect(java.util.stream.Collectors.toSet()))) {
                throw new IllegalArgumentException("bioform identities do not cover infection swarms");
            }
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) cells.get(y).get(x).requireCompatible(model.ecosystem.cell(x, y));
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) cells.get(y).get(x).applyTo(model.ecosystem.cell(x, y));
            model.rng.restore(rng); model.level = copy(level); model.organs.clear(); model.organs.putAll(organs);
            model.genome.clear(); model.genome.putAll(genome); model.damageMemory.clear(); model.damageMemory.putAll(damageMemory);
            replace(model.latentColonies, latentColonies); replace(model.nestProjects, projects); replace(model.intents, intents); replace(model.networkFlows, flows);
            model.nestEconomy.clear(); model.nestEconomy.putAll(nestEconomy); replace(model.nestEconomyHistory, economyHistory);
            replace(model.projectHistory, history); replace(model.pendingExploitation, exploitation); replace(model.swarms, swarms);
            for (ReferenceSwarm swarm : model.swarms) swarm.restoreBioformIds(identities.get(swarm.id()));
            model.harvestedBiomass = harvestedBiomass; model.harvestedGeneticMaterial = harvestedGeneticMaterial; model.growthRate = growthRate;
            model.spreadRate = spreadRate; model.spawnThreshold = spawnThreshold; model.maxSwarms = maxSwarms; model.nextOrganId = nextOrganId;
            model.nextSwarmId = nextSwarmId; model.lastEconomySnapshotDay = snapshotDay;
        }

        private static double[][] copy(double[][] source) {
            double[][] result = new double[source.length][];
            for (int index = 0; index < source.length; index++) result[index] = source[index].clone();
            return result;
        }

        private static <T> void replace(List<T> target, List<T> values) { target.clear(); target.addAll(values); }
    }

    record CellState(double flora, double fauna, double detritus, double nutrients, double moisture, double scar) {
        static CellState read(Object encoded) {
            Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.ecology.EcosystemCell", "fields");
            ReferenceGrayboxStateReader.exactKeys(fields, "ecosystem cell", "flora", "fauna", "detritus", "nutrients", "moisture", "scar");
            return new CellState(ReferenceGrayboxStateReader.number(fields.get("flora"), "cell flora"), ReferenceGrayboxStateReader.number(fields.get("fauna"), "cell fauna"),
                    ReferenceGrayboxStateReader.number(fields.get("detritus"), "cell detritus"), ReferenceGrayboxStateReader.number(fields.get("nutrients"), "cell nutrients"),
                    ReferenceGrayboxStateReader.number(fields.get("moisture"), "cell moisture"), ReferenceGrayboxStateReader.number(fields.get("scar"), "cell scar"));
        }

        void applyTo(ReferenceEcosystemCell cell) {
            requireCompatible(cell);
            cell.flora(flora); cell.fauna(fauna); cell.detritus(detritus); cell.nutrients(nutrients); cell.scar(scar);
        }

        void requireCompatible(ReferenceEcosystemCell cell) {
            if (Double.doubleToLongBits(cell.moisture()) != Double.doubleToLongBits(moisture)) throw new IllegalArgumentException("ecosystem moisture is incompatible");
        }
    }
}
