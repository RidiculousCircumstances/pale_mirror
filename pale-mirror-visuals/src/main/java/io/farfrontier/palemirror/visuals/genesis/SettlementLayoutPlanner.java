package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredSettlementSitePlan;
import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.SettlementFoundationPlan;
import io.farfrontier.palemirror.api.SettlementLayoutArchetype;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPort;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure, deterministic settlement grammar driven by the surveyed terrain shape. */
final class SettlementLayoutPlanner {
    static final int HALF_WIDTH = 45;
    static final int HALF_LENGTH = 65;
    private static final int FOUNDATION_APRON = 1;

    AuthoredSettlementSitePlan plan(String source, VisualPoint anchor, FrontierClimate climate,
                                    int freightDirection, TerrainCandidate terrain) {
        SettlementLayoutArchetype archetype = choose(terrain);
        List<Placement> grammar = grammar(archetype);
        List<VisualModulePlacement> modules = new ArrayList<>(grammar.size());
        List<SettlementFoundationPlan> foundations = new ArrayList<>(grammar.size());
        String family = climate == FrontierClimate.DRY_ARID ? "temperate"
                : climate.name().toLowerCase(Locale.ROOT);
        int ordinal = 0;
        for (Placement placement : grammar) {
            FrontierModuleCatalog.Definition definition = FrontierModuleCatalog.require(placement.template());
            if (definition.landmark() != placement.landmark()) {
                throw new IllegalStateException("settlement grammar/catalog landmark mismatch for "
                        + placement.template());
            }
            int tier = tier(archetype, placement.inward(), terrain.relief());
            VisualPoint origin = local(anchor, placement.right(), placement.inward(), tier, freightDirection);
            int rotation = Math.floorMod(freightDirection + placement.frontage(), 4);
            VisualBounds footprint = moduleFootprint(placement.template(), origin, rotation);
            String instanceId = placement.template() + "_" + ordinal++;
            String foundationId = "foundation_" + instanceId;
            VisualPoint entrance = entrance(footprint, rotation);
            List<VisualPort> ports = new ArrayList<>();
            ports.add(new VisualPort("public", VisualPortKind.PUBLIC_ENTRANCE, entrance, rotation));
            if (placement.role().equals("LOGISTICS") || placement.role().equals("INDUSTRY")) {
                ports.add(new VisualPort("service", VisualPortKind.SERVICE,
                        oppositeEntrance(footprint, rotation), rotation + 2));
            }
            if (placement.template().equals("receiving_depot")) {
                ports.add(new VisualPort("freight", VisualPortKind.FREIGHT, entrance, rotation));
                ports.add(new VisualPort("rail", VisualPortKind.RAIL,
                        oppositeEntrance(footprint, rotation), rotation + 2));
            }
            modules.add(new VisualModulePlacement(instanceId,
                    "pale_mirror_visuals:" + family + "/" + placement.template(),
                    climate.name().toLowerCase(Locale.ROOT), placement.role(), origin, rotation, footprint,
                    foundationId, definition.stateProfile(), ports));
            foundations.add(new SettlementFoundationPlan(foundationId, footprint, origin.y(),
                    FOUNDATION_APRON, definition.landmark() ? 5 : 3, definition.landmark() ? 5 : 3,
                    placement.role().equals("LOGISTICS") ? "FREIGHT" : "BUILDING"));
        }
        validateNoOverlap(modules);
        VisualPoint gate = local(anchor, 0, 62, 0, freightDirection);
        VisualModulePlacement depot = modules.stream().filter(value -> value.templateId().endsWith("/receiving_depot"))
                .findFirst().orElseThrow();
        List<LinearFeaturePlan> circulation = circulation(archetype, anchor, freightDirection, gate, depot, modules);
        List<LinearFeaturePlan> defences = defences(archetype, anchor, freightDirection);
        List<VisualBounds> plots = expansionPlots(anchor, freightDirection, archetype, modules);
        validateExpansionPlots(modules, plots);
        List<VisualPoint> shelters = List.of(local(anchor, 100, -16, 0, freightDirection),
                local(anchor, -104, -20, 0, freightDirection), local(anchor, 72, -96, 0, freightDirection));
        return new AuthoredSettlementSitePlan(source + ":" + archetype.name().toLowerCase(Locale.ROOT), archetype,
                orientedBounds(anchor, HALF_WIDTH, HALF_LENGTH, -8, 40, freightDirection), gate, depot.origin(),
                modules, foundations, circulation, defences, plots, shelters);
    }

    private static SettlementLayoutArchetype choose(TerrainCandidate terrain) {
        if (terrain.relief() >= 11) return SettlementLayoutArchetype.TERRACED_BASIN;
        if (terrain.relief() <= 4) return SettlementLayoutArchetype.FREIGHT_CROSSROADS;
        return SettlementLayoutArchetype.FOOTHILL_RIBBON;
    }

    private static List<Placement> grammar(SettlementLayoutArchetype archetype) {
        return switch (archetype) {
            case FOOTHILL_RIBBON -> List.of(
                    p("civic_hall", "CIVIC", 0, -4, 2, true),
                    p("receiving_depot", "LOGISTICS", 0, 50, 0, true),
                    p("barracks", "DEFENCE", -32, 31, 1, true),
                    p("smithy", "INDUSTRY", 24, 39, 2, false),
                    p("clinic", "CIVIC", -29, 5, 0, true),
                    p("inn", "CIVIC", 29, 3, 2, true),
                    p("stable", "LOGISTICS", -30, 51, 0, false),
                    p("market", "ECONOMY", 29, 23, 2, false),
                    p("residence_1", "HOUSING", -31, -22, 0, false),
                    p("residence_2", "HOUSING", -13, -34, 0, false),
                    p("residence_3", "HOUSING", 6, -34, 0, false),
                    p("residence_1", "HOUSING", 25, -29, 2, false),
                    p("residence_2", "HOUSING", -29, -50, 0, false),
                    p("residence_3", "HOUSING", 27, -50, 2, false),
                    p("workshop_1", "INDUSTRY", 10, 43, 2, false),
                    p("workshop_2", "INDUSTRY", -10, 22, 0, false));
            case TERRACED_BASIN -> List.of(
                    p("civic_hall", "CIVIC", 0, -5, 2, true),
                    p("receiving_depot", "LOGISTICS", 0, 50, 0, true),
                    p("barracks", "DEFENCE", -31, 35, 1, true),
                    p("smithy", "INDUSTRY", 24, 39, 2, false),
                    p("clinic", "CIVIC", -29, 7, 0, true),
                    p("inn", "CIVIC", 29, 6, 2, true),
                    p("stable", "LOGISTICS", -30, 56, 0, false),
                    p("market", "ECONOMY", 28, 24, 2, false),
                    p("residence_1", "HOUSING", -31, -23, 0, false),
                    p("residence_2", "HOUSING", -12, -35, 0, false),
                    p("residence_3", "HOUSING", 7, -35, 0, false),
                    p("residence_1", "HOUSING", 27, -25, 2, false),
                    p("residence_2", "HOUSING", -29, -52, 0, false),
                    p("residence_3", "HOUSING", 27, -51, 2, false),
                    p("workshop_1", "INDUSTRY", 10, 43, 2, false),
                    p("workshop_2", "INDUSTRY", -10, 22, 0, false));
            case FREIGHT_CROSSROADS -> List.of(
                    p("civic_hall", "CIVIC", -17, -12, 1, true),
                    p("receiving_depot", "LOGISTICS", 0, 50, 0, true),
                    p("barracks", "DEFENCE", -32, 32, 1, true),
                    p("smithy", "INDUSTRY", 18, 35, 2, false),
                    p("clinic", "CIVIC", -32, 7, 0, true),
                    p("inn", "CIVIC", 27, -10, 2, true),
                    p("stable", "LOGISTICS", -30, 51, 0, false),
                    p("market", "ECONOMY", 27, 14, 2, false),
                    p("residence_1", "HOUSING", -34, -36, 0, false),
                    p("residence_2", "HOUSING", -12, -39, 0, false),
                    p("residence_3", "HOUSING", 7, -39, 0, false),
                    p("residence_1", "HOUSING", 28, -31, 2, false),
                    p("residence_2", "HOUSING", -29, -55, 0, false),
                    p("residence_3", "HOUSING", 27, -54, 2, false),
                    p("workshop_1", "INDUSTRY", 8, 36, 2, false),
                    p("workshop_2", "INDUSTRY", -9, 23, 0, false));
        };
    }

    private static int tier(SettlementLayoutArchetype archetype, int inward, int relief) {
        if (archetype != SettlementLayoutArchetype.TERRACED_BASIN || relief < 6) return 0;
        if (inward < -20) return Math.min(3, relief / 5);
        if (inward > 30) return -Math.min(2, relief / 7);
        return 0;
    }

    private static List<LinearFeaturePlan> circulation(SettlementLayoutArchetype archetype, VisualPoint anchor,
                                                        int direction, VisualPoint gate,
                                                        VisualModulePlacement depot,
                                                        List<VisualModulePlacement> modules) {
        List<LinearFeaturePlan> result = new ArrayList<>();
        VisualPoint depotEntrance = depot.ports().stream()
                .filter(port -> port.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                .findFirst().orElseThrow().position();
        VisualPoint depotAccess = new VisualPoint(depotEntrance.x(), depotEntrance.y() - 1, depotEntrance.z());
        result.add(new LinearFeaturePlan("freight_spine", LinearFeatureKind.FREIGHT_ROAD,
                List.of(gate, depotAccess, depot.origin(), local(anchor, 0, -22, 0, direction)), 5, true));
        int crossInward = archetype == SettlementLayoutArchetype.FREIGHT_CROSSROADS ? 15 : 2;
        result.add(new LinearFeaturePlan("civic_street", LinearFeatureKind.STREET,
                List.of(local(anchor, -40, crossInward, 0, direction),
                        local(anchor, 40, crossInward, 0, direction)), 3, true));
        int index = 0;
        for (VisualModulePlacement module : modules) {
            VisualPoint entrance = module.ports().stream().filter(port -> port.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                    .findFirst().orElseThrow().position();
            VisualPoint access = new VisualPoint(entrance.x(), entrance.y() - 1, entrance.z());
            Local coordinates = relative(anchor, access, direction);
            VisualPoint spine = local(anchor, 0, coordinates.inward(), 0, direction);
            if (access.x() == spine.x() && access.z() == spine.z()) {
                spine = local(anchor, 0, coordinates.inward() - 1, 0, direction);
            }
            LinearFeatureKind kind = access.y() == spine.y() ? LinearFeatureKind.FOOTPATH : LinearFeatureKind.STAIRS;
            result.add(new LinearFeaturePlan("access_" + index++, kind,
                    List.of(access, spine), 2, true));
        }
        if (archetype == SettlementLayoutArchetype.TERRACED_BASIN) {
            result.add(new LinearFeaturePlan("upper_retaining", LinearFeatureKind.RETAINING_WALL,
                    List.of(local(anchor, -38, -20, 1, direction), local(anchor, 38, -20, 1, direction)), 1, false));
            result.add(new LinearFeaturePlan("upper_steps", LinearFeatureKind.STAIRS,
                    List.of(local(anchor, 0, -15, 0, direction), local(anchor, 0, -28, 2, direction)), 3, true));
        }
        return List.copyOf(result);
    }

    private static List<LinearFeaturePlan> defences(SettlementLayoutArchetype archetype, VisualPoint anchor,
                                                     int direction) {
        List<LinearFeaturePlan> result = new ArrayList<>();
        result.add(line("rear_west", LinearFeatureKind.PALISADE, anchor, direction, -42, -61, -7, -61, 2));
        result.add(line("rear_east", LinearFeatureKind.PALISADE, anchor, direction, 7, -61, 42, -61, 2));
        result.add(line("west_rear", LinearFeatureKind.PALISADE, anchor, direction, -42, -61, -42, -15, 2));
        result.add(line("east_rear", LinearFeatureKind.PALISADE, anchor, direction, 42, -61, 42, -31, 2));
        if (archetype != SettlementLayoutArchetype.FOOTHILL_RIBBON) {
            result.add(line("west_forward", LinearFeatureKind.DITCH, anchor, direction, -42, 16, -42, 49, 2));
        }
        return List.copyOf(result);
    }

    private static LinearFeaturePlan line(String id, LinearFeatureKind kind, VisualPoint anchor, int direction,
                                          int rightA, int inwardA, int rightB, int inwardB, int width) {
        return new LinearFeaturePlan(id, kind, List.of(local(anchor, rightA, inwardA, 0, direction),
                local(anchor, rightB, inwardB, 0, direction)), width, kind == LinearFeatureKind.PALISADE);
    }

    private static List<VisualBounds> expansionPlots(VisualPoint anchor, int direction,
                                                      SettlementLayoutArchetype archetype,
                                                      List<VisualModulePlacement> modules) {
        int crossInward = archetype == SettlementLayoutArchetype.FREIGHT_CROSSROADS ? 15 : 2;
        int[] inwardCandidates = {-57, -45, -32, -18, -5, 10, 24, 38, 51};
        int[] rightCandidates = {-38, 38, -25, 25, -12, 12};
        List<VisualBounds> result = new ArrayList<>();
        outer: for (int inward : inwardCandidates) for (int right : rightCandidates) {
            if (Math.abs(right) <= 7 || Math.abs(inward - crossInward) <= 4) continue;
            VisualPoint center = local(anchor, right, inward, 0, direction);
            VisualBounds candidate = new VisualBounds(new VisualPoint(center.x() - 5, center.y() - 2, center.z() - 5),
                    new VisualPoint(center.x() + 5, center.y() + 12, center.z() + 5));
            if (modules.stream().anyMatch(module -> overlaps(expand(module.footprint(), 2), candidate))) continue;
            if (result.stream().anyMatch(existing -> overlaps(expand(existing, 2), candidate))) continue;
            result.add(candidate);
            if (result.size() == 6) break outer;
        }
        if (result.size() != 6) throw new IllegalStateException("settlement grammar cannot reserve six buildable plots");
        return List.copyOf(result);
    }

    private static void validateExpansionPlots(List<VisualModulePlacement> modules, List<VisualBounds> plots) {
        for (int index = 0; index < plots.size(); index++) {
            VisualBounds plot = plots.get(index);
            if (modules.stream().anyMatch(module -> overlaps(module.footprint(), plot))) {
                throw new IllegalStateException("expansion plot overlaps authored module " + index);
            }
            for (int other = index + 1; other < plots.size(); other++) {
                if (overlaps(plot, plots.get(other))) throw new IllegalStateException("expansion plots overlap");
            }
        }
    }

    private static VisualBounds expand(VisualBounds bounds, int horizontal) {
        return new VisualBounds(new VisualPoint(bounds.min().x() - horizontal, bounds.min().y(),
                bounds.min().z() - horizontal), new VisualPoint(bounds.max().x() + horizontal,
                bounds.max().y(), bounds.max().z() + horizontal));
    }

    private static VisualBounds moduleFootprint(String name, VisualPoint origin, int rotation) {
        FrontierModuleCatalog.Definition definition = FrontierModuleCatalog.require(name);
        boolean swap = Math.floorMod(rotation, 2) == 1;
        int xSize = swap ? definition.sizeZ() : definition.sizeX();
        int zSize = swap ? definition.sizeX() : definition.sizeZ();
        VisualPoint min = new VisualPoint(origin.x() - xSize / 2, origin.y() + 1, origin.z() - zSize / 2);
        return new VisualBounds(min, new VisualPoint(min.x() + xSize - 1, min.y() + definition.sizeY() - 1,
                min.z() + zSize - 1));
    }

    private static VisualPoint entrance(VisualBounds bounds, int direction) {
        int x = (bounds.min().x() + bounds.max().x()) / 2;
        int z = (bounds.min().z() + bounds.max().z()) / 2;
        if (direction == 0) x = bounds.max().x();
        else if (direction == 1) z = bounds.max().z();
        else if (direction == 2) x = bounds.min().x();
        else z = bounds.min().z();
        return new VisualPoint(x, bounds.min().y(), z);
    }

    private static VisualPoint oppositeEntrance(VisualBounds bounds, int direction) {
        return entrance(bounds, Math.floorMod(direction + 2, 4));
    }

    private static void validateNoOverlap(List<VisualModulePlacement> modules) {
        Set<String> ids = new HashSet<>();
        for (int first = 0; first < modules.size(); first++) {
            VisualModulePlacement a = modules.get(first);
            if (!ids.add(a.instanceId())) throw new IllegalStateException("duplicate module id " + a.instanceId());
            for (int second = first + 1; second < modules.size(); second++) {
                VisualModulePlacement b = modules.get(second);
                if (overlaps(a.footprint(), b.footprint())) {
                    throw new IllegalStateException("settlement grammar overlaps " + a.instanceId()
                            + " and " + b.instanceId());
                }
            }
        }
    }

    private static boolean overlaps(VisualBounds a, VisualBounds b) {
        return a.min().x() <= b.max().x() && a.max().x() >= b.min().x()
                && a.min().z() <= b.max().z() && a.max().z() >= b.min().z();
    }

    private static VisualPoint local(VisualPoint anchor, int right, int inward, int up, int direction) {
        int dx = switch (Math.floorMod(direction, 4)) { case 0 -> inward; case 1 -> -right; case 2 -> -inward; default -> right; };
        int dz = switch (Math.floorMod(direction, 4)) { case 0 -> right; case 1 -> inward; case 2 -> -right; default -> -inward; };
        return new VisualPoint(anchor.x() + dx, anchor.y() + up, anchor.z() + dz);
    }

    private static Local relative(VisualPoint anchor, VisualPoint point, int direction) {
        int dx = point.x() - anchor.x(); int dz = point.z() - anchor.z();
        return switch (Math.floorMod(direction, 4)) {
            case 0 -> new Local(dz, dx); case 1 -> new Local(-dx, dz);
            case 2 -> new Local(-dz, -dx); default -> new Local(dx, -dz);
        };
    }

    private static VisualBounds orientedBounds(VisualPoint anchor, int right, int inward, int down, int up,
                                                int direction) {
        List<VisualPoint> corners = List.of(local(anchor, -right, -inward, down, direction),
                local(anchor, right, -inward, down, direction), local(anchor, -right, inward, up, direction),
                local(anchor, right, inward, up, direction));
        return new VisualBounds(new VisualPoint(corners.stream().mapToInt(VisualPoint::x).min().orElseThrow(),
                anchor.y() + down, corners.stream().mapToInt(VisualPoint::z).min().orElseThrow()),
                new VisualPoint(corners.stream().mapToInt(VisualPoint::x).max().orElseThrow(), anchor.y() + up,
                        corners.stream().mapToInt(VisualPoint::z).max().orElseThrow()));
    }

    private static Placement p(String template, String role, int right, int inward, int frontage,
                               boolean landmark) {
        return new Placement(template, role, right, inward, frontage, landmark);
    }

    private record Placement(String template, String role, int right, int inward, int frontage, boolean landmark) { }
    private record Local(int right, int inward) { }
}
