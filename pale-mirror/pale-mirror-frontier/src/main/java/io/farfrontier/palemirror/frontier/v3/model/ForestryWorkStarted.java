package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import java.util.*;

/** Accepted concrete offer; replay validates retained facts without selecting another tree/tool/store. */
public record ForestryWorkStarted(ForestryWork work, TreeDescriptor declaration, long expectedOrdinal,
                                   Optional<PedestrianRouteReceipt> toolRoute,
                                   Optional<PedestrianRouteReceipt> sourceRoute,
                                   Optional<PedestrianRouteReceipt> storageRoute) implements FrontierPayload {
    public static final String TYPE = "frontier.forestry_work_started.v1";
    public ForestryWorkStarted {
        Objects.requireNonNull(work); Objects.requireNonNull(declaration); Objects.requireNonNull(toolRoute);
        Objects.requireNonNull(sourceRoute); Objects.requireNonNull(storageRoute);
        if (expectedOrdinal < 1 || work.phase() != ForestryWork.Phase.TAKE_TOOL
                || work.target().filter(target -> target.tree().matches(declaration)).isEmpty())
            throw new IllegalArgumentException("forestry admission lacks the selected exact tree");
    }
    @Override public String type() { return TYPE; }
}
