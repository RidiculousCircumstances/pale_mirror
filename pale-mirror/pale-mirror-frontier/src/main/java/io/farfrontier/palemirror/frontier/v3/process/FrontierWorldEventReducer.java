package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.KernelQuarantineObserved;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticProducerContract;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestTrace;

/** Ordered reducer facade; type-specific policy belongs to the registered owning module. */
public final class FrontierWorldEventReducer {
    private FrontierWorldEventReducer() { }

    public static FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event,
                                            DeterministicProcessRegistry processRegistry) {
        return FrontierWorldState.duringUnpublishedTransition(() -> {
            DiagnosticProducerContract.requireAdmitted(event.payload());
            if (event.payload() instanceof KernelQuarantineObserved) {
                return FrontierEventDiagnosticSupport.reduceKernelQuarantine(state, event);
            }
            String processId = processRegistry.requireReducedEventOwner(event.payload().type());
            FrontierWorldState reduced = ResourceSiteHarvestTrace.retain(FrontierWorldProcessCatalog.reduce(processId, state, event), event);
            return FrontierEventDiagnosticSupport.retainIncident(reduced, event);
        });
    }
}
