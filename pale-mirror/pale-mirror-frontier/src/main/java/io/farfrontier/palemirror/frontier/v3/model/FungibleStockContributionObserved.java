package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One witnessed player gift into an exact settlement-owned depot. */
public record FungibleStockContributionObserved(SubjectId accountId, SubjectId containerId,
                                                 long authorityEpoch, UUID playerId, UUID interactionId,
                                                 ResourceLot contribution,
                                                 List<FungiblePhysicalObservation.Stack> observed)
        implements FrontierPayload {
    public FungibleStockContributionObserved {
        Objects.requireNonNull(accountId, "contribution account");
        Objects.requireNonNull(containerId, "contribution container");
        Objects.requireNonNull(playerId, "contribution player");
        Objects.requireNonNull(interactionId, "contribution interaction");
        Objects.requireNonNull(contribution, "contribution lot");
        observed = List.copyOf(Objects.requireNonNull(observed, "contribution physical layout"));
        if (authorityEpoch < 1 || observed.isEmpty() || observed.size() > FungibleResourceLedger.MAX_BINDINGS
                || observed.stream().anyMatch(stack -> !(stack.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                        || !slot.slot().containerId().equals(containerId))
                || observed.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != observed.size()) {
            throw new IllegalArgumentException("stock contribution requires bounded current container evidence");
        }
    }

    @Override public String type() { return "frontier.fungible_stock_contribution_observed"; }
}
