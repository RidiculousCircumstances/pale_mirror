package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.Objects;

/** Release the exact dead worker's reservation only after its resource owners settle. */
public record ExtractionDeathSettled(ActorActuationId identity, long expectedRevision) implements FrontierPayload {
    public ExtractionDeathSettled {
        Objects.requireNonNull(identity);
        if (identity.execution().activityKind() != ActorActivityKind.EXTRACTION || expectedRevision < 1)
            throw new IllegalArgumentException("extraction death completion needs its exact work generation");
    }
    @Override public String type() { return "frontier.extraction_death_settled"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
