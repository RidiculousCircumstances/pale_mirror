package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncidentExtractor;
import io.farfrontier.palemirror.frontier.v3.model.KernelQuarantineObserved;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncidentContext;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticProducerBoundary;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestTrace;

/** Ordered reducer facade; type-specific policy belongs to the registered owning module. */
public final class FrontierWorldEventReducer {
    private FrontierWorldEventReducer() { }

    public static FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event,
                                            DeterministicProcessRegistry processRegistry) {
        return FrontierWorldState.duringReducerTransition(() -> {
            DiagnosticProducerBoundary.requireAdmitted(event.payload());
            if (event.payload() instanceof KernelQuarantineObserved) {
                var tuple = ((KernelQuarantineObserved) event.payload()).diagnostic();
                return state.withDiagnosticIncidents(state.diagnosticIncidents().retain(tuple, event.id().value(),
                        event.causes().commands().stream().map(command -> command.value()).collect(java.util.stream.Collectors.joining("->")),
                        event.revision().value(), event.instant().ticks(), DiagnosticIncidentContext.capture(state, event)));
            }
            String processId = processRegistry.requireReducedEventOwner(event.payload().type());
            FrontierWorldState reduced = ResourceSiteHarvestTrace.retain(FrontierWorldProcessCatalog.reduce(processId, state, event), event);
            return DiagnosticIncidentExtractor.tuple(event.payload()).map(tuple -> reduced.withDiagnosticIncidents(
                    reduced.diagnosticIncidents().retain(DiagnosticIncidentExtractor.incidentId(event.payload(), reduced, tuple), tuple, event.id().value(),
                            event.causes().commands().stream().map(command -> command.value()).collect(java.util.stream.Collectors.joining("->")),
                            event.revision().value(), event.instant().ticks(), DiagnosticIncidentContext.capture(reduced, event)))).orElse(reduced);
        });
    }
}
