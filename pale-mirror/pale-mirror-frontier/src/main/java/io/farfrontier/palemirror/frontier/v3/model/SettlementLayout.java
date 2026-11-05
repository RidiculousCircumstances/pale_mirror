package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingStyleId;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingVolume;

import java.util.*;

/** Retained land allocation, independent of jobs, stock, navigation execution and construction. */
public record SettlementLayout(
        SubjectId settlementId,
        long revision,
        String scheme,
        BuildingStyleId style,
        Map<PlotId, Plot> plots,
        Set<BlockPosition> paving,
        List<SurfaceAnchor> extensionFronts) {
    public static final int MAX_PLOTS = 128;

    public record PlotId(String value) {
        public PlotId {
            if (value == null || !value.matches("[a-z0-9_:-]{1,128}"))
                throw new IllegalArgumentException("invalid plot id");
        }
    }

    public record Plot(
            PlotId id, SubjectId buildingId, BuildingVolume bounds, SurfaceAnchor streetAccess) {
        public Plot {
            Objects.requireNonNull(id);
            Objects.requireNonNull(buildingId);
            Objects.requireNonNull(bounds);
            Objects.requireNonNull(streetAccess);
        }
    }

    public SettlementLayout {
        Objects.requireNonNull(settlementId);
        Objects.requireNonNull(scheme);
        Objects.requireNonNull(style);
        plots = Collections.unmodifiableMap(new LinkedHashMap<>(plots));
        paving = Collections.unmodifiableSet(new LinkedHashSet<>(paving));
        extensionFronts = List.copyOf(extensionFronts);
        if (revision < 1
                || !Set.of("crossroads", "spine", "courtyards").contains(scheme)
                || plots.isEmpty()
                || plots.size() > MAX_PLOTS
                || paving.isEmpty()
                || paving.size() > 32768
                || extensionFronts.isEmpty()
                || extensionFronts.size() > 16
                || new HashSet<>(extensionFronts).size() != extensionFronts.size())
            throw new IllegalArgumentException("invalid settlement layout");
        var buildings = new HashSet<SubjectId>();
        for (var entry : plots.entrySet()) {
            var plot = entry.getValue();
            if (!entry.getKey().equals(plot.id())
                    || !buildings.add(plot.buildingId())
                    || !paving.contains(plot.streetAccess().support()))
                throw new IllegalArgumentException("invalid plot allocation");
        }
        var list = List.copyOf(plots.values());
        for (int i = 0; i < list.size(); i++)
            for (int j = i + 1; j < list.size(); j++)
                if (horizontalOverlap(list.get(i).bounds(), list.get(j).bounds()))
                    throw new IllegalArgumentException("overlapping settlement plots");
        for (var front : extensionFronts)
            if (!paving.contains(front.support()))
                throw new IllegalArgumentException("extension front disconnected from streets");
    }

    public SettlementLayout translated(int dx, int dy, int dz) {
        var next = new LinkedHashMap<PlotId, Plot>();
        plots.forEach(
                (id, p) ->
                        next.put(
                                id,
                                new Plot(
                                        id,
                                        p.buildingId(),
                                        new BuildingVolume(
                                                p.bounds().min().offset(dx, dy, dz),
                                                p.bounds().max().offset(dx, dy, dz)),
                                        new SurfaceAnchor(
                                                p.streetAccess().support().offset(dx, dy, dz)))));
        return new SettlementLayout(
                settlementId,
                revision,
                scheme,
                style,
                next,
                paving.stream()
                        .map(p -> p.offset(dx, dy, dz))
                        .collect(java.util.stream.Collectors.toSet()),
                extensionFronts.stream()
                        .map(p -> new SurfaceAnchor(p.support().offset(dx, dy, dz)))
                        .toList());
    }

    public SettlementLayout extend(
            List<Plot> additions, Set<BlockPosition> addedPaving, List<SurfaceAnchor> newFronts) {
        var next = new LinkedHashMap<>(plots);
        var surfaces = new LinkedHashSet<>(paving);
        surfaces.addAll(addedPaving);
        for (var plot : additions)
            if (next.putIfAbsent(plot.id(), plot) != null)
                throw new IllegalArgumentException("extension cannot replace an occupied plot");
        return new SettlementLayout(
                settlementId, Math.addExact(revision, 1), scheme, style, next, surfaces, newFronts);
    }

    public void requireBuildings(List<SettlementStructure> buildings) {
        if (buildings.size() != plots.size())
            throw new IllegalArgumentException("layout and buildings differ");
        for (var building : buildings) {
            var plot =
                    plots.values().stream()
                            .filter(p -> p.buildingId().equals(building.id()))
                            .findFirst()
                            .orElseThrow();
            if (!building.building().style().equals(style)
                    || !building.settlementId().equals(settlementId)
                    || !plot.bounds()
                            .equals(building.placement().volume(building.definition().parcel())))
                throw new IllegalArgumentException("building escaped its retained plot");
        }
    }

    public static boolean horizontalOverlap(BuildingVolume a, BuildingVolume b) {
        return a.min().x() < b.max().x()
                && a.max().x() > b.min().x()
                && a.min().z() < b.max().z()
                && a.max().z() > b.min().z();
    }
}
