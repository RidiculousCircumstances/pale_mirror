package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts one trusted current Vanilla layout into transient bindings without retaining a stack
 * tag. A HOT split or merge only repartitions exact account lots and allocations.
 */
public final class FungiblePhysicalObservation {
    private FungiblePhysicalObservation() { }

    public record Stack(PhysicalStackAddress address, String itemKind, int quantity) {
        public Stack {
            Objects.requireNonNull(address, "observed stack address");
            if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
                throw new IllegalArgumentException("observed stack kind must be namespace:path");
            }
            if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("observed stack quantity must be 1..64");
        }
    }

    /** Builds an exact current binding layout for one account. Unknown/mixed/partial evidence fails closed. */
    public static List<PhysicalStackBinding> bind(FungibleResourceLedger ledger, SubjectId accountId, long authorityEpoch,
                                                  List<Stack> observed) {
        Objects.requireNonNull(ledger, "fungible resource ledger"); Objects.requireNonNull(accountId, "resource account");
        if (authorityEpoch < 1 || observed == null || observed.isEmpty()) throw new IllegalArgumentException("physical observation requires current non-empty evidence");
        CustodyAccount account = ledger.accounts().get(accountId);
        if (account == null) throw new IllegalArgumentException("physical observation has no known custody account");
        List<Stack> ordered = observed.stream().sorted(Comparator.comparing(stack -> stack.address().toString())).toList();
        if (ordered.stream().map(Stack::address).distinct().count() != ordered.size()) throw new IllegalArgumentException("physical observation duplicates one stack address");
        Map<String, List<Stack>> observedByKind = ordered.stream().collect(java.util.stream.Collectors.groupingBy(Stack::itemKind));
        Map<String, List<LotPart>> lotsByKind = parts(ledger, account.lotQuantities()).stream()
                .collect(java.util.stream.Collectors.groupingBy(LotPart::itemKind));
        if (!observedByKind.keySet().equals(lotsByKind.keySet())) throw new IllegalArgumentException("physical observation has an unknown or missing resource kind");
        Map<String, List<ClaimPart>> claimsByKind = claimParts(ledger, account.claimQuantities()).stream()
                .collect(java.util.stream.Collectors.groupingBy(ClaimPart::itemKind));
        List<PhysicalStackBinding> bindings = new ArrayList<>(); int ordinal = 0;
        for (String kind : observedByKind.keySet().stream().sorted().toList()) {
            List<Stack> stacks = observedByKind.get(kind); List<LotPart> lots = lotsByKind.get(kind); List<ClaimPart> claims = claimsByKind.getOrDefault(kind, List.of());
            int expected = lots.stream().mapToInt(LotPart::quantity).sum();
            if (stacks.stream().mapToInt(Stack::quantity).sum() != expected) throw new IllegalArgumentException("physical observation does not retain exact account quantity");
            int lotCursor = 0, claimCursor = 0, remainingLot = lots.getFirst().quantity(), remainingClaim = claims.isEmpty() ? 0 : claims.getFirst().quantity();
            for (Stack stack : stacks) {
                Map<SubjectId, Integer> lotQuantities = new HashMap<>(); Map<SubjectId, Integer> claimQuantities = new HashMap<>(); int remaining = stack.quantity();
                while (remaining > 0) {
                    LotPart part = lots.get(lotCursor); int used = Math.min(remaining, remainingLot); lotQuantities.merge(part.id(), used, Integer::sum);
                    remaining -= used; remainingLot -= used; if (remainingLot == 0 && ++lotCursor < lots.size()) remainingLot = lots.get(lotCursor).quantity();
                }
                int claimRemaining = stack.quantity();
                while (claimRemaining > 0 && claimCursor < claims.size()) {
                    ClaimPart part = claims.get(claimCursor); int used = Math.min(claimRemaining, remainingClaim); claimQuantities.merge(part.id(), used, Integer::sum);
                    claimRemaining -= used; remainingClaim -= used; if (remainingClaim == 0 && ++claimCursor < claims.size()) remainingClaim = claims.get(claimCursor).quantity();
                }
                SubjectId bindingId = new SubjectId("binding:" + accountId.value().replace(':', '-') + "-e" + authorityEpoch + "-s" + ordinal++);
                bindings.add(new PhysicalStackBinding(bindingId, accountId, stack.address(), authorityEpoch, kind, lotQuantities, claimQuantities));
            }
            if (lotCursor != lots.size() || claimCursor != claims.size()) throw new IllegalArgumentException("physical observation omitted exact resource evidence");
        }
        return List.copyOf(bindings);
    }

    private static List<LotPart> parts(FungibleResourceLedger ledger, Map<SubjectId, Integer> quantities) {
        return quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            ResourceLot lot = ledger.lots().get(entry.getKey());
            if (lot == null) throw new IllegalArgumentException("resource account has an unknown lot");
            return new LotPart(lot.id(), lot.itemKind(), entry.getValue());
        }).toList();
    }
    private static List<ClaimPart> claimParts(FungibleResourceLedger ledger, Map<SubjectId, Integer> quantities) {
        return quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            ClaimAllocation claim = ledger.claims().get(entry.getKey());
            if (claim == null) throw new IllegalArgumentException("resource account has an unknown claim");
            return new ClaimPart(claim.id(), claim.itemKind(), entry.getValue());
        }).toList();
    }
    private record LotPart(SubjectId id, String itemKind, int quantity) { }
    private record ClaimPart(SubjectId id, String itemKind, int quantity) { }
}
