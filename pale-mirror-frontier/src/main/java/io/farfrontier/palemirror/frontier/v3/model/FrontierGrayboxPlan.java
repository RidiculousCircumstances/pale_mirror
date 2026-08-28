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
        addSettlementRoutes(cells, state);
        Map<InfectionCell, FixedRatio> infection = state.infection().entrySet().stream()
                .filter(entry -> entry.getValue().value().raw() > 0L)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new FrontierGrayboxPlan(cells, infection);
    }

    public Map<BlockPosition, GrayboxCell> cells() { return cells; }
    /** Sparse source cells; loaded-chunk materialization expands only the cells intersecting that chunk. */
    public Map<InfectionCell, FixedRatio> infection() { return infection; }

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

    private static void addSettlementRoutes(Map<BlockPosition, GrayboxCell> cells, FrontierWorldState state) {
        var settlements = state.bootstrap().settlements();
        SubjectId network = new SubjectId("route:frontier-network");
        for (int index = 0; index < settlements.size(); index++) {
            Settlement settlement = settlements.get(index); BlockPosition anchor = settlement.anchor();
            int laneZ = anchor.z() + 36; int laneX = anchor.x() + 36;
            line(cells, network, anchor.x(), laneZ, laneX, laneZ);
            line(cells, network, laneX, anchor.z(), laneX, laneZ);
            if (index % 4 != 3) line(cells, network, laneX, laneZ, settlements.get(index + 1).anchor().x() + 36, laneZ);
            if (index < 8) line(cells, network, laneX, laneZ, laneX, settlements.get(index + 4).anchor().z() + 36);
        }
    }

    private static void line(Map<BlockPosition, GrayboxCell> cells, SubjectId owner, int fromX, int fromZ, int toX, int toZ) {
        if (fromX != toX && fromZ != toZ) throw new IllegalArgumentException("graybox route must be axis aligned");
        int stepX = Integer.compare(toX, fromX), stepZ = Integer.compare(toZ, fromZ);
        for (int x = fromX, z = fromZ;; x += stepX, z += stepZ) {
            add(cells, new BlockPosition(x, 64, z), owner, GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE);
            if (x == toX && z == toZ) return;
        }
    }

    private static void add(Map<BlockPosition, GrayboxCell> cells, BlockPosition position, SubjectId owner, GrayboxMaterial material,
                            GrayboxSemanticPart part) {
        GrayboxCell cell = new GrayboxCell(position, owner, material, part);
        GrayboxCell prior = cells.putIfAbsent(position, cell);
        if (prior != null && !prior.equals(cell)) throw new IllegalArgumentException("overlapping graybox cells at " + position);
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("graybox plan cell limit exceeded");
    }

}
