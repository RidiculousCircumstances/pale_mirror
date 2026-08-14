package io.farfrontier.palemirror.visuals.genesis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.farfrontier.palemirror.api.BuildingFunctionId;
import io.farfrontier.palemirror.api.SettlementBuildingCategory;
import io.farfrontier.palemirror.api.SettlementDevelopmentStage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fail-closed, data-driven settlement composition catalog. */
final class SettlementArchetypeCatalog {
    private static final String RESOURCE = "/data/pale_mirror_visuals/settlement_archetype/iron_frontier.json";
    private static final Definition IRON_FRONTIER = load();

    private SettlementArchetypeCatalog() { }

    static Definition ironFrontier() { return IRON_FRONTIER; }

    private static Definition load() {
        try (var input = SettlementArchetypeCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing settlement archetype " + RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<SettlementDevelopmentStage, Stage> stages = new EnumMap<>(SettlementDevelopmentStage.class);
            for (JsonElement raw : root.getAsJsonArray("stages")) {
                JsonObject value = raw.getAsJsonObject();
                SettlementDevelopmentStage stageId = SettlementDevelopmentStage.valueOf(value.get("stage").getAsString());
                Map<String, Integer> cohorts = new LinkedHashMap<>();
                value.getAsJsonObject("cohorts").entrySet().forEach(entry -> cohorts.put(entry.getKey(), entry.getValue().getAsInt()));
                List<Building> buildings = buildings(value.getAsJsonArray("buildings"));
                String inherit = value.has("inherit") ? value.get("inherit").getAsString() : "";
                if (!inherit.isBlank()) {
                    Stage inherited = stages.get(SettlementDevelopmentStage.valueOf(inherit));
                    if (inherited == null) throw new IllegalStateException(stageId + " inherits an undefined stage " + inherit);
                    List<Building> combined = new ArrayList<>(inherited.buildings());
                    combined.addAll(buildings);
                    buildings = List.copyOf(combined);
                }
                Stage stage = new Stage(stageId, cohorts, buildings);
                if (stages.put(stageId, stage) != null) throw new IllegalStateException("Duplicate stage " + stageId);
            }
            Definition result = new Definition(root.get("id").getAsString(),
                    SettlementDevelopmentStage.valueOf(root.get("activeStage").getAsString()), stages);
            validate(result);
            return result;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read settlement archetype " + RESOURCE, failure);
        }
    }

    private static List<Building> buildings(JsonArray values) {
        List<Building> result = new ArrayList<>();
        for (JsonElement raw : values) {
            JsonObject value = raw.getAsJsonObject();
            List<BuildingFunctionId> functions = new ArrayList<>();
            value.getAsJsonArray("functions").forEach(entry -> functions.add(new BuildingFunctionId(entry.getAsString())));
            result.add(new Building(value.get("id").getAsString(),
                    SettlementBuildingCategory.valueOf(value.get("category").getAsString()),
                    value.get("template").getAsString(), value.get("housing").getAsInt(),
                    value.get("work").getAsInt(), functions));
        }
        return List.copyOf(result);
    }

    private static void validate(Definition definition) {
        if (!definition.id().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalStateException("Invalid settlement archetype id " + definition.id());
        }
        if (definition.stages().size() != SettlementDevelopmentStage.values().length) {
            throw new IllegalStateException("Settlement archetype must define every composition snapshot");
        }
        for (SettlementDevelopmentStage expected : SettlementDevelopmentStage.values()) {
            Stage stage = definition.stages().get(expected);
            if (stage == null) throw new IllegalStateException("Missing settlement stage " + expected);
            int population = stage.cohorts().values().stream().mapToInt(Integer::intValue).sum();
            if (population != expected.population()) throw new IllegalStateException(expected
                    + " cohort population is " + population + " instead of " + expected.population());
            if (stage.buildings().stream().map(Building::id).distinct().count() != stage.buildings().size()) {
                throw new IllegalStateException(expected + " contains duplicate building ids");
            }
            int housing = stage.buildings().stream().mapToInt(Building::housingCapacity).sum();
            if (housing < population) throw new IllegalStateException(expected + " housing " + housing
                    + " is below population " + population);
            int requiredWork = stage.cohorts().getOrDefault("WORKERS", 0)
                    + stage.cohorts().getOrDefault("SPECIALISTS", 0)
                    + stage.cohorts().getOrDefault("GUARDS", 0);
            int work = stage.buildings().stream().mapToInt(Building::workCapacity).sum();
            if (work < requiredWork) throw new IllegalStateException(expected + " work capacity " + work
                    + " is below assigned workforce " + requiredWork);
            stage.buildings().forEach(building -> FrontierModuleCatalog.require(building.template()));
        }
        Stage township = definition.stages().get(SettlementDevelopmentStage.TOWNSHIP);
        if (township.buildings().size() != 20) throw new IllegalStateException("Township requires 20 buildings");
        if (township.buildings().stream().mapToInt(Building::housingCapacity).sum() != 48) {
            throw new IllegalStateException("Township housing must equal 48 exactly");
        }
        if (definition.activeStage() != SettlementDevelopmentStage.TOWNSHIP) {
            throw new IllegalStateException("schema v40 materializes only Township");
        }
    }

    record Definition(String id, SettlementDevelopmentStage activeStage,
                      Map<SettlementDevelopmentStage, Stage> stages) {
        Definition { stages = Map.copyOf(stages); }
        Stage active() { return stages.get(activeStage); }
    }

    record Stage(SettlementDevelopmentStage stage, Map<String, Integer> cohorts, List<Building> buildings) {
        Stage { cohorts = Map.copyOf(cohorts); buildings = List.copyOf(buildings); }
    }

    record Building(String id, SettlementBuildingCategory category, String template,
                    int housingCapacity, int workCapacity, List<BuildingFunctionId> functions) {
        Building {
            if (id == null || id.isBlank() || template == null || template.isBlank()
                    || housingCapacity < 0 || workCapacity < 0 || functions.isEmpty()) {
                throw new IllegalArgumentException("Invalid settlement building definition " + id);
            }
            functions = List.copyOf(functions);
        }
    }
}
