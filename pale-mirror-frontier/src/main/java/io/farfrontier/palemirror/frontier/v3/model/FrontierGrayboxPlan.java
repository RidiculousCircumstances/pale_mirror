package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic v3 graybox plan derived only from canonical state.
 *
 * <p>It is an immutable desired projection, not materialization progress or a second world
 * state. NeoForge may only apply it to naturally loaded, unclaimed cells and records physical
 * drift separately.</p>
 */
public final class FrontierGrayboxPlan {
    private static final int MAX_CELLS = 65_536;
    private final Map<BlockPosition, GrayboxCell> cells;
    private final Map<InfectionCell, FixedRatio> infection;

    private FrontierGrayboxPlan(Map<BlockPosition, GrayboxCell> cells, Map<InfectionCell, FixedRatio> infection) {
        this.cells = Map.copyOf(cells);
        this.infection = Map.copyOf(infection);
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("graybox plan cell limit exceeded");
    }

    public static FrontierGrayboxPlan compile(FrontierWorldState state) {
        Objects.requireNonNull(state, "state");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        state.bootstrap().settlements().forEach(settlement -> settlement.structures().forEach(structure ->
                addStructure(cells, structure, state.structureConditions().get(structure.id()))));
        state.bootstrap().hive().organs().forEach(organ -> addOrgan(cells, organ));
        state.hiveColony().addedOrgans().values().forEach(organ -> addOrgan(cells, organ));
        addRoutes(cells, state.bootstrap(), state.routeTopology());
        // Physical deltas are canonical aftermath, not executor-local provenance.  Once an
        // observed cell is gone, desired-state projection must not ask a later loaded chunk to
        // recreate it, including after the SavedData ledger has been compacted or lost.
        state.physicalDeltas().keySet().forEach(cells::remove);
        Map<InfectionCell, FixedRatio> infection = state.infection().entrySet().stream()
                .filter(entry -> entry.getValue().value().raw() > 0L)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new FrontierGrayboxPlan(cells, infection);
    }

    public Map<BlockPosition, GrayboxCell> cells() { return cells; }
    /** Sparse source cells; the dedicated overlay projects one owned marker for each loaded 4×4 cell. */
    public Map<InfectionCell, FixedRatio> infection() { return infection; }

    /** Deterministic inactive cells that an exact-material replacement project must build. */
    public static java.util.List<BlockPosition> routeConstructionCells(FrontierWorldState state, RouteConstruction project) {
        return FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), project.settlementId(), project.waypoints());
    }

    /** Full intact geometry, used to validate observed damage after the desired silhouette changes. */
    public static GrayboxCell intactStructureCell(SettlementStructure structure, BlockPosition position) {
        Objects.requireNonNull(structure, "structure"); Objects.requireNonNull(position, "position");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        addStructure(cells, structure, StructureCondition.INTACT);
        return cells.get(position);
    }

    public static int intactStructureCellCount(SettlementStructure structure) {
        Objects.requireNonNull(structure, "structure");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        addStructure(cells, structure, StructureCondition.INTACT);
        return cells.size();
    }

    /**
     * Resolves one full-intact semantic cell without consulting desired state.  Observations use
     * this baseline because a previous loss may already have removed the cell from the current
     * projection.  It deliberately covers every currently materializable owner kind.
     */
    public static GrayboxCell intactSemanticCell(FrontierBootstrap bootstrap, HiveColony colony, SubjectId owner, BlockPosition position) {
        return intactSemanticCell(bootstrap, colony, RouteTopology.initial(), owner, position);
    }

    /** Resolves intact geometry against the accepted canonical route topology. */
    public static GrayboxCell intactSemanticCell(FrontierBootstrap bootstrap, HiveColony colony, RouteTopology topology, SubjectId owner, BlockPosition position) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(colony, "hive colony");
        Objects.requireNonNull(topology, "route topology"); Objects.requireNonNull(owner, "owner"); Objects.requireNonNull(position, "position");
        for (Settlement settlement : bootstrap.settlements()) for (SettlementStructure structure : settlement.structures()) {
            if (structure.id().equals(owner)) return intactStructureCell(structure, position);
        }
        for (HiveOrgan organ : bootstrap.hive().organs()) if (organ.id().equals(owner)) return intactOrganCell(organ, position);
        HiveOrgan added = colony.addedOrgans().get(owner);
        if (added != null) return intactOrganCell(added, position);
        if (FrontierRouteNetwork.OWNER.equals(owner) && FrontierRouteNetwork.surfaceCells(bootstrap, topology).contains(position)) {
            return new GrayboxCell(position, owner, GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE);
        }
        return null;
    }

    public static int intactOrganCellCount(HiveOrgan organ) {
        Objects.requireNonNull(organ, "organ");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        addOrgan(cells, organ);
        return cells.size();
    }

    private static GrayboxCell intactOrganCell(HiveOrgan organ, BlockPosition position) {
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        addOrgan(cells, organ);
        return cells.get(position);
    }

    private static void addStructure(Map<BlockPosition, GrayboxCell> cells, SettlementStructure structure, StructureCondition condition) {
        if (condition == StructureCondition.DESTROYED) return;
        int width = switch (structure.kind()) {
            case HALL, DEPOT -> 8; case FARM -> 9; case WORKSHOP, INFIRMARY -> 7; case HOUSING -> 6;
        };
        int depth = switch (structure.kind()) {
            case HALL, FARM, WORKSHOP, DEPOT -> 7; case HOUSING, INFIRMARY -> 6;
        };
        int height = condition == StructureCondition.DAMAGED ? 2 : switch (structure.kind()) {
            case HALL -> 5; case DEPOT, WORKSHOP -> 4; default -> 3;
        };
        GrayboxMaterial material = switch (structure.kind()) {
            case HALL -> GrayboxMaterial.HALL; case HOUSING -> GrayboxMaterial.HOUSING; case FARM -> GrayboxMaterial.FARM;
            case WORKSHOP -> GrayboxMaterial.WORKSHOP; case DEPOT -> GrayboxMaterial.DEPOT; case INFIRMARY -> GrayboxMaterial.INFIRMARY;
        };
        for (int x = -width / 2; x <= (width - 1) / 2; x++) for (int z = -depth / 2; z <= (depth - 1) / 2; z++) {
            add(cells, structure.anchor().offset(x, 0, z), structure.id(), material, GrayboxSemanticPart.FOUNDATION);
            for (int y = 1; y < height; y++) if (x == -width / 2 || x == (width - 1) / 2 || z == -depth / 2 || z == (depth - 1) / 2) {
                add(cells, structure.anchor().offset(x, y, z), structure.id(), material, GrayboxSemanticPart.WALL);
            }
            add(cells, structure.anchor().offset(x, height, z), structure.id(), material, GrayboxSemanticPart.ROOF);
        }
    }

    private static void addOrgan(Map<BlockPosition, GrayboxCell> cells, HiveOrgan organ) {
        GrayboxMaterial material = switch (organ.kind()) {
            case HEART -> GrayboxMaterial.HIVE_HEART; case BROOD -> GrayboxMaterial.HIVE_BROOD; case STORE -> GrayboxMaterial.HIVE_STORE;
        };
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++) {
            if (y == 3 || Math.abs(x) == 2 || Math.abs(z) == 2) add(cells, organ.anchor().offset(x, y, z), organ.id(), material, GrayboxSemanticPart.HIVE_TISSUE);
        }
    }

    private static void addRoutes(Map<BlockPosition, GrayboxCell> cells, FrontierBootstrap bootstrap, RouteTopology topology) {
        FrontierRouteNetwork.surfaceCells(bootstrap, topology).forEach(position ->
                add(cells, position, FrontierRouteNetwork.OWNER, GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE));
    }

    private static void add(Map<BlockPosition, GrayboxCell> cells, BlockPosition position, SubjectId owner, GrayboxMaterial material,
                            GrayboxSemanticPart part) {
        GrayboxCell cell = new GrayboxCell(position, owner, material, part);
        GrayboxCell prior = cells.putIfAbsent(position, cell);
        if (prior != null && !prior.equals(cell)) throw new IllegalArgumentException("overlapping graybox cells at " + position);
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("graybox plan cell limit exceeded");
    }

}
