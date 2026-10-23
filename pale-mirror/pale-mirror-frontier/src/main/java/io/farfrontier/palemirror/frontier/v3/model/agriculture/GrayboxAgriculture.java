package io.farfrontier.palemirror.frontier.v3.model.agriculture;

import java.util.List;
import java.util.Map;
import static io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture.*;

/** Authored spatial content shared by canonical unloaded queries and native biome generation. */
public final class GrayboxAgriculture {
    public static final String FERTILE = "pale_mirror:fertile_plain";
    public static final String ORDINARY = "pale_mirror:temperate_plain";
    public static final String ROCKY = "pale_mirror:rocky_upland";
    private static final io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState GRASS =
            new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState("minecraft:grass_block", Map.of("snowy", "false"));
    private static final io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState STONE =
            new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState("minecraft:stone", Map.of());
    private static final io.farfrontier.palemirror.frontier.v3.model.geometry.TerrainSurfacePalette PLAIN =
            new io.farfrontier.palemirror.frontier.v3.model.geometry.TerrainSurfacePalette(1, 0, 0, List.of(GRASS));
    private static final TerritoryAgriculture RULES = new TerritoryAgriculture(ORDINARY,
            Map.of(FERTILE, new Environment(2000, 1), ORDINARY, new Environment(1000, 1), ROCKY, new Environment(500, 2)),
            Map.of("minecraft:wheat", new Crop("minecraft:wheat", 2)), List.of(
                new Region(-512, -512, 0, -170, ROCKY),
                new Region(240, -512, 512, -170, ROCKY),
                new Region(-240, -170, 0, 170, ROCKY),
                new Region(240, -170, 512, 170, ROCKY),
                new Region(0, 170, 240, 512, ROCKY),
                new Region(0, -170, 240, 170, FERTILE),
                new Region(-240, 170, 0, 512, FERTILE),
                new Region(240, 170, 512, 512, FERTILE)), Map.of(FERTILE, PLAIN, ORDINARY, PLAIN,
                    ROCKY, new io.farfrontier.palemirror.frontier.v3.model.geometry.TerrainSurfacePalette(3, 31, 17,
                        List.of(new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState(
                            "minecraft:coarse_dirt", Map.of()), STONE, STONE, STONE, STONE))));
    public static TerritoryAgriculture forLayout(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldLayout layout) {
        var compact = io.farfrontier.palemirror.frontier.v3.model.FrontierWorldLayout.compact();
        if (layout.equals(compact)) return RULES;
        var regions = new java.util.ArrayList<Region>();
        for (int i = 0; i < layout.settlements().size(); i++) {
            var previous = compact.settlements().get(i); var current = layout.settlements().get(i);
            regions.add(new Region(current.x() - 320, current.z() - 320, current.x() + 320, current.z() + 320,
                    RULES.biomeAt(previous.x(), previous.z())));
        }
        return new TerritoryAgriculture(RULES.defaultBiome(), RULES.biomes(), RULES.crops(), regions, RULES.surfaces());
    }
    private GrayboxAgriculture() { }
    public static TerritoryAgriculture rules() { return RULES; }
}
