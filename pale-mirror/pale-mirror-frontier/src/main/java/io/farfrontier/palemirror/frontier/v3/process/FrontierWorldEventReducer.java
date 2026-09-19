package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncidentExtractor;

/** Ordered reducer facade; type-specific policy belongs to the registered owning module. */
public final class FrontierWorldEventReducer {
    private FrontierWorldEventReducer() { }

    public static FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event,
                                            DeterministicProcessRegistry processRegistry) {
        String processId = processRegistry.requireReducedEventOwner(event.payload().type());
        return FrontierWorldState.duringReducerTransition(() -> {
            FrontierWorldState reduced = FrontierWorldProcessCatalog.reduce(processId, state, event);
            return DiagnosticIncidentExtractor.tuple(event.payload()).map(tuple -> reduced.withDiagnosticIncidents(
                    reduced.diagnosticIncidents().retain(DiagnosticIncidentExtractor.incidentId(event.payload(), reduced, tuple), tuple, event.id().value(),
                            event.causes().commands().stream().map(command -> command.value()).collect(java.util.stream.Collectors.joining("->")),
                            event.revision().value(), event.instant().ticks()))).orElse(reduced);
        });
    }
}
