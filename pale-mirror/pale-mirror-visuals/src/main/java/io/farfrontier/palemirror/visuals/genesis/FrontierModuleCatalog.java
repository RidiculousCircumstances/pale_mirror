package io.farfrontier.palemirror.visuals.genesis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data-driven visual grammar metadata; NBT is content, this catalog is its semantic contract. */
final class FrontierModuleCatalog {
    private static final String RESOURCE = "/data/pale_mirror_visuals/module_catalog/frontier.json";
    private static final Catalog CATALOG = load();

    private FrontierModuleCatalog() { }

    static int version() { return CATALOG.version(); }

    static Definition require(String id) {
        Definition result = CATALOG.definitions().get(id);
        if (result == null) throw new IllegalArgumentException("Unknown frontier visual module " + id);
        return result;
    }

    static List<Definition> definitionsInOrder() {
        return CATALOG.definitions().values().stream().filter(value -> !value.mine()).toList();
    }

    static List<Definition> allDefinitionsInOrder() { return List.copyOf(CATALOG.definitions().values()); }

    private static Catalog load() {
        try (var input = FrontierModuleCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing frontier module catalog " + RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            int version = root.get("version").getAsInt();
            if (version < 1) throw new IllegalStateException("Invalid frontier module catalog version " + version);
            Map<String, Definition> definitions = new LinkedHashMap<>();
            for (JsonElement raw : root.getAsJsonArray("modules")) {
                JsonObject value = raw.getAsJsonObject();
                var size = value.getAsJsonArray("size");
                JsonObject entrance = value.getAsJsonObject("entrance");
                List<String> tags = new ArrayList<>();
                value.getAsJsonArray("tags").forEach(entry -> tags.add(entry.getAsString()));
                Definition definition = new Definition(value.get("id").getAsString(), size.get(0).getAsInt(),
                        size.get(1).getAsInt(), size.get(2).getAsInt(), value.get("landmark").getAsBoolean(),
                        value.get("stateProfile").getAsString(), entrance.get("x").getAsInt(),
                        entrance.get("y").getAsInt(), entrance.get("z").getAsInt(),
                        entrance.get("outward").getAsInt(), value.get("parcelClearance").getAsInt(),
                        value.get("foundationApron").getAsInt(), tags);
                if (definitions.put(definition.id(), definition) != null) {
                    throw new IllegalStateException("Duplicate frontier module " + definition.id());
                }
            }
            return new Catalog(version, Map.copyOf(definitions));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read frontier module catalog " + RESOURCE, failure);
        }
    }

    private record Catalog(int version, Map<String, Definition> definitions) { }

    record Definition(String id, int sizeX, int sizeY, int sizeZ, boolean landmark, String stateProfile,
                      int entranceX, int entranceY, int entranceZ, int entranceOutward,
                      int parcelClearance, int foundationApron, List<String> tags) {
        Definition {
            if (id == null || id.isBlank() || sizeX < 1 || sizeY < 1 || sizeZ < 1
                    || stateProfile == null || stateProfile.isBlank()
                    || entranceX < 0 || entranceX >= sizeX || entranceY < 0 || entranceY >= sizeY
                    || entranceZ < 0 || entranceZ >= sizeZ || entranceOutward < 0 || entranceOutward > 3
                    || parcelClearance < 0 || foundationApron < 0 || tags == null || tags.isEmpty()) {
                throw new IllegalArgumentException("Invalid frontier module definition " + id);
            }
            tags = List.copyOf(tags);
        }

        boolean mine() { return tags.contains("mine_surface"); }
    }
}
