package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable release of one completed HOT resource-layout epoch before COLD custody can resume. */
public record FungibleStackBindingsReleased(SubjectId accountId, long authorityEpoch) implements FrontierPayload {
    public FungibleStackBindingsReleased {
        Objects.requireNonNull(accountId, "released fungible account");
        if (authorityEpoch < 1) throw new IllegalArgumentException("released fungible authority epoch must be positive");
    }

    @Override public String type() { return "frontier.fungible_stack_bindings_released"; }
}
