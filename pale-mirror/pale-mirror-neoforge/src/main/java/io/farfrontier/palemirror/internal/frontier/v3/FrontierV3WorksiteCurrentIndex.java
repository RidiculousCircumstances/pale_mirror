package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Native chunk addresses over the common lazy regional composition. No cell enumeration here. */
final class FrontierV3WorksiteCurrentIndex {
    private final WorksiteRegionIndex index = new WorksiteRegionIndex();
    private Map<Region, List<WorksiteBlock>> version;
    private Map<Long, List<WorksiteBlock>> chunks = Map.of();
    Map<Long, List<WorksiteBlock>> update(List<Map<Region, List<WorksiteBlock>>> current) {
        var next = index.update(current);
        if (next == version) return chunks;
        var result = new TreeMap<Long, List<WorksiteBlock>>();
        next.forEach((region, cells) -> result.put(ChunkPos.asLong(region.x(), region.z()), cells));
        version = next; return chunks = Collections.unmodifiableMap(result);
    }
}
