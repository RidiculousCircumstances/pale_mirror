package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Pure bounded terrain-fit selection. Layout owns plots; this planner owns elevation and site work. */
public final class BuildingSitePlanner {
    private BuildingSitePlanner() { }
    public static BlockPosition anchor(TerrainSurfacePlan terrain, BuildingDefinition definition, int x, int z, FacilityFacing facing) {
        var provisional = definition.placement(new BlockPosition(x,0,z),facing);
        var footprint = provisional.volume(definition.fabric());
        long area = (long)(footprint.max().x()-footprint.min().x())*(footprint.max().z()-footprint.min().z());
        if (area > 4096) throw new IllegalArgumentException("site survey exceeds bounded footprint");
        var uniform = BuildingSiteSurvey.terrain(terrain).uniformClearSite(footprint);
        if (uniform.isPresent()) return new BlockPosition(x, uniform.getAsInt() + definition.origin().y() - definition.site().groundY(), z);
        var levels = new ArrayList<Integer>();
        for (int px=footprint.min().x();px<footprint.max().x();px++)
            for(int pz=footprint.min().z();pz<footprint.max().z();pz++) levels.add(terrain.supportYAt(px,pz));
        levels.sort(Integer::compareTo);
        // Median minimizes cut+fill volume. Individual plots may retain different terrace elevations.
        int ground = levels.get(levels.size()/2);
        return new BlockPosition(x, ground + definition.origin().y() - definition.site().groundY(), z);
    }
}
