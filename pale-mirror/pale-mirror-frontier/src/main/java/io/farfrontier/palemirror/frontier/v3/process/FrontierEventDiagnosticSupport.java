package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncidentContext;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncidentExtractor;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.KernelQuarantineObserved;

import java.util.stream.Collectors;

/** Retains diagnostic incidents without making the ordered reducer own incident formatting. */
final class FrontierEventDiagnosticSupport {
    private FrontierEventDiagnosticSupport() { }

    static FrontierWorldState reduceKernelQuarantine(FrontierWorldState state, FrontierEvent event) {
        var tuple = ((KernelQuarantineObserved) event.payload()).diagnostic();
        return state.withDiagnosticIncidents(state.diagnosticIncidents().retain(tuple, event.id().value(),
                causes(event), event.revision().value(), event.instant().ticks(), DiagnosticIncidentContext.capture(state, event)));
    }

    static FrontierWorldState retainIncident(FrontierWorldState reduced, FrontierEvent event) {
        return DiagnosticIncidentExtractor.tuple(event.payload()).map(tuple -> reduced.withDiagnosticIncidents(
                reduced.diagnosticIncidents().retain(DiagnosticIncidentExtractor.incidentId(event.payload(), reduced, tuple), tuple,
                        event.id().value(), causes(event), event.revision().value(), event.instant().ticks(),
                        DiagnosticIncidentContext.capture(reduced, event)))).orElse(reduced);
    }

    private static String causes(FrontierEvent event) {
        return event.causes().commands().stream().map(command -> command.value()).collect(Collectors.joining("->"));
    }
}
