package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Physical packing dependencies. Resource claims and title are not occupied space. */
final class ContainerCapacityChanges {
    private ContainerCapacityChanges() { }
    static Set<SubjectId> between(ExactInventory before, ExactInventory after) {
        var changed = new HashSet<SubjectId>();
        ImmutableMapChanges.forEach(before.containers(), after.containers(), change -> changed.add(change.key()));
        ImmutableMapChanges.forEach(before.occupiedSlots(), after.occupiedSlots(), change -> changed.add(change.key().containerId()));
        var old = before.fungibleResources(); var next = after.fungibleResources();
        if (old != next) {
            ImmutableMapChanges.forEach(old.accounts(), next.accounts(), change -> {
                var a = change.before(); var b = change.after();
                if (a != null && b != null && a.custody().equals(b.custody()) && a.lotQuantities().equals(b.lotQuantities())) return;
                account(changed, a); account(changed, b);
            });
            ImmutableMapChanges.forEach(old.bindings(), next.bindings(), change -> {
                var a = change.before(); var b = change.after();
                if (a != null && b != null && a.address().equals(b.address())) return;
                binding(changed, a); binding(changed, b);
            });
            var reclassified = new HashSet<SubjectId>();
            ImmutableMapChanges.forEach(old.lots(), next.lots(), change -> {
                if (change.before() != null && change.after() != null
                        && !change.before().itemKind().equals(change.after().itemKind())) reclassified.add(change.key());
            });
            if (!reclassified.isEmpty()) {
                for (var ledger : List.of(old, next)) for (var account : ledger.accounts().values())
                    if (!Collections.disjoint(account.lotQuantities().keySet(), reclassified)) account(changed, account);
            }
        }
        return Set.copyOf(changed);
    }
    private static void account(Set<SubjectId> changed, CustodyAccount account) {
        if (account != null && account.custody() instanceof ResourceCustody.Container holder) changed.add(holder.containerId());
    }
    private static void binding(Set<SubjectId> changed, PhysicalStackBinding binding) {
        if (binding != null && binding.address() instanceof PhysicalStackAddress.ContainerSlot slot) changed.add(slot.slot().containerId());
    }
}
