package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Immutable exact-item custody query. Title and admission stay with their owners. */
final class ExactItemCustodyView {
    private static final ImmutableResultView<String, Map<SubjectId, List<ExactItemStack>>> ACTORS = new ImmutableResultView<>(4);
    static List<ExactItemStack> actorItems(Map<SubjectId, ExactItemStack> items, SubjectId actor) {
        Objects.requireNonNull(actor, "actor id");
        var view = ACTORS.get(List.of(items), "actor-custody", () -> {
            var result = new HashMap<SubjectId, List<ExactItemStack>>();
            for (var item : items.values()) if (item.custody() instanceof InventoryCustody.Actor custody)
                result.computeIfAbsent(custody.actorId(), ignored -> new ArrayList<>()).add(item);
            result.replaceAll((id, held) -> held.stream().sorted(Comparator.comparing(ExactItemStack::id)).toList());
            return ImmutableLookupMap.copyOf(result);
        });
        return view.getOrDefault(actor, List.of());
    }
    private ExactItemCustodyView() { }
}
