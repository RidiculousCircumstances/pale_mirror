package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Recognizes a quantity-conserving move between declared sections in one witnessed container edit.
 * The ordinary physical handoff/claim-loss owners settle it; it is neither a gift nor destruction. */
public final class ContainerCompartmentTransfer {
    public static Optional<FungibleResourceHandoffObserved> propose(FrontierWorldState state, ContainerPlayerEditObserved.Member member) {
        if (member.accounts().size() < 2) return Optional.empty();
        var deltas = new TreeMap<ContainerSectionId, Map<String, Integer>>();
        for (var section : member.accounts().keySet()) {
            var holder = new ResourceCustody.Container(member.containerId(), section);
            var delta = new TreeMap<String, Integer>();
            ContainerResourceAccounts.observed(state.inventory(), holder, member.before()).forEach(stack -> delta.merge(stack.itemKind(), -stack.quantity(), Math::addExact));
            ContainerResourceAccounts.observed(state.inventory(), holder, member.after()).forEach(stack -> delta.merge(stack.itemKind(), stack.quantity(), Math::addExact));
            delta.values().removeIf(quantity -> quantity == 0);
            if (!delta.isEmpty()) deltas.put(section, delta);
        }
        if (deltas.size() != 2 || deltas.values().stream().anyMatch(delta -> delta.size() != 1)) return Optional.empty();
        var sourceSection = deltas.entrySet().stream().filter(entry -> entry.getValue().values().iterator().next() < 0).map(Map.Entry::getKey).findFirst();
        if (sourceSection.isEmpty()) return Optional.empty();
        var destinationSection = deltas.keySet().stream().filter(section -> !section.equals(sourceSection.orElseThrow())).findFirst().orElseThrow();
        var removed = deltas.get(sourceSection.orElseThrow()).entrySet().iterator().next();
        if (deltas.get(destinationSection).getOrDefault(removed.getKey(), 0) != -removed.getValue()) return Optional.empty();
        var sourceId = member.accounts().get(sourceSection.orElseThrow()); var destinationId = member.accounts().get(destinationSection);
        var ledger = state.inventory().fungibleResources(); var source = Objects.requireNonNull(ledger.accounts().get(sourceId));
        var losses = Map.of(removed.getKey(), -removed.getValue());
        var forfeited = SourceStockDepartureSelection.claimsNeededForExit(ledger, source, losses, SourceStockClaimLoss.admissibleClaims(state, sourceId));
        var cleared = forfeited.isEmpty() ? ledger : ledger.releaseClaims(forfeited);
        source = cleared.accounts().get(sourceId);
        var moved = SourceStockDepartureSelection.unclaimedDepartures(cleared, source, losses);
        var remainingLots = new TreeMap<>(source.lotQuantities());
        moved.forEach((lot, count) -> { int left = remainingLots.get(lot) - count; if (left == 0) remainingLots.remove(lot); else remainingLots.put(lot, left); });
        var remaining = remainingLots.isEmpty() ? null : source.withQuantities(remainingLots, source.claimQuantities());
        var holder = new ResourceCustody.Container(member.containerId(), destinationSection);
        var existing = cleared.accounts().get(destinationId);
        var arrived = new TreeMap<>(existing == null ? Map.<SubjectId, Integer>of() : existing.lotQuantities());
        moved.forEach((lot, count) -> arrived.merge(lot, count, Math::addExact));
        var destination = new CustodyAccount(destinationId, holder, arrived, existing == null ? Map.of() : existing.claimQuantities());
        var remainingImage = ContainerResourceAccounts.observed(state.inventory(), (ResourceCustody.Container) source.custody(), member.after());
        var destinationImage = ContainerResourceAccounts.observed(state.inventory(), holder, member.after());
        return Optional.of(new FungibleResourceHandoffObserved(sourceId, destination, member.epoch(), member.epoch(), moved, Map.of(),
                remainingLots.isEmpty() ? List.of() : FungiblePhysicalObservation.bindAccount(cleared, remaining, member.epoch(), remainingImage),
                FungiblePhysicalObservation.bindAccount(cleared, destination, member.epoch(), destinationImage), forfeited));
    }
    private ContainerCompartmentTransfer() { }
}
