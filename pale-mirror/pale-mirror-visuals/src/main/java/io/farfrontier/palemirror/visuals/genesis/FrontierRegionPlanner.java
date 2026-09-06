package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredMineRole;
import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.AuthoredBuildingPlan;
import io.farfrontier.palemirror.api.AuthoredSettlementSitePlan;
import io.farfrontier.palemirror.api.BuildingFunctionId;
import io.farfrontier.palemirror.api.BuildingSlot;
import io.farfrontier.palemirror.api.BuildingSlotKind;
import io.farfrontier.palemirror.api.MineFoundationPlan;
import io.farfrontier.palemirror.api.ResidentSeed;
import io.farfrontier.palemirror.api.SettlementBuildingCategory;
import io.farfrontier.palemirror.api.SettlementDevelopmentStage;
import io.farfrontier.palemirror.api.SemanticVisualVolume;
import io.farfrontier.palemirror.api.StagedVisualModule;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPort;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Pure deterministic layout grammar. Terrain selection supplies one settlement datum and two mine anchors. */
public final class FrontierRegionPlanner {
    public static final int DEFINITION_VERSION = 27;
    private final RegionPlacementProfile placementProfile;
    public FrontierRegionPlanner() {
        this(RegionPlacementProfiles.IRON_FRONTIER);
    }
    public FrontierRegionPlanner(RegionPlacementProfile placementProfile) {
        this.placementProfile = java.util.Objects.requireNonNull(placementProfile, "placementProfile");
        placementProfile.requireSite(RegionPlacementProfiles.PRIMARY_MINE);
        SitePlacementRequirement alternate = placementProfile.requireSite(RegionPlacementProfiles.ALTERNATE_MINE);
        if (!alternate.relatedSiteRole().equals(RegionPlacementProfiles.PRIMARY_MINE)
                || alternate.minimumSeparationFromRelatedSite() < 1) {
            throw new IllegalArgumentException(
                    "iron frontier alternate mine must be spatially separated from the primary mine");
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
        return plan(worldSeed, ordinal, terrain, climate, mineAnchors, railPaths,
                (source, anchor, selectedClimate, direction, selectedTerrain) -> new SettlementLayoutPlanner()
                        .plan(source, anchor, selectedClimate, direction, selectedTerrain));
    }

    public AuthoredRegionSeed plan(long worldSeed, int ordinal, TerrainCandidate terrain, FrontierClimate climate,
                                   MineAnchorResolver mineAnchors, RailPathResolver railPaths,
                                   SettlementLayoutResolver settlementLayouts) {
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
        var settlement = settlementLayouts.resolve(source, anchor, climate, direction, terrain);
        int excludedDirection = alternateRequirement.requireSeparateCardinalSector() ? direction : -1;
        long minimumMineSeparation = (long) alternateRequirement.minimumSeparationFromRelatedSite()
                * alternateRequirement.minimumSeparationFromRelatedSite();
        List<VisualPoint> alternateCandidates = siteCandidates(anchor, direction + 1, alternateDistance,
                alternateRequirement, excludedDirection).stream()
                .filter(candidate -> horizontalDistanceSquared(candidate, primaryAnchor.portal())
                        >= minimumMineSeparation)
                .toList();
        if (alternateCandidates.isEmpty()) throw new DryMineSiteUnavailableException(
                "Alternate MineSite has no candidate separated from the primary MineSite");
        MountainMineAnchor alternateAnchor = mineAnchors.resolve(alternateRequirement, alternateCandidates);
        VisualPoint gate = settlement.freightGate();
        VisualPoint depot = settlement.receivingDepot();
        AuthoredMineSitePlan primary = minePlan(planId, AuthoredMineRole.PRIMARY, primaryAnchor, climate, source);
        AuthoredMineSitePlan alternate = minePlan(planId, AuthoredMineRole.ALTERNATE, alternateAnchor, climate, source);
        List<ResidentSeed> residents = residents(source, settlement, primary);
        List<VisualPoint> rail = railPaths.resolve(placementProfile.route(), depot, primary.loadingEndpoint());
        var discoveryChunks = AuthoredDiscoveryChunkPlanner.plan(settlement, rail);
        String contentHash = sha256(planId + ":" + climate + ":" + direction + ":" + settlement + ":"
                + primary + ":" + alternate + ":" + rail + ":" + discoveryChunks);
        return new AuthoredRegionSeed(planId, placementProfile.id(), DEFINITION_VERSION,
                contentHash, "minecraft:overworld", climate.name().toLowerCase(Locale.ROOT), climate.palette(), anchor,
                settlement, primary, alternate, rail, discoveryChunks, residents);
    }

    private static TerrainCandidate syntheticTerrain(VisualPoint anchor) {
        return new TerrainCandidate(anchor, 6, 0, 0, 0, 1, 0);
    }

    private static AuthoredMineSitePlan minePlan(String planId, AuthoredMineRole role, MountainMineAnchor anchor,
                                                  FrontierClimate climate, String source) {
        VisualPoint portal = anchor.portal();
        int direction = anchor.inwardQuarterTurns();
        String family = climate.name().toLowerCase(Locale.ROOT);
        List<AuthoredBuildingPlan> surface = new ArrayList<>();
        List<VisualModulePlacement> underground = new ArrayList<>();
        List<StagedVisualModule> staged = new ArrayList<>();
        addSurfaceBuilding(surface, family, "portal_hoist", "portal", "MINE_PORTAL",
                SettlementBuildingCategory.INDUSTRY, List.of("mine_portal", "hoist"), 0, 4,
                role, anchor);
        addSurfaceBuilding(surface, family, "crew_outpost", "crew", "MINE_SUPPORT",
                SettlementBuildingCategory.UTILITY, List.of("crew_outpost", "mine_office"), 0, 4,
                role, anchor);
        addUndergroundMine(underground, family, "entrance_adit", "MINE_ADIT", portal,
                MineUndergroundLayout.ADIT, direction, 7, 6, 7);
        addUndergroundMine(underground, family, "controller_chamber", "MINE_CONTROLLER", portal,
                MineUndergroundLayout.CONTROLLER, direction, 17, 13, 13);
        if (role == AuthoredMineRole.PRIMARY) {
            addSurfaceBuilding(surface, family, "processing_hall", "processing", "MINE_PROCESSING",
                    SettlementBuildingCategory.INDUSTRY, List.of("ore_processing", "ore_sorting"), 0, 6,
                    role, anchor);
            addSurfaceBuilding(surface, family, "power_house", "power", "MINE_POWER",
                    SettlementBuildingCategory.INDUSTRY, List.of("power_house", "machinery"), 0, 3,
                    role, anchor);
            addSurfaceBuilding(surface, family, "loading_yard", "loading", "MINE_LOGISTICS",
                    SettlementBuildingCategory.LOGISTICS, List.of("loading_yard", "freight_endpoint"), 0, 4,
                    role, anchor);
            addSurfaceBuilding(surface, family, "../workshop_1", "maintenance", "MINE_MAINTENANCE",
                    SettlementBuildingCategory.INDUSTRY, List.of("maintenance_workshop"), 0, 3,
                    role, anchor);
            addUndergroundMine(underground, family, "iron_gallery", "MINE_GALLERY", portal,
                    MineUndergroundLayout.GALLERY, direction, 40, 9, 17);
        } else {
            staged.add(stageSurfaceMine("foundation", family, "dispatch_foundation", "dispatch",
                    "MINE_LOGISTICS", role, anchor));
            staged.add(stageSurfaceMine("shell", family, "dispatch_shell", "processing",
                    "MINE_PROCESSING", role, anchor));
            staged.add(stageSurfaceMine("machinery", family, "dispatch_machinery", "power",
                    "MINE_POWER", role, anchor));
            staged.add(stageSurfaceMine("commissioning", family, "dispatch_commissioning", "freight",
                    "MINE_LOGISTICS", role, anchor));
        }
        VisualPoint controller = local(portal, MineUndergroundLayout.CONTROLLER, direction);
        String loadingPad = role == AuthoredMineRole.PRIMARY ? "loading" : "dispatch";
        VisualPoint loadingCenter = anchor.surfaceCenter(role, loadingPad);
        // The route terminates at the outward apron, not in the middle of the
        // freight building. This keeps rails, minecarts and the loading canopy
        // readable as one physical transfer point.
        VisualPoint loading = local(loadingCenter, 0, -5, 1, direction);
        VisualBounds siteBounds = orientedBounds(portal, 96, 52, -22, 28, direction);
        VisualPoint machinery = anchor.surfaceCenter(role, "power");
        List<SemanticVisualVolume> volumes = List.of(
                new SemanticVisualVolume("infection_gallery", "INFECTION",
                        around(local(portal, MineUndergroundLayout.GALLERY, direction), 10, 8)),
                new SemanticVisualVolume("infection_controller", "INFECTION", around(controller, 8, 7)),
                new SemanticVisualVolume("machinery", "MACHINERY", around(machinery, 12, 12)),
                new SemanticVisualVolume("loading", "LOGISTICS", around(loading, 20, 7)));
        String id = planId + (role == AuthoredMineRole.PRIMARY ? ":mine17" : ":red_valley");
        List<MineFoundationPlan> mineFoundations = foundations(role, anchor);
        int minimumSurface = mineFoundations.stream().mapToInt(MineFoundationPlan::targetY).min().orElse(portal.y());
        int maximumSurface = mineFoundations.stream().mapToInt(MineFoundationPlan::targetY).max().orElse(portal.y());
        return new AuthoredMineSitePlan(id, role, portal, loading, controller, siteBounds, direction,
                surface, underground, staged, mineFoundations,
                new io.farfrontier.palemirror.api.SiteEnvironmentPlan("pale_mirror:authored_mine", portal,
                        role == AuthoredMineRole.PRIMARY ? 78 : 48,
                        role == AuthoredMineRole.PRIMARY ? 110 : 72, 12, minimumSurface, maximumSurface), volumes);
    }

    private static List<MineFoundationPlan> foundations(AuthoredMineRole role, MountainMineAnchor anchor) {
        return MineSurfaceLayout.pads(role).stream().map(pad -> {
            VisualPoint center = anchor.surfaceCenter(role, pad.id());
            return new MineFoundationPlan(pad.id(), pad.bounds(center, anchor.inwardQuarterTurns()),
                    center.y(), MineSurfaceLayout.APRON, MineSurfaceLayout.MAXIMUM_CUT,
                    MineSurfaceLayout.MAXIMUM_FILL);
        }).toList();
    }

    private static void addSurfaceBuilding(List<AuthoredBuildingPlan> target, String family, String template,
                                           String padId, String moduleRole, SettlementBuildingCategory category,
                                           List<String> functions, int housing, int work,
                                           AuthoredMineRole mineRole, MountainMineAnchor anchor) {
        VisualModulePlacement module = MineSurfaceModuleFactory.place(family, template, padId, moduleRole,
                mineRole, anchor);
        List<BuildingSlot> slots = new ArrayList<>();
        for (int index = 0; index < housing; index++) {
            slots.add(mineSlot("bed_" + index, BuildingSlotKind.BED, module.footprint(), index));
        }
        for (int index = 0; index < work; index++) {
            slots.add(mineSlot("work_" + index, BuildingSlotKind.WORKSTATION,
                    module.footprint(), housing + index));
        }
        if (category == SettlementBuildingCategory.LOGISTICS) {
            slots.add(mineSlot("storage", BuildingSlotKind.STORAGE, module.footprint(), housing + work));
        }
        if (slots.isEmpty()) slots.add(mineSlot("service", BuildingSlotKind.SERVICE, module.footprint(), 0));
        target.add(new AuthoredBuildingPlan(padId, SettlementDevelopmentStage.PROSPECTING_POST, category,
                functions.stream().map(value -> new BuildingFunctionId("pale_mirror:" + value)).toList(),
                expand(module.footprint(), 1), List.of(module), slots));
    }

    private static BuildingSlot mineSlot(String id, BuildingSlotKind kind, VisualBounds bounds, int index) {
        int width = Math.max(1, bounds.max().x() - bounds.min().x() - 3);
        int x = bounds.min().x() + 2 + index % width;
        int z = bounds.min().z() + 2 + index / width;
        return new BuildingSlot(id, kind, new VisualPoint(x, Math.min(bounds.max().y(), bounds.min().y() + 1), z), 1);
    }

    private static VisualBounds expand(VisualBounds bounds, int amount) {
        return new VisualBounds(new VisualPoint(bounds.min().x() - amount, bounds.min().y(), bounds.min().z() - amount),
                new VisualPoint(bounds.max().x() + amount, bounds.max().y(), bounds.max().z() + amount));
    }

    private static StagedVisualModule stageSurfaceMine(String stage, String family, String template,
                                                        String padId, String moduleRole, AuthoredMineRole mineRole,
                                                        MountainMineAnchor anchor) {
        return new StagedVisualModule(stage, surfaceMineModule(family, template, padId, moduleRole,
                mineRole, anchor));
    }

    private static VisualModulePlacement surfaceMineModule(String family, String template, String padId,
                                                            String moduleRole, AuthoredMineRole mineRole,
                                                            MountainMineAnchor anchor) {
        return MineSurfaceModuleFactory.place(family, template, padId, moduleRole,
                mineRole, anchor);
    }

    private static void addUndergroundMine(List<VisualModulePlacement> target, String family, String name,
                                           String role, VisualPoint portal, MineUndergroundLayout.Node node, int direction,
                                           int sx, int sy, int sz) {
        VisualPoint origin = local(portal, node, direction);
        target.add(module(family + "/mine/" + name, role, origin, direction, sx, sy, sz, "underground"));
    }

    private static VisualPoint local(VisualPoint portal, MineUndergroundLayout.Node node, int direction) {
        return local(portal, node.right(), node.inward(), node.up(), direction);
    }

    private static VisualModulePlacement module(String template, String role, VisualPoint origin,
                                                 int direction, int sx, int sy, int sz, String foundationId) {
        boolean swap = Math.floorMod(direction, 2) == 1;
        int width = swap ? sz : sx;
        int depth = swap ? sx : sz;
        VisualBounds footprint = new VisualBounds(new VisualPoint(origin.x() - width / 2, origin.y() + 1,
                origin.z() - depth / 2), new VisualPoint(origin.x() + (width - 1) / 2, origin.y() + sy,
                origin.z() + (depth - 1) / 2));
        VisualPoint entrance = new VisualPoint(origin.x(), origin.y() + 1, origin.z());
        return new VisualModulePlacement(template.replace('/', '_') + "_" + origin.x() + "_" + origin.z(),
                "pale_mirror_visuals:" + template, template.substring(0, template.indexOf('/')), role,
                origin, direction, footprint, foundationId, "frontier_mine",
                List.of(new VisualPort("public", VisualPortKind.PUBLIC_ENTRANCE, entrance, direction)));
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

    private static long horizontalDistanceSquared(VisualPoint first, VisualPoint second) {
        long dx = (long) first.x() - second.x();
        long dz = (long) first.z() - second.z();
        return dx * dx + dz * dz;
    }

    private static List<ResidentSeed> residents(String source, AuthoredSettlementSitePlan settlement,
                                                 AuthoredMineSitePlan primaryMine) {
        List<SlotBinding> homes = slots(settlement.buildings(), BuildingSlotKind.BED);
        if (homes.size() != SettlementDevelopmentStage.TOWNSHIP.population()) {
            throw new IllegalStateException("Township requires exactly 48 authored bed slots, got " + homes.size());
        }
        List<SlotBinding> mineWork = slots(primaryMine.surfaceBuildings(), BuildingSlotKind.WORKSTATION);
        List<SlotBinding> industryWork = workSlots(settlement, Set.of(SettlementBuildingCategory.INDUSTRY,
                SettlementBuildingCategory.LOGISTICS, SettlementBuildingCategory.FOOD));
        List<SlotBinding> specialistWork = workSlots(settlement, Set.of(SettlementBuildingCategory.CIVIC,
                SettlementBuildingCategory.HEALTH, SettlementBuildingCategory.TRADE));
        List<SlotBinding> guardWork = workSlots(settlement, Set.of(SettlementBuildingCategory.DEFENCE));
        List<SlotBinding> workers = new ArrayList<>(mineWork);
        workers.addAll(industryWork);
        List<ResidentSeed> out = new ArrayList<>(48);
        addResidents(out, source, homes, workers, "CIVILIANS", "pale_mirror:resident", 20, false);
        addResidents(out, source, homes, workers, "WORKERS", "pale_mirror:miner", 14, true);
        addResidents(out, source, homes, specialistWork, "SPECIALISTS", "pale_mirror:specialist", 4, true);
        addResidents(out, source, homes, guardWork, "GUARDS", "pale_mirror:guard", 6, true);
        addResidents(out, source, homes, List.of(), "CHILDREN", "pale_mirror:child", 4, false);
        return List.copyOf(out);
    }

    private static List<SlotBinding> workSlots(AuthoredSettlementSitePlan settlement,
                                                Set<SettlementBuildingCategory> categories) {
        return slots(settlement.buildings().stream().filter(building -> categories.contains(building.category())).toList(),
                BuildingSlotKind.WORKSTATION);
    }

    private static List<SlotBinding> slots(List<AuthoredBuildingPlan> buildings, BuildingSlotKind kind) {
        List<SlotBinding> result = new ArrayList<>();
        for (AuthoredBuildingPlan building : buildings) for (BuildingSlot slot : building.slots()) {
            if (slot.kind() == kind) result.add(new SlotBinding(building.buildingId(), slot));
        }
        return List.copyOf(result);
    }

    private static void addResidents(List<ResidentSeed> out, String source, List<SlotBinding> homes,
                                     List<SlotBinding> workplaces, String cohort, String role,
                                     int count, boolean requiresWorkplace) {
        if (requiresWorkplace && workplaces.size() < count) {
            throw new IllegalStateException(cohort + " requires " + count + " work slots, got " + workplaces.size());
        }
        for (int index = 0; index < count; index++) {
            int serial = out.size();
            SlotBinding home = homes.get(serial);
            SlotBinding work = requiresWorkplace ? workplaces.get(index) : null;
            String id = UUID.nameUUIDFromBytes((source + ":resident:" + serial).getBytes(StandardCharsets.UTF_8)).toString();
            out.add(new ResidentSeed(id, "pale_mirror_visuals.resident." + keyedInt(source, "name:" + serial, 64),
                    cohort, role, home.buildingId(), home.slot().id(),
                    work == null ? "" : work.buildingId(), work == null ? "" : work.slot().id(),
                    home.slot().position(), work == null ? null : work.slot().position()));
        }
    }

    private record SlotBinding(String buildingId, BuildingSlot slot) { }

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
