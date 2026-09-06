package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Durable fact that detaches one fully settled logistics graph into bounded historical evidence. */
public record TerminalLogisticsCompacted(SubjectId operationId) implements FrontierPayload {
    public TerminalLogisticsCompacted { operationId = Objects.requireNonNull(operationId, "terminal operation id"); }
    @Override public String type() { return "frontier.terminal_logistics_compacted"; }
}
