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
        var view = new ExtractionWorksiteView(); var before = view.current(state);
        assertSame(before, view.current(state));
        var cell = site.site().layout().cells().getFirst();
        var changed = site.extracted(cell.id(), 1, "effect:worksite-view");
        var next = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(changed)));
        var after = view.current(next);
        assertEquals(Set.of(Region.at(cell.source())), after.changesFrom(before));
        assertEquals("minecraft:air", after.get(cell.source()).block().kind());
        for (var region : before.regions()) if (!region.equals(Region.at(cell.source()))) assertSame(before.in(region), after.in(region));
        assertEquals(new ExtractionWorksiteView().current(next), after);
        assertEquals(before, view.current(state));
        var position = site.site().layout().terrainSupports().keySet().stream()
                .filter(p -> site.site().layout().cells().stream().noneMatch(c -> c.source().equals(p))).findFirst().orElseThrow();
        var external = site.observeGeometry(position, 1, new BlockExtraction.Block("minecraft:air", Map.of()));
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
        var extendedView = view.current(extension);
        assertEquals(Set.of(Region.at(source)), extendedView.changesFrom(before));
        assertEquals(new ExtractionWorksiteView().current(extension), extendedView);
    }
}
