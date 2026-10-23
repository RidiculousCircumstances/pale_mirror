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
    private static final ImmutableResultView<Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId>, Protection> PROTECTION =
            new ImmutableResultView<>(64);
    private ExtractionGroundKnowledge(FrontierWorldState state, Set<TerrainColumn> protectedColumns, Set<BlockPosition> declared,
                                      ExtractionDeposit deposit) {
        this.state = state; this.protectedColumns = protectedColumns; this.declared = declared;
        this.deposit = deposit;
        local = deposit == null ? null : new ExtractionWorkingSurfaces(state, deposit);
        sources = deposit == null ? Map.of() : deposit.site().layout().cellsByPosition();
    }
    static ExtractionGroundKnowledge forState(FrontierWorldState state) {
        var protection = cachedProtection(state, Optional.empty());
        return new ExtractionGroundKnowledge(state, protection.columns(), protection.blocks(), null);
    }
    private static Protection cachedProtection(FrontierWorldState state,
            Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> localSite) {
        var inputs = new ArrayList<Object>(List.of(state.bootstrap(), state.routeTopology(), state.hiveColony().addedOrgans(),
                state.physicalDeltas(), state.extractionSites().geologicalExclusions()));
        inputs.add(LandUseExclusions.columns(state));
        state.extractionSites().deposits().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> { inputs.add(entry.getKey()); inputs.add(entry.getValue().site().layout()); });
        return PROTECTION.get(inputs, localSite, () -> protection(state, localSite));
    }
    static ExtractionGroundKnowledge forSite(FrontierWorldState state, ExtractionDeposit deposit) {
        var other = cachedProtection(state, Optional.of(deposit.site().id()));
        return new ExtractionGroundKnowledge(state, other.columns(), other.blocks(), deposit);
    }
    private static Protection protection(FrontierWorldState state, Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> localSite) {
        var columns = new HashSet<>(baseColumns(state));
        state.physicalDeltas().keySet().forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        state.extractionSites().geologicalExclusions().positions().forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        var declared = new HashSet<BlockPosition>();
        for (var deposit : state.extractionSites().deposits().values()) {
            declared.addAll(deposit.site().layout().fixedBlocks().keySet());
            if (!localSite.filter(deposit.site().id()::equals).isPresent()) {
                deposit.site().layout().cells().forEach(cell -> declared.add(cell.source()));
                declared.addAll(deposit.site().layout().terrainSupports().keySet());
                deposit.site().layout().accessSurfaces().forEach(surface -> columns.add(new TerrainColumn(surface.x(), surface.z())));
                deposit.site().layout().fixedBlocks().keySet().forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
                deposit.site().layout().cells().forEach(cell -> columns.add(new TerrainColumn(cell.source().x(), cell.source().z())));
            }
        }
        // Dense grid hashes are highly clustered; retain bucketed immutable views,
        // not SetN's quadratic linear-probe construction across all worksite blocks.
        return new Protection(Collections.unmodifiableSet(columns), Collections.unmodifiableSet(declared));
    }

    /** Excavation fronts change frequently; immutable towns/roads do not. */
    static Set<TerrainColumn> baseColumns(FrontierWorldState state) {
        return LandUseExclusions.columns(state);
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
        // A global terrain probe has no settlement survey authority. Never flatten
        // a declared mineral inclusion into the background stone stratum.
        if (state.extractionSites().geology().at(position).isPresent()) return Optional.empty();
        return ExtractionTerrainFacts.at(state.bootstrap(), position).map(fact -> new Sample<>(
                fact.block(), fact.revision(),
                protectedColumns.contains(new TerrainColumn(position.x(), position.z())) || declared.contains(position)
                        ? Sample.Permission.PROTECTED : Sample.Permission.PUBLIC));
    }
}
