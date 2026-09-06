package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** Ordered reducer facade; type-specific policy belongs to the registered owning module. */
public final class FrontierWorldEventReducer {
    private FrontierWorldEventReducer() { }

    public static FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event,
                                            DeterministicProcessRegistry processRegistry) {
        String processId = processRegistry.requireReducedEventOwner(event.payload().type());
        return FrontierWorldState.duringReducerTransition(() -> FrontierWorldProcessCatalog.reduce(processId, state, event));
    }
}
