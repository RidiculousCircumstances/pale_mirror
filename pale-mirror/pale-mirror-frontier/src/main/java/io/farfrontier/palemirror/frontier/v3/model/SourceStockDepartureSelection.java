package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Source-neutral selection of exact debits and explicitly authorized allocation losses. */
final class SourceStockDepartureSelection {
    private SourceStockDepartureSelection() { }
    /** Physical accounting selects losses only from explicitly owner-authorized allocations. */
    static Set<SubjectId> claimsNeededForExit(FungibleResourceLedger ledger,
                                                            CustodyAccount account,
                                                            Map<String, Integer> losses, Set<SubjectId> withdrawableClaims) {
        Set<SubjectId> forfeited = new HashSet<>();
        for (var loss : losses.entrySet()) {
            int stock = account.lotQuantities().entrySet().stream()
                    .filter(entry -> ledger.lots().get(entry.getKey()).itemKind().equals(loss.getKey()))
                    .mapToInt(Map.Entry::getValue).sum();
            int held = account.claimQuantities().entrySet().stream()
                    .filter(entry -> ledger.claims().get(entry.getKey()).itemKind().equals(loss.getKey()))
                    .mapToInt(Map.Entry::getValue).sum();
            int need = loss.getValue() - (stock - held);
            if (need <= 0) continue;
            for (SubjectId id : account.claimQuantities().keySet().stream().sorted().toList()) {
                ClaimAllocation claim = ledger.claims().get(id);
                if (!claim.itemKind().equals(loss.getKey()) || !withdrawableClaims.contains(id))
                    continue;
                forfeited.add(id);
                need -= account.claimQuantities().get(id);
                if (need <= 0) break;
            }
            if (need > 0) throw new IllegalArgumentException("stock departure touches a claim without a source-loss replan");
        }
        return Set.copyOf(forfeited);
    }

    /** Selects physical stock exits only after any declared owner-local claim retirement. */
    static Map<SubjectId, Integer> unclaimedDepartures(FungibleResourceLedger ledger,
                                                                CustodyAccount account,
                                                                Map<String, Integer> losses) {
        Map<SubjectId, Integer> pinned = new HashMap<>();
        Map<String, Integer> generic = new HashMap<>();
        account.claimQuantities().forEach((id, quantity) -> {
            ClaimAllocation claim = ledger.claims().get(id);
            if (claim.lotQuantities().isEmpty()) generic.merge(claim.itemKind(), quantity, Math::addExact);
            else claim.lotQuantities().forEach((lot, held) -> pinned.merge(lot, held, Math::addExact));
        });
        Map<SubjectId, Integer> result = new HashMap<>();
        for (var loss : losses.entrySet()) {
            int remaining = loss.getValue();
            int stock = account.lotQuantities().entrySet().stream()
                    .filter(entry -> ledger.lots().get(entry.getKey()).itemKind().equals(loss.getKey()))
                    .mapToInt(Map.Entry::getValue).sum();
            int held = account.claimQuantities().entrySet().stream()
                    .filter(entry -> ledger.claims().get(entry.getKey()).itemKind().equals(loss.getKey()))
                    .mapToInt(Map.Entry::getValue).sum();
            if (remaining > stock - held) throw new IllegalArgumentException("stock departure touches reserved stock");
            for (var entry : account.lotQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if (!ledger.lots().get(entry.getKey()).itemKind().equals(loss.getKey())) continue;
                int available = entry.getValue() - pinned.getOrDefault(entry.getKey(), 0);
                int taken = Math.min(remaining, available);
                if (taken > 0) result.put(entry.getKey(), taken);
                remaining -= taken;
                if (remaining == 0) break;
            }
            if (remaining != 0 || generic.getOrDefault(loss.getKey(), 0) > stock - loss.getValue()
                    - pinned.entrySet().stream().filter(entry -> ledger.lots().get(entry.getKey()).itemKind().equals(loss.getKey()))
                    .mapToInt(Map.Entry::getValue).sum()) {
                throw new IllegalArgumentException("stock departure cannot retain its current claims");
            }
        }
        return Map.copyOf(result);
    }
}
