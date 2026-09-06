package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Atomically closes one arrived exact travel segment and advances its strategic route cursor. */
public record OperationTravelSegmentCompleted(SubjectId operationId) implements FrontierPayload {
    public OperationTravelSegmentCompleted { operationId = Objects.requireNonNull(operationId, "operation travel operation id"); }
    @Override public String type() { return "frontier.operation_travel_segment_completed"; }
}
