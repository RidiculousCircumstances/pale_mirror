package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geology.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockVolume;
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
        return compile(bootstrap).sites();
    }
    /** Fresh-world equipment is not a permanent worksite-to-storage relationship. */
    public record Installation(ExtractionSiteState sites, Map<SubjectId, SubjectId> initialContainers) {
        public Installation { initialContainers = Map.copyOf(initialContainers); }
    }
    public static Installation compile(FrontierBootstrap bootstrap) {
        ExtractionRules rules = bootstrap.ruleset().extraction();
        if (!rules.enabled()) return new Installation(ExtractionSiteState.empty(), Map.of());
        Set<BlockPosition> occupied = new HashSet<>();
        for (var settlement : bootstrap.settlements()) {
            occupied.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
            settlement.residents().forEach(resident -> occupied.add(resident.home()));
        }
        FrontierResourceSitePlan.compile(bootstrap).values().forEach(site -> occupied.addAll(site.managedSlots()));
        var roads = FrontierRouteNetwork.footprint(bootstrap, RouteTopology.initial());
        occupied.addAll(roads.foundationCells()); occupied.addAll(roads.surfaceCells());
        occupied.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        // Lookup only: JDK compact SetN suffers clustered coordinate hashes.
        // Build once and extend with each accepted site, not per facing trial.
        var occupiedColumns = new HashSet<TerrainColumn>();
        occupied.forEach(position -> occupiedColumns.add(new TerrainColumn(position.x(), position.z())));
        Map<SubjectId, ExtractionDeposit> deposits = new LinkedHashMap<>();
        Map<SubjectId, SubjectId> containers = new LinkedHashMap<>();
        Map<SubjectId, MineralDeposit> minerals = new LinkedHashMap<>();
        Map<SubjectId, Map<SubjectId, Long>> surveys = new LinkedHashMap<>();
        int producerIndex = 0;
        for (Settlement home : producerSettlements(bootstrap)) {
            var local = new HashSet<BlockPosition>(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), home.structures()));
            home.residents().forEach(resident -> local.add(resident.home()));
            FrontierResourceSitePlan.compile(bootstrap).values().stream().filter(site -> site.settlementId().equals(home.id()))
                    .forEach(site -> local.addAll(site.managedSlots()));
            int extent = local.stream().mapToInt(position -> Math.max(Math.abs(position.x() - home.anchor().x()),
                    Math.abs(position.z() - home.anchor().z()))).max().orElseThrow();
            for (int installationIndex = 0; installationIndex <= rules.additionalPockets().size(); installationIndex++) {
                var siteRules = rules.installation(installationIndex);
                // Optional installations describe real deposits, not empty stone
                // quarries in towns where that material was never distributed.
                if (installationIndex > 0 && producerIndex % siteRules.pocket().orElseThrow().producerPeriod() != 0) continue;
                ExtractionSite selected = null;
                for (FacilityFacing facing : List.of(FacilityFacing.EAST, FacilityFacing.SOUTH, FacilityFacing.WEST, FacilityFacing.NORTH)) {
                    int distance = extent + rules.width() + rules.exteriorClearance();
                    BlockPosition anchor = home.anchor().offset(facing.x() * distance, 0, facing.z() * distance);
                    int supportY = bootstrap.terrain().supportYAt(anchor.x(), anchor.z());
                    anchor = new BlockPosition(anchor.x(), supportY - rules.pitDepth(), anchor.z());
                    ExtractionLayout layout = layout(anchor, facing, siteRules, producerIndex);
                    var positions = new HashSet<>(layout.fixedBlocks().keySet());
                    positions.addAll(layout.terrainSupports().keySet());
                    layout.cells().forEach(cell -> positions.add(cell.source()));
                    // A quarry must not overlap another owner even when its pit is vertically below it.
                    if (positions.stream().anyMatch(position -> !bootstrap.bounds().contains(position)
                            || occupiedColumns.contains(new TerrainColumn(position.x(), position.z())))) continue;
                    String identity = UUID.nameUUIDFromBytes((bootstrap.worldId().value() + "|" + home.id().value() + "|quarry"
                            + (installationIndex == 0 ? "" : "|" + installationIndex))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                    SubjectId id = new SubjectId("extraction:quarry-" + identity);
                    var mineral = mineral(anchor, facing, siteRules, producerIndex, new SubjectId("deposit:mineral-" + identity));
                    selected = new ExtractionSite(id, home.id(), layout, mineral.map(value -> Set.of(value.id())).orElse(Set.of()));
                    mineral.ifPresent(value -> {
                        minerals.put(value.id(), value);
                        surveys.computeIfAbsent(home.id(), ignored -> new LinkedHashMap<>()).put(value.id(), value.revision());
                    });
                    containers.put(id, new SubjectId("container:quarry-" + identity));
                    positions.forEach(position -> occupiedColumns.add(new TerrainColumn(position.x(), position.z()))); break;
                }
                if (selected == null) throw new IllegalArgumentException("no clear exterior quarry for " + home.id());
                var opened = rules.developmentBatchCells() == 0 ? selected.layout().cells().stream().map(ExtractionLayout.Cell::id)
                        : selected.layout().cells().stream().filter(cell -> cell.prerequisites().isEmpty()).map(ExtractionLayout.Cell::id);
                deposits.put(selected.id(), ExtractionDeposit.initial(selected,
                        opened.collect(java.util.stream.Collectors.toUnmodifiableSet())));
            }
            producerIndex++;
        }
        return new Installation(new ExtractionSiteState(deposits, Map.of(), 1,
                io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockExclusions.empty(),
                new MineralGeology(minerals), new GeologicalKnowledge(surveys)), containers);
    }
    private static Optional<MineralDeposit> mineral(BlockPosition anchor, FacilityFacing facing, ExtractionRules rules,
            int producerIndex, SubjectId id) {
        return rules.pocket().filter(pocket -> producerIndex % pocket.producerPeriod() == 0).map(pocket -> {
            var a = at(anchor, facing, pocket.firstDepth(), 1, pocket.firstRow());
            var b = at(anchor, facing, pocket.firstDepth() + pocket.depthCells() - 1, 2, pocket.firstRow() + pocket.rowCells() - 1);
            var bounds = new BlockVolume(new BlockPosition(Math.min(a.x(), b.x()), a.y(), Math.min(a.z(), b.z())),
                    new BlockPosition(Math.max(a.x(), b.x()), b.y(), Math.max(a.z(), b.z())));
            return new MineralDeposit(id, 1, List.of(new MineralDeposit.Section(bounds, pocket.material().before())));
        });
    }
    private static ExtractionLayout layout(BlockPosition anchor, FacilityFacing facing, ExtractionRules rules, int producerIndex) {
        var fixed = new LinkedHashMap<BlockPosition, BlockExtraction.Block>();
        var natural = new LinkedHashMap<BlockPosition, BlockExtraction.Block>();
        var cells = new ArrayList<ExtractionLayout.Cell>();
        var surfaces = new LinkedHashMap<TerrainColumn, SurfaceAnchor>();
        var stone = new BlockExtraction.Block("minecraft:stone", Map.of());
        var air = new BlockExtraction.Block("minecraft:air", Map.of());
        rules.rockRise().filter(rise -> producerIndex % rise.producerPeriod() == 0).ifPresent(rise -> {
            int top = rules.surfaceCut().orElseThrow().topY() + rise.height();
            for (int x = rules.depth(); x < rules.depth() + rise.depth(); x++)
                for (int z = 0; z < rules.width(); z++)
                    for (int y = 0; y <= top - anchor.y(); y++) natural.put(at(anchor, facing, x, y, z), stone);
        });
        for (int x = -5; x < rules.depth(); x++) for (int z = -1; z <= rules.width(); z++) {
            var support = at(anchor, facing, x, 0, z);
            if (x >= 0 && z >= 0 && z < rules.width()) natural.put(support, stone);
            else fixed.put(support, stone);
            SurfaceAnchor surface = new SurfaceAnchor(at(anchor, facing, x, 0, z));
            surfaces.put(new TerrainColumn(surface.x(), surface.z()), surface);
            for (int y = 1; y <= rules.pitDepth() + 2; y++) if (!(x >= 0 && z >= 0 && z < rules.width() && y <= 2))
                fixed.put(at(anchor, facing, x, y, z), air);
        }
        // A finite inclusion is a terrain declaration, not thousands of already
        // admitted source cells. The common development owner later reclassifies
        // these exact blocks into its work front; ordinary extraction consumes them.
        rules.pocket().filter(pocket -> producerIndex % pocket.producerPeriod() == 0).ifPresent(pocket -> {
            for (int depth = pocket.firstDepth(); depth < pocket.firstDepth() + pocket.depthCells(); depth++)
                for (int row = pocket.firstRow(); row < pocket.firstRow() + pocket.rowCells(); row++)
                    for (int y = 1; y <= 2; y++) {
                        if (depth < rules.depth() && row < rules.width()) continue; // Already a declared initial source.
                        var position = at(anchor, facing, depth, y, row);
                        if (!fixed.containsKey(position)) natural.put(position, pocket.material().before());
                    }
        });
        for (int row = 0; row < rules.width(); row++) {
            Set<Long> previous = Set.of();
            for (int depth = 0; depth < rules.depth(); depth++) {
                SurfaceAnchor work = new SurfaceAnchor(at(anchor, facing, depth - 1, 0, row));
                int localDepth = depth, localRow = row;
                var material = rules.pocket().filter(pocket -> pocket.contains(producerIndex, localDepth, localRow))
                        .map(ExtractionPocketProfile::material).orElse(rules.source());
                long upper = cells.size() + 1L;
                cells.add(new ExtractionLayout.Cell(upper, at(anchor, facing, depth, 2, row), work, material, previous));
                var lowerPrerequisites = new HashSet<>(previous); lowerPrerequisites.add(upper);
                long lower = cells.size() + 1L;
                cells.add(new ExtractionLayout.Cell(lower, at(anchor, facing, depth, 1, row), work, material, lowerPrerequisites));
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
        var infrastructureIds = new LinkedHashMap<BlockPosition, Long>();
        fixed.keySet().stream().sorted(Comparator.comparingInt(BlockPosition::x).thenComparingInt(BlockPosition::y)
                .thenComparingInt(BlockPosition::z)).forEach(position -> infrastructureIds.put(position, infrastructureIds.size() + 1L));
        return new ExtractionLayout(cells, fixed, List.copyOf(surfaces.values()),
                new SurfaceAnchor(at(anchor, facing, -3, rules.pitDepth(), -rules.pitDepth() - 1)),
                container, new SurfaceAnchor(at(anchor, facing, -3, 0, 1)), infrastructureIds, natural);
    }
    private static BlockPosition at(BlockPosition anchor, FacilityFacing facing, int forward, int y, int sideways) {
        return anchor.offset(facing.x() * forward - facing.z() * sideways, y,
                facing.z() * forward + facing.x() * sideways);
    }
}
