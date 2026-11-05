package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.*;

/** Shared immutable placement projection; no actor, item, route or service reservation owner. */
public record BuildingSpatialPlan(
        BuildingDefinition definition,
        BuildingPlacement placement,
        Map<BlockPosition, GrayboxCell> blocks,
        Map<TerrainColumn, List<BlockPosition>> columns,
        Set<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState> palette,
        Map<String, FacilityTraversalPort> entrances,
        Set<SurfaceAnchor> circulation,
        Set<SurfaceAnchor> workstations,
        Set<SurfaceAnchor> authoredFloors) {
    // A projection has the lifetime of its immutable structure. Neither the key nor the
    // containing bootstrap is retained by its value; retired worlds must release their fabric.
    private static final Map<SettlementStructure, BuildingSpatialPlan> VIEWS = new WeakHashMap<>();

    public BuildingSpatialPlan {
        blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
        var immutableColumns = new HashMap<TerrainColumn, List<BlockPosition>>();
        columns.forEach((column, values) -> immutableColumns.put(column, List.copyOf(values)));
        columns = Collections.unmodifiableMap(immutableColumns);
        palette = Set.copyOf(palette);
        entrances = Collections.unmodifiableMap(new LinkedHashMap<>(entrances));
        circulation = Set.copyOf(circulation);
        workstations = Set.copyOf(workstations);
        authoredFloors = Set.copyOf(authoredFloors);
    }

    public static synchronized BuildingSpatialPlan of(SettlementStructure structure) {
        return VIEWS.computeIfAbsent(structure, BuildingSpatialPlan::compile);
    }

    private static BuildingSpatialPlan compile(SettlementStructure structure) {
        var definition = structure.definition();
        var placement = structure.placement();
        var blocks = new LinkedHashMap<BlockPosition, GrayboxCell>();
        var columns = new HashMap<TerrainColumn, List<BlockPosition>>();
        var floors = new LinkedHashSet<SurfaceAnchor>();
        var palette =
                new HashSet<
                        io.farfrontier.palemirror.frontier.v3.model.geometry
                                .NavigationBlockState>();
        definition
                .blocks()
                .forEach(
                        (local, block) -> {
                            var point = placement.world(local);
                            var nativeState = placement.state(block.state());
                            palette.add(nativeState);
                            switch (block.part()) {
                                case FOUNDATION,
                                                PUBLIC_ACCESS_SURFACE,
                                                WORKSHOP_INPUT_STATION,
                                                WORKSHOP_PROCESS_STATION ->
                                        floors.add(new SurfaceAnchor(point));
                                default -> {}
                            }
                            // Candidate cells only; the common collision provider decides support.
                            columns.computeIfAbsent(
                                            new TerrainColumn(point.x(), point.z()),
                                            ignored -> new ArrayList<>())
                                    .add(point);
                            blocks.put(
                                    point,
                                    new GrayboxCell(
                                            point,
                                            new PhysicalDeltaSemanticTarget(
                                                    PhysicalDeltaSemanticTargetKind
                                                            .SETTLEMENT_STRUCTURE,
                                                    structure.id()),
                                            new StructureMaterial.Authored(
                                                    nativeState, block.repairItem(), block.data()),
                                            block.part()));
                        });
        var stations = new LinkedHashSet<SurfaceAnchor>();
        definition
                .workplaces()
                .values()
                .forEach(
                        work -> {
                            stations.add(placement.support(work.input()));
                            stations.add(placement.support(work.worker()));
                            floors.add(placement.support(work.input()));
                            floors.add(placement.support(work.worker()));
                            floors.add(placement.support(work.socket()));
                        });
        var entrances = new LinkedHashMap<String, FacilityTraversalPort>();
        var circulation = new LinkedHashSet<SurfaceAnchor>();
        for (var entry : definition.passages().values()) {
            if (entry.from().kind() != BuildingDefinition.EndpointKind.EXTERIOR) continue;
            var supports = entry.supports().stream().map(placement::support).toList();
            int threshold = entry.thresholdIndex();
            if (threshold < 1 || threshold + 1 >= supports.size())
                throw new IllegalArgumentException(
                        "entrance requires exterior and interior support");
            entrances.put(
                    entry.id(),
                    new FacilityTraversalPort(
                            structure.id(),
                            structure.facing(),
                            supports.subList(0, threshold),
                            supports.get(threshold),
                            supports.get(threshold + 1),
                            List.copyOf(stations),
                            List.of(),
                            Set.of(TraversalCapability.PEDESTRIAN)));
            circulation.addAll(supports);
        }
        for (var zone : definition.zones().values())
            if (zone.use() == BuildingDefinition.ZoneUse.TRANSIT
                    || zone.use() == BuildingDefinition.ZoneUse.QUEUE) {
                var bounds = zone.volume();
                // Zones constrain standing bodies on any real level inside their volume; these
                // support candidates never invent a floor or override native clearance.
                for (int x = bounds.min().x(); x < bounds.max().x(); x++)
                    for (int y = bounds.min().y(); y < bounds.max().y(); y++)
                        for (int z = bounds.min().z(); z < bounds.max().z(); z++)
                            circulation.add(placement.support(new BlockPosition(x, y - 1, z)));
            }
        return new BuildingSpatialPlan(
                definition,
                placement,
                blocks,
                columns,
                palette,
                entrances,
                circulation,
                stations,
                floors);
    }

    public List<FacilityTraversalPort> publicEntrances() {
        return definition.passages().values().stream()
                .filter(BuildingDefinition.Passage::publicAccess)
                .map(p -> entrances.get(p.id()))
                .filter(Objects::nonNull)
                .toList();
    }

    public FacilityTraversalPort primaryEntrance() {
        var id =
                definition
                        .primaryEntrance()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "building has no primary entrance"));
        return Objects.requireNonNull(entrances.get(id), "primary building entrance");
    }

    /** Spatial containment is geometry only, never a discovery of the owner of a pending action. */
    public boolean inZone(BuildingDefinition.Zone zone, BlockPosition worldFeet) {
        return zone.volume().contains(placement.local(worldFeet));
    }
}
