package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashMap;
import java.util.Map;

/** Structural custody/reservation/binding invariants, checked by the immutable ledger constructor.
 * No transitions, recipes or resource producers are owned here. */
final class FungibleResourceValidation {
    private FungibleResourceValidation() { }

    static void validateAccounts(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                                         Map<SubjectId, CustodyAccount> accounts) {
        Map<SubjectId, Integer> lotTotals = new HashMap<>(); Map<SubjectId, Integer> claimTotals = new HashMap<>();
        for (CustodyAccount account : accounts.values()) {
            account.lotQuantities().forEach((id, quantity) -> { if (!lots.containsKey(id)) throw new IllegalArgumentException("custody account references an unknown lot"); lotTotals.merge(id, quantity, Integer::sum); });
            account.claimQuantities().forEach((id, quantity) -> {
                ClaimAllocation claim = claims.get(id);
                if (claim == null) throw new IllegalArgumentException("custody account references an unknown claim");
                claimTotals.merge(id, quantity, Integer::sum);
                int compatible = account.lotQuantities().entrySet().stream().filter(entry -> {
                    ResourceLot lot = lots.get(entry.getKey());
                    return lot.economicOwnerId().equals(claim.economicOwnerId()) && lot.itemKind().equals(claim.itemKind());
                }).mapToInt(Map.Entry::getValue).sum();
                if (quantity > compatible) throw new IllegalArgumentException("claim allocation exceeds compatible account stock");
            });
        }
        lots.forEach((id, lot) -> { if (lotTotals.getOrDefault(id, 0) != lot.quantity()) throw new IllegalArgumentException("resource lot must have exact canonical custody"); });
        claims.forEach((id, claim) -> { if (claimTotals.getOrDefault(id, 0) != claim.quantity()) throw new IllegalArgumentException("claim allocation must have exact canonical custody"); });
        for (CustodyAccount account : accounts.values()) {
            Map<String, Integer> claimedByKind = new HashMap<>(); Map<String, Integer> stockByKind = new HashMap<>();
            account.claimQuantities().forEach((id, quantity) -> { ClaimAllocation claim = claims.get(id); claimedByKind.merge(claim.economicOwnerId().value() + "|" + claim.itemKind(), quantity, Integer::sum); });
            account.lotQuantities().forEach((id, quantity) -> { ResourceLot lot = lots.get(id); stockByKind.merge(lot.economicOwnerId().value() + "|" + lot.itemKind(), quantity, Integer::sum); });
            claimedByKind.forEach((key, quantity) -> { if (quantity > stockByKind.getOrDefault(key, 0)) throw new IllegalArgumentException("claim allocations exceed exact account stock"); });
            Map<SubjectId, Integer> pinnedByLot = new HashMap<>();
            account.claimQuantities().forEach((claimId, accountQuantity) -> {
                ClaimAllocation claim = claims.get(claimId);
                if (claim.lotQuantities().isEmpty()) return;
                if (accountQuantity != claim.quantity()) throw new IllegalArgumentException("pinned claim cannot split across custody accounts");
                claim.lotQuantities().forEach((lotId, quantity) -> {
                    ResourceLot lot = lots.get(lotId);
                    if (lot == null || !lot.economicOwnerId().equals(claim.economicOwnerId()) || !lot.itemKind().equals(claim.itemKind())) {
                        throw new IllegalArgumentException("pinned claim references a foreign lot");
                    }
                    pinnedByLot.merge(lotId, quantity, Integer::sum);
                });
            });
            pinnedByLot.forEach((lotId, quantity) -> {
                if (quantity > account.lotQuantities().getOrDefault(lotId, 0)) {
                    throw new IllegalArgumentException("pinned claims exceed their exact account lot stock");
                }
            });
        }
    }

    static void validateBindings(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                                         Map<SubjectId, CustodyAccount> accounts,
                                         Map<SubjectId, PhysicalStackBinding> bindings) {
        Map<PhysicalStackAddress, SubjectId> addresses = new HashMap<>(); Map<SubjectId, Map<SubjectId, Integer>> boundLots = new HashMap<>();
        Map<SubjectId, Map<SubjectId, Integer>> boundClaims = new HashMap<>();
        for (PhysicalStackBinding binding : bindings.values()) {
            CustodyAccount account = accounts.get(binding.accountId());
            if (account == null || addresses.put(binding.address(), binding.id()) != null || binding.quantity() > 64) throw new IllegalArgumentException("physical stack binding is not one unique current stack");
            if (!addressMatchesCustody(binding.address(), account.custody())) {
                throw new IllegalArgumentException("physical stack address disagrees with its canonical custody owner");
            }
            if (account.actorPresentation().isPresent()) {
                var declared = account.actorPresentation().orElseThrow();
                boolean matches = switch (binding.address()) {
                    case PhysicalStackAddress.ActorPocket pocket -> declared.equals(new ActorItemSlot.Pocket(pocket.slot()));
                    case PhysicalStackAddress.ActorHand hand -> declared.equals(new ActorItemSlot.Hand(hand.hand()));
                    default -> false;
                };
                if (!matches) throw new IllegalArgumentException("physical stack address differs from retained personal placement");
            }
            binding.lotQuantities().forEach((id, quantity) -> { ResourceLot lot = lots.get(id); if (lot == null || !lot.itemKind().equals(binding.itemKind())) throw new IllegalArgumentException("physical stack binding has incompatible lot evidence");
                boundLots.computeIfAbsent(account.id(), ignored -> new HashMap<>()).merge(id, quantity, Integer::sum); });
            binding.claimQuantities().forEach((id, quantity) -> {
                ClaimAllocation claim = claims.get(id);
                if (claim == null || !claim.itemKind().equals(binding.itemKind())) {
                    throw new IllegalArgumentException("physical stack binding has incompatible claim evidence");
                }
                boundClaims.computeIfAbsent(account.id(), ignored -> new HashMap<>()).merge(id, quantity, Integer::sum);
            });
            int boundClaimsAtStack = binding.claimQuantities().values().stream().mapToInt(Integer::intValue).sum();
            if (boundClaimsAtStack > binding.quantity()) {
                throw new IllegalArgumentException("physical stack binding claims exceed its exact stack quantity");
            }
        }
        boundLots.forEach((account, quantities) -> {
            if (!accounts.get(account).lotQuantities().equals(quantities)) {
                throw new IllegalArgumentException("physical stack bindings must cover their complete active account");
            }
            if (!accounts.get(account).claimQuantities().equals(boundClaims.getOrDefault(account, Map.of()))) {
                throw new IllegalArgumentException("physical stack bindings must cover their complete active claim allocation");
            }
        });
    }

    private static boolean addressMatchesCustody(PhysicalStackAddress address, ResourceCustody custody) {
        return switch (address) {
            case PhysicalStackAddress.ContainerSlot slot -> custody instanceof ResourceCustody.Container owner
                    && owner.containerId().equals(slot.slot().containerId());
            case PhysicalStackAddress.PlayerSlot slot -> custody instanceof ResourceCustody.Player owner
                    && owner.playerId().equals(slot.playerId());
            // The hopper carrier UUID is a separate physical admission claim;
            // its block position alone cannot establish that UUID here.
            case PhysicalStackAddress.HopperSlot ignored -> custody instanceof ResourceCustody.WorldCarrier;
            case PhysicalStackAddress.WorldEntity entity -> custody instanceof ResourceCustody.WorldCarrier owner
                    && owner.carrierId().equals(entity.entityId());
            case PhysicalStackAddress.ActorHand hand -> custody instanceof ResourceCustody.Actor owner
                    && owner.actorId().equals(hand.actorId());
            case PhysicalStackAddress.ActorPocket pocket -> custody instanceof ResourceCustody.Actor owner
                    && owner.actorId().equals(pocket.actorId());
        };
    }

}
