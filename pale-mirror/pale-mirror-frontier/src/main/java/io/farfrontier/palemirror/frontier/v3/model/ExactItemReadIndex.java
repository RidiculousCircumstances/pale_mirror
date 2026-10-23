package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Exact inventory-owned item index. Never supplies permission, availability or custody authority. */
final class ExactItemReadIndex {
    private record Key(SubjectId owner, String kind) { }
    private record View(Map<SubjectId, List<ExactItemStack>> owners, Map<Key, List<ExactItemStack>> kinds) { }
    private static final ImmutableInputView<View> VIEWS = new ImmutableInputView<>();
    static List<ExactItemStack> owned(ExactInventory inventory, SubjectId owner) {
        return view(inventory).owners().getOrDefault(owner, List.of());
    }
    static List<ExactItemStack> owned(ExactInventory inventory, SubjectId owner, String kind) {
        return view(inventory).kinds().getOrDefault(new Key(owner, kind), List.of());
    }
    private static View view(ExactInventory inventory) {
        return VIEWS.get(List.of(inventory.items()), () -> {
            var owners = new HashMap<SubjectId, List<ExactItemStack>>();
            var kinds = new HashMap<Key, List<ExactItemStack>>();
            for (var item : inventory.items().values()) {
                owners.computeIfAbsent(item.economicOwnerId(), ignored -> new ArrayList<>()).add(item);
                kinds.computeIfAbsent(new Key(item.economicOwnerId(), item.itemKind()), ignored -> new ArrayList<>()).add(item);
            }
            owners.replaceAll((key, items) -> List.copyOf(items));
            kinds.replaceAll((key, items) -> List.copyOf(items));
            return new View(Map.copyOf(owners), Map.copyOf(kinds));
        });
    }
    private ExactItemReadIndex() { }
}
