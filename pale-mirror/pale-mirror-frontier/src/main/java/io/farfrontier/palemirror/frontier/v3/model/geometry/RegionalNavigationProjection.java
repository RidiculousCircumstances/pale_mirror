package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Derived, owner-thread block-to-navigation projection. No resource, work or movement authority.
 * Regions contain complete vertical columns, so a ceiling and its upper floor update together. */
public final class RegionalNavigationProjection {
    public record View(KnownBlockNavigationProjection.View geometry, RegionalBlockMap<NavigationBlockState> blocks) { }
    private RegionalBlockMap<Optional<NavigationBlockState>> previous = RegionalBlockMap.copyOf(Map.of());
    private Object definitions;
    private final Map<Region, Set<NavigationBlockState>> regionalTypes = new HashMap<>();
    private final Map<NavigationBlockState, Set<Region>> typeRegions = new HashMap<>();
    private final Map<NavigationBlockState, Optional<KnownBlockNavigationProjection.Cell>> classifications = new HashMap<>();
    private View view = new View(new KnownBlockNavigationProjection.View(Map.of(), Set.of(), Set.of(), Set.of()),
            RegionalBlockMap.copyOf(Map.of()));

    public RegionalNavigationProjection() { }
    private RegionalNavigationProjection(RegionalNavigationProjection source) {
        previous = source.previous; definitions = source.definitions; view = source.view;
        regionalTypes.putAll(source.regionalTypes); classifications.putAll(source.classifications);
        source.typeRegions.forEach((type, regions) -> typeRegions.put(type, new HashSet<>(regions)));
    }
    /** Independent writer indexes, shared immutable spatial parts. Source must be quiescent. */
    public RegionalNavigationProjection fork() { return new RegionalNavigationProjection(this); }

    public View read(RegionalBlockMap<Optional<NavigationBlockState>> facts, NavigationGeometryState knowledge) {
        var changed = new HashSet<>(facts.changesFrom(previous));
        // A catalog update matters here only where a used type changes its
        // coarse contact classification. Detailed shape invalidation is owned
        // by the navigation observation lineage, not this spatial projection.
        if (definitions != knowledge.definitions()) typeRegions.forEach((block, regions) -> {
            var classification = KnownBlockCollision.coarse(knowledge, block);
            if (!classification.equals(classifications.get(block))) changed.addAll(regions);
            classifications.put(block, classification);
        });
        if (changed.isEmpty()) { previous = facts; definitions = knowledge.definitions(); return view; }
        var supports = new HashMap<Region, Map<TerrainColumn, List<SurfaceAnchor>>>();
        var volumes = new HashMap<Region, Set<BlockPosition>>();
        var absent = new HashMap<Region, Set<BlockPosition>>();
        var unknown = new HashMap<Region, Set<BlockPosition>>();
        var blocks = new HashMap<Region, Map<BlockPosition, NavigationBlockState>>();
        for (var region : changed) {
            var local = facts.in(region);
            indexTypes(region, local, knowledge);
            var projected = KnownBlockNavigationProjection.project(local.keySet(), position ->
                    local.get(position).flatMap(block -> KnownBlockCollision.coarse(knowledge, block)));
            supports.put(region, projected.supports()); volumes.put(region, projected.volumes());
            absent.put(region, projected.absentSupports()); unknown.put(region, projected.unknown());
            var known = new HashMap<BlockPosition, NavigationBlockState>();
            local.forEach((position, block) -> block.ifPresent(value -> known.put(position, value)));
            blocks.put(region, known);
        }
        var old = view.geometry();
        var nextSupports = RegionalSurfaceMap.copyOf(old.supports()).replace(supports);
        var nextVolumes = RegionalBlockSet.copyOf(old.volumes()).replace(volumes);
        var nextAbsent = RegionalBlockSet.copyOf(old.absentSupports()).replace(absent);
        var nextUnknown = RegionalBlockSet.copyOf(old.unknown()).replace(unknown);
        var nextBlocks = view.blocks().replace(blocks);
        if (nextSupports != old.supports() || nextVolumes != old.volumes() || nextAbsent != old.absentSupports()
                || nextUnknown != old.unknown() || nextBlocks != view.blocks())
            view = new View(new KnownBlockNavigationProjection.View(nextSupports, nextVolumes, nextAbsent, nextUnknown), nextBlocks);
        previous = facts; definitions = knowledge.definitions();
        return view;
    }
    private void indexTypes(Region region, Map<BlockPosition, Optional<NavigationBlockState>> local,
                            NavigationGeometryState knowledge) {
        var types = new HashSet<NavigationBlockState>();
        local.values().forEach(block -> block.ifPresent(types::add));
        var prior = regionalTypes.getOrDefault(region, Set.of());
        for (var block : prior) if (!types.contains(block)) {
            var regions = typeRegions.get(block);
            regions.remove(region);
            if (regions.isEmpty()) { typeRegions.remove(block); classifications.remove(block); }
        }
        for (var block : types) if (!prior.contains(block)) {
            typeRegions.computeIfAbsent(block, ignored -> new HashSet<>()).add(region);
            classifications.put(block, KnownBlockCollision.coarse(knowledge, block));
        }
        if (types.isEmpty()) regionalTypes.remove(region); else regionalTypes.put(region, Set.copyOf(types));
    }

}
