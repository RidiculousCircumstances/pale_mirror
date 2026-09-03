package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        return compile(state, true);
    }

    /**
     * Compiles the immutable structural baseline without applying the current physical-loss
     * mask.  The NeoForge projector retains this large plan across individual broken/repaired
     * cells and consults the exact canonical loss map immediately before projection.  This is
     * deliberately not a second desired-state authority: callers that present the plan to a
     * player must use {@link #compile(FrontierWorldState)}, which includes those losses.
     */
    public static FrontierGrayboxPlan compileStructuralBaseline(FrontierWorldState state) {
        return compile(state, false);
    }

    private static FrontierGrayboxPlan compile(FrontierWorldState state, boolean applyPhysicalLossMask) {
        Objects.requireNonNull(state, "state");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        state.bootstrap().settlements().forEach(settlement -> settlement.structures().forEach(structure ->
                addStructure(cells, state.bootstrap().terrain(), structure, state.structureConditions().get(structure.id()))));
        state.bootstrap().hive().organs().forEach(organ -> addOrgan(cells, state.bootstrap().terrain(), organ));
        state.hiveColony().addedOrgans().values().forEach(organ -> addOrgan(cells, state.bootstrap().terrain(), organ));
        addOccupiedCocoons(cells, state.bootstrap(), state.hiveColony());
        addRoutes(cells, state.bootstrap(), state.routeTopology());
        addActiveWorksiteStaging(cells, state);
        // Physical deltas are canonical aftermath, not executor-local provenance.  Once an
        // observed cell is gone, desired-state projection must not ask a later loaded chunk to
        // recreate it, including after the SavedData ledger has been compacted or lost.
        if (applyPhysicalLossMask) state.physicalDeltas().keySet().forEach(cells::remove);
        Map<InfectionCell, FixedRatio> infection = state.infection().entrySet().stream()
                .filter(entry -> entry.getValue().value().raw() > 0L)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new FrontierGrayboxPlan(cells, infection);
    }

    public Map<BlockPosition, GrayboxCell> cells() { return cells; }
    /** Sparse source cells; the dedicated overlay projects one owned marker for each loaded 4×4 cell. */
    public Map<InfectionCell, FixedRatio> infection() { return infection; }

    /**
     * Exact immutable inputs for the static structural baseline, deliberately excluding moving
     * actors, inventory, active operations, infection and individual physical losses. Those
     * domains have independent executors (or, for loss, a per-cell canonical mask) and must not
     * make an unchanged settlement/hive/route silhouette allocate a new full graybox plan. The
     * inputs are already immutable canonical values; this value object retains references only
     * for read-only equality at the NeoForge projection boundary.
     */
    public static StructuralInput structuralInput(FrontierWorldState state) {
        Objects.requireNonNull(state, "structural projection state");
        return new StructuralInput(state.bootstrap(), state.structureConditions(), state.hiveColony().addedOrgans(),
                retainedCocoonLifecycles(state.hiveColony()), state.routeTopology(), activeWorksiteStaging(state));
    }

    private static Map<SubjectId, BioformLifecycle> retainedCocoonLifecycles(HiveColony colony) {
        return colony.bioformLifecycles().entrySet().stream()
                .filter(entry -> entry.getValue().phase().occupiesCocoon() || entry.getValue().phase() == BioformLifecyclePhase.WAKING)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static final class StructuralInput {
        private final FrontierBootstrap bootstrap;
        private final Map<SubjectId, StructureCondition> structureConditions;
        private final Map<SubjectId, HiveOrgan> addedOrgans;
        private final Map<SubjectId, BioformLifecycle> retainedCocoonLifecycles;
        private final RouteTopology routeTopology;
        private final Map<SubjectId, java.util.List<BlockPosition>> activeWorksiteStaging;

        private StructuralInput(FrontierBootstrap bootstrap, Map<SubjectId, StructureCondition> structureConditions,
                                Map<SubjectId, HiveOrgan> addedOrgans, Map<SubjectId, BioformLifecycle> retainedCocoonLifecycles,
                                RouteTopology routeTopology,
                                Map<SubjectId, java.util.List<BlockPosition>> activeWorksiteStaging) {
            this.bootstrap = bootstrap;
            this.structureConditions = structureConditions;
            this.addedOrgans = addedOrgans;
            this.retainedCocoonLifecycles = retainedCocoonLifecycles;
            this.routeTopology = routeTopology;
            this.activeWorksiteStaging = activeWorksiteStaging;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StructuralInput input)) return false;
            return bootstrap.equals(input.bootstrap) && structureConditions.equals(input.structureConditions)
                    && addedOrgans.equals(input.addedOrgans) && retainedCocoonLifecycles.equals(input.retainedCocoonLifecycles)
                    && routeTopology.equals(input.routeTopology)
                    && activeWorksiteStaging.equals(input.activeWorksiteStaging);
        }

        @Override public int hashCode() {
            return Objects.hash(bootstrap, structureConditions, addedOrgans, retainedCocoonLifecycles, routeTopology, activeWorksiteStaging);
        }
    }

    /**
     * Exact current semantic body occupancy for pure COLD route planners.  Visible route and
     * public-access cells are deliberate floor support, not body geometry: an actor standing on
     * either needs the two cells above it clear.  This avoids compiling the whole render plan
     * merely to answer that collision question while retaining the same structure, organ and
     * physical-loss rules.
     */
    static Set<BlockPosition> currentBodyGeometry(FrontierWorldState state) {
        Objects.requireNonNull(state, "body geometry state");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        state.bootstrap().settlements().forEach(settlement -> settlement.structures().forEach(structure ->
                addStructure(cells, state.bootstrap().terrain(), structure, state.structureConditions().get(structure.id()))));
        state.bootstrap().hive().organs().forEach(organ -> addOrgan(cells, state.bootstrap().terrain(), organ));
        state.hiveColony().addedOrgans().values().forEach(organ -> addOrgan(cells, state.bootstrap().terrain(), organ));
        addOccupiedCocoons(cells, state.bootstrap(), state.hiveColony());
        state.physicalDeltas().keySet().forEach(cells::remove);
        return cells.values().stream()
                .filter(cell -> cell.semanticPart() != GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE)
                .map(GrayboxCell::position)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Current object-owned cells for local presentation rules.
     *
     * <p>This deliberately excludes the world-wide route grid, worksite staging and infection
     * overlay.  Boards need to explain contamination on buildings and hive organs, not rebuild
     * every road in order to answer that local question.  The same structure and organ
     * compilers as graybox projection remain the sole definition of the included geometry.</p>
     */
    static Map<SubjectId, Set<BlockPosition>> currentObjectCellsByOwner(FrontierWorldState state) {
        Objects.requireNonNull(state, "state");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        state.bootstrap().settlements().forEach(settlement -> settlement.structures().forEach(structure ->
                addStructure(cells, state.bootstrap().terrain(), structure, state.structureConditions().get(structure.id()))));
        state.bootstrap().hive().organs().forEach(organ -> addOrgan(cells, state.bootstrap().terrain(), organ));
        state.hiveColony().addedOrgans().values().forEach(organ -> addOrgan(cells, state.bootstrap().terrain(), organ));
        state.physicalDeltas().keySet().forEach(cells::remove);
        Map<SubjectId, Set<BlockPosition>> byOwner = new LinkedHashMap<>();
        cells.values().forEach(cell -> byOwner.computeIfAbsent(cell.ownerId(), ignored -> new LinkedHashSet<>()).add(cell.position()));
        Map<SubjectId, Set<BlockPosition>> immutable = new LinkedHashMap<>();
        byOwner.forEach((owner, positions) -> immutable.put(owner, Set.copyOf(positions)));
        return Map.copyOf(immutable);
    }

    /**
     * Tests whether one settlement's current semantic structures still touch a live infection
     * cell. Health reviews reuse the exact structure-cell grammar but never construct the
     * unrelated world plan or temporary GrayboxCell map on their recurring COLD path.
     */
    public static boolean settlementHasInfectionContact(FrontierWorldState state, Settlement settlement) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(settlement, "settlement");
        if (state.infection().isEmpty()) return false;
        for (SettlementStructure structure : settlement.structures()) {
            StructureCondition condition = state.structureConditions().get(structure.id());
            Set<InfectionCell> candidates = infectedStructureCells(state, structure, condition);
            if (candidates.isEmpty()) continue;
            // Every non-destroyed structure has a semantic foundation at every footprint column.
            // With no aftermath, the small grid-cell candidate query is already an exact contact
            // answer; do not perform one sparse-map lookup for every wall and roof block.
            if (state.physicalDeltas().isEmpty()) return true;
            if (visitStructureCells(state.bootstrap().terrain(), structure, condition, (position, ignored) ->
                    candidates.contains(InfectionCell.at(position)) && !state.physicalDeltas().containsKey(position))) return true;
        }
        return false;
    }

    /**
     * Returns only live sparse infection cells that can geometrically intersect this structure.
     * The rectangle covers its complete foundation; Hall access cells are added separately
     * because they can extend beyond the box.  A physical aftermath still takes the exact
     * semantic-cell path above, so this is an index, never an approximate disease rule.
     */
    private static Set<InfectionCell> infectedStructureCells(FrontierWorldState state, SettlementStructure structure,
                                                              StructureCondition condition) {
        if (condition == StructureCondition.DESTROYED) return Set.of();
        int width = SettlementStructureFootprint.width(structure.kind()), depth = SettlementStructureFootprint.depth(structure.kind());
        int minX = structure.anchor().x() - width / 2, maxX = structure.anchor().x() + (width - 1) / 2;
        int minZ = structure.anchor().z() - depth / 2, maxZ = structure.anchor().z() + (depth - 1) / 2;
        Set<InfectionCell> candidates = new LinkedHashSet<>();
        for (int x = Math.floorDiv(minX, InfectionCell.BLOCKS); x <= Math.floorDiv(maxX, InfectionCell.BLOCKS); x++) {
            for (int z = Math.floorDiv(minZ, InfectionCell.BLOCKS); z <= Math.floorDiv(maxZ, InfectionCell.BLOCKS); z++) {
                InfectionCell cell = new InfectionCell(x, z);
                if (state.infection().containsKey(cell)) candidates.add(cell);
            }
        }
        publicAccessSurfaces(structure).forEach(access -> {
            InfectionCell cell = InfectionCell.at(access.support());
            if (state.infection().containsKey(cell)) candidates.add(cell);
        });
        return candidates;
    }

    /** Every exterior semantic access floor is part of the same exact structure exposure model. */
    private static List<SurfaceAnchor> publicAccessSurfaces(SettlementStructure structure) {
        return switch (structure.kind()) {
            case HALL -> SettlementAccessPort.forHall(structure).ownedSurfaces();
            case DEPOT -> SettlementDepotServicePort.forDepot(structure).ownedAccessSurfaces();
            case INFIRMARY -> SettlementInfirmaryTreatmentPort.forInfirmary(structure).ownedAccessSurfaces();
            default -> List.of();
        };
    }

    /** Deterministic inactive cells that an exact-material replacement project must build. */
    public static java.util.List<BlockPosition> routeConstructionCells(FrontierWorldState state, RouteConstruction project) {
        return FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), project.settlementId(), project.waypoints());
    }

    /** Full intact geometry, used to validate observed damage after the desired silhouette changes. */
    public static GrayboxCell intactStructureCell(TerrainSurfacePlan terrain, SettlementStructure structure, BlockPosition position) {
        Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(structure, "structure"); Objects.requireNonNull(position, "position");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        addStructure(cells, terrain, structure, StructureCondition.INTACT);
        return cells.get(position);
    }

    public static int intactStructureCellCount(TerrainSurfacePlan terrain, SettlementStructure structure) {
        Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(structure, "structure");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        addStructure(cells, terrain, structure, StructureCondition.INTACT);
        return cells.size();
    }

    /**
     * One immutable full-height occupancy view of a settlement's stable structures.
     * Bootstrap placement and later births deliberately use this same authoritative geometry
     * rather than re-deriving a partial footprint for every candidate coordinate.
     */
    static java.util.Set<BlockPosition> intactStructureOccupancy(TerrainSurfacePlan terrain, java.util.List<SettlementStructure> structures) {
        Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(structures, "structures");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        structures.forEach(structure -> addStructure(cells, terrain, structure, StructureCondition.INTACT));
        return java.util.Set.copyOf(cells.keySet());
    }

    /** Full-intact organ occupancy used by the canonical hive actor slot compiler. */
    static java.util.Set<BlockPosition> intactOrganOccupancy(java.util.List<HiveOrgan> organs) {
        Objects.requireNonNull(organs, "organs");
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        organs.forEach(organ -> addOrgan(cells, organ));
        return java.util.Set.copyOf(cells.keySet());
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
        return intactSemanticCell(bootstrap, colony, topology, Map.of(), owner, position);
    }

    /** Resolves an intact semantic baseline including a retained construction worksite. */
    public static GrayboxCell intactSemanticCell(FrontierBootstrap bootstrap, HiveColony colony, RouteTopology topology,
                                                 Map<SubjectId, RouteConstruction> constructions, SubjectId owner, BlockPosition position) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(colony, "hive colony");
        Objects.requireNonNull(topology, "route topology"); Objects.requireNonNull(constructions, "route constructions");
        Objects.requireNonNull(owner, "owner"); Objects.requireNonNull(position, "position");
        BioformLifecycle lifecycle = colony.bioformLifecycles().get(owner);
        if (lifecycle != null && lifecycle.homeSlot().isPresent()) {
            HiveCocoonSlot slot = lifecycle.homeSlot().orElseThrow();
            HiveOrgan hibernaculum = findHiveOrgan(bootstrap, colony, slot.hibernaculumId());
            if (position.equals(HiveCocoonPlan.cocoonCell(hibernaculum, slot))) {
                // The tray and its occupant are distinct exact semantic cells. Observation and
                // projection must agree on the occupant palette too.
                return new GrayboxCell(position, owner, GrayboxMaterial.HIVE_COCOON, GrayboxSemanticPart.COCOON);
            }
        }
        for (Settlement settlement : bootstrap.settlements()) for (SettlementStructure structure : settlement.structures()) {
            if (structure.id().equals(owner)) return intactStructureCell(bootstrap.terrain(), structure, position);
        }
        for (HiveOrgan organ : bootstrap.hive().organs()) if (organ.id().equals(owner)) return intactOrganCell(bootstrap.terrain(), organ, position);
        HiveOrgan added = colony.addedOrgans().get(owner);
        if (added != null) return intactOrganCell(bootstrap.terrain(), added, position);
        for (Settlement settlement : bootstrap.settlements()) {
            if (!settlement.id().equals(owner)) continue;
            SettlementResidentIngressPlan.Plan ingress = SettlementResidentIngressPlan.compile(bootstrap.bounds(), bootstrap.terrain(), settlement,
                    bootstrap.ruleset().facilityCapacity().intactHousingBeds());
            if (ingress.foundationCells().contains(position)) {
                return new GrayboxCell(position, owner, GrayboxMaterial.ROUTE_FOUNDATION, GrayboxSemanticPart.FOUNDATION);
            }
            if (ingress.ownedSurfaces().stream().map(SurfaceAnchor::support).anyMatch(position::equals)) {
                return new GrayboxCell(position, owner, GrayboxMaterial.ROUTE, GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE);
            }
            if (SettlementLocalCirculation.foundationCells(bootstrap.terrain(), settlement).contains(position)) {
                return new GrayboxCell(position, owner, GrayboxMaterial.ROUTE_FOUNDATION, GrayboxSemanticPart.FOUNDATION);
            }
            if (SettlementLocalCirculation.surfaceCells(settlement).contains(position)) {
                return new GrayboxCell(position, owner, GrayboxMaterial.ROUTE, GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE);
            }
        }
        if (FrontierRouteNetwork.OWNER.equals(owner)) {
            if (FrontierRouteNetwork.isSurfaceCell(bootstrap, topology, position)) {
                return new GrayboxCell(position, owner, GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE);
            }
            if (FrontierRouteNetwork.isFoundationCell(bootstrap, topology, position)) {
                return new GrayboxCell(position, owner, GrayboxMaterial.ROUTE_FOUNDATION, GrayboxSemanticPart.ROUTE_FOUNDATION);
            }
        }
        RouteConstruction project = constructions.get(owner);
        if (project != null && EngineeringWorksite.intactStagingCells(bootstrap, topology, project).contains(position)) {
            return new GrayboxCell(position, owner, GrayboxMaterial.WORKSITE, GrayboxSemanticPart.WORKSITE_STAGING);
        }
        return null;
    }

    public static int intactOrganCellCount(HiveOrgan organ) {
        Objects.requireNonNull(organ, "organ");
        if (organ.kind() == HiveOrganKind.HIBERNACULUM) {
            // An open 5×5 tray.  Cocoon blocks belong to individual bioforms and therefore are
            // projected separately by lifecycle state, not counted as organ tissue.
            return 25;
        }
        // addOrgan creates a 5x5 top (25 cells) plus the three lower perimeter rings
        // (3 * 16). A GANGLION reserves four two-cell exterior throats, while a STORE adds
        // its otherwise hollow central socket at y=0. Keep this
        // exact geometry formula beside the compiler instead of allocating a temporary
        // position/cell map on every infection or storage capability check.
        return 25 + 3 * 16 - (organ.kind() == HiveOrganKind.GANGLION ? 8 : 0) + (organ.containerId().isPresent() ? 1 : 0);
    }

    /** Exact organ-owned tissue plus terrain-provider hiveroot for damage accounting. */
    public static int intactOrganCellCount(TerrainSurfacePlan terrain, HiveOrgan organ) {
        return Math.addExact(intactOrganCellCount(organ), HiveOrganSupportPlan.foundationCells(terrain, organ).size());
    }

    private static GrayboxCell intactOrganCell(TerrainSurfacePlan terrain, HiveOrgan organ, BlockPosition position) {
        Map<BlockPosition, GrayboxCell> cells = new LinkedHashMap<>();
        addOrgan(cells, terrain, organ);
        return cells.get(position);
    }

    private static void addStructure(Map<BlockPosition, GrayboxCell> cells, TerrainSurfacePlan terrain, SettlementStructure structure, StructureCondition condition) {
        GrayboxMaterial material = switch (structure.kind()) {
            case HALL -> GrayboxMaterial.HALL; case HOUSING -> GrayboxMaterial.HOUSING; case FARM -> GrayboxMaterial.FARM;
            case WORKSHOP -> GrayboxMaterial.WORKSHOP; case DEPOT -> GrayboxMaterial.DEPOT; case INFIRMARY -> GrayboxMaterial.INFIRMARY;
        };
        visitStructureCells(terrain, structure, condition, (position, part) -> {
            add(cells, position, structure.id(), material, part);
            return false;
        });
    }

    /** Iterates precisely the semantic cells used by materialization, stopping on visitor demand. */
    private static boolean visitStructureCells(TerrainSurfacePlan terrain, SettlementStructure structure, StructureCondition condition, StructureCellVisitor visitor) {
        if (condition == StructureCondition.DESTROYED) return false;
        GrayboxSemanticPart foundation = GrayboxSemanticPart.FOUNDATION;
        for (BlockPosition footing : SettlementStructureFootprint.foundationFill(terrain, structure)) {
            if (visitor.visit(footing, foundation)) return true;
        }
        int width = SettlementStructureFootprint.width(structure.kind());
        int depth = SettlementStructureFootprint.depth(structure.kind());
        int height = condition == StructureCondition.DAMAGED ? 2 : switch (structure.kind()) {
            case HALL -> 5; case DEPOT, WORKSHOP -> 4; default -> 3;
        };
        SettlementAccessPort access = structure.kind() == StructureKind.HALL ? SettlementAccessPort.forHall(structure) : null;
        SettlementDepotServicePort depotService = structure.kind() == StructureKind.DEPOT ? SettlementDepotServicePort.forDepot(structure) : null;
        SettlementInfirmaryTreatmentPort treatment = structure.kind() == StructureKind.INFIRMARY ? SettlementInfirmaryTreatmentPort.forInfirmary(structure) : null;
        for (int x = -width / 2; x <= (width - 1) / 2; x++) for (int z = -depth / 2; z <= (depth - 1) / 2; z++) {
            if (visitor.visit(structure.anchor().offset(x, 0, z), GrayboxSemanticPart.FOUNDATION)) return true;
            for (int y = 1; y < height; y++) if (x == -width / 2 || x == (width - 1) / 2 || z == -depth / 2 || z == (depth - 1) / 2) {
                BlockPosition wall = structure.anchor().offset(x, y, z);
                if ((access == null || !access.throatAirCells().contains(wall))
                        && (depotService == null || !depotService.throatAirCells().contains(wall))
                        && (treatment == null || !treatment.throatAirCells().contains(wall))
                        && visitor.visit(wall, GrayboxSemanticPart.WALL)) return true;
            }
            BlockPosition roof = structure.anchor().offset(x, height, z);
            if ((access == null || !access.throatAirCells().contains(roof))
                    && (depotService == null || !depotService.throatAirCells().contains(roof))
                    && (treatment == null || !treatment.throatAirCells().contains(roof))
                    && visitor.visit(roof, GrayboxSemanticPart.ROOF)) return true;
        }
        if (access != null) for (SurfaceAnchor surface : access.ownedSurfaces()) {
            if (visitor.visit(surface.support(), GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE)) return true;
        }
        if (depotService != null) for (SurfaceAnchor surface : depotService.ownedAccessSurfaces()) {
            if (visitor.visit(surface.support(), GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE)) return true;
        }
        if (treatment != null) for (SurfaceAnchor surface : treatment.ownedAccessSurfaces()) {
            if (visitor.visit(surface.support(), GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE)) return true;
        }
        return false;
    }

    private static void addOrgan(Map<BlockPosition, GrayboxCell> cells, TerrainSurfacePlan terrain, HiveOrgan organ) {
        GrayboxMaterial material = organMaterial(organ);
        for (BlockPosition root : HiveOrganSupportPlan.foundationCells(terrain, organ)) {
            // Hiveroot is organ-owned support, not a second organ body.  Its FOUNDATION
            // semantic makes the generic executor require a real lower block while its
            // exact organ owner keeps physical loss/accounting causal.
            add(cells, root, organ.id(), material, GrayboxSemanticPart.FOUNDATION);
        }
        addOrgan(cells, organ, material);
    }

    private static void addOrgan(Map<BlockPosition, GrayboxCell> cells, HiveOrgan organ) {
        addOrgan(cells, organ, organMaterial(organ));
    }

    private static GrayboxMaterial organMaterial(HiveOrgan organ) {
        GrayboxMaterial material = switch (organ.kind()) {
            case GANGLION -> GrayboxMaterial.HIVE_GANGLION; case RELAY -> GrayboxMaterial.HIVE_RELAY;
            case BROOD -> GrayboxMaterial.HIVE_BROOD; case STORE -> GrayboxMaterial.HIVE_STORE;
            case DIGESTER -> GrayboxMaterial.HIVE_DIGESTER; case HIBERNACULUM -> GrayboxMaterial.HIVE_HIBERNACULUM;
            case MORPHER -> GrayboxMaterial.HIVE_MORPHER; case SPORULATOR -> GrayboxMaterial.HIVE_SPORULATOR;
            case SENSOR -> GrayboxMaterial.HIVE_SENSOR;
        };
        return material;
    }

    private static void addOrgan(Map<BlockPosition, GrayboxCell> cells, HiveOrgan organ, GrayboxMaterial material) {
        if (organ.kind() == HiveOrganKind.HIBERNACULUM) {
            // Unlike sealed hive organs, a hibernaculum is deliberately a shallow open tray.
            // Its living cocoons are visible, reachable and individually causal to the player.
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                add(cells, organ.anchor().offset(x, 0, z), organ.id(), material, GrayboxSemanticPart.HIVE_TISSUE);
            }
            return;
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++) {
            // The four exact two-cell Ganglion throats are semantic ports, not a later mob
            // exception: task assembly retains one exterior port, while the physical grammar
            // visibly exposes all valid directions without a hidden anchor offset.
            boolean ganglionThroat = organ.kind() == HiveOrganKind.GANGLION && y > 0 && y < 3
                    && (Math.abs(x) == 2 && z == 0 || Math.abs(z) == 2 && x == 0);
            if (!ganglionThroat && (y == 3 || Math.abs(x) == 2 || Math.abs(z) == 2)) {
                add(cells, organ.anchor().offset(x, y, z), organ.id(), material, GrayboxSemanticPart.HIVE_TISSUE);
            }
        }
        // A STORE is hollow like the other organs, but its exact chest must stand on a planned
        // tissue socket rather than on an arbitrary world block.
        if (organ.containerId().isPresent()) add(cells, organ.anchor(), organ.id(), material, GrayboxSemanticPart.HIVE_TISSUE);
    }

    private static void addOccupiedCocoons(Map<BlockPosition, GrayboxCell> cells, FrontierBootstrap bootstrap, HiveColony colony) {
        // WAKING is task custody, not a physical removal.  Until the registered RELEASE
        // executor records its exact receipt, this remains the player's real breakable cocoon.
        // Removing it from desired geometry here would make a task selection silently erase
        // physical cause before either the executor or an ordinary player had acted.
        colony.bioformLifecycles().entrySet().stream().filter(entry -> entry.getValue().phase().occupiesCocoon()
                        || entry.getValue().phase() == BioformLifecyclePhase.WAKING)
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    HiveCocoonSlot slot = entry.getValue().homeSlot().orElseThrow();
                    BlockPosition position = HiveCocoonPlan.cocoonCell(findHiveOrgan(bootstrap, colony, slot.hibernaculumId()), slot);
                    add(cells, position, entry.getKey(), GrayboxMaterial.HIVE_COCOON, GrayboxSemanticPart.COCOON);
                });
    }

    private static HiveOrgan findHiveOrgan(FrontierBootstrap bootstrap, HiveColony colony, SubjectId id) {
        return java.util.stream.Stream.concat(bootstrap.hive().organs().stream(), colony.addedOrgans().values().stream())
                .filter(organ -> organ.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("unknown hive organ: " + id.value()));
    }

    private static void addRoutes(Map<BlockPosition, GrayboxCell> cells, FrontierBootstrap bootstrap, RouteTopology topology) {
        FrontierRouteNetwork.RouteFootprint footprint = FrontierRouteNetwork.footprint(bootstrap, topology);
        footprint.foundationCells().forEach(position -> {
            if (!cells.containsKey(position)) add(cells, position, FrontierRouteNetwork.OWNER, GrayboxMaterial.ROUTE_FOUNDATION, GrayboxSemanticPart.ROUTE_FOUNDATION);
        });
        for (Settlement settlement : bootstrap.settlements()) {
            SettlementResidentIngressPlan.Plan ingress = SettlementResidentIngressPlan.compile(bootstrap.bounds(), bootstrap.terrain(), settlement,
                    bootstrap.ruleset().facilityCapacity().intactHousingBeds());
            for (BlockPosition foundation : ingress.foundationCells()) {
                if (cells.containsKey(foundation)) continue;
                add(cells, foundation, settlement.id(), GrayboxMaterial.ROUTE_FOUNDATION, GrayboxSemanticPart.FOUNDATION);
            }
            for (BlockPosition foundation : SettlementLocalCirculation.foundationCells(bootstrap.terrain(), settlement)) {
                if (cells.containsKey(foundation)) continue;
                add(cells, foundation, settlement.id(), GrayboxMaterial.ROUTE_FOUNDATION, GrayboxSemanticPart.FOUNDATION);
            }
        }
        footprint.surfaceCells().forEach(position -> {
            GrayboxCell existing = cells.get(position);
            // A Hall's declared sill is the one intentional seam where the public carriageway
            // meets a facility.  It remains Hall-owned so damage has Hall provenance, while the
            // route topology can still require the physical support.  Any other overlap is an
            // invalid compiler layout, never an arbitrary ownership winner.
            if (existing != null && existing.semanticPart() == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE) return;
            add(cells, position, FrontierRouteNetwork.OWNER, GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE);
        });
        for (Settlement settlement : bootstrap.settlements()) {
            SettlementResidentIngressPlan.Plan ingress = SettlementResidentIngressPlan.compile(bootstrap.bounds(), bootstrap.terrain(), settlement,
                    bootstrap.ruleset().facilityCapacity().intactHousingBeds());
            for (SurfaceAnchor surface : ingress.ownedSurfaces()) {
                BlockPosition position = surface.support();
                if (cells.containsKey(position)) continue;
                add(cells, position, settlement.id(), GrayboxMaterial.ROUTE, GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE);
            }
            for (BlockPosition surface : SettlementLocalCirculation.surfaceCells(settlement)) {
                // The Hall route node is deliberately already route-network owned. Every other
                // compiled sidewalk cell belongs to the settlement's public circulation plan;
                // an audit/materializer may observe loss but never replace it opportunistically.
                if (cells.containsKey(surface)) continue;
                add(cells, surface, settlement.id(), GrayboxMaterial.ROUTE, GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE);
            }
        }
    }

    private static void addActiveWorksiteStaging(Map<BlockPosition, GrayboxCell> cells, FrontierWorldState state) {
        activeWorksiteStaging(state).forEach((projectId, positions) -> positions.forEach(position ->
                add(cells, position, projectId, GrayboxMaterial.WORKSITE, GrayboxSemanticPart.WORKSITE_STAGING)));
    }

    private static Map<SubjectId, java.util.List<BlockPosition>> activeWorksiteStaging(FrontierWorldState state) {
        Map<SubjectId, java.util.List<BlockPosition>> staging = new LinkedHashMap<>();
        state.routeConstructions().values().stream().sorted(java.util.Comparator.comparing(RouteConstruction::id)).forEach(project -> {
            java.util.List<BlockPosition> positions = EngineeringWorksite.activeStagingCells(state.bootstrap(), state.routeTopology(), project);
            if (!positions.isEmpty()) staging.put(project.id(), positions);
        });
        return Map.copyOf(staging);
    }

    private static void add(Map<BlockPosition, GrayboxCell> cells, BlockPosition position, SubjectId owner, GrayboxMaterial material,
                            GrayboxSemanticPart part) {
        GrayboxCell cell = new GrayboxCell(position, owner, material, part);
        GrayboxCell prior = cells.putIfAbsent(position, cell);
        if (prior != null && !prior.equals(cell)) throw new IllegalArgumentException("overlapping graybox cells at " + position);
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("graybox plan cell limit exceeded");
    }

    @FunctionalInterface
    private interface StructureCellVisitor {
        boolean visit(BlockPosition position, GrayboxSemanticPart part);
    }

}
