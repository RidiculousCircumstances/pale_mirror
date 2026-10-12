package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.WorkingSurfaceTransition;
import java.util.*;

/** Mining-owned current effect constraints; pure geometry owns contact/edge preservation. */
public final class ExtractionWorkSafety {
    private record View(Set<BlockPosition> held, Map<TerrainColumn, SurfaceAnchor> current, Set<SurfaceAnchor> surfaces) {
        boolean available(ExtractionLayout.Cell cell) {
            if (held.contains(cell.source())) return false;
            var contact = current.get(new TerrainColumn(cell.source().x(), cell.source().z()));
            if (contact == null || !contact.support().equals(cell.source())) return true;
            var lower = SurfaceAnchor.at(contact.x(), contact.y() - 1, contact.z());
            return surfaces.contains(lower) && WorkingSurfaceTransition.preservesAccess(lower, current, ignored -> true, held);
        }
    }
    private static final Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ImmutableInputView<View>> VIEWS = new LinkedHashMap<>();
    private ExtractionWorkSafety() { }
    public static synchronized boolean available(FrontierWorldState state, ExtractionDeposit deposit, ExtractionLayout.Cell cell) {
        if (!VIEWS.containsKey(deposit.site().id()) && VIEWS.size() >= ExtractionSiteState.MAX_WORK) VIEWS.clear();
        var view = VIEWS.computeIfAbsent(deposit.site().id(), ignored -> new ImmutableInputView<>());
        return view.get(List.of(state.bootstrap(), state.actorLocations(), deposit.site().layout(), deposit.cells(),
                deposit.geometry(), state.extractionSites().geologicalExclusions()), () -> read(state, deposit)).available(cell);
    }
    private static View read(FrontierWorldState state, ExtractionDeposit deposit) {
        var held = new HashSet<BlockPosition>();
        state.actorLocations().values().forEach(actor -> held.add(actor.supportingSurface().support()));
        deposit.site().layout().cells().stream().filter(source -> deposit.cells().get(source.id()).disposition()
                == ExtractionDeposit.Disposition.PRESENT).forEach(source -> held.add(source.workstation().support()));
        var current = new ExtractionWorkingSurfaces(state, deposit).current();
        return new View(Set.copyOf(held), current, Set.copyOf(deposit.site().layout().accessSurfaces()));
    }
}
