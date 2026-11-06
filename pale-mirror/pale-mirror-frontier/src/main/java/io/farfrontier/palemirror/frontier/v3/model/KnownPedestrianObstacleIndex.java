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
    private final ImmutableResultView<Set<BlockPosition>, RegionalBlockSet> passages = new ImmutableResultView<>(64);
    private final Map<Source, Object> versions = new EnumMap<>(Source.class);
    private final RegionalBlockContributions<Source> complete = new RegionalBlockContributions<>();

    void refresh(FrontierWorldState state, KnownSiteGeometry.View sites, Set<BlockPosition> storage,
                 Supplier<Set<BlockPosition>> staticBlocks) {
        var updates = new EnumMap<Source, RegionalBlockSet>(Source.class);
        replace(updates, Source.STATIC, state.bootstrap(), staticBlocks);
        replace(updates, Source.SITES, sites.obstacles(), sites::obstacles);
        replace(updates, Source.WITHDRAWN, sites.withdrawnSupports(), sites::withdrawnSupports);
        replace(updates, Source.UNKNOWN, sites.unknown(), sites::unknown);
        replace(updates, Source.ORGANS, state.hiveColony().addedOrgans(), () -> FrontierGrayboxPlan.intactOrganOccupancy(
                List.copyOf(state.hiveColony().addedOrgans().values())));
        var unknown = PhysicalDeltaGeometry.unknown(state);
        replace(updates, Source.PHYSICAL, unknown, () -> unknown);
        replace(updates, Source.STORAGE, storage, () -> storage);
        var observed = new EnumMap<Source, RegionalBlockSet>(updates);
        observed.keySet().retainAll(EnumSet.of(Source.PHYSICAL, Source.STORAGE));
        physical.replaceAll(observed);
        var grounded = new EnumMap<Source, RegionalBlockSet>(updates); grounded.remove(Source.STATIC);
        ground.replaceAll(grounded);
        updates.remove(Source.WITHDRAWN); complete.replaceAll(updates);
    }
    private void replace(Map<Source, RegionalBlockSet> updates, Source owner, Object version, Supplier<Set<BlockPosition>> project) {
        if (versions.get(owner) == version) return;
        var next = RegionalBlockSet.copyOf(project.get());
        versions.put(owner, version);
        updates.put(owner, next);
    }
    RegionalBlockSet groundChanges() { return ground.snapshot(); }
    RegionalBlockSet complete() { return complete.snapshot(); }
    RegionalBlockSet withPassages(Set<BlockPosition> cleared) {
        if (cleared.isEmpty()) return complete();
        // All other regions share the complete snapshot. Within the small
        // permission footprint physical changes take precedence over authored clearance.
        var whole = complete(); var observed = physical.snapshot(); var permission = Set.copyOf(cleared);
        return passages.get(List.of(whole, observed), permission, () -> {
            var removals = new HashSet<BlockPosition>();
            for (var position : permission) if (!observed.contains(position)) removals.add(position);
            return whole.overlay(removals, Set.of());
        });
    }
}
