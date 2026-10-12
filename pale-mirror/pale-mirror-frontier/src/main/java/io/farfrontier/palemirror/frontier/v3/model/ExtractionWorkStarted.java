package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import java.util.Objects;
import java.util.Optional;
/** Selected admission proof; applying it neither discovers nor ranks a different work offer. */
public record ExtractionWorkStarted(ExtractionWork work, long expectedOrdinal,
        Optional<PedestrianRouteReceipt> toolRoute, Optional<PedestrianRouteReceipt> sourceRoute,
        Optional<PedestrianRouteReceipt> storageRoute) implements FrontierPayload {
    public ExtractionWorkStarted {
        Objects.requireNonNull(work); Objects.requireNonNull(toolRoute);
        Objects.requireNonNull(sourceRoute); Objects.requireNonNull(storageRoute);
        if (expectedOrdinal < 1) throw new IllegalArgumentException("invalid mining admission ordinal");
    }
    @Override public String type() { return "frontier.extraction_work_started"; }
}
