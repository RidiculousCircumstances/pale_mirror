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
    public static final int DEFINITION_VERSION = 1;
    public static final int SETTLEMENT_RADIUS = 88;
    private static final int PRIMARY_MIN = 384;
    private static final int PRIMARY_SPAN = 129;
    private static final int ALTERNATE_MIN = 512;
    private static final int ALTERNATE_SPAN = 257;

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, VisualPoint anchor, FrontierClimate climate) {
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be non-negative");
        String source = worldSeed + ":iron_frontier:" + ordinal + ":" + anchor.x() + ":" + anchor.z();
        String key = shortHash(source);
        String planId = "pale_mirror:iron_frontier_" + key;
        int direction = keyedInt(source, "freight_direction", 4);
        int primaryDistance = PRIMARY_MIN + keyedInt(source, "primary_distance", PRIMARY_SPAN);
        int alternateDistance = ALTERNATE_MIN + keyedInt(source, "alternate_distance", ALTERNATE_SPAN);
        VisualPoint gate = offset(anchor, direction, 78);
        VisualPoint depot = offset(anchor, direction, 58);
        VisualPoint primary = offset(anchor, direction, primaryDistance);
        VisualPoint alternate = offset(anchor, (direction + 1) % 4, alternateDistance);
        List<VisualModulePlacement> modules = modules(anchor, climate, direction);
        List<VisualBounds> plots = expansionPlots(anchor);
        List<ResidentSeed> residents = residents(source, modules);
        List<VisualPoint> rail = cardinalRail(gate, primary, anchor.y());
        String contentHash = sha256(planId + ":" + climate + ":" + direction + ":" + modules + ":" + rail);
        return new AuthoredRegionSeed(planId, "pale_mirror:iron_frontier", DEFINITION_VERSION,
                contentHash, "minecraft:overworld", climate.name().toLowerCase(Locale.ROOT), climate.palette(), anchor,
                bounds(anchor, SETTLEMENT_RADIUS, -8, 40), gate, depot, primary, alternate,
                rail, modules, residents, plots);
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
        out.add(new VisualModulePlacement("pale_mirror_visuals:" + climate.name().toLowerCase(Locale.ROOT)
                + "/" + name, role, offset(anchor, direction, radius), rotation));
    }

    private static List<VisualBounds> expansionPlots(VisualPoint anchor) {
        List<VisualBounds> plots = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            VisualPoint p = offset(anchor, i, 72 + (i % 2) * 8);
            plots.add(bounds(p, 8, -2, 12));
        }
        return List.copyOf(plots);
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
