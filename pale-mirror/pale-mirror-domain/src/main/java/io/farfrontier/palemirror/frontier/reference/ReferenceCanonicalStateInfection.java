package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Source-shaped owner mapper for Python's {@code InfectionModel}. */
final class ReferenceCanonicalStateInfection {
    private ReferenceCanonicalStateInfection() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        return infection(required.infection());
    }

    private static Map<String, Object> infection(ReferenceInfectionModel value) {
        return typed("simulation.infection.InfectionModel", "attributes", object(
                "_next_nest_id", value.nextOrganId(),
                "_next_swarm_id", value.nextSwarmSequence(),
                "biomes", biomes(value.biomes),
                "combat_scale", value.combatScale(),
                "damage_memory", stringDoubleMap(value.damageMemory()),
                "discrete_bioforms", value.discreteBioforms(),
                "ecosystem", ecosystem(value.ecosystem(), value.biomes),
                "genome", stringDoubleMap(value.genome()),
                "growth_rate", value.growthRate(),
                "harvested_biomass", value.harvestedBiomass(),
                "harvested_genetic_material", value.harvestedGeneticMaterial(),
                "height", value.height(),
                "intents", sequence("list", value.intents().stream().map(ReferenceCanonicalStateInfection::intent).toList()),
                "latent_colonies", sequence("list", value.latentColonies().stream().map(ReferenceCanonicalStateInfection::latent).toList()),
                "level", levels(value.level),
                "max_swarms", value.maxSwarms(),
                "nest_economy", nestEconomy(value.nestEconomy()),
                "nest_economy_history", sequence("list", value.nestEconomyHistory().stream()
                        .map(ReferenceCanonicalStateInfection::economySnapshot).toList()),
                "nest_projects", sequence("list", value.nestProjects().stream().map(ReferenceCanonicalStateInfection::project).toList()),
                "nests", organs(value.organs()),
                "network_flows", sequence("list", value.networkFlows().stream().map(ReferenceCanonicalStateInfection::flow).toList()),
                "pending_exploitation", sequence("list", value.pendingExploitation().stream()
                        .map(ReferenceCanonicalStateInfection::exploitation).toList()),
                "project_history", sequence("list", value.projectHistory().stream().map(ReferenceCanonicalStateInfection::history).toList()),
                "rng", random(value.rng),
                "spawn_threshold", value.spawnThreshold(),
                "spread_rate", value.spreadRate(),
                "swarms", sequence("list", value.swarms().stream().map(ReferenceCanonicalStateInfection::swarm).toList()),
                "width", value.width()));
    }

    private static Map<String, Object> ecosystem(ReferenceEcosystem value, List<List<ReferenceBiome>> biomes) {
        List<Object> rows = new ArrayList<>();
        for (int y = 0; y < value.height(); y++) {
            List<Object> row = new ArrayList<>();
            for (int x = 0; x < value.width(); x++) row.add(cell(value.cell(x, y)));
            rows.add(sequence("list", row));
        }
        return typed("simulation.ecology.Ecosystem", "attributes", object(
                "biomes", biomes(biomes), "cells", sequence("list", rows), "height", value.height(), "width", value.width()));
    }

    private static Map<String, Object> cell(ReferenceEcosystemCell value) {
        return typed("simulation.ecology.EcosystemCell", "fields", object(
                "flora", value.flora(), "fauna", value.fauna(), "detritus", value.detritus(),
                "nutrients", value.nutrients(), "moisture", value.moisture(), "scar", value.scar()));
    }

    private static Map<String, Object> organs(Map<Integer, ReferenceHiveOrgan> values) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceHiveOrgan> entry : values.entrySet()) pairs.add(pair(entry.getKey(), organ(entry.getValue())));
        return map(pairs);
    }

    private static Map<String, Object> organ(ReferenceHiveOrgan value) {
        return typed("simulation.infection.Nest", "fields", object(
                "id", value.id(), "x", value.x(), "y", value.y(), "biomass", value.biomass(), "samples", value.samples(),
                "mutations", mutationMap(value.mutations()), "last_project_day", value.lastProjectDay(), "colony_id", value.colonyId(),
                "parent_nest_id", value.parentNestId(), "role", value.role(), "kind", organKind(value.kind()),
                "vitality", value.vitality(), "feral", value.feral()));
    }

    private static Map<String, Object> latent(ReferenceLatentColony value) {
        return typed("simulation.infection.LatentColony", "fields", object(
                "x", value.x(), "y", value.y(), "spores", value.spores(), "strength", value.strength(),
                "memory", stringDoubleMap(value.memory()), "source_nest_id", value.sourceOrganId()));
    }

    private static Map<String, Object> project(ReferenceNestProject value) {
        return typed("simulation.infection.NestProject", "fields", object(
                "source_nest_id", value.sourceOrganId(), "x", value.x(), "y", value.y(), "days_remaining", value.daysRemaining(),
                "kind", organKind(value.kind()), "committed_biomass", value.committedBiomass()));
    }

    private static Map<String, Object> flow(ReferenceNetworkFlow value) {
        return typed("simulation.infection.NetworkFlow", "fields", object(
                "day", value.day(), "source_kind", value.sourceKind(), "source_id", value.sourceId(), "source_x", sourceCoordinate(value, value.sourceX()),
                "source_y", sourceCoordinate(value, value.sourceY()), "nest_id", value.organId(), "amount", value.amount(), "retained", value.retained(),
                "loss", value.loss(), "distance", value.distance()));
    }

    private static Map<String, Object> swarm(ReferenceSwarm value) {
        return typed("simulation.infection.Swarm", "fields", object(
                "id", value.id(), "x", value.x(), "y", value.y(), "power", value.power(), "target_id", value.targetId(),
                "speed", value.speed(), "kind", bioformKind(value.kind()), "composition", bioformMap(value.composition()),
                "phase", formationPhase(value.phase()), "readiness", value.readiness(), "losses", bioformMap(value.losses()),
                "source_nest_id", value.sourceOrganId(), "target_x", value.targetX(), "target_y", value.targetY(), "cargo", value.cargo(),
                "genetic_cargo", value.geneticCargo(), "state", value.state(), "forage_x", value.forageX(), "forage_y", value.forageY(),
                "feral", value.feral()));
    }

    private static Map<String, Object> intent(ReferenceHiveIntent value) {
        return typed("simulation.infection.HiveIntent", "fields", object(
                "kind", value.kind(), "source_id", value.sourceId(), "target_x", value.targetX(), "target_y", value.targetY(),
                "target_id", value.targetId(), "until_day", value.untilDay(), "reason", value.reason()));
    }

    private static Map<String, Object> nestEconomy(Map<Integer, ReferenceHiveEconomyEntry> values) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceHiveEconomyEntry> entry : values.entrySet()) {
            ReferenceHiveEconomyEntry value = entry.getValue();
            pairs.add(pair(entry.getKey(), mapping(
                    "day", (double) value.day(), "nest_id", (double) value.organId(), "opening_biomass", value.openingBiomass(),
                    "substrate_in", value.substrateIn(), "biomass_income", value.biomassIncome(), "samples_in", value.samplesIn(),
                    "maintenance", value.maintenance())));
        }
        return map(pairs);
    }

    private static Map<String, Object> economySnapshot(ReferenceHiveEconomySnapshot value) {
        List<Object> entries = new ArrayList<>();
        if (value.openingBiomass() != null) Collections.addAll(entries,
                "opening_biomass", value.openingBiomass(), "substrate_in", value.substrateIn(), "biomass_income", value.biomassIncome(),
                "samples_in", value.samplesIn(), "maintenance", value.maintenance());
        Collections.addAll(entries, "day", (double) value.day(), "nest_id", (double) value.organId(), "biomass", value.biomass(),
                "samples", value.samples(), "vitality", value.vitality());
        return mapping(entries.toArray());
    }

    private static Map<String, Object> history(ReferenceHiveHistoryEvent value) {
        List<Object> entries = new ArrayList<>(List.of(
                "day", value.day(), "kind", value.kind(), "source_nest_id", value.sourceOrganId(), "x", value.x(), "y", value.y()));
        if (value.organId() != null) entries.addAll(List.of("nest_id", value.organId()));
        return mapping(entries.toArray());
    }

    private static Map<String, Object> exploitation(ReferenceExploitationSite value) {
        return mapping("x", value.x(), "y", value.y(), "mass", value.mass(), "genes", value.genes(), "day", value.day(),
                "source_nest_id", value.sourceOrganId());
    }

    /** Python preserves integer cell coordinates except for explicitly float harvester forage coordinates. */
    private static Number sourceCoordinate(ReferenceNetworkFlow value, double coordinate) {
        if (value.sourceKind().equals("harvester")) return Double.valueOf(coordinate);
        return Integer.valueOf((int) coordinate);
    }

    private static Map<String, Object> levels(double[][] values) {
        List<Object> rows = new ArrayList<>();
        for (double[] row : values) {
            List<Double> encoded = new ArrayList<>(row.length);
            for (double value : row) encoded.add(value);
            rows.add(sequence("list", encoded));
        }
        return sequence("list", rows);
    }

    private static Map<String, Object> biomes(List<List<ReferenceBiome>> values) {
        List<Object> rows = new ArrayList<>();
        for (List<ReferenceBiome> row : values) rows.add(sequence("list", row.stream().map(ReferenceCanonicalStateInfection::biome).toList()));
        return sequence("list", rows);
    }

    private static Map<String, Object> mutationMap(Map<ReferenceMutation, Integer> values) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<ReferenceMutation, Integer> entry : values.entrySet()) pairs.add(pair(mutation(entry.getKey()), entry.getValue()));
        return map(pairs);
    }

    private static Map<String, Object> bioformMap(Map<ReferenceBioformKind, Double> values) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<ReferenceBioformKind, Double> entry : values.entrySet()) pairs.add(pair(bioformKind(entry.getKey()), entry.getValue()));
        return map(pairs);
    }

    private static Map<String, Object> stringDoubleMap(Map<String, Double> values) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<String, Double> entry : values.entrySet()) pairs.add(pair(entry.getKey(), entry.getValue()));
        return map(pairs);
    }

    private static Map<String, Object> random(PythonRandom value) {
        long[] words = value.state().words();
        List<Long> state = new ArrayList<>(words.length);
        for (long word : words) state.add(word);
        return object("$random_mt19937", object("version", 3, "state", List.copyOf(state), "gaussian_cache", null));
    }

    private static Map<String, Object> biome(ReferenceBiome value) { return enumValue("simulation.ecology.Biome", value.name().toLowerCase()); }
    private static Map<String, Object> mutation(ReferenceMutation value) { return enumValue("simulation.infection.Mutation", value.name().toLowerCase()); }
    private static Map<String, Object> organKind(ReferenceOrganKind value) { return enumValue("simulation.infection.OrganKind", value.name().toLowerCase()); }
    private static Map<String, Object> bioformKind(ReferenceBioformKind value) { return enumValue("simulation.infection.BioformKind", value.id()); }
    private static Map<String, Object> formationPhase(ReferenceFormationPhase value) { return enumValue("simulation.formations.FormationPhase", value.id()); }
    private static Map<String, Object> enumValue(String type, String value) { return object("$enum", type, "value", value); }
    private static Map<String, Object> typed(String type, String fieldName, Map<String, Object> values) { return object("$type", type, fieldName, values); }
    private static Map<String, Object> sequence(String kind, List<?> items) { return object("$sequence", kind, "items", List.copyOf(items)); }

    private static Map<String, Object> mapping(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("mapping entries must be pairs");
        List<List<Object>> pairs = new ArrayList<>();
        for (int index = 0; index < entries.length; index += 2) pairs.add(pair(entries[index], entries[index + 1]));
        return map(pairs);
    }

    private static Map<String, Object> map(List<List<Object>> pairs) {
        pairs.sort(Comparator.comparing(pair -> ReferenceV2PublicSnapshot.canonicalJson(pair.getFirst())));
        return object("$map", List.copyOf(pairs));
    }

    private static List<Object> pair(Object key, Object value) {
        ArrayList<Object> result = new ArrayList<>(2);
        result.add(key); result.add(value);
        return Collections.unmodifiableList(result);
    }

    private static Map<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return Collections.unmodifiableMap(result);
    }
}
