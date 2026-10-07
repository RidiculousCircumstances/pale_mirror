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
        List<PhysicalStackBinding> bindings = new ArrayList<>(); int ordinal = 0;
        for (String kind : observedByKind.keySet().stream().sorted().toList()) {
            List<Stack> stacks = observedByKind.get(kind); List<LotPart> lots = lotsByKind.get(kind);
            int expected = lots.stream().mapToInt(LotPart::quantity).sum();
            if (stacks.stream().mapToInt(Stack::quantity).sum() != expected) throw new IllegalArgumentException("physical observation does not retain exact account quantity");
            int lotCursor = 0, remainingLot = lots.getFirst().quantity();
            for (Stack stack : stacks) {
                Map<SubjectId, Integer> lotQuantities = new HashMap<>(); int remaining = stack.quantity();
                while (remaining > 0) {
                    LotPart part = lots.get(lotCursor); int used = Math.min(remaining, remainingLot); lotQuantities.merge(part.id(), used, Integer::sum);
                    remaining -= used; remainingLot -= used; if (remainingLot == 0 && ++lotCursor < lots.size()) remainingLot = lots.get(lotCursor).quantity();
                }
                SubjectId bindingId = new SubjectId("binding:" + accountId.value().replace(':', '-') + "-e" + authorityEpoch + "-s" + ordinal++);
                bindings.add(new PhysicalStackBinding(bindingId, accountId, stack.address(), authorityEpoch, kind, lotQuantities, Map.of()));
            }
            if (lotCursor != lots.size()) throw new IllegalArgumentException("physical observation omitted exact resource evidence");
        }
        return allocateClaims(ledger, accountId, bindings);
    }

    /** Extends pinned allocations without moving an already admitted physical source.
     * A genuinely new observed layout has no retained columns and allocates them afresh. */
    static List<PhysicalStackBinding> allocateClaims(FungibleResourceLedger ledger, SubjectId accountId,
                                                     List<PhysicalStackBinding> layout) {
        CustodyAccount account = ledger.accounts().get(accountId);
        if (account == null || layout.isEmpty()) throw new IllegalArgumentException("claim layout has no current account or stacks");
        List<PhysicalStackBinding> ordered = layout.stream().sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        List<Map<SubjectId, Integer>> allocated = new ArrayList<>();
        List<Map<SubjectId, Integer>> occupiedLots = new ArrayList<>();
        for (var ignored : ordered) { allocated.add(new HashMap<>()); occupiedLots.add(new HashMap<>()); }
        var retainedPins = new java.util.HashSet<SubjectId>();
        for (var claimId : account.claimQuantities().keySet().stream().sorted().toList()) {
            ClaimAllocation claim = ledger.claims().get(claimId);
            if (claim == null || claim.lotQuantities().isEmpty()) continue;
            int retained = ordered.stream().mapToInt(binding -> binding.claimQuantities().getOrDefault(claimId, 0)).sum();
            if (retained == 0) continue;
            if (retained != account.claimQuantities().get(claimId))
                throw new IllegalArgumentException("retained pinned claim has an incomplete physical allocation");
            Map<SubjectId, Integer> remaining = new HashMap<>(claim.lotQuantities());
            for (int i = 0; i < ordered.size(); i++) {
                PhysicalStackBinding binding = ordered.get(i);
                int count = binding.claimQuantities().getOrDefault(claimId, 0);
                if (count == 0) continue;
                int needed = count;
                for (var lotId : remaining.keySet().stream().sorted().toList()) {
                    int available = binding.lotQuantities().getOrDefault(lotId, 0)
                            - occupiedLots.get(i).getOrDefault(lotId, 0);
                    int take = Math.min(needed, Math.min(remaining.get(lotId), available));
                    if (take <= 0) continue;
                    occupiedLots.get(i).merge(lotId, take, Integer::sum);
                    remaining.put(lotId, remaining.get(lotId) - take);
                    needed -= take;
                }
                if (needed != 0) throw new IllegalArgumentException("retained pinned claim changed its physical lot source");
                allocated.get(i).put(claimId, count);
            }
            if (remaining.values().stream().anyMatch(quantity -> quantity != 0))
                throw new IllegalArgumentException("retained pinned claim omitted its exact lot portion");
            retainedPins.add(claimId);
        }
        for (var claimId : account.claimQuantities().keySet().stream().sorted().toList()) {
            ClaimAllocation claim = ledger.claims().get(claimId);
            if (claim == null || claim.lotQuantities().isEmpty() || retainedPins.contains(claimId)) continue;
            for (var portion : claim.lotQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                int remaining = portion.getValue();
                for (int i = 0; i < ordered.size() && remaining > 0; i++) {
                    PhysicalStackBinding binding = ordered.get(i);
                    int available = binding.lotQuantities().getOrDefault(portion.getKey(), 0)
                            - occupiedLots.get(i).getOrDefault(portion.getKey(), 0);
                    int take = Math.min(remaining, available);
                    if (take > 0) {
                        occupiedLots.get(i).merge(portion.getKey(), take, Integer::sum);
                        allocated.get(i).merge(claimId, take, Integer::sum);
                        remaining -= take;
                    }
                }
                if (remaining != 0) throw new IllegalArgumentException("pinned claim has no matching physical lot portion");
            }
        }
        for (var claimId : account.claimQuantities().keySet().stream().sorted().toList()) {
            ClaimAllocation claim = ledger.claims().get(claimId);
            if (!claim.lotQuantities().isEmpty()) continue;
            int remaining = account.claimQuantities().get(claimId);
            for (int i = 0; i < ordered.size() && remaining > 0; i++) {
                PhysicalStackBinding binding = ordered.get(i);
                int stock = binding.lotQuantities().entrySet().stream().filter(entry -> {
                    ResourceLot lot = ledger.lots().get(entry.getKey());
                    return lot.economicOwnerId().equals(claim.economicOwnerId()) && lot.itemKind().equals(claim.itemKind());
                }).mapToInt(Map.Entry::getValue).sum();
                int occupied = allocated.get(i).entrySet().stream().filter(entry -> {
                    ClaimAllocation other = ledger.claims().get(entry.getKey());
                    return other.economicOwnerId().equals(claim.economicOwnerId()) && other.itemKind().equals(claim.itemKind());
                }).mapToInt(Map.Entry::getValue).sum();
                int take = Math.min(remaining, stock - occupied);
                if (take > 0) { allocated.get(i).merge(claimId, take, Integer::sum); remaining -= take; }
            }
            if (remaining != 0) throw new IllegalArgumentException("generic claim has no compatible physical stock");
        }
        List<PhysicalStackBinding> result = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            PhysicalStackBinding binding = ordered.get(i);
            result.add(new PhysicalStackBinding(binding.id(), binding.accountId(), binding.address(), binding.authorityEpoch(),
                    binding.itemKind(), binding.lotQuantities(), allocated.get(i), binding.playerSaveFence()));
        }
        return List.copyOf(result);
    }

    private static List<LotPart> parts(FungibleResourceLedger ledger, Map<SubjectId, Integer> quantities) {
        return quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            ResourceLot lot = ledger.lots().get(entry.getKey());
            if (lot == null) throw new IllegalArgumentException("resource account has an unknown lot");
            return new LotPart(lot.id(), lot.itemKind(), entry.getValue());
        }).toList();
    }
    private record LotPart(SubjectId id, String itemKind, int quantity) { }
}
