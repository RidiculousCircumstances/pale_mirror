package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredMineRole;
import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.ResidentSeed;
import io.farfrontier.palemirror.api.SemanticVisualVolume;
import io.farfrontier.palemirror.api.StagedVisualModule;
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

/** Pure deterministic layout grammar. Terrain selection supplies one settlement datum and two mine anchors. */
public final class FrontierRegionPlanner {
    public static final int DEFINITION_VERSION = 6;
    public static final int SETTLEMENT_RADIUS = 88;
    private final RegionPlacementProfile placementProfile;

    public FrontierRegionPlanner() {
        this(RegionPlacementProfiles.IRON_FRONTIER);
    }

    public FrontierRegionPlanner(RegionPlacementProfile placementProfile) {
        this.placementProfile = java.util.Objects.requireNonNull(placementProfile, "placementProfile");
        placementProfile.requireSite(RegionPlacementProfiles.PRIMARY_MINE);
        SitePlacementRequirement alternate = placementProfile.requireSite(RegionPlacementProfiles.ALTERNATE_MINE);
        if (!alternate.separateCardinalSectorFromRole().equals(RegionPlacementProfiles.PRIMARY_MINE)) {
            throw new IllegalArgumentException("iron frontier alternate mine must use a separate primary-mine sector");
        }
        if (!placementProfile.route().originRole().equals("settlement")
                || !placementProfile.route().destinationRole().equals(RegionPlacementProfiles.PRIMARY_MINE)) {
            throw new IllegalArgumentException("iron frontier baseline route must connect settlement to primary mine");
        }
    }

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, VisualPoint anchor, FrontierClimate climate) {
        return plan(worldSeed, ordinal, anchor, climate,
                (requirement, candidates) -> new MountainMineAnchor(candidates.getFirst(),
                        cardinalDirection(anchor, candidates.getFirst())),
                FrontierRegionPlanner::gradedManhattanRail);
    }

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, VisualPoint anchor, FrontierClimate climate,
                                   java.util.function.IntBinaryOperator mineHeight) {
        return plan(worldSeed, ordinal, anchor, climate, (requirement, candidates) -> {
            VisualPoint candidate = candidates.getFirst();
            VisualPoint portal = new VisualPoint(candidate.x(), mineHeight.applyAsInt(candidate.x(), candidate.z()), candidate.z());
            return new MountainMineAnchor(portal, cardinalDirection(anchor, candidate));
        }, FrontierRegionPlanner::gradedManhattanRail);
    }

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, VisualPoint anchor, FrontierClimate climate,
                                   MineAnchorResolver mineAnchors, RailPathResolver railPaths) {
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be non-negative");
        String source = worldSeed + ":" + placementProfile.addressSalt() + ":" + ordinal + ":"
                + anchor.x() + ":" + anchor.z();
        String key = shortHash(source);
        String planId = "pale_mirror:iron_frontier_" + key;
        SitePlacementRequirement primaryRequirement = placementProfile.requireSite(RegionPlacementProfiles.PRIMARY_MINE);
        SitePlacementRequirement alternateRequirement = placementProfile.requireSite(RegionPlacementProfiles.ALTERNATE_MINE);
        int preferredDirection = keyedInt(source, "freight_direction", 4);
        int primaryDistance = preferredDistance(source, "primary_distance", primaryRequirement.distanceFromSettlement());
        int alternateDistance = preferredDistance(source, "alternate_distance", alternateRequirement.distanceFromSettlement());
        MountainMineAnchor primaryAnchor = mineAnchors.resolve(primaryRequirement,
                siteCandidates(anchor, preferredDirection, primaryDistance, primaryRequirement, -1));
        int direction = cardinalDirection(anchor, primaryAnchor.portal());
        MountainMineAnchor alternateAnchor = mineAnchors.resolve(alternateRequirement,
                siteCandidates(anchor, direction + 1, alternateDistance, alternateRequirement, direction));
        VisualPoint gate = offset(anchor, direction, 78);
        VisualPoint depot = offset(anchor, direction, 58);
        List<VisualModulePlacement> modules = modules(anchor, climate, direction);
        List<VisualBounds> plots = expansionPlots(anchor);
        List<ResidentSeed> residents = residents(source, modules);
        AuthoredMineSitePlan primary = minePlan(planId, AuthoredMineRole.PRIMARY, primaryAnchor, climate, source);
        AuthoredMineSitePlan alternate = minePlan(planId, AuthoredMineRole.ALTERNATE, alternateAnchor, climate, source);
        VisualPoint railStart = new VisualPoint(depot.x(), depot.y() + 1, depot.z());
        List<VisualPoint> rail = railPaths.resolve(placementProfile.route(), railStart, primary.loadingEndpoint());
        List<VisualPoint> shelters = shelterCandidates(anchor, direction);
        String contentHash = sha256(planId + ":" + climate + ":" + direction + ":" + modules + ":"
                + primary + ":" + alternate + ":" + rail);
        return new AuthoredRegionSeed(planId, placementProfile.id(), DEFINITION_VERSION,
                contentHash, "minecraft:overworld", climate.name().toLowerCase(Locale.ROOT), climate.palette(), anchor,
                bounds(anchor, SETTLEMENT_RADIUS, -8, 40), gate, depot, primary, alternate,
                rail, modules, residents, plots, shelters);
    }

    private static AuthoredMineSitePlan minePlan(String planId, AuthoredMineRole role, MountainMineAnchor anchor,
                                                  FrontierClimate climate, String source) {
        VisualPoint portal = anchor.portal();
        int direction = anchor.inwardQuarterTurns();
        String family = climate == FrontierClimate.DRY_ARID ? "temperate" : climate.name().toLowerCase(Locale.ROOT);
        List<VisualModulePlacement> initial = new ArrayList<>();
        List<StagedVisualModule> staged = new ArrayList<>();
        addMine(initial, family, "portal_hoist", "MINE_PORTAL", portal, 0, 0, direction, 45, 19, 37);
        addMine(initial, family, "entrance_adit", "MINE_ADIT", portal, 0, 28, direction, 7, 6, 7);
        addMine(initial, family, "crew_outpost", "MINE_SUPPORT", portal, -18, 18, direction, 12, 6, 9);
        addMine(initial, family, "controller_chamber", "MINE_CONTROLLER", portal, 0, 76, direction, 17, 13, 13);
        if (role == AuthoredMineRole.PRIMARY) {
            addMine(initial, family, "processing_hall", "MINE_PROCESSING", portal, 25, -10, direction, 31, 10, 17);
            addMine(initial, family, "power_house", "MINE_POWER", portal, -27, -8, direction, 13, 15, 15);
            addMine(initial, family, "loading_yard", "MINE_LOGISTICS", portal, 0, -30, direction, 14, 10, 9);
            addMine(initial, family, "iron_gallery", "MINE_GALLERY", portal, 0, 50, direction, 40, 9, 17);
        } else {
            staged.add(stageMine("foundation", family, "dispatch_foundation", "MINE_LOGISTICS", portal,
                    0, -28, direction, 13, 7, 13));
            staged.add(stageMine("shell", family, "dispatch_shell", "MINE_PROCESSING", portal,
                    20, -8, direction, 31, 10, 17));
            staged.add(stageMine("machinery", family, "dispatch_machinery", "MINE_POWER", portal,
                    -22, -8, direction, 19, 9, 22));
            staged.add(stageMine("commissioning", family, "dispatch_commissioning", "MINE_LOGISTICS", portal,
                    0, -28, direction, 14, 10, 9));
        }
        VisualPoint controller = local(portal, 0, 76, -18, direction);
        VisualPoint loading = local(portal, 0, -42, 1, direction);
        VisualBounds siteBounds = orientedBounds(portal, 50, 90, -26, 28, direction);
        List<SemanticVisualVolume> volumes = List.of(
                new SemanticVisualVolume("infection_gallery", "INFECTION", around(local(portal, 0, 50, -12, direction), 10, 8)),
                new SemanticVisualVolume("infection_controller", "INFECTION", around(controller, 8, 7)),
                new SemanticVisualVolume("machinery", "MACHINERY", around(local(portal, -22, -8, 5, direction), 12, 12)),
                new SemanticVisualVolume("loading", "LOGISTICS", around(loading, 20, 7)));
        String id = planId + (role == AuthoredMineRole.PRIMARY ? ":mine17" : ":red_valley");
        return new AuthoredMineSitePlan(id, role, portal, loading, controller, siteBounds, direction,
                initial, staged, volumes);
    }

    private static void addMine(List<VisualModulePlacement> target, String family, String name, String role,
                                VisualPoint portal, int right, int inward, int direction, int sx, int sy, int sz) {
        target.add(mineModule(family, name, role, portal, right, inward, direction, sx, sy, sz));
    }

    private static StagedVisualModule stageMine(String stage, String family, String name, String role,
                                                 VisualPoint portal, int right, int inward, int direction,
                                                 int sx, int sy, int sz) {
        return new StagedVisualModule(stage, mineModule(family, name, role, portal, right, inward, direction, sx, sy, sz));
    }

    private static VisualModulePlacement mineModule(String family, String name, String role, VisualPoint portal,
                                                     int right, int inward, int direction, int sx, int sy, int sz) {
        VisualPoint origin = local(portal, right, inward, name.contains("adit") || name.contains("gallery")
                || name.contains("controller") ? -18 : 0, direction);
        boolean swap = Math.floorMod(direction, 2) == 1;
        int width = swap ? sz : sx;
        int depth = swap ? sx : sz;
        VisualBounds footprint = new VisualBounds(new VisualPoint(origin.x() - width / 2, origin.y() + 1,
                origin.z() - depth / 2), new VisualPoint(origin.x() + (width - 1) / 2, origin.y() + sy,
                origin.z() + (depth - 1) / 2));
        return new VisualModulePlacement("pale_mirror_visuals:" + family + "/mine/" + name,
                role, origin, direction, footprint);
    }

    private static VisualPoint local(VisualPoint portal, int right, int inward, int up, int direction) {
        int dx = switch (Math.floorMod(direction, 4)) { case 0 -> inward; case 1 -> -right; case 2 -> -inward; default -> right; };
        int dz = switch (Math.floorMod(direction, 4)) { case 0 -> right; case 1 -> inward; case 2 -> -right; default -> -inward; };
        return new VisualPoint(portal.x() + dx, portal.y() + up, portal.z() + dz);
    }

    private static VisualBounds orientedBounds(VisualPoint portal, int horizontal, int inward, int down, int up,
                                                int direction) {
        List<VisualPoint> corners = List.of(local(portal, -horizontal, -horizontal, down, direction),
                local(portal, horizontal, -horizontal, down, direction),
                local(portal, -horizontal, inward, up, direction),
                local(portal, horizontal, inward, up, direction));
        return new VisualBounds(new VisualPoint(corners.stream().mapToInt(VisualPoint::x).min().orElseThrow(),
                portal.y() + down, corners.stream().mapToInt(VisualPoint::z).min().orElseThrow()),
                new VisualPoint(corners.stream().mapToInt(VisualPoint::x).max().orElseThrow(),
                        portal.y() + up, corners.stream().mapToInt(VisualPoint::z).max().orElseThrow()));
    }

    private static VisualBounds around(VisualPoint point, int radius, int vertical) {
        return new VisualBounds(new VisualPoint(point.x() - radius, point.y() - 1, point.z() - radius),
                new VisualPoint(point.x() + radius, point.y() + vertical, point.z() + radius));
    }

    private static int preferredDistance(String source, String key, DistanceBand band) {
        return band.minimum() + keyedInt(source, key, band.span());
    }

    private static List<VisualPoint> siteCandidates(VisualPoint anchor, int firstDirection, int preferredDistance,
                                                     SitePlacementRequirement requirement, int excludedDirection) {
        int minimumDistance = requirement.distanceFromSettlement().minimum();
        int maximumDistance = requirement.distanceFromSettlement().maximum();
        List<Integer> distances = new ArrayList<>();
        distances.add(preferredDistance);
        for (int delta = 16; distances.size() < requirement.preferredDistanceCandidateLimit()
                && delta <= maximumDistance - minimumDistance; delta += 16) {
            int farther = preferredDistance + delta;
            int nearer = preferredDistance - delta;
            if (farther <= maximumDistance) distances.add(farther);
            if (nearer >= minimumDistance) distances.add(nearer);
        }
        if (!distances.contains(minimumDistance)) distances.add(minimumDistance);
        if (!distances.contains(maximumDistance)) distances.add(maximumDistance);
        java.util.LinkedHashSet<VisualPoint> result = new java.util.LinkedHashSet<>();
        for (int turn = 0; turn < 4; turn++) {
            int direction = Math.floorMod(firstDirection + turn, 4);
            if (excludedDirection >= 0 && direction == Math.floorMod(excludedDirection, 4)) continue;
            for (int distance : distances) for (int lateral : requirement.lateralOffsets()) {
                VisualPoint candidate = offset(offset(anchor, direction, distance), direction + 1, lateral);
                long dx = (long) candidate.x() - anchor.x();
                long dz = (long) candidate.z() - anchor.z();
                long radialSquared = dx * dx + dz * dz;
                if (requirement.distanceFromSettlement().containsSquared(radialSquared)) result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    private static int cardinalDirection(VisualPoint from, VisualPoint to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (dx == 0 && dz == 0) throw new IllegalStateException("Resolved MineSite overlaps its settlement");
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 0 : 2) : (dz > 0 ? 1 : 3);
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

    static List<VisualPoint> gradedCardinalRail(VisualPoint from, VisualPoint to) {
        int dx = Integer.signum(to.x() - from.x()); int dz = Integer.signum(to.z() - from.z());
        if (dx != 0 && dz != 0) throw new IllegalArgumentException("Frontier freight endpoints must share a cardinal axis");
        int segments = Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z());
        int elevation = to.y() - from.y();
        if (Math.abs(elevation) > segments) throw new IllegalArgumentException(
                "Rail elevation exceeds grade-safe path length: " + elevation + " over " + segments);
        List<VisualPoint> result = new ArrayList<>(segments + 1);
        int elevationMagnitude = Math.abs(elevation);
        int elevationSign = Integer.signum(elevation);
        for (int index = 0; index <= segments; index++) {
            int x = from.x() + dx * index;
            int z = from.z() + dz * index;
            int progressed = segments == 0 ? 0 : (elevationMagnitude * index + segments / 2) / segments;
            result.add(new VisualPoint(x, from.y() + elevationSign * progressed, z));
        }
        return List.copyOf(result);
    }

    static List<VisualPoint> gradedManhattanRail(RoutePlacementRequirement requirement,
                                                 VisualPoint from, VisualPoint to) {
        List<VisualPoint> horizontal = cardinalRail(from, to, from.y());
        int segments = horizontal.size() - 1;
        int elevation = to.y() - from.y();
        if (Math.abs(elevation) * requirement.horizontalBlocksPerVerticalBlock() > Math.max(1, segments)) {
            throw new IllegalArgumentException("Rail elevation exceeds one-in-"
                    + requirement.horizontalBlocksPerVerticalBlock() + " grade: " + elevation + " over " + segments);
        }
        List<VisualPoint> result = new ArrayList<>(horizontal.size());
        for (int index = 0; index < horizontal.size(); index++) {
            VisualPoint point = horizontal.get(index);
            int progressed = segments == 0 ? 0 : Math.floorDiv(Math.abs(elevation) * index + segments / 2, segments);
            result.add(new VisualPoint(point.x(), from.y() + Integer.signum(elevation) * progressed, point.z()));
        }
        return List.copyOf(result);
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
