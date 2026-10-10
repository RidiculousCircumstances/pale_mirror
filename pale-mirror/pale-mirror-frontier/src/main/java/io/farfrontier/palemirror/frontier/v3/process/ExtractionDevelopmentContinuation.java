package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import java.util.*;

/** One family-owned continuation after either HOT/COLD depletion or external clearance. */
final class ExtractionDevelopmentContinuation {
    private ExtractionDevelopmentContinuation() { }
    static FrontierWorldState append(FrontierWorldState state, SubjectId site, List<ProposedEvent> events, long tick) {
        var opening = ExtractionDevelopment.proposal(state, site);
        FrontierPayload event;
        FrontierWorldState after;
        if (opening.isPresent()) {
            event = opening.orElseThrow(); after = ExtractionDevelopment.apply(state, site, opening.orElseThrow());
        } else {
            var extension = ExtractionAreaPlanning.proposal(state, site);
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
