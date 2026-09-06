package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Source-shaped value readers for the InfectionModel's nested records. */
final class ReferenceGrayboxStateInfectionRecords {
    private ReferenceGrayboxStateInfectionRecords() { }

    static List<List<ReferenceBiome>> biomes(Object encoded, int width, int height, String label) {
        List<Object> rows = ReferenceGrayboxStateReader.sequence(encoded, "list", label);
        if (rows.size() != height) throw new IllegalArgumentException(label + " height differs from topology");
        List<List<ReferenceBiome>> result = new ArrayList<>(height);
        for (Object encodedRow : rows) {
            List<Object> row = ReferenceGrayboxStateReader.sequence(encodedRow, "list", label + " row");
            if (row.size() != width) throw new IllegalArgumentException(label + " width differs from topology");
            List<ReferenceBiome> values = new ArrayList<>(width);
            for (Object item : row) values.add(biome(item, label + " biome"));
            result.add(List.copyOf(values));
        }
        return List.copyOf(result);
    }

    static LinkedHashMap<String, Double> stringDoubles(Object encoded, String label) {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            String key = ReferenceGrayboxStateReader.string(entry.key(), label + " key");
            if (result.putIfAbsent(key, ReferenceGrayboxStateReader.number(entry.value(), label + " value")) != null) {
                throw new IllegalArgumentException(label + " has duplicate " + key);
            }
        }
        return result;
    }

    static LinkedHashMap<Integer, ReferenceHiveOrgan> organs(Object encoded, int width, int height) {
        LinkedHashMap<Integer, ReferenceHiveOrgan> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "infection organs")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "organ map key");
            Map<String, Object> fields = typed(entry.value(), "simulation.infection.Nest", "organ", "id", "x", "y", "biomass", "samples",
                    "mutations", "last_project_day", "colony_id", "parent_nest_id", "role", "kind", "vitality", "feral");
            int x = coordinate(fields.get("x"), width, "organ x"), y = coordinate(fields.get("y"), height, "organ y");
            ReferenceHiveOrgan organ = new ReferenceHiveOrgan(ReferenceGrayboxStateReader.integer(fields.get("id"), "organ id"), x, y,
                    number(fields.get("biomass"), "organ biomass"), number(fields.get("samples"), "organ samples"),
                    ReferenceGrayboxStateReader.nullableInteger(fields.get("parent_nest_id"), "organ parent"), organKind(fields.get("kind"), "organ kind"));
            if (organ.id() != id || id < 1 || result.putIfAbsent(id, organ) != null) throw new IllegalArgumentException("duplicate organ " + id);
            for (ReferenceGrayboxStateReader.Entry mutation : ReferenceGrayboxStateReader.mapEntries(fields.get("mutations"), "organ mutations")) {
                ReferenceMutation kind = mutation(mutation.key(), "organ mutation");
                int level = ReferenceGrayboxStateReader.integer(mutation.value(), "organ mutation level");
                if (level < 0 || organ.mutations().containsKey(kind)) throw new IllegalArgumentException("organ mutation is invalid");
                organ.mutation(kind, level);
            }
            organ.lastProjectDay(ReferenceGrayboxStateReader.integer(fields.get("last_project_day"), "organ project day"));
            organ.colonyId(ReferenceGrayboxStateReader.nullableInteger(fields.get("colony_id"), "organ colony"));
            organ.role(ReferenceGrayboxStateReader.string(fields.get("role"), "organ role"));
            organ.vitality(number(fields.get("vitality"), "organ vitality"));
            organ.feral(ReferenceGrayboxStateReader.bool(fields.get("feral"), "organ feral"));
        }
        return result;
    }

    static List<ReferenceSwarm> swarms(Object encoded, Map<Integer, ReferenceHiveOrgan> organs, int width, int height) {
        LinkedHashMap<Integer, ReferenceSwarm> result = new LinkedHashMap<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "infection swarms")) {
            Map<String, Object> fields = typed(item, "simulation.infection.Swarm", "swarm", "id", "x", "y", "power", "target_id", "speed",
                    "kind", "composition", "phase", "readiness", "losses", "source_nest_id", "target_x", "target_y", "cargo", "genetic_cargo",
                    "state", "forage_x", "forage_y", "feral");
            int id = ReferenceGrayboxStateReader.integer(fields.get("id"), "swarm id");
            Integer source = ReferenceGrayboxStateReader.nullableInteger(fields.get("source_nest_id"), "swarm source organ");
            // A swarm retains its originating nest identity after that nest is
            // destroyed.  This is source behaviour: a returning harvester
            // without a live origin falls back to the nearest viable receiver.
            // The ID is therefore historical provenance, not a live ownership
            // reference, and must survive hydration even when its organ is gone.
            if (id < 1 || result.containsKey(id)) throw new IllegalArgumentException("swarm identity is invalid");
            ReferenceSwarm swarm = new ReferenceSwarm(id, number(fields.get("x"), "swarm x"), number(fields.get("y"), "swarm y"),
                    number(fields.get("power"), "swarm power"), ReferenceGrayboxStateReader.integer(fields.get("target_id"), "swarm target id"),
                    number(fields.get("speed"), "swarm speed"), bioformKind(fields.get("kind"), "swarm kind"),
                    bioforms(fields.get("composition"), "swarm composition", true), phase(fields.get("phase"), "swarm phase"),
                    number(fields.get("readiness"), "swarm readiness"), source,
                    nullableCoordinate(fields.get("target_x"), width, "swarm target x"), nullableCoordinate(fields.get("target_y"), height, "swarm target y"),
                    ReferenceGrayboxStateReader.bool(fields.get("feral"), "swarm feral"));
            swarm.restoreLosses(bioforms(fields.get("losses"), "swarm losses", false));
            swarm.cargo(number(fields.get("cargo"), "swarm cargo")); swarm.geneticCargo(number(fields.get("genetic_cargo"), "swarm genetic cargo"));
            swarm.state(ReferenceGrayboxStateReader.string(fields.get("state"), "swarm state"));
            swarm.forageX(nullableCoordinate(fields.get("forage_x"), width, "swarm forage x"));
            swarm.forageY(nullableCoordinate(fields.get("forage_y"), height, "swarm forage y"));
            result.put(id, swarm);
        }
        return List.copyOf(result.values());
    }

    static List<ReferenceLatentColony> latentColonies(Object encoded, int width, int height) {
        List<ReferenceLatentColony> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "latent colonies")) {
            Map<String, Object> fields = typed(item, "simulation.infection.LatentColony", "latent colony", "x", "y", "spores", "strength", "memory", "source_nest_id");
            result.add(new ReferenceLatentColony(coordinate(fields.get("x"), width, "latent x"), coordinate(fields.get("y"), height, "latent y"),
                    number(fields.get("spores"), "latent spores"), number(fields.get("strength"), "latent strength"),
                    stringDoubles(fields.get("memory"), "latent memory"), ReferenceGrayboxStateReader.nullableInteger(fields.get("source_nest_id"), "latent source")));
        }
        return List.copyOf(result);
    }

    static List<ReferenceNestProject> projects(Object encoded, int nextOrganId, int width, int height) {
        List<ReferenceNestProject> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "organ projects")) {
            Map<String, Object> fields = typed(item, "simulation.infection.NestProject", "organ project", "source_nest_id", "x", "y", "days_remaining", "kind", "committed_biomass");
            int source = ReferenceGrayboxStateReader.integer(fields.get("source_nest_id"), "project source");
            int days = ReferenceGrayboxStateReader.integer(fields.get("days_remaining"), "project days remaining");
            // A player may destroy the source organ between source days.  The
            // source project then remains in the saved state until the next
            // daily lifecycle deterministically records its cancellation.
            if (source < 1 || source >= nextOrganId || days < 0) throw new IllegalArgumentException("organ project is invalid");
            result.add(new ReferenceNestProject(source, coordinate(fields.get("x"), width, "project x"), coordinate(fields.get("y"), height, "project y"), days,
                    organKind(fields.get("kind"), "project kind"), number(fields.get("committed_biomass"), "project biomass")));
        }
        return List.copyOf(result);
    }

    static List<ReferenceHiveIntent> intents(Object encoded, int width, int height) {
        List<ReferenceHiveIntent> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "hive intents")) {
            Map<String, Object> fields = typed(item, "simulation.infection.HiveIntent", "hive intent", "kind", "source_id", "target_x", "target_y", "target_id", "until_day", "reason");
            result.add(new ReferenceHiveIntent(ReferenceGrayboxStateReader.string(fields.get("kind"), "intent kind"),
                    ReferenceGrayboxStateReader.integer(fields.get("source_id"), "intent source"), coordinate(fields.get("target_x"), width, "intent x"),
                    coordinate(fields.get("target_y"), height, "intent y"), ReferenceGrayboxStateReader.nullableInteger(fields.get("target_id"), "intent target"),
                    ReferenceGrayboxStateReader.integer(fields.get("until_day"), "intent day"), ReferenceGrayboxStateReader.string(fields.get("reason"), "intent reason")));
        }
        return List.copyOf(result);
    }

    static List<ReferenceNetworkFlow> flows(Object encoded, int nextOrganId, int width, int height) {
        List<ReferenceNetworkFlow> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "infection network flows")) {
            Map<String, Object> fields = typed(item, "simulation.infection.NetworkFlow", "network flow", "day", "source_kind", "source_id", "source_x", "source_y",
                    "nest_id", "amount", "retained", "loss", "distance");
            String sourceKind = ReferenceGrayboxStateReader.string(fields.get("source_kind"), "flow source kind");
            int organId = ReferenceGrayboxStateReader.integer(fields.get("nest_id"), "flow organ id");
            // Flows are the completed daily accounting report.  An immediate
            // physical organ casualty makes this ID historical provenance
            // until the following digest clears the report.
            if (organId < 1 || organId >= nextOrganId) throw new IllegalArgumentException("flow organ was never issued");
            double sourceX = sourceCoordinate(fields.get("source_x"), sourceKind, width, "flow source x");
            double sourceY = sourceCoordinate(fields.get("source_y"), sourceKind, height, "flow source y");
            result.add(new ReferenceNetworkFlow(ReferenceGrayboxStateReader.integer(fields.get("day"), "flow day"), sourceKind,
                    ReferenceGrayboxStateReader.integer(fields.get("source_id"), "flow source id"), sourceX, sourceY, organId,
                    number(fields.get("amount"), "flow amount"), number(fields.get("retained"), "flow retained"),
                    number(fields.get("loss"), "flow loss"), number(fields.get("distance"), "flow distance")));
        }
        return List.copyOf(result);
    }

    static LinkedHashMap<Integer, ReferenceHiveEconomyEntry> nestEconomy(Object encoded, int nextOrganId) {
        LinkedHashMap<Integer, ReferenceHiveEconomyEntry> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "nest economy")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "nest economy key");
            Map<String, Object> fields = mapping(entry.value(), "nest economy row", "day", "nest_id", "opening_biomass", "substrate_in", "biomass_income", "samples_in", "maintenance");
            int rowId = integral(fields.get("nest_id"), "nest economy organ");
            // This is the same daily report as networkFlows.  Its key remains
            // a report subject after an in-between-days organ casualty.
            if (id < 1 || id >= nextOrganId || id != rowId || result.containsKey(id)) {
                throw new IllegalArgumentException("nest economy owner is invalid");
            }
            ReferenceHiveEconomyEntry value = new ReferenceHiveEconomyEntry(integral(fields.get("day"), "nest economy day"), id,
                    number(fields.get("opening_biomass"), "nest economy opening biomass"));
            value.addSubstrateIn(number(fields.get("substrate_in"), "nest economy substrate")); value.addBiomassIncome(number(fields.get("biomass_income"), "nest economy income"));
            value.addSamplesIn(number(fields.get("samples_in"), "nest economy samples")); value.maintenance(number(fields.get("maintenance"), "nest economy maintenance"));
            result.put(id, value);
        }
        return result;
    }

    static List<ReferenceHiveEconomySnapshot> economyHistory(Object encoded) {
        List<ReferenceHiveEconomySnapshot> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "nest economy history")) {
            Map<String, Object> fields = ReferenceGrayboxStateReader.stringMap(item, "nest economy snapshot");
            Set<String> standard = Set.of("day", "nest_id", "biomass", "samples", "vitality");
            Set<String> detailed = Set.of("opening_biomass", "substrate_in", "biomass_income", "samples_in", "maintenance");
            if (!fields.keySet().containsAll(standard) || !(fields.keySet().equals(standard) || fields.keySet().equals(union(standard, detailed)))) {
                throw new IllegalArgumentException("nest economy snapshot fields are invalid");
            }
            boolean hasLedger = fields.containsKey("opening_biomass");
            result.add(new ReferenceHiveEconomySnapshot(integral(fields.get("day"), "economy snapshot day"), integral(fields.get("nest_id"), "economy snapshot organ"),
                    hasLedger ? number(fields.get("opening_biomass"), "economy snapshot opening biomass") : null,
                    hasLedger ? number(fields.get("substrate_in"), "economy snapshot substrate") : null,
                    hasLedger ? number(fields.get("biomass_income"), "economy snapshot income") : null,
                    hasLedger ? number(fields.get("samples_in"), "economy snapshot samples in") : null,
                    hasLedger ? number(fields.get("maintenance"), "economy snapshot maintenance") : null,
                    number(fields.get("biomass"), "economy snapshot biomass"), number(fields.get("samples"), "economy snapshot samples"),
                    number(fields.get("vitality"), "economy snapshot vitality")));
        }
        return List.copyOf(result);
    }

    static List<ReferenceHiveHistoryEvent> history(Object encoded, int width, int height) {
        List<ReferenceHiveHistoryEvent> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "infection project history")) {
            Map<String, Object> fields = ReferenceGrayboxStateReader.stringMap(item, "infection history row");
            if (!(fields.keySet().equals(Set.of("day", "kind", "source_nest_id", "x", "y"))
                    || fields.keySet().equals(Set.of("day", "kind", "source_nest_id", "nest_id", "x", "y")))) {
                throw new IllegalArgumentException("infection history fields are invalid");
            }
            result.add(new ReferenceHiveHistoryEvent(ReferenceGrayboxStateReader.integer(fields.get("day"), "history day"),
                    ReferenceGrayboxStateReader.string(fields.get("kind"), "history kind"), ReferenceGrayboxStateReader.integer(fields.get("source_nest_id"), "history source"),
                    ReferenceGrayboxStateReader.nullableInteger(fields.get("nest_id"), "history organ"), ReferenceGrayboxStateReader.integer(fields.get("x"), "history x"),
                    ReferenceGrayboxStateReader.integer(fields.get("y"), "history y")));
        }
        return List.copyOf(result);
    }

    static List<ReferenceExploitationSite> exploitation(Object encoded, int width, int height) {
        List<ReferenceExploitationSite> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "pending exploitation")) {
            Map<String, Object> fields = mapping(item, "exploitation row", "x", "y", "mass", "genes", "day", "source_nest_id");
            result.add(new ReferenceExploitationSite(coordinate(fields.get("x"), width, "exploitation x"), coordinate(fields.get("y"), height, "exploitation y"),
                    number(fields.get("mass"), "exploitation mass"), number(fields.get("genes"), "exploitation genes"),
                    ReferenceGrayboxStateReader.integer(fields.get("day"), "exploitation day"), ReferenceGrayboxStateReader.integer(fields.get("source_nest_id"), "exploitation source")));
        }
        return List.copyOf(result);
    }

    static Map<Integer, Map<ReferenceBioformKind, List<String>>> bioformIdentities(Object encoded, List<ReferenceSwarm> swarms) {
        LinkedHashMap<Integer, ReferenceSwarm> known = new LinkedHashMap<>();
        for (ReferenceSwarm swarm : swarms) known.put(swarm.id(), swarm);
        LinkedHashMap<Integer, Map<ReferenceBioformKind, List<String>>> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "bioform identities")) {
            int swarmId = ReferenceGrayboxStateReader.integer(entry.key(), "bioform identity swarm");
            ReferenceSwarm swarm = known.get(swarmId);
            if (swarm == null || result.containsKey(swarmId)) throw new IllegalArgumentException("bioform identity swarm is unknown");
            LinkedHashMap<ReferenceBioformKind, List<String>> ids = new LinkedHashMap<>();
            for (ReferenceGrayboxStateReader.Entry kindEntry : ReferenceGrayboxStateReader.mapEntries(entry.value(), "bioform identity kinds")) {
                ReferenceBioformKind kind = bioformKind(kindEntry.key(), "bioform identity kind");
                List<String> values = new ArrayList<>();
                for (Object value : ReferenceGrayboxStateReader.sequence(kindEntry.value(), "list", "bioform identities")) values.add(ReferenceGrayboxStateReader.string(value, "bioform identity"));
                String prefix = "bioform:" + swarmId + ":" + kind.id() + ":";
                if (values.stream().distinct().count() != values.size() || values.stream().anyMatch(value -> !validBioformId(value, prefix))) {
                    throw new IllegalArgumentException("bioform identity is malformed");
                }
                if (ids.putIfAbsent(kind, List.copyOf(values)) != null) throw new IllegalArgumentException("duplicate bioform identity kind");
            }
            if (!ids.keySet().equals(swarm.composition().keySet())) throw new IllegalArgumentException("bioform identities differ from swarm composition");
            for (Map.Entry<ReferenceBioformKind, Double> composition : swarm.composition().entrySet()) {
                if (composition.getValue() != ids.get(composition.getKey()).size()) throw new IllegalArgumentException("bioform identity count is stale");
            }
            result.put(swarmId, Map.copyOf(ids));
        }
        if (!result.keySet().equals(known.keySet())) throw new IllegalArgumentException("bioform identities do not cover all swarms");
        return Map.copyOf(result);
    }

    private static Map<String, Object> typed(Object encoded, String type, String label, String... keys) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, type, "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, label, keys);
        return fields;
    }

    private static Map<String, Object> mapping(Object encoded, String label, String... keys) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.stringMap(encoded, label);
        ReferenceGrayboxStateReader.exactKeys(fields, label, keys);
        return fields;
    }

    private static LinkedHashMap<ReferenceBioformKind, Double> bioforms(Object encoded, String label, boolean positive) {
        LinkedHashMap<ReferenceBioformKind, Double> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            ReferenceBioformKind kind = bioformKind(entry.key(), label + " kind");
            double value = number(entry.value(), label + " value");
            if ((positive && value <= 0.0d) || (!positive && value < 0.0d) || result.putIfAbsent(kind, value) != null) {
                throw new IllegalArgumentException(label + " is invalid");
            }
        }
        if (positive && result.isEmpty()) throw new IllegalArgumentException(label + " cannot be empty");
        return result;
    }

    private static double sourceCoordinate(Object value, String kind, int size, String label) {
        return switch (kind) {
            case "ecosystem", "organ" -> coordinate(value, size, label);
            case "harvester" -> number(value, label);
            default -> throw new IllegalArgumentException("flow source kind is unsupported");
        };
    }

    private static int coordinate(Object value, int size, String label) {
        int result = ReferenceGrayboxStateReader.integer(value, label);
        if (result < 0 || result >= size) throw new IllegalArgumentException(label + " is outside infection topology");
        return result;
    }

    private static Integer nullableCoordinate(Object value, int size, String label) {
        return value == null ? null : coordinate(value, size, label);
    }

    private static double number(Object value, String label) { return ReferenceGrayboxStateReader.number(value, label); }

    private static int integral(Object value, String label) {
        double result = number(value, label);
        if (result != Math.rint(result) || result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException(label + " must be integral");
        return (int) result;
    }

    private static ReferenceBiome biome(Object value, String label) { return enumValue(value, "simulation.ecology.Biome", label, ReferenceBiome.class); }
    private static ReferenceMutation mutation(Object value, String label) { return enumValue(value, "simulation.infection.Mutation", label, ReferenceMutation.class); }
    private static ReferenceOrganKind organKind(Object value, String label) { return enumValue(value, "simulation.infection.OrganKind", label, ReferenceOrganKind.class); }
    private static ReferenceBioformKind bioformKind(Object value, String label) {
        String id = ReferenceGrayboxStateReader.enumValue(value, "simulation.infection.BioformKind", label);
        for (ReferenceBioformKind kind : ReferenceBioformKind.values()) if (kind.id().equals(id)) return kind;
        throw new IllegalArgumentException(label + " is unsupported");
    }

    private static ReferenceFormationPhase phase(Object value, String label) {
        String id = ReferenceGrayboxStateReader.enumValue(value, "simulation.formations.FormationPhase", label);
        for (ReferenceFormationPhase phase : ReferenceFormationPhase.values()) if (phase.id().equals(id)) return phase;
        throw new IllegalArgumentException(label + " is unsupported");
    }

    private static <T extends Enum<T>> T enumValue(Object value, String type, String label, Class<T> enumType) {
        String source = ReferenceGrayboxStateReader.enumValue(value, type, label);
        try { return Enum.valueOf(enumType, source.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException(label + " is unsupported", error); }
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left); result.addAll(right); return result;
    }

    private static boolean validBioformId(String value, String prefix) {
        if (!value.startsWith(prefix)) return false;
        try { return Integer.parseInt(value.substring(prefix.length())) > 0; }
        catch (NumberFormatException ignored) { return false; }
    }
}
