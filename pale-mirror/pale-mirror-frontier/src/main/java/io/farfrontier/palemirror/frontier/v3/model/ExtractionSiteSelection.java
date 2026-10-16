package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPriority;
import java.util.*;

/** Settlement-wide opportunity order, shared by first admission and safe relocation.
 * Distance is only a probe-order lower bound, never a reachability receipt. */
public final class ExtractionSiteSelection {
    private ExtractionSiteSelection() { }
    private record Candidate(SubjectId site, SupplyPriority priority, long approach) { }
    private static final Comparator<Candidate> ORDER = Comparator.comparing(Candidate::priority)
            .thenComparingLong(Candidate::approach).thenComparing(Candidate::site);

    public static List<SubjectId> ranked(FrontierWorldState state, SubjectId home, SubjectId actor,
            Set<SubjectId> excluded, Optional<SubjectId> completing) {
        var candidates = new ArrayList<Candidate>();
        var origin = state.actorLocations().get(actor).supportingSurface().support();
        for (var deposit : state.extractionSites().deposits().values()) {
            if (!deposit.site().settlementId().equals(home) || excluded.contains(deposit.site().id())) continue;
            var priorities = ExtractionPriorities.read(state, deposit, completing);
            Candidate best = null;
            for (var cell : deposit.available(state.extractionSites().reservedCells(deposit.site().id()))) {
                var priority = priorities.cells().get(cell.id());
                if (priority == null || priority.missing() == 0) continue;
                var target = cell.workstation().support();
                long distance = Math.abs((long) target.x() - origin.x()) + Math.abs((long) target.y() - origin.y())
                        + Math.abs((long) target.z() - origin.z());
                var candidate = new Candidate(deposit.site().id(), priority, distance);
                if (best == null || ORDER.compare(candidate, best) < 0) best = candidate;
            }
            if (best != null) candidates.add(best);
        }
        candidates.sort(ORDER);
        return candidates.stream().map(Candidate::site).toList();
    }
}
