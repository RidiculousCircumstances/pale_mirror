package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

/** Resolves an account portion against current HOT bindings, never against a saved slot. */
public final class MaterialSourceSelection {
    private MaterialSourceSelection() { }

    public record Slice(PhysicalStackAddress address, int before, int moved, long epoch) {
        public Slice {
            Objects.requireNonNull(address, "source address");
            if (before < 1 || before > 64 || moved < 1 || moved > before || epoch < 1)
                throw new IllegalArgumentException("invalid bounded physical material slice");
        }
    }

    /** Selects the process-declared lot portion, including an unclaimed subset of a larger account. */
    public static List<Slice> select(FungibleResourceLedger ledger, ActorContainerItemOrder order) {
        Objects.requireNonNull(ledger, "resource ledger");
        FungibleActorOrderTransfer.accounts(ledger, order);
        ActorContainerItemOrder.Portion.Fungible portion = (ActorContainerItemOrder.Portion.Fungible) order.portion();
        if (portion.claimId().isPresent() && portion.delegation().isEmpty())
            return select(ledger, portion.sourceAccountId(), portion.itemKind(), portion.quantity(), portion.claimId());
        List<PhysicalStackBinding> bindings = ledger.bindings().values().stream()
                .filter(value -> value.accountId().equals(portion.sourceAccountId()))
                .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        Map<SubjectId, Integer> remaining = new HashMap<>(portion.lotQuantities());
        List<Slice> slices = new ArrayList<>();
        for (PhysicalStackBinding binding : bindings) {
            if (!binding.itemKind().equals(portion.itemKind())) continue;
            int moved = 0;
            for (SubjectId lot : remaining.keySet().stream().sorted().toList()) {
                int take = Math.min(remaining.get(lot), binding.lotQuantities().getOrDefault(lot, 0));
                if (take > 0) {
                    remaining.put(lot, remaining.get(lot) - take);
                    moved += take;
                }
            }
            if (moved == 0) continue;
            slices.add(new Slice(binding.address(), binding.quantity(), moved, binding.authorityEpoch()));
        }
        if (remaining.values().stream().anyMatch(value -> value != 0)
                || slices.isEmpty() || slices.stream().map(Slice::epoch).distinct().count() != 1)
            throw new IllegalArgumentException("material source lacks the declared current lot portion and authority epoch");
        return List.copyOf(slices);
    }

    public static List<Slice> select(FungibleResourceLedger ledger, SubjectId accountId, String itemKind,
                                     int quantity, Optional<SubjectId> claimId) {
        Objects.requireNonNull(ledger, "resource ledger"); Objects.requireNonNull(accountId, "source account");
        Objects.requireNonNull(itemKind, "source item kind"); Objects.requireNonNull(claimId, "source claim");
        if (quantity < 1 || quantity > 64 || !ledger.accounts().containsKey(accountId))
            throw new IllegalArgumentException("material source needs a bounded current account portion");
        CustodyAccount account = ledger.accounts().get(accountId);
        claimId.ifPresent(id -> {
            ClaimAllocation claim = ledger.claims().get(id);
            if (claim == null || !itemKind.equals(claim.itemKind()) || claim.quantity() != quantity
                    || account.claimQuantities().getOrDefault(id, 0) != quantity)
                throw new IllegalArgumentException("material source has no current exact account claim");
        });
        List<PhysicalStackBinding> bindings = ledger.bindings().values().stream()
                .filter(value -> value.accountId().equals(accountId))
                .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        List<Slice> slices = new ArrayList<>(); int selected = 0;
        for (PhysicalStackBinding binding : bindings) {
            int moved = claimId.map(id -> binding.claimQuantities().getOrDefault(id, 0)).orElseGet(binding::quantity);
            if (moved == 0) continue;
            if (!binding.itemKind().equals(itemKind))
                throw new IllegalArgumentException("material source binding has a different item kind");
            slices.add(new Slice(binding.address(), binding.quantity(), moved, binding.authorityEpoch()));
            selected = Math.addExact(selected, moved);
        }
        if (selected != quantity || slices.isEmpty() || slices.stream().map(Slice::epoch).distinct().count() != 1)
            throw new IllegalArgumentException("material source lacks one complete portion and authority epoch");
        return List.copyOf(slices);
    }
}
