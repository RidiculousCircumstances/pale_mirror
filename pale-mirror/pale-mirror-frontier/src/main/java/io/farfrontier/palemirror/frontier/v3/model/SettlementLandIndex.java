package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingVolume;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;

import java.util.*;

/** Immutable spatial reservation view. Exact plot/street records retain land authority. */
final class SettlementLandIndex {
    private record Index(
            Set<BlockPosition> publicSurfaces, Map<Region, List<BuildingVolume>> plots) {}

    private static final ImmutableResultView<String, Index> VIEWS = new ImmutableResultView<>(2);

    static boolean ownsFinish(FrontierBootstrap bootstrap, BlockPosition point) {
        var index = VIEWS.get(List.of(bootstrap), "land", () -> compile(bootstrap));
        if (index.publicSurfaces().contains(point)) return true;
        for (var plot :
                index.plots()
                        .getOrDefault(
                                new Region(
                                        Math.floorDiv(point.x(), 16), Math.floorDiv(point.z(), 16)),
                                List.of()))
            if (point.x() >= plot.min().x()
                    && point.x() < plot.max().x()
                    && point.z() >= plot.min().z()
                    && point.z() < plot.max().z()) return true;
        return false;
    }

    static boolean intersectsPrivatePlots(
            FrontierBootstrap bootstrap, int minX, int minZ, int maxX, int maxZ) {
        var index = VIEWS.get(List.of(bootstrap), "land", () -> compile(bootstrap));
        for (int x = Math.floorDiv(minX, 16); x <= Math.floorDiv(maxX, 16); x++)
            for (int z = Math.floorDiv(minZ, 16); z <= Math.floorDiv(maxZ, 16); z++)
                for (var box : index.plots().getOrDefault(new Region(x, z), List.of()))
                    if (minX < box.max().x()
                            && maxX >= box.min().x()
                            && minZ < box.max().z()
                            && maxZ >= box.min().z()) return true;
        return false;
    }

    private static Index compile(FrontierBootstrap bootstrap) {
        var paving = new HashSet<BlockPosition>();
        var plots = new HashMap<Region, List<BuildingVolume>>();
        for (var home : bootstrap.settlements()) {
            paving.addAll(home.layout().paving());
            for (var plot : home.layout().plots().values()) {
                var box = plot.bounds();
                for (int x = Math.floorDiv(box.min().x(), 16);
                        x <= Math.floorDiv(box.max().x() - 1, 16);
                        x++)
                    for (int z = Math.floorDiv(box.min().z(), 16);
                            z <= Math.floorDiv(box.max().z() - 1, 16);
                            z++)
                        plots.computeIfAbsent(new Region(x, z), ignored -> new ArrayList<>())
                                .add(box);
            }
        }
        var frozen = new HashMap<Region, List<BuildingVolume>>();
        plots.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return new Index(RegionalBlockSet.copyOf(paving), Map.copyOf(frozen));
    }

    private SettlementLandIndex() {}
}
