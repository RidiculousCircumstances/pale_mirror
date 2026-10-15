package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockContributions;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet;
import java.util.*;
import java.util.function.Supplier;

/** Geometry-only composition root. Authored passage permissions never clear witnessed physical obstacles. */
final class KnownPedestrianObstacleIndex {
    private enum Source { STATIC, SITES, WITHDRAWN, UNKNOWN, ORGANS, PHYSICAL, STORAGE }
    private final RegionalBlockContributions<Source> physical = new RegionalBlockContributions<>();
    private final RegionalBlockContributions<Source> ground = new RegionalBlockContributions<>();
    private final Map<Source, Object> versions = new EnumMap<>(Source.class);
    private final RegionalBlockContributions<Source> complete = new RegionalBlockContributions<>();

    void refresh(FrontierWorldState state, KnownSiteGeometry.View sites, Set<BlockPosition> storage,
                 Supplier<Set<BlockPosition>> staticBlocks) {
        replace(Source.STATIC, state.bootstrap(), staticBlocks);
        replace(Source.SITES, sites.obstacles(), sites::obstacles);
        replace(Source.WITHDRAWN, sites.withdrawnSupports(), sites::withdrawnSupports);
        replace(Source.UNKNOWN, sites.unknown(), sites::unknown);
        replace(Source.ORGANS, state.hiveColony().addedOrgans(), () -> FrontierGrayboxPlan.intactOrganOccupancy(
                List.copyOf(state.hiveColony().addedOrgans().values())));
        replace(Source.PHYSICAL, state.physicalDeltas(), () -> state.physicalDeltas().keySet());
        replace(Source.STORAGE, storage, () -> storage);
    }
    private void replace(Source owner, Object version, Supplier<Set<BlockPosition>> project) {
        if (versions.get(owner) == version) return;
        var next = RegionalBlockSet.copyOf(project.get());
        versions.put(owner, version);
        if (owner == Source.PHYSICAL || owner == Source.STORAGE) physical.replace(owner, next);
        if (owner != Source.STATIC) ground.replace(owner, next);
        if (owner != Source.WITHDRAWN) complete.replace(owner, next);
    }
    RegionalBlockSet groundChanges() { return ground.snapshot(); }
    RegionalBlockSet complete() { return complete.snapshot(); }
    RegionalBlockSet withPassages(Set<BlockPosition> cleared) {
        if (cleared.isEmpty()) return complete();
        // All other regions share the complete snapshot. Within the small
        // permission footprint physical changes take precedence over authored clearance.
        var removals = new HashSet<BlockPosition>();
        for (var position : cleared) if (!physical.snapshot().contains(position)) removals.add(position);
        return complete().overlay(removals, Set.of());
    }
}
