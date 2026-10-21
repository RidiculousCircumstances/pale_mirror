package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionGeometryIncrementalTest {
    private static final BlockExtraction.Block AIR = new BlockExtraction.Block("minecraft:air", Map.of());
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:regional-extraction"),
                125, FrontierRulesets.installed("frontier-v3-quarry-graybox-r22")));
    }
    private static ExtractionDeposit deposit(FrontierWorldState state) {
        return state.extractionSites().deposits().values().stream().sorted(Comparator.comparing(d -> d.site().id())).findFirst().orElseThrow();
    }
    private static FrontierWorldState replace(FrontierWorldState state, ExtractionDeposit deposit) {
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(deposit)));
    }
    @Test void oneMinedBlockUpdatesOnlyItsRegionAndPreservesOtherMinesAndHistoricalViews() {
        var state = initial(); var original = deposit(state); var cell = original.site().layout().cells().getFirst();
        var facts = new ExtractionGeometryFacts(); var prior = facts.read(state, original);
        var provider = new ExtractionKnownGeometry(); var before = provider.project(state);
        var changed = original.extracted(cell.id(), 1, "effect:regional-test"); var after = replace(state, changed);
        var ownership = ExtractionGeometryIndex.forDeposits(state.extractionSites().deposits());
        assertSame(ownership, ExtractionGeometryIndex.forDeposits(after.extractionSites().deposits()),
                "depletion preserves the validated declaration ownership index");
        assertTrue(ownership.owns(cell.source()), "depleted sources remain owned until explicit retirement");
        var current = facts.read(after, changed);
        assertEquals(Set.of(Region.at(cell.source())), current.changesFrom(prior));
        for (var region : prior.regions()) if (!region.equals(Region.at(cell.source()))) assertSame(prior.in(region), current.in(region));
        var result = provider.project(after);
        assertTrue(result.withdrawnSupports().contains(cell.source()));
        assertFalse(result.obstacles().contains(cell.source()));
        assertTrue(before.obstacles().contains(cell.source()), "published predecessor remains immutable");
        var remote = state.extractionSites().deposits().values().stream().filter(d -> !d.site().id().equals(original.site().id())).findFirst().orElseThrow();
        var remoteRegion = Region.at(remote.site().layout().entrance().support());
        assertSame(RegionalBlockMap.copyOf(before.retainedBlocks()).in(remoteRegion), RegionalBlockMap.copyOf(result.retainedBlocks()).in(remoteRegion));
        assertEquals(new ExtractionKnownGeometry().project(after), result, "incremental result equals a complete reconstruction");
        assertEquals(before, provider.project(state), "candidate rollback must restore exact predecessor facts");
        assertEquals(result, provider.project(after));
        assertEquals(state.inventory(), after.inventory(), "the geometry view owns no yield or custody");
    }
    @Test void localUnknownKnowledgeAndObservedReplacementAreNotHiddenByRegionalReuse() {
        var state = initial(); var original = deposit(state);
        var sources = new HashSet<BlockPosition>(); original.site().layout().cells().forEach(c -> sources.add(c.source()));
        var local = original.site().layout().terrainSupports().keySet().stream().filter(p -> !sources.contains(p)).findFirst().orElseThrow();
        var facts = new ExtractionGeometryFacts(); var prior = facts.read(state, original);
        var remote = new ExtractionGeologyInvalidated(new BlockPosition(0, 65, 0), 1).apply(state, ExtractionGeologyInvalidated.OWNER);
        assertSame(prior, facts.read(remote, original));
        var unknown = new ExtractionGeologyInvalidated(local, 1).apply(remote, ExtractionGeologyInvalidated.OWNER);
        var withdrawn = facts.read(unknown, original);
        assertEquals(Set.of(Region.at(local)), withdrawn.changesFrom(prior));
        assertEquals(Optional.empty(), withdrawn.get(local));
        var observed = original.observeGeometry(local, 1, AIR); var known = replace(unknown, observed);
        assertEquals("minecraft:air", facts.read(known, observed).get(local).orElseThrow().kind());
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(known));
        assertEquals(new ExtractionKnownGeometry().project(known), new ExtractionKnownGeometry().project(recovered));
    }
    @Test void extensionKeepsUnchangedRegionsAndPublishesTheNewSourceWithoutRebuildingAllDeposits() {
        var state = initial(); var original = deposit(state); var layout = original.site().layout();
        var station = layout.entrance();
        var source = List.of(station.support().offset(1, 1, 0), station.support().offset(-1, 1, 0),
                station.support().offset(0, 1, 1), station.support().offset(0, 1, -1)).stream()
                .filter(p -> !layout.fixedBlocks().containsKey(p) && layout.cells().stream().noneMatch(c -> c.source().equals(p))).findFirst().orElseThrow();
        var cells = new ArrayList<>(layout.cells());
        cells.add(new ExtractionLayout.Cell(cells.stream().mapToLong(ExtractionLayout.Cell::id).max().orElseThrow() + 1,
                source, station, cells.getFirst().definition(), Set.of()));
        var extended = original.extend(original.development().revision(), new ExtractionLayout(cells, layout.fixedBlocks(),
                layout.accessSurfaces(), layout.entrance(), layout.container(), layout.storagePort(), layout.infrastructureIds(),
                layout.terrainSupports(), layout.terrainIds()));
        var facts = new ExtractionGeometryFacts(); var before = facts.read(state, original);
        var afterState = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().extend(original, extended)));
        var ownership = ExtractionGeometryIndex.forDeposits(state.extractionSites().deposits());
        assertFalse(ownership.owns(source));
        assertTrue(ExtractionGeometryIndex.forDeposits(afterState.extractionSites().deposits()).owns(source));
        assertFalse(ownership.owns(source), "an extension cannot mutate the predecessor declaration");
        var after = facts.read(afterState, extended);
        assertEquals(Set.of(Region.at(source)), after.changesFrom(before));
        assertEquals(cells.getLast().definition().before().kind(), after.get(source).orElseThrow().kind());
        for (var region : before.regions()) if (!region.equals(Region.at(source))) assertSame(before.in(region), after.in(region));
        var provider = new ExtractionKnownGeometry(); provider.project(state);
        assertEquals(new ExtractionKnownGeometry().project(afterState), provider.project(afterState));
    }
}
