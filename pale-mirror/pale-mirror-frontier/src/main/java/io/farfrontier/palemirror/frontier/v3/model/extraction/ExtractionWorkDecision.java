package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.model.ShipmentEndpoint;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import java.util.Objects;
import java.util.Optional;

/** The family producer supplies an exhaustive selected successor, not instructions to rediscover it. */
public sealed interface ExtractionWorkDecision {
    ActorExecutionId execution();
    record Source(ActorExecutionId execution, SurfaceAnchor origin, ExtractionTarget target,
                  Optional<PedestrianRouteReceipt> route) implements ExtractionWorkDecision {
        public Source { requireExecution(execution); Objects.requireNonNull(origin); Objects.requireNonNull(target); Objects.requireNonNull(route); }
    }
    record Storage(ActorExecutionId execution, SurfaceAnchor origin, ShipmentEndpoint destination,
                   Optional<PedestrianRouteReceipt> route) implements ExtractionWorkDecision {
        public Storage { requireExecution(execution); Objects.requireNonNull(origin); Objects.requireNonNull(destination); Objects.requireNonNull(route); }
    }
    record Finished(ActorExecutionId execution) implements ExtractionWorkDecision {
        public Finished { requireExecution(execution); }
    }
    /** Settle a held part before another selection, without ending the site mandate. */
    record StorePart(ActorExecutionId execution) implements ExtractionWorkDecision {
        public StorePart { requireExecution(execution); }
    }
    private static void requireExecution(ActorExecutionId execution) {
        if (Objects.requireNonNull(execution).activityKind() != ActorActivityKind.EXTRACTION)
            throw new IllegalArgumentException("extraction choice has a foreign execution declaration");
    }
}
