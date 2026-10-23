package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import java.util.*;

/** One family-owned continuation after either HOT/COLD depletion or external clearance. */
final class ExtractionDevelopmentContinuation {
    private ExtractionDevelopmentContinuation() { }
    static void requestAfterObservation(FrontierWorldState state, SubjectId site, List<ProposedEvent> events, long tick) {
        // Accept the physical fact independently of economic/geometric planning.
        // The existing detached settlement review owns development, staffing and
        // retained route waits. Reconsideration coalesces at its earliest deadline.
        var home = state.extractionSites().deposits().get(site).site().settlementId();
        events.add(new ProposedEvent(home, new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.ReconsiderationRequested(
                ExtractionContinuation.review(home, Math.addExact(tick, 1)))));
    }
    static FrontierWorldState append(FrontierWorldState state, SubjectId site, List<ProposedEvent> events, long tick) {
        if (!ExtractionWorkPlanning.demanded(state, state.extractionSites().deposits().get(site).site().settlementId(), tick)) return state;
        var opening = ExtractionDevelopment.proposal(state, site, tick);
        FrontierPayload event;
        FrontierWorldState after;
        if (opening.isPresent()) {
            event = opening.orElseThrow(); after = ExtractionDevelopment.apply(state, site, opening.orElseThrow(), tick);
        } else {
            var extension = ExtractionAreaPlanning.proposal(state, site, tick);
            if (extension.isEmpty()) return state;
            event = extension.orElseThrow(); after = ExtractionAreaPlanning.preview(state, site, extension.orElseThrow());
        }
        events.add(new ProposedEvent(site, event));
        for (var job : after.extractionSites().work().values().stream()
                .filter(job -> job.siteId().equals(site) && job.phase() == ExtractionWork.Phase.SELECT_SOURCE)
                .sorted(Comparator.comparing(ExtractionWork::id)).toList())
            events.add(ExtractionContinuation.wake(job.id(), tick));
        return after;
    }
}
