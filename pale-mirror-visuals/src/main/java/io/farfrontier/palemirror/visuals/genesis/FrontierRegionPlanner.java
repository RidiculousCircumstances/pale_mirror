package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredMineRole;
import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.MineFoundationPlan;
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
    public static final int DEFINITION_VERSION = 10;
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
        return plan(worldSeed, ordinal, syntheticTerrain(anchor), climate,
                (requirement, candidates) -> new MountainMineAnchor(candidates.getFirst(),
                        cardinalDirection(anchor, candidates.getFirst())),
                FrontierRegionPlanner::gradedManhattanRail);
    }

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, VisualPoint anchor, FrontierClimate climate,
                                   java.util.function.IntBinaryOperator mineHeight) {
        return plan(worldSeed, ordinal, syntheticTerrain(anchor), climate, (requirement, candidates) -> {
            VisualPoint candidate = candidates.getFirst();
            VisualPoint portal = new VisualPoint(candidate.x(), mineHeight.applyAsInt(candidate.x(), candidate.z()), candidate.z());
            return new MountainMineAnchor(portal, cardinalDirection(anchor, candidate));
        }, FrontierRegionPlanner::gradedManhattanRail);
    }

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, VisualPoint anchor, FrontierClimate climate,
                                   MineAnchorResolver mineAnchors, RailPathResolver railPaths) {
        return plan(worldSeed, ordinal, syntheticTerrain(anchor), climate, mineAnchors, railPaths);
    }

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, TerrainCandidate terrain, FrontierClimate climate,
                                   MineAnchorResolver mineAnchors, RailPathResolver railPaths) {
        VisualPoint anchor = terrain.anchor();
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
        var settlement = new SettlementLayoutPlanner().plan(source, anchor, climate, direction, terrain);
        VisualPoint gate = settlement.freightGate();
        VisualPoint depot = settlement.receivingDepot();
        List<VisualModulePlacement> modules = settlement.modules();
        List<ResidentSeed> residents = residents(source, modules);
        AuthoredMineSitePlan primary = minePlan(planId, AuthoredMineRole.PRIMARY, primaryAnchor, climate, source);
        AuthoredMineSitePlan alternate = minePlan(planId, AuthoredMineRole.ALTERNATE, alternateAnchor, climate, source);
        VisualPoint railStart = new VisualPoint(depot.x(), depot.y() + 1, depot.z());
        List<VisualPoint> rail = railPaths.resolve(placementProfile.route(), railStart, primary.loadingEndpoint());
        String contentHash = sha256(planId + ":" + climate + ":" + direction + ":" + settlement + ":"
                + primary + ":" + alternate + ":" + rail);
        return new AuthoredRegionSeed(planId, placementProfile.id(), DEFINITION_VERSION,
                contentHash, "minecraft:overworld", climate.name().toLowerCase(Locale.ROOT), climate.palette(), anchor,
                settlement, primary, alternate, rail, residents);
    }

    private static TerrainCandidate syntheticTerrain(VisualPoint anchor) {
        return new TerrainCandidate(anchor, 6, 0, 0, 0, 1, 0);
    }

    private static AuthoredMineSitePlan minePlan(String planId, AuthoredMineRole role, MountainMineAnchor anchor,
                                                  FrontierClimate climate, String source) {
        VisualPoint portal = anchor.portal();
        int direction = anchor.inwardQuarterTurns();
        String family = climate == FrontierClimate.DRY_ARID ? "temperate" : climate.name().toLowerCase(Locale.ROOT);
        List<VisualModulePlacement> initial = new ArrayList<>();
        List<StagedVisualModule> staged = new ArrayList<>();
        addSurfaceMine(initial, family, "stable", "portal", "MINE_PORTAL",
                role, anchor, 19, 8, 16);
        addSurfaceMine(initial, family, "residence_1", "crew", "MINE_SUPPORT",
                role, anchor, 10, 7, 9);
        addUndergroundMine(initial, family, "entrance_adit", "MINE_ADIT", portal,
                MineUndergroundLayout.ADIT, direction, 7, 6, 7);
        addUndergroundMine(initial, family, "controller_chamber", "MINE_CONTROLLER", portal,
                MineUndergroundLayout.CONTROLLER, direction, 17, 13, 13);
        if (role == AuthoredMineRole.PRIMARY) {
            addSurfaceMine(initial, family, "workshop_2", "processing", "MINE_PROCESSING",
                    role, anchor, 16, 6, 20);
            addSurfaceMine(initial, family, "workshop_1", "power", "MINE_POWER",
                    role, anchor, 10, 7, 8);
            addSurfaceMine(initial, family, "receiving_depot", "loading", "MINE_LOGISTICS",
                    role, anchor, 10, 6, 6);
            addUndergroundMine(initial, family, "iron_gallery", "MINE_GALLERY", portal,
                    MineUndergroundLayout.GALLERY, direction, 40, 9, 17);
        } else {
            staged.add(stageSurfaceMine("foundation", family, "receiving_depot", "dispatch",
                    "MINE_LOGISTICS", role, anchor, 10, 6, 6));
            staged.add(stageSurfaceMine("shell", family, "workshop_2", "processing",
                    "MINE_PROCESSING", role, anchor, 16, 6, 20));
            staged.add(stageSurfaceMine("machinery", family, "workshop_1", "power",
                    "MINE_POWER", role, anchor, 10, 7, 8));
            staged.add(stageSurfaceMine("commissioning", family, "receiving_depot", "freight",
                    "MINE_LOGISTICS", role, anchor, 10, 6, 6));
        }
        VisualPoint controller = local(portal, MineUndergroundLayout.CONTROLLER, direction);
        String loadingPad = role == AuthoredMineRole.PRIMARY ? "loading" : "dispatch";
        VisualPoint loadingCenter = anchor.surfaceCenter(role, loadingPad);
        // The route terminates at the outward apron, not in the middle of the
        // freight building. This keeps rails, minecarts and the loading canopy
        // readable as one physical transfer point.
        VisualPoint loading = local(loadingCenter, 0, -5, 1, direction);
        VisualBounds siteBounds = orientedBounds(portal, 72, 52, -22, 28, direction);
        VisualPoint machinery = anchor.surfaceCenter(role, "power");
        List<SemanticVisualVolume> volumes = List.of(
                new SemanticVisualVolume("infection_gallery", "INFECTION",
                        around(local(portal, MineUndergroundLayout.GALLERY, direction), 10, 8)),
                new SemanticVisualVolume("infection_controller", "INFECTION", around(controller, 8, 7)),
                new SemanticVisualVolume("machinery", "MACHINERY", around(machinery, 12, 12)),
                new SemanticVisualVolume("loading", "LOGISTICS", around(loading, 20, 7)));
        String id = planId + (role == AuthoredMineRole.PRIMARY ? ":mine17" : ":red_valley");
        return new AuthoredMineSitePlan(id, role, portal, loading, controller, siteBounds, direction,
                initial, staged, foundations(role, anchor), volumes);
    }

    private static List<MineFoundationPlan> foundations(AuthoredMineRole role, MountainMineAnchor anchor) {
        return MineSurfaceLayout.pads(role).stream().map(pad -> {
            VisualPoint center = anchor.surfaceCenter(role, pad.id());
            return new MineFoundationPlan(pad.id(), pad.bounds(center, anchor.inwardQuarterTurns()),
                    center.y(), MineSurfaceLayout.APRON, MineSurfaceLayout.MAXIMUM_CUT,
                    MineSurfaceLayout.MAXIMUM_FILL);
        }).toList();
    }

    private static void addSurfaceMine(List<VisualModulePlacement> target, String family, String template,
                                       String padId, String moduleRole, AuthoredMineRole mineRole,
                                       MountainMineAnchor anchor, int sx, int sy, int sz) {
        target.add(surfaceMineModule(family, template, padId, moduleRole, mineRole, anchor, sx, sy, sz));
    }

    private static StagedVisualModule stageSurfaceMine(String stage, String family, String template,
                                                        String padId, String moduleRole, AuthoredMineRole mineRole,
                                                        MountainMineAnchor anchor, int sx, int sy, int sz) {
        return new StagedVisualModule(stage, surfaceMineModule(family, template, padId, moduleRole,
                mineRole, anchor, sx, sy, sz));
    }

    private static VisualModulePlacement surfaceMineModule(String family, String template, String padId,
                                                            String moduleRole, AuthoredMineRole mineRole,
                                                            MountainMineAnchor anchor, int sx, int sy, int sz) {
        VisualPoint origin = anchor.surfaceCenter(mineRole, padId);
        return module(family + "/" + template, moduleRole, origin, anchor.inwardQuarterTurns(), sx, sy, sz);
    }

    private static void addUndergroundMine(List<VisualModulePlacement> target, String family, String name,
                                           String role, VisualPoint portal, MineUndergroundLayout.Node node, int direction,
                                           int sx, int sy, int sz) {
        VisualPoint origin = local(portal, node, direction);
        target.add(module(family + "/mine/" + name, role, origin, direction, sx, sy, sz));
    }

    private static VisualPoint local(VisualPoint portal, MineUndergroundLayout.Node node, int direction) {
        return local(portal, node.right(), node.inward(), node.up(), direction);
    }

    private static VisualModulePlacement module(String template, String role, VisualPoint origin,
                                                 int direction, int sx, int sy, int sz) {
        boolean swap = Math.floorMod(direction, 2) == 1;
        int width = swap ? sz : sx;
        int depth = swap ? sx : sz;
        VisualBounds footprint = new VisualBounds(new VisualPoint(origin.x() - width / 2, origin.y() + 1,
                origin.z() - depth / 2), new VisualPoint(origin.x() + (width - 1) / 2, origin.y() + sy,
                origin.z() + (depth - 1) / 2));
        return new VisualModulePlacement("pale_mirror_visuals:" + template,
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

    static List<VisualPoint> siteCandidates(VisualPoint anchor, int firstDirection, int preferredDistance,
                                            SitePlacementRequirement requirement, int excludedDirection) {
        int minimumDistance = requirement.distanceFromSettlement().minimum();
        int maximumDistance = requirement.distanceFromSettlement().maximum();
        java.util.LinkedHashSet<Integer> distanceSet = new java.util.LinkedHashSet<>();
        distanceSet.add(preferredDistance);
        requirement.landscapeEvidenceDistances().stream()
                .filter(value -> value >= minimumDistance && value <= maximumDistance)
                .sorted(java.util.Comparator.comparingInt((Integer value) -> Math.abs(value - preferredDistance)))
                .forEach(distanceSet::add);
        for (int delta = 16; distanceSet.size() < requirement.preferredDistanceCandidateLimit()
                && delta <= maximumDistance - minimumDistance; delta += 16) {
            int farther = preferredDistance + delta;
            int nearer = preferredDistance - delta;
            if (farther <= maximumDistance) distanceSet.add(farther);
            if (nearer >= minimumDistance) distanceSet.add(nearer);
        }
        distanceSet.add(minimumDistance);
        distanceSet.add(maximumDistance);
        List<Integer> distances = List.copyOf(distanceSet);
        java.util.LinkedHashSet<VisualPoint> result = new java.util.LinkedHashSet<>();
        // Exact validation is deliberately bounded. Interleave cardinal sectors and
        // radial distances and lateral refinements diagonally. A small budget must
        // reach the biome-evidenced radii as well as the width of a broken mountain front.
        int maximumDiagonal = distances.size() + requirement.lateralOffsets().size() - 2;
        for (int diagonal = 0; diagonal <= maximumDiagonal; diagonal++) {
            for (int distanceIndex = 0; distanceIndex < distances.size(); distanceIndex++) {
                int lateralIndex = diagonal - distanceIndex;
                if (lateralIndex < 0 || lateralIndex >= requirement.lateralOffsets().size()) continue;
                int distance = distances.get(distanceIndex);
                int lateral = requirement.lateralOffsets().get(lateralIndex);
                for (int turn = 0; turn < 4; turn++) {
                    int direction = Math.floorMod(firstDirection + turn, 4);
                    if (excludedDirection >= 0 && direction == Math.floorMod(excludedDirection, 4)) continue;
                    VisualPoint candidate = offset(offset(anchor, direction, distance), direction + 1, lateral);
                    long dx = (long) candidate.x() - anchor.x();
                    long dz = (long) candidate.z() - anchor.z();
                    long radialSquared = dx * dx + dz * dz;
                    if (requirement.distanceFromSettlement().containsSquared(radialSquared)) result.add(candidate);
                }
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
            VisualModulePlacement homeModule = modules.get(
                    homeStart + i % Math.min(6, modules.size() - homeStart));
            VisualPoint home = homeModule.ports().stream()
                    .filter(port -> port.kind() == io.farfrontier.palemirror.api.VisualPortKind.PUBLIC_ENTRANCE)
                    .findFirst().orElseThrow().position();
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
