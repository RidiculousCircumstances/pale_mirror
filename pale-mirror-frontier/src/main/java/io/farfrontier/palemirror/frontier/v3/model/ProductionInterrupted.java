package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * A deliberate pre-effect transfer of one exact civilian worker into defence of the
 * settlement named by the same fresh hive sighting. The reducer returns the held
 * input, releases the invoice hold and closes the matching work order atomically.
 */
public record ProductionInterrupted(
        SubjectId jobId, SubjectId workerId, SubjectId assaultTaskId, HiveSettlementKnowledge.Sighting sighting
) implements FrontierPayload {
    public ProductionInterrupted {
        Objects.requireNonNull(jobId, "production job");
        Objects.requireNonNull(workerId, "production worker");
        Objects.requireNonNull(assaultTaskId, "assault task");
        Objects.requireNonNull(sighting, "settlement sighting");
    }

    @Override public String type() { return "frontier.production_interrupted"; }
}
