package io.farfrontier.palemirror.frontier.v3.model.agriculture;

import java.util.List;
import java.util.Map;
import static io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture.*;

/** Authored spatial content shared by canonical unloaded queries and native biome generation. */
public final class GrayboxAgriculture {
    public static final String FERTILE = "pale_mirror:fertile_plain";
    public static final String ORDINARY = "pale_mirror:temperate_plain";
    public static final String ROCKY = "pale_mirror:rocky_upland";
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
                new Region(240, 170, 512, 512, FERTILE)));
    private GrayboxAgriculture() { }
    public static TerritoryAgriculture rules() { return RULES; }
}
