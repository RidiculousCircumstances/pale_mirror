package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPort;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic gold-master matrix consumed by graphical review tooling and tests. */
public final class VisualShowcasePlan {
    public static final int CELL_SPACING = 64;
    private static final List<String> STATES = List.of("INTACT", "DAMAGED", "RUINED");

    private VisualShowcasePlan() { }

    public static List<Entry> matrix(VisualPoint origin) {
        List<Entry> result = new ArrayList<>();
        int moduleIndex = 0;
        for (FrontierModuleCatalog.Definition definition : FrontierModuleCatalog.definitionsInOrder()) {
            for (FrontierClimate climate : FrontierClimate.values()) for (int rotation = 0; rotation < 4; rotation++) {
                for (int stateIndex = 0; stateIndex < STATES.size(); stateIndex++) {
                    int x = origin.x() + (rotation * STATES.size() + stateIndex) * CELL_SPACING;
                    int z = origin.z() + (moduleIndex * FrontierClimate.values().length + climate.ordinal())
                            * CELL_SPACING;
                    VisualPoint center = new VisualPoint(x, origin.y(), z);
                    VisualBounds footprint = footprint(definition, center, rotation);
                    VisualPoint entrance = entrance(footprint, rotation);
                    String family = climate == FrontierClimate.DRY_ARID ? "temperate"
                            : climate.name().toLowerCase(Locale.ROOT);
                    String key = definition.id() + ":" + climate.name().toLowerCase(Locale.ROOT)
                            + ":r" + rotation + ":" + STATES.get(stateIndex).toLowerCase(Locale.ROOT);
                    VisualModulePlacement placement = new VisualModulePlacement("showcase_" + key,
                            "pale_mirror_visuals:" + family + "/" + definition.id(),
                            climate.name().toLowerCase(Locale.ROOT), "SHOWCASE", center, rotation, footprint,
                            "showcase_foundation_" + key, definition.stateProfile(),
                            List.of(new VisualPort("public", VisualPortKind.PUBLIC_ENTRANCE, entrance, rotation)));
                    result.add(new Entry(key, climate, rotation, STATES.get(stateIndex), placement));
                }
            }
            moduleIndex++;
        }
        return List.copyOf(result);
    }

    private static VisualBounds footprint(FrontierModuleCatalog.Definition definition, VisualPoint origin,
                                          int rotation) {
        boolean swap = Math.floorMod(rotation, 2) == 1;
        int width = swap ? definition.sizeZ() : definition.sizeX();
        int depth = swap ? definition.sizeX() : definition.sizeZ();
        VisualPoint min = new VisualPoint(origin.x() - width / 2, origin.y() + 1, origin.z() - depth / 2);
        return new VisualBounds(min, new VisualPoint(min.x() + width - 1,
                min.y() + definition.sizeY() - 1, min.z() + depth - 1));
    }

    private static VisualPoint entrance(VisualBounds bounds, int rotation) {
        int x = (bounds.min().x() + bounds.max().x()) / 2;
        int z = (bounds.min().z() + bounds.max().z()) / 2;
        switch (Math.floorMod(rotation, 4)) {
            case 0 -> x = bounds.max().x();
            case 1 -> z = bounds.max().z();
            case 2 -> x = bounds.min().x();
            default -> z = bounds.min().z();
        }
        return new VisualPoint(x, bounds.min().y(), z);
    }

    public record Entry(String key, FrontierClimate climate, int rotation, String state,
                        VisualModulePlacement module) { }
}
