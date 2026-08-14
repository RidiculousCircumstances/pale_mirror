package io.farfrontier.palemirror.visuals.genesis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned visual grammar metadata; NBT is content, this catalog is its semantic contract. */
final class FrontierModuleCatalog {
    static final int VERSION = 2;
    private static final Map<String, Definition> DEFINITIONS = definitions();

    private FrontierModuleCatalog() { }

    static Definition require(String id) {
        Definition result = DEFINITIONS.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown frontier visual module " + id);
        return result;
    }

    static List<Definition> definitionsInOrder() { return List.copyOf(DEFINITIONS.values()); }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> values = new LinkedHashMap<>();
        add(values, "barracks", 9, 8, 18, true, "frontier_defence");
        add(values, "civic_hall", 29, 37, 22, true, "frontier_community");
        add(values, "clinic", 13, 18, 14, true, "frontier_community");
        add(values, "inn", 17, 13, 26, true, "frontier_community");
        add(values, "market", 19, 8, 16, false, "frontier_market");
        add(values, "receiving_depot", 10, 6, 6, true, "frontier_freight");
        add(values, "residence_1", 10, 7, 9, false, "frontier_community");
        add(values, "residence_2", 11, 11, 14, false, "frontier_community");
        add(values, "residence_3", 10, 7, 9, false, "frontier_community");
        add(values, "smithy", 5, 7, 7, false, "frontier_industry");
        add(values, "stable", 19, 8, 16, false, "frontier_freight");
        add(values, "workshop_1", 10, 7, 8, false, "frontier_industry");
        add(values, "workshop_2", 16, 6, 20, false, "frontier_industry");
        return Map.copyOf(values);
    }

    private static void add(Map<String, Definition> values, String id, int sizeX, int sizeY, int sizeZ,
                            boolean landmark, String stateProfile) {
        values.put(id, new Definition(id, sizeX, sizeY, sizeZ, landmark, stateProfile));
    }

    record Definition(String id, int sizeX, int sizeY, int sizeZ, boolean landmark, String stateProfile) {
        Definition {
            if (id == null || id.isBlank() || sizeX < 1 || sizeY < 1 || sizeZ < 1
                    || stateProfile == null || stateProfile.isBlank()) {
                throw new IllegalArgumentException("Invalid frontier module definition");
            }
        }
    }
}
