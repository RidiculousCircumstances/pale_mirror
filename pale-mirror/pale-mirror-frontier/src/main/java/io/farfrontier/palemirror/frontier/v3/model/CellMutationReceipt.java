package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

/** Canonical receipt must be durable before its physical witness can change or retire. */
public interface CellMutationReceipt extends FrontierPayload {
    CellMutationKey mutationKey();
    String mutationCause();
    @Override default boolean requiresDurableBeforeEffect() { return true; }
}
