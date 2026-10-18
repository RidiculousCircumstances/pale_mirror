package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Shared read-only land-development view. Observed uncertainty overrides the authored terrain baseline. */
public final class DevelopmentGroundKnowledge implements KnownBlockGeometry<NavigationBlockState> {
    private final FrontierWorldState state;
    private final Set<TerrainColumn> protectedColumns;
    public DevelopmentGroundKnowledge(FrontierWorldState state) {
        this.state = Objects.requireNonNull(state);
        var protectedLand = new HashSet<>(LandUseExclusions.columns(state));
        for (var deposit : state.extractionSites().deposits().values()) {
            deposit.site().layout().cells().forEach(cell -> add(protectedLand, cell.source()));
            deposit.site().layout().terrainSupports().keySet().forEach(p -> add(protectedLand, p));
            deposit.site().layout().fixedBlocks().keySet().forEach(p -> add(protectedLand, p));
            deposit.site().layout().accessSurfaces().forEach(surface -> add(protectedLand, surface.support()));
        }
        protectedColumns = Collections.unmodifiableSet(protectedLand);
    }
    @Override public Optional<Sample<NavigationBlockState>> at(BlockPosition position) {
        if (!state.bootstrap().bounds().contains(position) || state.physicalDeltas().containsKey(position)
                || state.extractionSites().geologicalExclusions().positions().contains(position)) return Optional.empty();
        var permission = protectedColumns.contains(new TerrainColumn(position.x(), position.z()))
                ? Sample.Permission.PROTECTED : Sample.Permission.PUBLIC;
        var observed = state.navigationGeometry().cells().get(position);
        if (observed != null) return observed.cell().flatMap(NavigationCell::observedState)
                .map(block -> new Sample<>(block, Math.max(1, state.navigationGeometry().revision()), permission));
        if (state.extractionSites().geology().at(position).isPresent()) return Optional.empty();
        return ExtractionTerrainFacts.at(state.bootstrap(), position)
                .map(sample -> new Sample<>(new NavigationBlockState(sample.block().kind(), sample.block().properties()),
                        sample.revision(), permission));
    }
    private static void add(Set<TerrainColumn> columns, BlockPosition p) { columns.add(new TerrainColumn(p.x(), p.z())); }
}
