package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.ResidentSeed;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Pure deterministic layout grammar. Terrain selection supplies only the accepted surface anchor. */
public final class FrontierRegionPlanner {
    public static final int DEFINITION_VERSION = 3;
    public static final int SETTLEMENT_RADIUS = 88;
    private static final int PRIMARY_MIN = 384;
    private static final int PRIMARY_SPAN = 129;
    private static final int ALTERNATE_MIN = 512;
    private static final int ALTERNATE_SPAN = 257;

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, VisualPoint anchor, FrontierClimate climate) {
        return plan(worldSeed, ordinal, anchor, climate, (x, z) -> anchor.y());
    }

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, VisualPoint anchor, FrontierClimate climate,
                                   java.util.function.IntBinaryOperator surfaceHeight) {
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be non-negative");
        String source = worldSeed + ":iron_frontier:" + ordinal + ":" + anchor.x() + ":" + anchor.z();
        String key = shortHash(source);
        String planId = "pale_mirror:iron_frontier_" + key;
        int direction = keyedInt(source, "freight_direction", 4);
        int primaryDistance = PRIMARY_MIN + keyedInt(source, "primary_distance", PRIMARY_SPAN);
        int alternateDistance = ALTERNATE_MIN + keyedInt(source, "alternate_distance", ALTERNATE_SPAN);
        VisualPoint gate = offset(anchor, direction, 78);
        VisualPoint depot = offset(anchor, direction, 58);
        VisualPoint rawPrimary = offset(anchor, direction, primaryDistance);
        VisualPoint primary = new VisualPoint(rawPrimary.x(), surfaceHeight.applyAsInt(rawPrimary.x(), rawPrimary.z()), rawPrimary.z());
        VisualPoint rawAlternate = offset(anchor, (direction + 1) % 4, alternateDistance);
        VisualPoint alternate = new VisualPoint(rawAlternate.x(), surfaceHeight.applyAsInt(rawAlternate.x(), rawAlternate.z()), rawAlternate.z());
        List<VisualModulePlacement> modules = modules(anchor, climate, direction);
        List<VisualBounds> plots = expansionPlots(anchor);
        List<ResidentSeed> residents = residents(source, modules);
        VisualPoint railStart = new VisualPoint(depot.x(), depot.y() + 1, depot.z());
        VisualPoint railEnd = new VisualPoint(primary.x(), primary.y() + 1, primary.z());
        List<VisualPoint> rail = costedRail(railStart, railEnd, surfaceHeight);
        List<VisualPoint> shelters = shelterCandidates(anchor, direction);
        String contentHash = sha256(planId + ":" + climate + ":" + direction + ":" + modules + ":" + rail);
        return new AuthoredRegionSeed(planId, "pale_mirror:iron_frontier", DEFINITION_VERSION,
                contentHash, "minecraft:overworld", climate.name().toLowerCase(Locale.ROOT), climate.palette(), anchor,
                bounds(anchor, SETTLEMENT_RADIUS, -8, 40), gate, depot, primary, alternate,
                rail, modules, residents, plots, shelters);
    }

    private static List<VisualModulePlacement> modules(VisualPoint a, FrontierClimate climate, int gateDirection) {
        List<VisualModulePlacement> out = new ArrayList<>();
        add(out, climate, "civic_hall", "CIVIC", a, 0, 0, gateDirection);
        add(out, climate, "receiving_depot", "LOGISTICS", a, gateDirection, 54, gateDirection + 2);
        add(out, climate, "barracks", "DEFENCE", a, gateDirection + 1, 47, gateDirection + 3);
        add(out, climate, "smithy", "INDUSTRY", a, gateDirection + 3, 44, gateDirection + 1);
        add(out, climate, "clinic", "CIVIC", a, gateDirection + 2, 34, gateDirection);
        add(out, climate, "inn", "CIVIC", a, gateDirection + 1, 25, gateDirection + 3);
        add(out, climate, "stable", "LOGISTICS", a, gateDirection + 3, 62, gateDirection + 1);
        add(out, climate, "market", "ECONOMY", a, gateDirection + 2, 18, gateDirection);
        for (int i = 0; i < 6; i++) add(out, climate, "residence_" + (i % 3 + 1), "HOUSING", a, i, 42 + (i % 2) * 17, i + 2);
        add(out, climate, "workshop_1", "INDUSTRY", a, gateDirection, 31, gateDirection + 2);
        add(out, climate, "workshop_2", "INDUSTRY", a, gateDirection + 2, 57, gateDirection);
        return List.copyOf(out);
    }

    private static void add(List<VisualModulePlacement> out, FrontierClimate climate, String name, String role,
                            VisualPoint anchor, int direction, int radius, int rotation) {
        String assetClimate = climate == FrontierClimate.DRY_ARID ? "temperate" : climate.name().toLowerCase(Locale.ROOT);
        VisualPoint origin = offset(anchor, direction, radius);
        out.add(new VisualModulePlacement("pale_mirror_visuals:" + assetClimate
                + "/" + name, role, origin, rotation, moduleFootprint(name, origin, rotation)));
    }

    private static VisualBounds moduleFootprint(String name, VisualPoint origin, int rotation) {
        int[] size = switch (name) {
            case "barracks" -> new int[]{9, 8, 18}; case "civic_hall" -> new int[]{29, 37, 22};
            case "clinic" -> new int[]{13, 18, 14}; case "inn" -> new int[]{17, 13, 26};
            case "market", "stable" -> new int[]{19, 8, 16}; case "receiving_depot" -> new int[]{10, 6, 6};
            case "residence_1", "residence_3" -> new int[]{10, 7, 9}; case "residence_2" -> new int[]{11, 11, 14};
            case "smithy" -> new int[]{5, 7, 7}; case "workshop_1" -> new int[]{10, 7, 8};
            case "workshop_2" -> new int[]{16, 6, 20};
            default -> throw new IllegalArgumentException("Unknown authored module " + name);
        };
        boolean swap = Math.floorMod(rotation, 2) == 1;
        int xSize = swap ? size[2] : size[0]; int zSize = swap ? size[0] : size[2];
        VisualPoint min = new VisualPoint(origin.x() - xSize / 2, origin.y() + 1, origin.z() - zSize / 2);
        return new VisualBounds(min, new VisualPoint(min.x() + xSize - 1, min.y() + size[1] - 1, min.z() + zSize - 1));
    }

    private static List<VisualBounds> expansionPlots(VisualPoint anchor) {
        List<VisualBounds> plots = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            VisualPoint p = offset(anchor, i, 72 + (i % 2) * 8);
            plots.add(bounds(p, 8, -2, 12));
        }
        return List.copyOf(plots);
    }

    private static List<VisualPoint> shelterCandidates(VisualPoint anchor, int freightDirection) {
        return List.of(offset(anchor, freightDirection + 1, 128), offset(anchor, freightDirection + 2, 144),
                offset(anchor, freightDirection + 3, 120));
    }

    private static List<ResidentSeed> residents(String source, List<VisualModulePlacement> modules) {
        List<ResidentSeed> out = new ArrayList<>(48);
        addResidents(out, source, modules, "CIVILIANS", "resident", 20, 8, 0);
        addResidents(out, source, modules, "WORKERS", "worker", 14, 8, 2);
        addResidents(out, source, modules, "SPECIALISTS", "specialist", 4, 1, 3);
        addResidents(out, source, modules, "GUARDS", "guard", 6, 2, 1);
        addResidents(out, source, modules, "CHILDREN", "child", 4, 8, 0);
        return List.copyOf(out);
    }

    private static void addResidents(List<ResidentSeed> out, String source, List<VisualModulePlacement> modules,
                                     String cohort, String role, int count, int homeStart, int workStart) {
        for (int i = 0; i < count; i++) {
            int serial = out.size();
            VisualPoint home = modules.get(homeStart + i % Math.min(6, modules.size() - homeStart)).origin();
            VisualPoint work = modules.get(workStart + i % Math.max(1, Math.min(6, modules.size() - workStart))).origin();
            String id = UUID.nameUUIDFromBytes((source + ":resident:" + serial).getBytes(StandardCharsets.UTF_8)).toString();
            out.add(new ResidentSeed(id, "pale_mirror_visuals.resident." + keyedInt(source, "name:" + serial, 64),
                    cohort, role, home, work));
        }
    }

    static List<VisualPoint> cardinalRail(VisualPoint from, VisualPoint to, int y) {
        List<VisualPoint> nodes = new ArrayList<>();
        int x = from.x();
        int z = from.z();
        nodes.add(new VisualPoint(x, y, z));
        while (x != to.x()) {
            x += Integer.signum(to.x() - x);
            nodes.add(new VisualPoint(x, y, z));
        }
        while (z != to.z()) {
            z += Integer.signum(to.z() - z);
            nodes.add(new VisualPoint(x, y, z));
        }
        return List.copyOf(nodes);
    }

    static List<VisualPoint> costedRail(VisualPoint from, VisualPoint to,
                                       java.util.function.IntBinaryOperator surfaceHeight) {
        int dx = Integer.signum(to.x() - from.x()); int dz = Integer.signum(to.z() - from.z());
        if (dx != 0 && dz != 0) throw new IllegalArgumentException("Frontier freight endpoints must share a cardinal axis");
        int sideX = -dz; int sideZ = dx;
        List<List<VisualPoint>> candidates = new ArrayList<>();
        for (int offset : new int[]{0, -32, 32, -64, 64}) {
            List<int[]> horizontal = new ArrayList<>();
            appendLine(horizontal, from.x(), from.z(), from.x() + sideX * offset, from.z() + sideZ * offset);
            appendLine(horizontal, horizontal.getLast()[0], horizontal.getLast()[1],
                    to.x() + sideX * offset, to.z() + sideZ * offset);
            appendLine(horizontal, horizontal.getLast()[0], horizontal.getLast()[1], to.x(), to.z());
            candidates.add(grade(horizontal, from.y(), to.y(), surfaceHeight));
        }
        return candidates.stream().min(java.util.Comparator.comparingLong(path -> railCost(path, surfaceHeight)))
                .orElseThrow();
    }

    private static void appendLine(List<int[]> result, int fromX, int fromZ, int toX, int toZ) {
        int x = fromX; int z = fromZ;
        if (result.isEmpty()) result.add(new int[]{x, z});
        while (x != toX) { x += Integer.signum(toX - x); result.add(new int[]{x, z}); }
        while (z != toZ) { z += Integer.signum(toZ - z); result.add(new int[]{x, z}); }
    }

    private static List<VisualPoint> grade(List<int[]> horizontal, int startY, int endY,
                                           java.util.function.IntBinaryOperator surfaceHeight) {
        List<VisualPoint> result = new ArrayList<>(horizontal.size()); int previous = startY;
        for (int index = 0; index < horizontal.size(); index++) {
            int[] point = horizontal.get(index); int remaining = horizontal.size() - 1 - index;
            int desired = surfaceHeight.applyAsInt(point[0], point[1]) + 1;
            int lower = Math.max(previous - 1, endY - remaining); int upper = Math.min(previous + 1, endY + remaining);
            int y = index == 0 ? startY : index == horizontal.size() - 1 ? endY : Math.max(lower, Math.min(upper, desired));
            result.add(new VisualPoint(point[0], y, point[1])); previous = y;
        }
        return List.copyOf(result);
    }

    private static long railCost(List<VisualPoint> path, java.util.function.IntBinaryOperator surfaceHeight) {
        long cost = path.size();
        for (int index = 0; index < path.size(); index++) {
            VisualPoint point = path.get(index); cost += Math.abs(point.y() - (surfaceHeight.applyAsInt(point.x(), point.z()) + 1)) * 10L;
            if (index > 0) cost += Math.abs(point.y() - path.get(index - 1).y()) * 4L;
        }
        return cost;
    }

    private static VisualBounds bounds(VisualPoint center, int radius, int down, int up) {
        return new VisualBounds(new VisualPoint(center.x() - radius, center.y() + down, center.z() - radius),
                new VisualPoint(center.x() + radius, center.y() + up, center.z() + radius));
    }

    private static VisualPoint offset(VisualPoint p, int direction, int distance) {
        return switch (Math.floorMod(direction, 4)) {
            case 0 -> new VisualPoint(p.x() + distance, p.y(), p.z());
            case 1 -> new VisualPoint(p.x(), p.y(), p.z() + distance);
            case 2 -> new VisualPoint(p.x() - distance, p.y(), p.z());
            default -> new VisualPoint(p.x(), p.y(), p.z() - distance);
        };
    }

    private static int keyedInt(String source, String purpose, int bound) {
        byte[] hash = digest(source + ":" + purpose);
        return Math.floorMod(ByteBuffer.wrap(hash).getInt(), bound);
    }

    private static String shortHash(String source) { return sha256(source).substring(0, 12); }
    private static String sha256(String source) { return HexFormat.of().formatHex(digest(source)); }
    private static byte[] digest(String source) {
        try { return MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
