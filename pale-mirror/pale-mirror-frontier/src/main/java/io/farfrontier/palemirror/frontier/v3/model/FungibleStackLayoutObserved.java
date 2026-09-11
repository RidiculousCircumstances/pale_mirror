package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** One fenced trusted physical-layout observation; it carries no permanent Vanilla stack identity. */
public record FungibleStackLayoutObserved(SubjectId accountId, long authorityEpoch,
                                          List<FungiblePhysicalObservation.Stack> stacks) implements FrontierPayload {
    public FungibleStackLayoutObserved {
        Objects.requireNonNull(accountId, "resource account");
        if (authorityEpoch < 1) throw new IllegalArgumentException("resource observation authority epoch must be positive");
        stacks = List.copyOf(stacks);
        if (stacks.isEmpty() || stacks.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != stacks.size()) {
            throw new IllegalArgumentException("resource observation must retain distinct current stacks");
        }
    }
    @Override public String type() { return "frontier.fungible_stack_layout_observed"; }
}
