package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Pure classification of one explicitly witnessed player edit to a declared container. This is not a
 * polling inference: the caller must own a pre-effect interaction fence and supply the exact
 * post-effect chest layout before publishing the returned fact.
 */
public final class FungibleContainerPlayerEdit {
    private FungibleContainerPlayerEdit() { }

    public static FrontierPayload classify(FungibleResourceLedger ledger, SubjectId accountId,
                                           SubjectId containerId, SubjectId settlementId,
                                           long epoch, UUID playerId, UUID interactionId,
                                           List<FungiblePhysicalObservation.Stack> observed,
                                           Set<SubjectId> withdrawableClaims) {
        Objects.requireNonNull(ledger, "depot resource ledger");
        Objects.requireNonNull(playerId, "depot player");
        Objects.requireNonNull(interactionId, "depot interaction");
        Objects.requireNonNull(observed, "depot observed layout");
        Objects.requireNonNull(withdrawableClaims, "owner-authorized claim losses");
        CustodyAccount account = ledger.accounts().get(accountId);
        if (account != null && !account.custody().equals(new ResourceCustody.Container(containerId))
                || account == null && (!accountId.equals(ReferenceContainerCustody.scopeId(containerId))
                    || ledger.accounts().values().stream().anyMatch(other ->
                            other.custody().equals(new ResourceCustody.Container(containerId))))) {
            throw new IllegalArgumentException("player edit lacks the exact depot account");
        }
        List<PhysicalStackBinding> current = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(accountId)).toList();
        if (epoch < 1 || account != null && current.isEmpty()
                || current.stream().anyMatch(binding -> binding.authorityEpoch() != epoch)) {
            throw new IllegalArgumentException("player edit lacks the current HOT binding epoch");
        }
        if (observed.stream().anyMatch(stack -> !(stack.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                || !slot.slot().containerId().equals(containerId))) {
            throw new IllegalArgumentException("player edit observed a foreign physical address");
        }
        Map<String, Integer> before = account == null ? Map.of() : quantitiesByKind(ledger, account);
        Map<String, Integer> after = new HashMap<>();
        observed.forEach(stack -> after.merge(stack.itemKind(), stack.quantity(), Math::addExact));
        Map<String, Integer> losses = new HashMap<>();
        Map<String, Integer> gifts = new HashMap<>();
        for (String kind : java.util.stream.Stream.concat(before.keySet().stream(), after.keySet().stream()).distinct().toList()) {
            int delta = after.getOrDefault(kind, 0) - before.getOrDefault(kind, 0);
            if (delta < 0) losses.put(kind, -delta);
            if (delta > 0) gifts.put(kind, delta);
        }
        if (!losses.isEmpty() && !gifts.isEmpty()) {
            throw new IllegalArgumentException("mixed player swap requires one atomic debit/credit event");
        }
        if (losses.isEmpty() && gifts.isEmpty()) {
            if (account == null) throw new IllegalArgumentException("empty depot click has no stock change");
            FungiblePhysicalObservation.bind(ledger, accountId, epoch, observed);
            return new FungibleStackLayoutObserved(accountId, epoch, observed);
        }
        if (losses.isEmpty()) {
            if (gifts.size() != 1) throw new IllegalArgumentException("one player gift may add one resource kind");
            var gift = gifts.entrySet().iterator().next();
            ResourceLot lot = new ResourceLot(new SubjectId("lot:player-gift-" + interactionId.toString().replace("-", "")),
                    settlementId, gift.getKey(), gift.getValue(), "player-gift:" + interactionId, List.of());
            ledger.contributeObserved(accountId, containerId, epoch, lot, observed);
            return new FungibleStockContributionObserved(accountId, containerId, epoch, playerId, interactionId, lot, observed);
        }
        if (account == null) throw new IllegalArgumentException("empty depot cannot lose stock");
        Set<SubjectId> forfeited = SourceStockDepartureSelection.claimsNeededForExit(ledger, account, losses, withdrawableClaims);
        FungibleResourceLedger cleared = forfeited.isEmpty() ? ledger : ledger.releaseClaims(forfeited);
        Map<SubjectId, Integer> departed = SourceStockDepartureSelection.unclaimedDepartures(cleared, cleared.accounts().get(accountId), losses);
        cleared.departObserved(accountId, epoch, departed, observed);
        return new FungibleStockDepartureObserved(accountId, containerId, settlementId, epoch,
                playerId, interactionId, departed, forfeited, observed);
    }

    private static Map<String, Integer> quantitiesByKind(FungibleResourceLedger ledger, CustodyAccount account) {
        Map<String, Integer> totals = new HashMap<>();
        account.lotQuantities().forEach((id, quantity) -> {
            ResourceLot lot = ledger.lots().get(id);
            totals.merge(lot.itemKind(), quantity, Math::addExact);
        });
        return totals;
    }
}
