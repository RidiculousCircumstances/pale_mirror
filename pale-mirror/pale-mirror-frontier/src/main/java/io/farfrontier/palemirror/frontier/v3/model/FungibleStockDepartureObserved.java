package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One player-caused, physically witnessed exit from an exact owned container. */
public record FungibleStockDepartureObserved(SubjectId sourceAccountId, SubjectId containerId,
                                             SubjectId economicOwnerId, long authorityEpoch,
                                             UUID playerId, UUID interactionId,
                                             Map<SubjectId, Integer> departedLots,
                                             List<FungiblePhysicalObservation.Stack> remaining)
        implements FrontierPayload {
    public FungibleStockDepartureObserved {
        Objects.requireNonNull(sourceAccountId, "departure source account");
        Objects.requireNonNull(containerId, "departure container");
        Objects.requireNonNull(economicOwnerId, "departure economic owner");
        Objects.requireNonNull(playerId, "departure player");
        Objects.requireNonNull(interactionId, "departure interaction");
        departedLots = Map.copyOf(Objects.requireNonNull(departedLots, "departed lots"));
        remaining = List.copyOf(Objects.requireNonNull(remaining, "remaining physical stacks"));
        if (authorityEpoch < 1 || departedLots.isEmpty() || departedLots.size() > 64
                || departedLots.values().stream().anyMatch(q -> q == null || q < 1 || q > ResourceLot.MAX_QUANTITY)
                || remaining.size() > FungibleResourceLedger.MAX_BINDINGS
                || remaining.stream().anyMatch(stack -> !(stack.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                        || !slot.slot().containerId().equals(containerId))
                || remaining.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != remaining.size()) {
            throw new IllegalArgumentException("stock departure requires bounded current container evidence");
        }
    }

    @Override public String type() { return "frontier.fungible_stock_departure_observed"; }
}
