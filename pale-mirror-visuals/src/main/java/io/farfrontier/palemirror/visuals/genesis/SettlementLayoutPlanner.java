package io.farfrontier.palemirror.visuals.genesis;

import static io.farfrontier.palemirror.visuals.genesis.SettlementLayoutGeometry.*;

import io.farfrontier.palemirror.visuals.genesis.SettlementLayoutGeometry.Local;
import io.farfrontier.palemirror.api.AuthoredBuildingPlan;
import io.farfrontier.palemirror.api.AuthoredOpenSpacePlan;
import io.farfrontier.palemirror.api.AuthoredSettlementSitePlan;
import io.farfrontier.palemirror.api.DevelopmentReservation;
import io.farfrontier.palemirror.api.DevelopmentReservationKind;
import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.ManagedAreaPlan;
import io.farfrontier.palemirror.api.OpenSpaceKind;
import io.farfrontier.palemirror.api.SettlementDevelopmentStage;
import io.farfrontier.palemirror.api.SettlementFoundationPlan;
import io.farfrontier.palemirror.api.SettlementLayoutArchetype;
import io.farfrontier.palemirror.api.SiteEnvironmentPlan;
import io.farfrontier.palemirror.api.SiteSurfacePlan;
import io.farfrontier.palemirror.api.PerimeterPlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPort;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic functional-district grammar for the static v40 Township snapshot. */
final class SettlementLayoutPlanner {
    static final int ACTIVE_HALF_WIDTH = 60;
    static final int ACTIVE_HALF_LENGTH = 80;
    static final int MASTER_HALF_WIDTH = 72;
    static final int MASTER_HALF_LENGTH = 96;
    static final int VARIANT_COUNT = 24;
    private static final int FOUNDATION_APRON = 2;

    AuthoredSettlementSitePlan plan(String source, VisualPoint anchor, FrontierClimate climate,
                                    int freightDirection, TerrainCandidate terrain) {
        return plan(source, anchor, climate, freightDirection, terrain, SettlementTerrainSnapshot.flat(anchor));
    }

    AuthoredSettlementSitePlan plan(String source, VisualPoint anchor, FrontierClimate climate,
                                    int freightDirection, TerrainCandidate terrain,
                                    SettlementTerrainSnapshot snapshot) {
        SettlementLayoutArchetype archetype = choose(terrain);
        SettlementArchetypeCatalog.Definition definition = SettlementArchetypeCatalog.ironFrontier();
        DryMineSiteUnavailableException lastExactFailure = null;
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            try {
                LayoutTransform transform = LayoutTransform.forVariant(variant);
                List<Placement> grammar = grammar(archetype, definition.active().buildings(), transform);
                List<Placement> adapted = adaptToTerrain(anchor, freightDirection, archetype, terrain, grammar,
                        snapshot, transform);
                if (!adapted.isEmpty()) {
                    return planVariant(source, anchor, climate, freightDirection, terrain, snapshot,
                            archetype, definition, adapted, transform, variant);
                }
            } catch (DryMineSiteUnavailableException unavailable) {
                lastExactFailure = unavailable;
            }
        }
        throw new DryMineSiteUnavailableException("Township has no bounded <=4 cut/fill layout variant at "
                + anchor.x() + "," + anchor.z() + (lastExactFailure == null ? ""
                : "; last exact rejection=" + lastExactFailure.getMessage()));
    }

    AuthoredSettlementSitePlan planKnownVariant(String source, VisualPoint anchor, FrontierClimate climate,
                                                int freightDirection, TerrainCandidate terrain,
                                                SettlementTerrainSnapshot snapshot, int variant) {
        SettlementLayoutArchetype archetype = choose(terrain);
        SettlementArchetypeCatalog.Definition definition = SettlementArchetypeCatalog.ironFrontier();
        LayoutTransform transform = LayoutTransform.forVariant(variant);
        List<Placement> grammar = grammar(archetype, definition.active().buildings(), transform);
        return planVariant(source, anchor, climate, freightDirection, terrain, snapshot,
                archetype, definition, grammar, transform, variant);
    }

    private static List<Placement> adaptToTerrain(VisualPoint anchor, int freightDirection,
                                                   SettlementLayoutArchetype archetype, TerrainCandidate terrain,
                                                   List<Placement> grammar, SettlementTerrainSnapshot snapshot,
                                                   LayoutTransform transform) {
        List<VisualBounds> occupied = new ArrayList<>();
        List<VisualBounds> openSpaces = openSpaces(anchor, freightDirection, transform).stream()
                .map(AuthoredOpenSpacePlan::bounds).toList();
        VisualBounds master = orientedBounds(anchor, MASTER_HALF_WIDTH, MASTER_HALF_LENGTH,
                -8, 40, freightDirection);
        List<Placement> adapted = new ArrayList<>(grammar.size());
        int[][] refinements = {{0, 0}, {-4, 0}, {4, 0}, {0, -4}, {0, 4},
                {-4, -4}, {4, -4}, {-4, 4}, {4, 4}, {-8, 0}, {8, 0}, {0, -8}, {0, 8}};
        for (Placement original : grammar) {
            Placement accepted = null;
            VisualBounds acceptedParcel = null;
            FrontierModuleCatalog.Definition definition = FrontierModuleCatalog.require(original.spec().template());
            for (int[] refinement : refinements) {
                Placement candidate = new Placement(original.spec(), original.right() + refinement[0],
                        original.inward() + refinement[1], original.frontage());
                VisualPoint origin = local(anchor, candidate.right(), candidate.inward(),
                        tier(archetype, candidate.inward(), terrain.relief()), freightDirection);
                int desiredFrontage = Math.floorMod(freightDirection + candidate.frontage(), 4);
                int rotation = Math.floorMod(desiredFrontage - definition.entranceOutward(), 4);
                VisualBounds footprint = moduleFootprint(definition, origin, rotation);
                VisualBounds parcel = expand(footprint, 1, 0, 1);
                if (!containsHorizontal(master, parcel) || !snapshot.roughlyAccepts(footprint)) continue;
                if (!snapshot.resolvePad(footprint).accepted()) continue;
                if (occupied.stream().anyMatch(value -> overlaps(value, parcel))) continue;
                if (openSpaces.stream().anyMatch(value -> overlaps(value, parcel))) continue;
                accepted = candidate;
                acceptedParcel = parcel;
                break;
            }
            if (accepted == null) return List.of();
            adapted.add(accepted);
            occupied.add(acceptedParcel);
        }
        return List.copyOf(adapted);
    }

    private AuthoredSettlementSitePlan planVariant(String source, VisualPoint anchor, FrontierClimate climate,
                                                    int freightDirection, TerrainCandidate terrain,
                                                    SettlementTerrainSnapshot snapshot,
                                                    SettlementLayoutArchetype archetype,
                                                    SettlementArchetypeCatalog.Definition definition,
                                                    List<Placement> grammar, LayoutTransform transform, int variant) {
        List<AuthoredBuildingPlan> buildings = new ArrayList<>(grammar.size());
        List<SettlementFoundationPlan> foundations = new ArrayList<>(grammar.size());
        String family = climate.name().toLowerCase(Locale.ROOT);
        for (Placement placement : grammar) {
            FrontierModuleCatalog.Definition moduleDefinition = FrontierModuleCatalog.require(placement.spec().template());
            int tier = tier(archetype, placement.inward(), terrain.relief());
            VisualPoint roughOrigin = local(anchor, placement.right(), placement.inward(), tier, freightDirection);
            int desiredFrontage = Math.floorMod(freightDirection + placement.frontage(), 4);
            int rotation = Math.floorMod(desiredFrontage - moduleDefinition.entranceOutward(), 4);
            VisualBounds roughFootprint = moduleFootprint(moduleDefinition, roughOrigin, rotation);
            SettlementTerrainSnapshot.PadResolution pad = snapshot.resolvePad(roughFootprint);
            if (!pad.accepted()) throw new DryMineSiteUnavailableException("Township building "
                    + placement.spec().id() + " failed exact " + pad.failure() + " validation at "
                    + anchor.x() + "," + anchor.z());
            // Exact terrain probes return first air. Module origins and
            // foundations use the solid surface immediately below it.
            VisualPoint origin = new VisualPoint(roughOrigin.x(), pad.targetY() - 1, roughOrigin.z());
            VisualBounds footprint = moduleFootprint(moduleDefinition, origin, rotation);
            String foundationId = "foundation_" + placement.spec().id();
            VisualPoint entrance = authoredEntrance(moduleDefinition, footprint, rotation);
            VisualPoint freightRailhead = SettlementFreightPorts.loadingThreshold(
                    entrance, footprint, desiredFrontage);
            List<VisualPort> ports = new ArrayList<>();
            ports.add(new VisualPort("public", VisualPortKind.PUBLIC_ENTRANCE, entrance, desiredFrontage));
            if (placement.spec().category() == io.farfrontier.palemirror.api.SettlementBuildingCategory.LOGISTICS
                    || placement.spec().category() == io.farfrontier.palemirror.api.SettlementBuildingCategory.INDUSTRY) {
                ports.add(new VisualPort("service", VisualPortKind.SERVICE,
                        oppositeEntrance(footprint, desiredFrontage), desiredFrontage + 2));
            }
            if (placement.spec().id().equals("receiving_depot")) {
                // The passenger threshold is a real NBT door. The canonical
                // railway terminates on a separate loading threshold so
                // rail compilation can never replace that door.
                ports.add(new VisualPort("freight", VisualPortKind.FREIGHT,
                        freightRailhead, desiredFrontage));
                ports.add(new VisualPort("rail", VisualPortKind.RAIL,
                        freightRailhead, desiredFrontage));
            }
            VisualModulePlacement module = new VisualModulePlacement(placement.spec().id() + "_shell",
                    "pale_mirror_visuals:" + family + "/" + placement.spec().template(),
                    family, placement.spec().category().name(), origin, rotation, footprint,
                    foundationId, moduleDefinition.stateProfile(), ports);
            VisualBounds parcel = expand(footprint, 1, 0, 1);
            var slots = SettlementBuildingSlots.forBuilding(placement.spec(), footprint);
            buildings.add(new AuthoredBuildingPlan(placement.spec().id(), earliestStage(placement.spec().id()),
                    placement.spec().category(), placement.spec().functions(), parcel, List.of(module), slots));
            foundations.add(new SettlementFoundationPlan(foundationId, footprint, origin.y(),
                    FOUNDATION_APRON, 4, 4,
                    placement.spec().category() == io.farfrontier.palemirror.api.SettlementBuildingCategory.LOGISTICS
                            ? "FREIGHT" : "BUILDING"));
        }
        validateNoOverlap(buildings);
        VisualPoint roughGate = local(anchor, 0, 84, 0, freightDirection);
        // The exported gate point is the first-air block occupied by its arch.
        // The freight road below it uses the solid surface explicitly.
        VisualPoint gate = withY(roughGate, snapshot.approximateHeight(roughGate.x(), roughGate.z()));
        AuthoredBuildingPlan depot = building(buildings, "receiving_depot");
        List<LinearFeaturePlan> circulation = circulation(archetype, anchor, freightDirection, gate, depot, buildings,
                transform).stream().map(value -> value.id().startsWith("access_")
                        ? followAccessTerrain(value, snapshot)
                        : value.id().equals("freight_spine")
                        ? followFreightTerrain(value, snapshot)
                        : followTerrain(value, snapshot)).toList();
        requireDryCirculation(circulation, snapshot, anchor);
        List<LinearFeaturePlan> defences = SettlementDefencePlanner.plan(archetype, anchor, freightDirection).stream()
                .map(value -> followTerrain(value, snapshot)).toList();
        List<AuthoredOpenSpacePlan> openSpaces = openSpaces(anchor, freightDirection, transform).stream()
                .map(value -> resolveOpenSpace(value, snapshot)).toList();
        List<DevelopmentReservation> reservations = reservations(anchor, freightDirection, buildings, openSpaces,
                transform);
        ManagedAreaPlan managedArea = managedArea(buildings, circulation, defences, openSpaces, reservations);
        SiteSurfacePlan surfacePlan = SiteSurfacePlanner.settlement(foundations, openSpaces, circulation,
                defences, reservations);
        PerimeterPlan perimeter = StructuralPerimeterPlanner.plan(defences);
        List<VisualPoint> shelters = List.of(terrainPoint(local(anchor, 108, -22, 0, freightDirection), snapshot),
                terrainPoint(local(anchor, -112, -26, 0, freightDirection), snapshot),
                terrainPoint(local(anchor, 78, -108, 0, freightDirection), snapshot));
        VisualBounds master = SettlementSiteBounds.fitVertical(
                orientedBounds(anchor, MASTER_HALF_WIDTH, MASTER_HALF_LENGTH, -8, 40, freightDirection),
                gate, SettlementFreightPorts.railhead(depot), buildings, foundations, circulation, defences,
                openSpaces, managedArea, reservations);
        AuthoredSettlementSitePlan result = new AuthoredSettlementSitePlan(
                source + ":" + archetype.name().toLowerCase(Locale.ROOT)
                + ":v" + variant,
                SettlementDevelopmentStage.TOWNSHIP, archetype,
                master,
                gate, SettlementFreightPorts.railhead(depot), buildings, foundations, circulation, defences,
                openSpaces, managedArea,
                new SiteEnvironmentPlan("pale_mirror:authored_settlement", anchor, 128, 176, 16,
                        surfacePlan.columns().stream().mapToInt(value -> value.groundY()).min().orElse(anchor.y()) - 1,
                        surfacePlan.columns().stream().mapToInt(value -> value.groundY()).max().orElse(anchor.y()) - 1),
                surfacePlan, perimeter, reservations, shelters);
        IronFrontierTownshipContract.validate(result);
        return result;
    }

    private static SettlementDevelopmentStage earliestStage(String buildingId) {
        for (SettlementDevelopmentStage stage : SettlementDevelopmentStage.values()) {
            if (SettlementArchetypeCatalog.ironFrontier().stages().get(stage).buildings().stream()
                    .anyMatch(value -> value.id().equals(buildingId))) return stage;
        }
        return SettlementDevelopmentStage.TOWNSHIP;
    }

    private static SettlementLayoutArchetype choose(TerrainCandidate terrain) {
        if (terrain.relief() >= 11) return SettlementLayoutArchetype.TERRACED_BASIN;
        if (terrain.relief() <= 4) return SettlementLayoutArchetype.FREIGHT_CROSSROADS;
        return SettlementLayoutArchetype.FOOTHILL_RIBBON;
    }

    private static List<Placement> grammar(SettlementLayoutArchetype archetype,
                                            List<SettlementArchetypeCatalog.Building> specifications,
                                            LayoutTransform transform) {
        java.util.Map<String, SettlementArchetypeCatalog.Building> values = specifications.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(SettlementArchetypeCatalog.Building::id, value -> value));
        int civicShift = archetype == SettlementLayoutArchetype.FREIGHT_CROSSROADS ? 5 : 0;
        int rearTier = archetype == SettlementLayoutArchetype.TERRACED_BASIN ? -4 : 0;
        List<Placement> base = List.of(
                p(values, "town_hall", -13, 6 + civicShift, 2),
                p(values, "market_hall", 22, 10 + civicShift, 2),
                p(values, "inn", 54, -7 + civicShift, 2),
                p(values, "clinic", -52, -8 + civicShift, 0),
                p(values, "community_bakery", 13, -14 + civicShift, 0),
                p(values, "receiving_depot", 0, 70, 0),
                p(values, "smeltery", 25, 54, 2),
                p(values, "smithy", 42, 38, 2),
                p(values, "mechanical_workshop", 8, 54, 2),
                p(values, "stable", -38, 66, 0),
                p(values, "assay_office", -17, 43, 0),
                p(values, "barracks", -42, 34, 1),
                p(values, "watch_house", -61, 51, 0),
                p(values, "family_house_1", -47, -34 + rearTier, 0),
                p(values, "family_house_2", -27, -48 + rearTier, 0),
                p(values, "family_house_3", -7, -54 + rearTier, 0),
                p(values, "family_house_4", 16, -51 + rearTier, 2),
                p(values, "family_house_5", 41, -37 + rearTier, 2),
                p(values, "workers_bunkhouse_1", -26, -72 + rearTier, 0),
                p(values, "workers_bunkhouse_2", 27, -72 + rearTier, 2));
        return base.stream().map(value -> new Placement(value.spec(), transform.right(value.right()),
                transform.inward(value.inward()), transform.mirror() == 1 ? value.frontage()
                : Math.floorMod(2 - value.frontage(), 4))).toList();
    }

    private static Placement p(java.util.Map<String, SettlementArchetypeCatalog.Building> values,
                               String id, int right, int inward, int frontage) {
        SettlementArchetypeCatalog.Building value = values.get(id);
        if (value == null) throw new IllegalStateException("Township grammar requires " + id);
        return new Placement(value, right, inward, frontage);
    }

    private static int tier(SettlementLayoutArchetype archetype, int inward, int relief) {
        if (archetype != SettlementLayoutArchetype.TERRACED_BASIN || relief < 6) return 0;
        if (inward < -25) return Math.min(3, relief / 5);
        if (inward > 32) return -Math.min(2, relief / 7);
        return 0;
    }

    private static List<LinearFeaturePlan> circulation(SettlementLayoutArchetype archetype, VisualPoint anchor,
                                                        int direction, VisualPoint gate, AuthoredBuildingPlan depot,
                                                        List<AuthoredBuildingPlan> buildings,
                                                        LayoutTransform transform) {
        List<LinearFeaturePlan> result = new ArrayList<>();
        VisualPoint depotEntrance = publicEntrance(depot);
        VisualPoint depotAccess = below(depotEntrance);
        result.add(new LinearFeaturePlan("freight_spine", LinearFeatureKind.FREIGHT_ROAD,
                List.of(below(gate), depotAccess, local(anchor, transform.right(0), transform.inward(28), 0, direction),
                        local(anchor, transform.right(0), transform.inward(-28), 0, direction)), 7, true));
        int cross = transform.inward(archetype == SettlementLayoutArchetype.FREIGHT_CROSSROADS ? 15 : 8);
        result.add(new LinearFeaturePlan("civic_street", LinearFeatureKind.STREET,
                List.of(local(anchor, transform.right(-55), cross, 0, direction),
                        local(anchor, transform.right(55), cross, 0, direction)), 5, true));
        result.add(new LinearFeaturePlan("civic_sidewalk_west", LinearFeatureKind.SIDEWALK,
                List.of(local(anchor, transform.right(-55), cross - 4, 0, direction),
                        local(anchor, transform.right(55), cross - 4, 0, direction)), 1, true));
        result.add(new LinearFeaturePlan("civic_sidewalk_east", LinearFeatureKind.SIDEWALK,
                List.of(local(anchor, transform.right(-55), cross + 4, 0, direction),
                        local(anchor, transform.right(55), cross + 4, 0, direction)), 1, true));
        result.add(new LinearFeaturePlan("industrial_lane", LinearFeatureKind.STREET,
                List.of(local(anchor, transform.right(-47), transform.inward(43), 0, direction),
                        local(anchor, transform.right(47), transform.inward(43), 0, direction)), 3, true));
        result.add(new LinearFeaturePlan("residential_lane", LinearFeatureKind.STREET,
                List.of(local(anchor, transform.right(-49), transform.inward(-39), 0, direction),
                        local(anchor, transform.right(-26), transform.inward(-57), 0, direction),
                        local(anchor, transform.right(27), transform.inward(-57), 0, direction),
                        local(anchor, transform.right(49), transform.inward(-39), 0, direction)), 3, true));
        result.add(new LinearFeaturePlan("market_square", LinearFeatureKind.PLAZA,
                List.of(local(anchor, transform.right(5), transform.inward(10), 0, direction),
                        local(anchor, transform.right(13), transform.inward(18), 0, direction)), 9, true));
        List<LinearFeaturePlan> publicGraph = List.copyOf(result);
        int index = 0;
        for (AuthoredBuildingPlan building : buildings) {
            result.add(new LinearFeaturePlan("access_" + index++, LinearFeatureKind.FOOTPATH,
                    SettlementAccessRouter.route(building, buildings, publicGraph), 3, true));
        }
        if (archetype == SettlementLayoutArchetype.TERRACED_BASIN) {
            result.add(new LinearFeaturePlan("upper_retaining", LinearFeatureKind.RETAINING_WALL,
                    List.of(local(anchor, -52, -29, 1, direction), local(anchor, 52, -29, 1, direction)), 1, false));
            result.add(new LinearFeaturePlan("upper_steps", LinearFeatureKind.STAIRS,
                    List.of(local(anchor, 0, -24, 0, direction), local(anchor, 0, -39, 2, direction)), 3, true));
        }
        return List.copyOf(result);
    }

    private static List<AuthoredOpenSpacePlan> openSpaces(VisualPoint anchor, int direction,
                                                           LayoutTransform transform) {
        return List.of(
                open("market_square", OpenSpaceKind.MARKET_SQUARE, anchor, direction,
                        transform.right(0), transform.inward(34), 8, 7),
                open("civic_green", OpenSpaceKind.CIVIC_GREEN, anchor, direction,
                        transform.right(-11), transform.inward(-28), 9, 7),
                open("allotments", OpenSpaceKind.GARDEN, anchor, direction,
                        transform.right(54), transform.inward(-58), 9, 7),
                open("freight_yard", OpenSpaceKind.FREIGHT_YARD, anchor, direction,
                        transform.right(-15), transform.inward(60), 10, 6),
                open("smeltery_yard", OpenSpaceKind.INDUSTRIAL_YARD, anchor, direction,
                        transform.right(25), transform.inward(34), 9, 7),
                open("training_yard", OpenSpaceKind.TRAINING_YARD, anchor, direction,
                        transform.right(-43), transform.inward(15), 9, 7));
    }

    private static AuthoredOpenSpacePlan open(String id, OpenSpaceKind kind, VisualPoint anchor, int direction,
                                               int right, int inward, int halfWidth, int halfLength) {
        VisualPoint center = local(anchor, right, inward, 0, direction);
        VisualBounds bounds = orientedBounds(center, halfWidth, halfLength, 0, 5, direction);
        return new AuthoredOpenSpacePlan(id, kind, bounds,
                List.of(new VisualPort("public", VisualPortKind.PUBLIC_ENTRANCE, center, direction)));
    }

    private static List<DevelopmentReservation> reservations(VisualPoint anchor, int direction,
                                                              List<AuthoredBuildingPlan> buildings,
                                                              List<AuthoredOpenSpacePlan> openSpaces,
                                                              LayoutTransform transform) {
        List<DevelopmentReservation> result = new ArrayList<>();
        VisualBounds master = orientedBounds(anchor, MASTER_HALF_WIDTH, MASTER_HALF_LENGTH,
                -8, 40, direction);
        DevelopmentReservation depotAnnex = resolveAnnex("depot_annex", "receiving_depot",
                translatedAnnex(anchor, direction, transform, buildings, "receiving_depot", 0, 70,
                        -10, 70, 6, 8), buildings, openSpaces, result, master);
        result.add(depotAnnex);
        DevelopmentReservation smelteryAnnex = resolveAnnex("smeltery_annex", "smeltery",
                translatedAnnex(anchor, direction, transform, buildings, "smeltery", 25, 54,
                        44, 54, 8, 9), buildings, openSpaces, result, master);
        result.add(smelteryAnnex);
        List<int[]> candidates = new ArrayList<>(List.of(
                new int[]{-64, -70}, new int[]{64, -70}, new int[]{-64, -42}, new int[]{64, -42},
                new int[]{0, -75}, new int[]{0, -89}, new int[]{-64, -10}, new int[]{64, -10},
                new int[]{-42, -89}, new int[]{42, -89}, new int[]{-64, 10}, new int[]{64, 10}));
        for (int inward = -88; inward <= 72; inward += 16) {
            for (int right = -64; right <= 64; right += 16) candidates.add(new int[]{right, inward});
        }
        for (int[] candidate : candidates) {
            VisualPoint center = local(anchor, candidate[0], candidate[1], 0, direction);
            VisualBounds bounds = around(center, 6, 14);
            if (buildings.stream().anyMatch(building -> overlaps(expand(building.parcel(), 2, 0, 2), bounds))) continue;
            if (openSpaces.stream().anyMatch(space -> overlaps(expand(space.bounds(), 2, 0, 2), bounds))) continue;
            if (overlaps(bounds, depotAnnex.bounds()) || overlaps(bounds, smelteryAnnex.bounds())) continue;
            if (result.stream().anyMatch(reservation -> overlaps(bounds, reservation.bounds()))) continue;
            long parcelIndex = result.stream().filter(value -> value.kind() == DevelopmentReservationKind.PARCEL)
                    .count();
            result.add(new DevelopmentReservation("town_parcel_" + parcelIndex, DevelopmentReservationKind.PARCEL,
                    SettlementDevelopmentStage.MINING_TOWN, bounds, ""));
            if (parcelIndex == 4) break;
        }
        if (result.stream().filter(value -> value.kind() == DevelopmentReservationKind.PARCEL).count() != 5) {
            throw new DryMineSiteUnavailableException("Township cannot reserve five free Mining Town parcels");
        }
        return List.copyOf(result);
    }

    /**
     * Keeps a future annex attached to its functional owner even when exact
     * terrain adaptation nudges that building away from its nominal grammar
     * coordinate. The art-directed location wins when it remains free; the
     * bounded alternatives walk the four parcel edges instead of silently
     * reserving space through a neighbour.
     */
    private static DevelopmentReservation resolveAnnex(
            String id, String ownerId, VisualBounds preferred,
            List<AuthoredBuildingPlan> buildings, List<AuthoredOpenSpacePlan> openSpaces,
            List<DevelopmentReservation> occupied, VisualBounds master) {
        AuthoredBuildingPlan owner = building(buildings, ownerId);
        List<VisualBounds> candidates = new ArrayList<>();
        candidates.add(preferred);
        int width = preferred.max().x() - preferred.min().x() + 1;
        int depth = preferred.max().z() - preferred.min().z() + 1;
        int ownerCenterX = (owner.parcel().min().x() + owner.parcel().max().x()) / 2;
        int ownerCenterZ = (owner.parcel().min().z() + owner.parcel().max().z()) / 2;
        for (int tangent : new int[]{0, -4, 4, -8, 8, -12, 12}) {
            candidates.add(horizontalBounds(owner.parcel().max().x() + 1,
                    ownerCenterZ - depth / 2 + tangent, width, depth, preferred));
            candidates.add(horizontalBounds(owner.parcel().min().x() - width,
                    ownerCenterZ - depth / 2 + tangent, width, depth, preferred));
            candidates.add(horizontalBounds(ownerCenterX - width / 2 + tangent,
                    owner.parcel().max().z() + 1, width, depth, preferred));
            candidates.add(horizontalBounds(ownerCenterX - width / 2 + tangent,
                    owner.parcel().min().z() - depth, width, depth, preferred));
        }
        return candidates.stream().distinct()
                .filter(candidate -> containsHorizontal(master, candidate))
                .filter(candidate -> touchesOrOverlaps(candidate, owner.parcel()))
                .filter(candidate -> buildings.stream().noneMatch(value -> !value.buildingId().equals(ownerId)
                        && overlaps(candidate, value.parcel())))
                .filter(candidate -> openSpaces.stream().noneMatch(value -> overlaps(candidate, value.bounds())))
                .filter(candidate -> occupied.stream().noneMatch(value -> overlaps(candidate, value.bounds())))
                .findFirst()
                .map(bounds -> new DevelopmentReservation(id, DevelopmentReservationKind.ANNEX,
                        SettlementDevelopmentStage.MINING_TOWN, bounds, ownerId))
                .orElseThrow(() -> new DryMineSiteUnavailableException(
                        "Township cannot reserve a free annex for " + ownerId));
    }

    private static VisualBounds horizontalBounds(int minX, int minZ, int width, int depth,
                                                  VisualBounds verticalSource) {
        return new VisualBounds(new VisualPoint(minX, verticalSource.min().y(), minZ),
                new VisualPoint(minX + width - 1, verticalSource.max().y(), minZ + depth - 1));
    }

    private static boolean touchesOrOverlaps(VisualBounds first, VisualBounds second) {
        return first.min().x() <= second.max().x() + 1 && first.max().x() + 1 >= second.min().x()
                && first.min().z() <= second.max().z() + 1 && first.max().z() + 1 >= second.min().z();
    }

    private static VisualBounds translatedAnnex(VisualPoint anchor, int direction, LayoutTransform transform,
                                                 List<AuthoredBuildingPlan> buildings, String buildingId,
                                                 int expectedRight, int expectedInward,
                                                 int annexRight, int annexInward,
                                                 int halfWidth, int halfLength) {
        VisualPoint expected = local(anchor, transform.right(expectedRight), transform.inward(expectedInward),
                0, direction);
        VisualPoint actual = building(buildings, buildingId).modules().getFirst().origin();
        VisualPoint base = local(anchor, transform.right(annexRight), transform.inward(annexInward),
                0, direction);
        VisualPoint shifted = new VisualPoint(base.x() + actual.x() - expected.x(), base.y(),
                base.z() + actual.z() - expected.z());
        return orientedBounds(shifted, halfWidth, halfLength, -2, 14, direction);
    }

    private record Placement(SettlementArchetypeCatalog.Building spec, int right, int inward, int frontage) { }

    private record LayoutTransform(int mirror, int rightShift, int inwardShift) {
        private static final List<Translation> TRANSLATIONS = List.of(
                new Translation(0, 0), new Translation(0, -8), new Translation(0, 8),
                new Translation(-4, 0), new Translation(4, 0),
                new Translation(-4, -8), new Translation(4, -8),
                new Translation(-4, 8), new Translation(4, 8),
                new Translation(0, 12), new Translation(-4, 12), new Translation(4, 12));

        private LayoutTransform {
            if (mirror != -1 && mirror != 1) throw new IllegalArgumentException("layout mirror must be -1 or 1");
        }

        static LayoutTransform forVariant(int variant) {
            if (variant < 0 || variant >= VARIANT_COUNT) {
                throw new IllegalArgumentException("layout variant must be in [0, " + VARIANT_COUNT + ")");
            }
            Translation translation = TRANSLATIONS.get(variant / 2);
            return new LayoutTransform(variant % 2 == 0 ? 1 : -1,
                    translation.right(), translation.inward());
        }

        int right(int value) { return value * mirror + rightShift; }
        int inward(int value) { return value + inwardShift; }
    }

    private record Translation(int right, int inward) { }
}
