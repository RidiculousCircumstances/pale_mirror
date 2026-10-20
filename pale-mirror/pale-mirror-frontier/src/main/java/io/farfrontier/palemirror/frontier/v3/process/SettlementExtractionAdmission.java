package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.*;

/** Settlement-wide discovery only. Accepted jobs retain their site and existing execution owner. */
final class SettlementExtractionAdmission implements ResidentWorkProvider<ExtractionWorkStarted> {
    private final Set<SubjectId> developing;
    SettlementExtractionAdmission(Set<SubjectId> developing) { this.developing = Set.copyOf(developing); }
    @Override public ResidentWorkKind kind() { return ResidentWorkKind.EXTRACTION; }
    @Override public HumanCapability capability() { return HumanCapability.EXTRACTION; }

    @Override public Optional<ResidentWorkOffer<ExtractionWorkStarted>> discover(
            FrontierWorldState state, ResidentProfile resident, long tick) {
        var candidates = ExtractionSiteSelection.ranked(state, resident.settlementId(), resident.id(), developing, Optional.empty(), tick);
        KnownPedestrianNavigation.RouteUnavailable waiting = null;
        for (var candidate : candidates) {
            try {
                var selected = ExtractionWorkAdmission.opportunity(state, resident, candidate, tick);
                if (selected.isPresent()) return Optional.of(ExtractionWorkAdmission.offer(state, resident,
                        candidate, selected.orElseThrow()));
            } catch (KnownPedestrianNavigation.RouteUnavailable pending) {
                // Keep the exact route wait, but do not let one inaccessible deposit hide another.
                waiting = pending;
            }
        }
        if (waiting != null) throw waiting;
        return Optional.empty();
    }
}
