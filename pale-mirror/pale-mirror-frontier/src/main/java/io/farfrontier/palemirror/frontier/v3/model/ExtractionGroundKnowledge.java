package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Family adapter: declared geology plus current physical/site facts, never a generated work-cell pool. */
final class ExtractionGroundKnowledge implements KnownBlockGeometry<BlockExtraction.Block> {
    private final FrontierWorldState state;
    private final Set<TerrainColumn> protectedColumns;
    private final Set<BlockPosition> declared;
    private final ExtractionWorkingSurfaces local;
    private final Map<BlockPosition, ExtractionLayout.Cell> sources;
    private final ExtractionDeposit deposit;
    private record Protection(Set<TerrainColumn> columns, Set<BlockPosition> blocks) { }
    private static final ImmutableInputView<Protection> VIEW = new ImmutableInputView<>();
    private static final Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ImmutableInputView<Protection>> SITE_VIEWS = new LinkedHashMap<>();
    private ExtractionGroundKnowledge(FrontierWorldState state, Set<TerrainColumn> protectedColumns, Set<BlockPosition> declared,
                                      ExtractionDeposit deposit) {
        this.state = state; this.protectedColumns = protectedColumns; this.declared = declared;
        this.deposit = deposit;
        local = deposit == null ? null : new ExtractionWorkingSurfaces(state, deposit);
        sources = deposit == null ? Map.of() : deposit.site().layout().cells().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(ExtractionLayout.Cell::source, cell -> cell));
    }
    static ExtractionGroundKnowledge forState(FrontierWorldState state) {
        var protection = cachedProtection(state, Optional.empty());
        return new ExtractionGroundKnowledge(state, protection.columns(), protection.blocks(), null);
    }
    private static synchronized Protection cachedProtection(FrontierWorldState state,
            Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> localSite) {
        var inputs = new ArrayList<Object>(List.of(state.bootstrap(), state.routeTopology(), state.hiveColony().addedOrgans(),
                state.physicalDeltas(), state.extractionSites().geologicalExclusions()));
        state.extractionSites().deposits().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> { inputs.add(entry.getKey()); inputs.add(entry.getValue().site().layout()); });
        var view = VIEW;
        if (localSite.isPresent()) {
            if (!SITE_VIEWS.containsKey(localSite.orElseThrow()) && SITE_VIEWS.size() >= AdjacentExcavationPlanner.MAX_COLUMNS)
                SITE_VIEWS.clear();
            view = SITE_VIEWS.computeIfAbsent(localSite.orElseThrow(), ignored -> new ImmutableInputView<>());
        }
        return view.get(inputs, () -> protection(state, localSite));
    }
    static ExtractionGroundKnowledge forSite(FrontierWorldState state, ExtractionDeposit deposit) {
        var other = cachedProtection(state, Optional.of(deposit.site().id()));
        return new ExtractionGroundKnowledge(state, other.columns(), other.blocks(), deposit);
    }
    private static Protection protection(FrontierWorldState state, Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> localSite) {
        var columns = new HashSet<TerrainColumn>();
        for (var settlement : state.bootstrap().settlements()) {
            FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), settlement.structures())
                    .forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
            settlement.residents().forEach(resident -> columns.add(new TerrainColumn(resident.home().x(), resident.home().z())));
        }
        for (var site : FrontierResourceSitePlan.compile(state.bootstrap()).values())
            site.managedSlots().forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        var roads = FrontierRouteNetwork.footprint(state.bootstrap(), state.routeTopology());
        roads.foundationCells().forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        roads.surfaceCells().forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        FrontierGrayboxPlan.intactOrganOccupancy(state.bootstrap().hive().organs())
                .forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        FrontierGrayboxPlan.intactOrganOccupancy(List.copyOf(state.hiveColony().addedOrgans().values()))
                .forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        state.physicalDeltas().keySet().forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        state.extractionSites().geologicalExclusions().positions().forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        var declared = new HashSet<BlockPosition>();
        for (var deposit : state.extractionSites().deposits().values()) {
            declared.addAll(deposit.site().layout().fixedBlocks().keySet());
            if (!localSite.filter(deposit.site().id()::equals).isPresent()) {
                deposit.site().layout().cells().forEach(cell -> declared.add(cell.source()));
                declared.addAll(deposit.site().layout().terrainSupports().keySet());
            }
        }
        // Retained access and another worksite's land are whole-column obligations,
        // not permission to create a competing underground surface beneath them.
        // Own air clearance is not a permanent ban on the natural floor below it.
        for (var deposit : state.extractionSites().deposits().values()) {
            if (localSite.filter(deposit.site().id()::equals).isPresent()) continue;
            deposit.site().layout().accessSurfaces().forEach(surface -> columns.add(new TerrainColumn(surface.x(), surface.z())));
            deposit.site().layout().fixedBlocks().keySet().forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
            deposit.site().layout().cells().forEach(cell -> columns.add(new TerrainColumn(cell.source().x(), cell.source().z())));
        }
        return new Protection(Set.copyOf(columns), Set.copyOf(declared));
    }
    @Override public Optional<Sample<BlockExtraction.Block>> at(BlockPosition position) {
        if (state.extractionSites().geologicalExclusions().positions().contains(position)) return Optional.empty();
        if (local != null) return local.at(position).map(block -> new Sample<>(block,
                Optional.ofNullable(sources.get(position)).map(cell -> deposit.cells().get(cell.id()).revision())
                        .orElseGet(() -> Optional.ofNullable(deposit.geometry().get(position)).map(ExtractionDeposit.GeometryState::revision).orElse(1L)),
                block.kind().equals("minecraft:air") ? Sample.Permission.PUBLIC
                        : protectedColumns.contains(new TerrainColumn(position.x(), position.z()))
                            || declared.contains(position) || sources.containsKey(position)
                            ? Sample.Permission.PROTECTED : Sample.Permission.PUBLIC));
        return ExtractionTerrainFacts.at(state.bootstrap(), position).map(fact -> new Sample<>(
                fact.block(), fact.revision(),
                protectedColumns.contains(new TerrainColumn(position.x(), position.z())) || declared.contains(position)
                        ? Sample.Permission.PROTECTED : Sample.Permission.PUBLIC));
    }
}
