package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Shared read-only land-development view. Observed uncertainty overrides the authored terrain baseline. */
public final class DevelopmentGroundKnowledge implements KnownBlockGeometry<NavigationBlockState> {
    private static final ImmutableResultView<String, RegionalColumnSet> PROTECTED = new ImmutableResultView<>(16);
    private static final ImmutableInputView<RegionalBlockMap<PhysicalDelta>> DELTAS = new ImmutableInputView<>();
    private final FrontierWorldState state;
    private final RegionalColumnSet protectedColumns;
    public DevelopmentGroundKnowledge(FrontierWorldState state) {
        this.state = Objects.requireNonNull(state);
        var land = LandUseExclusions.columns(state);
        var layouts = state.extractionSites().deposits().values().stream()
                .sorted(Comparator.comparing(deposit -> deposit.site().id())).map(deposit -> deposit.site().layout()).toList();
        var inputs = new ArrayList<Object>(); inputs.add(land); inputs.addAll(layouts);
        protectedColumns = PROTECTED.get(inputs, "columns", () -> {
            var parts = new ArrayList<>(List.of(land));
            layouts.forEach(layout -> parts.add(ExtractionLandProtection.of(layout).developmentColumns()));
            return RegionalColumnSet.union(parts);
        });
    }

    @Override public Optional<Object> regionalReadVersion(Set<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region> regions) {
        var observations = RegionalBlockMap.copyOf(state.navigationGeometry().cells());
        var deltas = DELTAS.get(List.of(state.physicalDeltas()), () -> RegionalBlockMap.copyOf(state.physicalDeltas()));
        var exclusions = RegionalBlockSet.copyOf(state.extractionSites().geologicalExclusions().positions());
        var facts = new HashMap<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region, List<Object>>();
        for (var region : regions) facts.put(region, List.of(observations.token(region), deltas.token(region),
                exclusions.token(region), state.extractionSites().geology().in(region), protectedColumns.token(region)));
        return Optional.of(new RegionalReadVersion(List.of(state.bootstrap()), facts));
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
}
