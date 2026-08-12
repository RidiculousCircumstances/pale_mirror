package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure deterministic hierarchical selector for a bounded set of authored region sites. */
public final class FrontierSiteSelector {
    private static final int DETAIL_RADIUS = 72;
    private static final int DETAIL_STEP = 24;
    private static final int COARSE_RADIUS = 72;
    private static final int NEAR_CANDIDATES = 24;
    private static final int CANDIDATES_PER_REMOTE_REGION = 12;
    private static final int REMOTE_CANDIDATE_FLOOR = 64;
    private static final int REGION_ENVELOPE = 900;
    private static final double GOLDEN_ANGLE = Math.PI * (3D - Math.sqrt(5D));

    public List<SelectedSite> select(long worldSeed, VisualPoint spawn, int count, int mapRadius,
                                     int minimumSpacing, TerrainAccess terrain) {
        validate(count, mapRadius, minimumSpacing);
        List<Center> near = candidateCenters(worldSeed, spawn, NEAR_CANDIDATES, 1_024, 2_048, 0);
        List<ScoredCenter> coarseNear = coarse(near, terrain);
        TerrainCandidate primary = refine(coarseNear.subList(0, Math.min(6, coarseNear.size())), terrain).stream()
                .min(TerrainCandidate.ordering()).orElseThrow();
        List<TerrainCandidate> selected = new ArrayList<>(count);
        selected.add(primary);
        if (count > 1) selectRemote(worldSeed, spawn, count, mapRadius, minimumSpacing, terrain, selected);
        return selected.stream().map(candidate -> new SelectedSite(candidate,
                terrain.climate(candidate.anchor().x(), candidate.anchor().z()))).toList();
    }

    private static void selectRemote(long worldSeed, VisualPoint spawn, int count, int mapRadius,
                                     int minimumSpacing, TerrainAccess terrain,
                                     List<TerrainCandidate> selected) {
        int remaining = count - 1;
        int remoteCount = Math.max(REMOTE_CANDIDATE_FLOOR, remaining * CANDIDATES_PER_REMOTE_REGION);
        int maximumDistance = mapRadius - REGION_ENVELOPE;
        int minimumDistance = Math.min(maximumDistance - 1, Math.max(2_600, minimumSpacing + 1_000));
        if (maximumDistance <= minimumDistance) throw impossible(count, mapRadius, minimumSpacing, 1);
        List<Center> centers = candidateCenters(worldSeed ^ 0x6a09e667f3bcc909L, spawn, remoteCount,
                minimumDistance, maximumDistance, NEAR_CANDIDATES);
        List<ScoredCenter> coarse = coarse(centers, terrain);
        Map<Long, TerrainCandidate> detailed = new LinkedHashMap<>();
        int cursor = 0;
        int increment = Math.max(24, remaining * 4);
        while (selected.size() < count && cursor < coarse.size()) {
            int end = Math.min(coarse.size(), cursor + increment);
            for (ScoredCenter candidate : coarse.subList(cursor, end)) {
                long key = pack(candidate.center().x(), candidate.center().z());
                detailed.computeIfAbsent(key, ignored -> detailed(candidate.center(), terrain));
            }
            cursor = end;
            List<TerrainCandidate> next = new ArrayList<>();
            next.add(selected.getFirst());
            detailed.values().stream().sorted(TerrainCandidate.ordering()).forEach(candidate -> {
                if (next.size() < count && separated(candidate, next, minimumSpacing)) next.add(candidate);
            });
            selected.clear();
            selected.addAll(next);
        }
        if (selected.size() != count) throw impossible(count, mapRadius, minimumSpacing, selected.size());
    }

    private static List<ScoredCenter> coarse(List<Center> centers, TerrainAccess terrain) {
        return centers.stream().map(center -> new ScoredCenter(center, coarseCandidate(center, terrain)))
                .sorted(Comparator.comparing(ScoredCenter::score, TerrainCandidate.ordering())).toList();
    }

    private static List<TerrainCandidate> refine(List<ScoredCenter> candidates, TerrainAccess terrain) {
        return candidates.stream().map(candidate -> detailed(candidate.center(), terrain)).toList();
    }

    private static TerrainCandidate coarseCandidate(Center center, TerrainAccess terrain) {
        return evaluate(center, terrain, List.of(new Offset(0, 0), new Offset(-COARSE_RADIUS, 0),
                new Offset(COARSE_RADIUS, 0), new Offset(0, -COARSE_RADIUS), new Offset(0, COARSE_RADIUS)));
    }

    private static TerrainCandidate detailed(Center center, TerrainAccess terrain) {
        List<Offset> offsets = new ArrayList<>(49);
        for (int dx = -DETAIL_RADIUS; dx <= DETAIL_RADIUS; dx += DETAIL_STEP) {
            for (int dz = -DETAIL_RADIUS; dz <= DETAIL_RADIUS; dz += DETAIL_STEP) offsets.add(new Offset(dx, dz));
        }
        return evaluate(center, terrain, offsets);
    }

    private static TerrainCandidate evaluate(Center center, TerrainAccess terrain, List<Offset> offsets) {
        List<TerrainSample> samples = offsets.stream().map(offset -> terrain.sample(
                center.x() + offset.x(), center.z() + offset.z())).toList();
        return TerrainCandidate.evaluate(center.x(), center.z(), samples);
    }

    private static List<Center> candidateCenters(long seed, VisualPoint spawn, int count, int minimumDistance,
                                                  int maximumDistance, int addressOffset) {
        Map<Long, Center> unique = new LinkedHashMap<>();
        double phase = unit(mix(seed)) * Math.PI * 2D;
        for (int index = 0; unique.size() < count && index < count * 3; index++) {
            long address = mix(seed + (long) (index + addressOffset) * 0x9E3779B97F4A7C15L);
            double fraction = (index + 0.5D) / count;
            double radial = Math.sqrt(minimumDistance * (double) minimumDistance
                    + fraction * (maximumDistance * (double) maximumDistance
                    - minimumDistance * (double) minimumDistance));
            double angle = phase + index * GOLDEN_ANGLE + (unit(address) - 0.5D) * 0.24D;
            int x = align(spawn.x() + (int) Math.round(Math.cos(angle) * radial));
            int z = align(spawn.z() + (int) Math.round(Math.sin(angle) * radial));
            unique.putIfAbsent(pack(x, z), new Center(x, z));
        }
        return List.copyOf(unique.values());
    }

    private static boolean separated(TerrainCandidate candidate, List<TerrainCandidate> selected, int spacing) {
        long required = (long) spacing * spacing;
        for (TerrainCandidate existing : selected) {
            long dx = (long) candidate.anchor().x() - existing.anchor().x();
            long dz = (long) candidate.anchor().z() - existing.anchor().z();
            if (dx * dx + dz * dz < required) return false;
        }
        return true;
    }

    private static void validate(int count, int mapRadius, int minimumSpacing) {
        if (count < 1 || count > 64) throw new IllegalArgumentException("region count must be between 1 and 64");
        if (mapRadius < 3_000) throw new IllegalArgumentException("map radius must be at least 3000 blocks");
        if (minimumSpacing < 1_024) throw new IllegalArgumentException("region spacing must be at least 1024 blocks");
    }

    private static IllegalStateException impossible(int count, int radius, int spacing, int selected) {
        return new IllegalStateException("Cannot place " + count + " authored regions inside radius " + radius
                + " with spacing " + spacing + "; only " + selected + " valid sites were selected");
    }

    private static int align(int coordinate) { return Math.floorDiv(coordinate, 16) * 16 + 8; }
    private static long pack(int x, int z) { return (long) x << 32 ^ Integer.toUnsignedLong(z); }
    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27; value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    public interface TerrainAccess {
        TerrainSample sample(int x, int z);
        FrontierClimate climate(int x, int z);
    }

    public record SelectedSite(TerrainCandidate terrain, FrontierClimate climate) { }
    private record Center(int x, int z) { }
    private record Offset(int x, int z) { }
    private record ScoredCenter(Center center, TerrainCandidate score) { }
}
