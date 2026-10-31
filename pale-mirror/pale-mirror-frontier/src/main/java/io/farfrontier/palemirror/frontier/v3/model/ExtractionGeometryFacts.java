package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Extraction's spatial adapter: immutable declarations, current block overrides and withdrawn knowledge.
 * Geological volumes remain owned by geology; this derived view never expands the world's reserves
 * on a worker progress update. Only changed declared regions are sampled from that owner. */
final class ExtractionGeometryFacts {
    private ExtractionLayout layout;
    private RegionalBlockMap<BlockExtraction.Block> declared = RegionalBlockMap.copyOf(Map.of());
    private RegionalBlockMap<BlockExtraction.Block> overrides = RegionalBlockMap.copyOf(Map.of());
    private Map<Long, ExtractionDeposit.CellState> cells;
    private Map<BlockPosition, ExtractionDeposit.GeometryState> observations;
    private RegionalBlockSet footprint = RegionalBlockSet.copyOf(Set.of());
    private RegionalBlockSet excluded = RegionalBlockSet.copyOf(Set.of());
    private final LocalBlockExclusions exclusions;
    private Object exclusionRoot;
    private Object bootstrap;
    private Object geology;
    private Object knowledge;
    private record GeologicalRegion(List<io.farfrontier.palemirror.frontier.v3.model.geology.MineralGeology.Match> declarations,
            Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> surveyed) { }
    private Map<Region, GeologicalRegion> geologicalRegions = Map.of();
    private io.farfrontier.palemirror.frontier.v3.api.SubjectId settlement;
    private Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> minerals = Set.of();
    private RegionalBlockMap<Optional<NavigationBlockState>> facts = RegionalBlockMap.copyOf(Map.of());

    ExtractionGeometryFacts() { exclusions = new LocalBlockExclusions(); }
    ExtractionGeometryFacts(ExtractionGeometryFacts source) {
        exclusions = new LocalBlockExclusions(source.exclusions); exclusionRoot = source.exclusionRoot;
        layout = source.layout; declared = source.declared; overrides = source.overrides;
        cells = source.cells; observations = source.observations; footprint = source.footprint;
        excluded = source.excluded; bootstrap = source.bootstrap; geology = source.geology; knowledge = source.knowledge;
        geologicalRegions = source.geologicalRegions; settlement = source.settlement;
        minerals = source.minerals; facts = source.facts;
    }

    boolean unchanged(FrontierWorldState state, ExtractionDeposit deposit) {
        return layout == deposit.site().layout() && cells == deposit.cells() && observations == deposit.geometry()
                && bootstrap == state.bootstrap() && geology == state.extractionSites().geology()
                && knowledge == state.extractionSites().knowledge()
                && exclusionRoot == state.extractionSites().geologicalExclusions()
                && deposit.site().settlementId().equals(settlement) && deposit.site().mineralDeposits().equals(minerals);
    }
    RegionalBlockMap<Optional<NavigationBlockState>> snapshot() { return facts; }

    RegionalBlockMap<Optional<NavigationBlockState>> read(FrontierWorldState state, ExtractionDeposit deposit) {
        var changed = new HashSet<Region>();
        var nextLayout = deposit.site().layout();
        boolean layoutChanged = layout != nextLayout;
        var nextDeclared = declared; var nextFootprint = footprint; var nextOverrides = overrides;
        if (layoutChanged) {
            var blocks = new HashMap<>(nextLayout.terrainSupports()); blocks.putAll(nextLayout.fixedBlocks());
            nextDeclared = RegionalBlockMap.copyOf(blocks);
            changed.addAll(nextDeclared.changesFrom(declared));
            var positions = new HashSet<>(blocks.keySet());
            nextLayout.accessSurfaces().forEach(surface -> positions.add(surface.support()));
            nextLayout.cells().forEach(cell -> positions.add(cell.source()));
            // Every vertical contact and its clearance are retained; no single-height terrain approximation.
            for (var position : new ArrayList<>(positions)) for (int y = 1; y <= 3; y++) positions.add(position.offset(0, y, 0));
            nextFootprint = RegionalBlockSet.copyOf(positions);
            changed.addAll(nextFootprint.changesFrom(footprint));
        }
        if (layoutChanged || cells != deposit.cells() || observations != deposit.geometry()) {
            var blocks = new HashMap<BlockPosition, BlockExtraction.Block>();
            deposit.geometry().forEach((position, observation) -> blocks.put(position, observation.block()));
            // Source-cell facts have precedence over authored/observed substrate, as in the owning point view.
            for (var cell : nextLayout.cells()) blocks.put(cell.source(), deposit.cells().get(cell.id()).knownBlock());
            nextOverrides = RegionalBlockMap.copyOf(blocks);
            changed.addAll(nextOverrides.changesFrom(overrides));
        }
        var nextExcluded = RegionalBlockSet.copyOf(exclusions.read(nextFootprint, state.extractionSites().geologicalExclusions()));
        changed.addAll(nextExcluded.changesFrom(excluded));
        var sites = state.extractionSites();
        boolean ownerChanged = !deposit.site().settlementId().equals(settlement)
                || !deposit.site().mineralDeposits().equals(minerals);
        var nextRegions = geologicalRegions;
        if (layoutChanged || ownerChanged || geology != sites.geology() || knowledge != sites.knowledge()) {
            var local = new HashMap<Region, GeologicalRegion>();
            for (var region : nextFootprint.regions()) {
                var declarations = sites.geology().in(region);
                var surveyed = new HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
                for (var match : declarations) if (deposit.site().mineralDeposits().contains(match.depositId())
                        && sites.knowledge().knows(deposit.site().settlementId(), sites.geology().deposits().get(match.depositId())))
                    surveyed.add(match.depositId());
                var image = new GeologicalRegion(declarations, Set.copyOf(surveyed));
                var prior = geologicalRegions.get(region);
                local.put(region, image.equals(prior) ? prior : image);
                if (!image.equals(prior)) changed.add(region);
            }
            nextRegions = Map.copyOf(local);
        }
        if (bootstrap != state.bootstrap()) changed.addAll(nextFootprint.regions());
        if (!changed.isEmpty()) {
            var working = new ExtractionWorkingSurfaces(state, deposit);
            var updates = new HashMap<Region, Map<BlockPosition, Optional<NavigationBlockState>>>();
            for (var region : changed) {
                var blocks = new HashMap<BlockPosition, Optional<NavigationBlockState>>();
                for (var position : nextFootprint.in(region)) blocks.put(position, working.at(position)
                        .map(block -> new NavigationBlockState(block.kind(), block.properties())));
                updates.put(region, blocks);
            }
            facts = facts.replace(updates);
        }
        declared = nextDeclared; footprint = nextFootprint; layout = nextLayout; overrides = nextOverrides;
        cells = deposit.cells(); observations = deposit.geometry(); excluded = nextExcluded;
        bootstrap = state.bootstrap(); geology = sites.geology(); knowledge = sites.knowledge();
        exclusionRoot = sites.geologicalExclusions();
        geologicalRegions = nextRegions; settlement = deposit.site().settlementId(); minerals = deposit.site().mineralDeposits();
        return facts;
    }
}
