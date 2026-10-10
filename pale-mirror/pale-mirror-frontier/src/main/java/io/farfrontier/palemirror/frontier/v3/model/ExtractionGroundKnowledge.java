package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Family adapter: declared geology plus current physical/site facts, never a generated work-cell pool. */
final class ExtractionGroundKnowledge implements KnownBlockGeometry<BlockExtraction.Block> {
    private final FrontierWorldState state;
    private final Set<TerrainColumn> protectedColumns;
    private final Set<BlockPosition> declared;
    private record Protection(Set<TerrainColumn> columns, Set<BlockPosition> blocks) { }
    private static final ImmutableInputView<Protection> VIEW = new ImmutableInputView<>();
    private ExtractionGroundKnowledge(FrontierWorldState state, Set<TerrainColumn> protectedColumns, Set<BlockPosition> declared) {
        this.state = state; this.protectedColumns = protectedColumns; this.declared = declared;
    }
    static ExtractionGroundKnowledge forState(FrontierWorldState state) {
        var inputs = new ArrayList<Object>(List.of(state.bootstrap(), state.routeTopology(), state.hiveColony().addedOrgans(),
                state.physicalDeltas(), state.extractionSites().geologicalExclusions()));
        state.extractionSites().deposits().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> { inputs.add(entry.getKey()); inputs.add(entry.getValue().site().layout()); });
        var protection = VIEW.get(inputs, () -> protection(state));
        return new ExtractionGroundKnowledge(state, protection.columns(), protection.blocks());
    }
    private static Protection protection(FrontierWorldState state) {
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
            deposit.site().layout().cells().forEach(cell -> declared.add(cell.source()));
        }
        // Retained access and another worksite's land are whole-column obligations,
        // not permission to create a competing underground surface beneath them.
        declared.forEach(position -> columns.add(new TerrainColumn(position.x(), position.z())));
        return new Protection(Set.copyOf(columns), Set.copyOf(declared));
    }
    @Override public Optional<Sample<BlockExtraction.Block>> at(BlockPosition position) {
        if (state.extractionSites().geologicalExclusions().positions().contains(position)) return Optional.empty();
        var geology = state.bootstrap().ruleset().extraction().geology();
        if (geology.isEmpty()) return Optional.empty();
        return geology.get().at(state.bootstrap().bounds(), position).map(kind -> new Sample<>(
                new BlockExtraction.Block(kind, Map.of()), geology.get().revision(),
                protectedColumns.contains(new TerrainColumn(position.x(), position.z())) || declared.contains(position)
                        ? Sample.Permission.PROTECTED : Sample.Permission.PUBLIC));
    }
}
