package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionWorksiteViewTest {
    @Test void currentViewTracksDepletionExternalEditsRollbackAndExtensionLocally() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:worksite-view"),
                125, FrontierRulesets.installed("frontier-v3-quarry-graybox-r22")));
        var site = state.extractionSites().deposits().values().iterator().next();
        assertPointDeclarations(site);
        var view = new ExtractionWorksiteView(); var before = view.current(state);
        assertSame(before, view.current(state));
        var cell = site.site().layout().cells().getFirst();
        var changed = site.extracted(cell.id(), 1, "effect:worksite-view");
        assertPointDeclarations(changed);
        var next = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(changed)));
        var after = view.current(next);
        assertEquals(Set.of(Region.at(cell.source())), changed(after, before));
        assertEquals("minecraft:air", point(after, cell.source()).block().kind());
        for (var region : before.keySet()) if (!region.equals(Region.at(cell.source()))) assertSame(before.get(region), after.get(region));
        assertEquals(new ExtractionWorksiteView().current(next), after);
        assertEquals(before, view.current(state));
        var position = site.site().layout().terrainSupports().keySet().stream()
                .filter(p -> site.site().layout().cells().stream().noneMatch(c -> c.source().equals(p))).findFirst().orElseThrow();
        var external = site.observeGeometry(position, 1, new BlockExtraction.Block("minecraft:air", Map.of()));
        assertPointDeclarations(external);
        var observed = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(external)));
        assertEquals(new ExtractionWorksiteView().current(observed), view.current(observed));
        assertEquals(before, view.current(state), "rollback removes observed override, not just its revision");
        var layout = site.site().layout(); var station = layout.entrance();
        var source = List.of(station.support().offset(1, 1, 0), station.support().offset(-1, 1, 0),
                station.support().offset(0, 1, 1), station.support().offset(0, 1, -1)).stream()
                .filter(p -> !layout.fixedBlocks().containsKey(p) && layout.cells().stream().noneMatch(c -> c.source().equals(p))).findFirst().orElseThrow();
        var cells = new ArrayList<>(layout.cells());
        cells.add(new ExtractionLayout.Cell(cells.stream().mapToLong(ExtractionLayout.Cell::id).max().orElseThrow() + 1,
                source, station, cell.definition(), Set.of()));
        var extended = site.extend(site.development().revision(), new ExtractionLayout(cells, layout.fixedBlocks(),
                layout.accessSurfaces(), station, layout.container(), layout.storagePort(), layout.infrastructureIds(),
                layout.terrainSupports(), layout.terrainIds()));
        var extension = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().extend(site, extended)));
        assertPointDeclarations(extended);
        var extendedView = view.current(extension);
        assertEquals(Set.of(Region.at(source)), changed(extendedView, before));
        assertEquals(new ExtractionWorksiteView().current(extension), extendedView);
    }
    private static Set<Region> changed(Map<Region, List<WorksiteBlock>> current, Map<Region, List<WorksiteBlock>> before) {
        var result = new HashSet<>(current.keySet()); result.addAll(before.keySet());
        result.removeIf(region -> Objects.equals(current.get(region), before.get(region))); return result;
    }
    private static WorksiteBlock point(Map<Region, List<WorksiteBlock>> regions, BlockPosition position) {
        return regions.get(Region.at(position)).stream().filter(cell -> cell.position().equals(position)).findFirst().orElseThrow();
    }
    private static void assertPointDeclarations(ExtractionDeposit deposit) {
        for (var cell : ExtractionWorksiteBlocks.declared(deposit)) {
            var point = ExtractionWorksiteBlocks.declaredAt(deposit, cell.position()).orElseThrow();
            assertEquals(cell, point);
            assertEquals(ExtractionWorksiteBlocks.current(deposit, cell), ExtractionWorksiteBlocks.current(deposit, point));
        }
        assertTrue(ExtractionWorksiteBlocks.declaredAt(deposit, new BlockPosition(100000, -64, 100000)).isEmpty());
    }
}
