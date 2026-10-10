package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Fresh-world content producer. Runtime excavation consumes its declared geometry, not this compiler. */
public final class GrayboxQuarryPlan {
    private GrayboxQuarryPlan() { }
    public static List<Settlement> producerSettlements(FrontierBootstrap bootstrap) {
        if (!bootstrap.ruleset().extraction().enabled()) return List.of();
        var settlements = bootstrap.settlements().stream().sorted(Comparator.comparing(Settlement::id)).toList();
        var producers = new ArrayList<Settlement>();
        for (int index = 0; index < settlements.size(); index += bootstrap.ruleset().extraction().producerStride())
            producers.add(settlements.get(index));
        return List.copyOf(producers);
    }
    public static ExtractionSiteState initial(FrontierBootstrap bootstrap) {
        ExtractionRules rules = bootstrap.ruleset().extraction();
        if (!rules.enabled()) return ExtractionSiteState.empty();
        Set<BlockPosition> occupied = new HashSet<>();
        for (var settlement : bootstrap.settlements()) {
            occupied.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
            settlement.residents().forEach(resident -> occupied.add(resident.home()));
        }
        FrontierResourceSitePlan.compile(bootstrap).values().forEach(site -> occupied.addAll(site.managedSlots()));
        var roads = FrontierRouteNetwork.footprint(bootstrap, RouteTopology.initial());
        occupied.addAll(roads.foundationCells()); occupied.addAll(roads.surfaceCells());
        occupied.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        Map<SubjectId, ExtractionDeposit> deposits = new LinkedHashMap<>();
        for (Settlement home : producerSettlements(bootstrap)) {
            var local = new HashSet<BlockPosition>(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), home.structures()));
            home.residents().forEach(resident -> local.add(resident.home()));
            FrontierResourceSitePlan.compile(bootstrap).values().stream().filter(site -> site.settlementId().equals(home.id()))
                    .forEach(site -> local.addAll(site.managedSlots()));
            int extent = local.stream().mapToInt(position -> Math.max(Math.abs(position.x() - home.anchor().x()),
                    Math.abs(position.z() - home.anchor().z()))).max().orElseThrow();
            ExtractionSite selected = null;
            for (FacilityFacing facing : List.of(FacilityFacing.EAST, FacilityFacing.SOUTH, FacilityFacing.WEST, FacilityFacing.NORTH)) {
                int distance = extent + rules.width() + rules.exteriorClearance();
                BlockPosition anchor = home.anchor().offset(facing.x() * distance, 0, facing.z() * distance);
                int supportY = bootstrap.terrain().supportYAt(anchor.x(), anchor.z());
                anchor = new BlockPosition(anchor.x(), supportY - rules.pitDepth(), anchor.z());
                ExtractionLayout layout = layout(anchor, facing, rules);
                var positions = new HashSet<>(layout.fixedBlocks().keySet());
                layout.cells().forEach(cell -> positions.add(cell.source()));
                // A quarry must not overlap another owner even when its pit is vertically below it.
                var columns = occupied.stream().map(position -> new TerrainColumn(position.x(), position.z()))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                if (positions.stream().anyMatch(position -> !bootstrap.bounds().contains(position)
                        || columns.contains(new TerrainColumn(position.x(), position.z())))) continue;
                String identity = UUID.nameUUIDFromBytes((bootstrap.worldId().value() + "|" + home.id().value() + "|quarry")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                SubjectId id = new SubjectId("extraction:quarry-" + identity);
                selected = new ExtractionSite(id, home.id(), new SubjectId("container:quarry-" + identity), layout);
                occupied.addAll(positions); break;
            }
            if (selected == null) throw new IllegalArgumentException("no clear exterior quarry for " + home.id());
            var opened = rules.developmentBatchCells() == 0 ? selected.layout().cells().stream().map(ExtractionLayout.Cell::id)
                    : selected.layout().cells().stream().filter(cell -> cell.prerequisites().isEmpty()).map(ExtractionLayout.Cell::id);
            deposits.put(selected.id(), ExtractionDeposit.initial(selected,
                    opened.collect(java.util.stream.Collectors.toUnmodifiableSet())));
        }
        return new ExtractionSiteState(deposits);
    }
    private static ExtractionLayout layout(BlockPosition anchor, FacilityFacing facing, ExtractionRules rules) {
        var fixed = new LinkedHashMap<BlockPosition, BlockExtraction.Block>();
        var cells = new ArrayList<ExtractionLayout.Cell>();
        var surfaces = new LinkedHashMap<TerrainColumn, SurfaceAnchor>();
        var stone = new BlockExtraction.Block("minecraft:stone", Map.of());
        var air = new BlockExtraction.Block("minecraft:air", Map.of());
        for (int x = -5; x < rules.depth(); x++) for (int z = -1; z <= rules.width(); z++) {
            fixed.put(at(anchor, facing, x, 0, z), stone);
            SurfaceAnchor surface = new SurfaceAnchor(at(anchor, facing, x, 0, z));
            surfaces.put(new TerrainColumn(surface.x(), surface.z()), surface);
            for (int y = 1; y <= rules.pitDepth() + 2; y++) if (!(x >= 0 && z >= 0 && z < rules.width() && y <= 2))
                fixed.put(at(anchor, facing, x, y, z), air);
        }
        for (int row = 0; row < rules.width(); row++) {
            Set<Long> previous = Set.of();
            for (int depth = 0; depth < rules.depth(); depth++) {
                SurfaceAnchor work = new SurfaceAnchor(at(anchor, facing, depth - 1, 0, row));
                long upper = cells.size() + 1L;
                cells.add(new ExtractionLayout.Cell(upper, at(anchor, facing, depth, 2, row), work, rules.source(), previous));
                var lowerPrerequisites = new HashSet<>(previous); lowerPrerequisites.add(upper);
                long lower = cells.size() + 1L;
                cells.add(new ExtractionLayout.Cell(lower, at(anchor, facing, depth, 1, row), work, rules.source(), lowerPrerequisites));
                previous = Set.of(upper, lower);
            }
        }
        // Ordinary one-block rises, with retained two-block standing clearance.
        for (int step = 1; step <= rules.pitDepth(); step++) for (int width = -4; width <= -2; width++) {
            BlockPosition support = at(anchor, facing, width, step, -step - 1);
            fixed.put(support, stone); fixed.put(support.offset(0, 1, 0), air); fixed.put(support.offset(0, 2, 0), air);
            surfaces.put(new TerrainColumn(support.x(), support.z()), new SurfaceAnchor(support));
        }
        BlockPosition container = at(anchor, facing, -3, 1, 2);
        fixed.put(container, new BlockExtraction.Block("minecraft:chest", Map.of(
                "facing", facing.name().toLowerCase(java.util.Locale.ROOT), "type", "single", "waterlogged", "false")));
        // Functional floor/access plus a small shelter: decoration is not deposit stock.
        for (int z : List.of(1, 3)) for (int y = 1; y <= 3; y++)
            fixed.put(at(anchor, facing, -5, y, z), new BlockExtraction.Block("minecraft:oak_log", Map.of("axis", "y")));
        for (int x = -5; x <= -2; x++) for (int z = 1; z <= 3; z++)
            fixed.put(at(anchor, facing, x, 4, z), new BlockExtraction.Block("minecraft:oak_planks", Map.of()));
        fixed.put(at(anchor, facing, -4, 1, 0), new BlockExtraction.Block("minecraft:torch", Map.of()));
        return new ExtractionLayout(cells, fixed, List.copyOf(surfaces.values()),
                new SurfaceAnchor(at(anchor, facing, -3, rules.pitDepth(), -rules.pitDepth() - 1)),
                container, new SurfaceAnchor(at(anchor, facing, -3, 0, 1)));
    }
    private static BlockPosition at(BlockPosition anchor, FacilityFacing facing, int forward, int y, int sideways) {
        return anchor.offset(facing.x() * forward - facing.z() * sideways, y,
                facing.z() * forward + facing.x() * sideways);
    }
}
