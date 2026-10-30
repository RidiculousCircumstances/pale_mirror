package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Objects;

/** Inventory-owned immutable reference facts. Wear/count/material changes are not custody changes. */
final class ExactInventoryItemFacts {
    record Row(SubjectId id, SubjectId economicOwnerId, InventoryCustody custody) { }
    // No new item admission limit: this view retains only the current owned registry.
    private static final ImmutableRegistryView<SubjectId, ExactItemStack, Row> ROWS =
            new ImmutableRegistryView<>(Integer.MAX_VALUE, Objects::equals);
    private static final ImmutableInputView<Map<InventoryCustody.ContainerSlot, SubjectId>> SLOTS = new ImmutableInputView<>();
    private ExactInventoryItemFacts() { }
    static Map<InventoryCustody.ContainerSlot, SubjectId> slots(Map<SubjectId, ExactItemStack> items) {
        var facts = rows(items);
        return SLOTS.get(List.of(facts), () -> {
            var result = new HashMap<InventoryCustody.ContainerSlot, SubjectId>();
            for (var item : facts.values()) if (item.custody() instanceof InventoryCustody.ContainerSlot slot)
                result.put(slot, item.id());
            return ImmutableLookupMap.copyOf(result);
        });
    }
    static Map<SubjectId, Row> rows(Map<SubjectId, ExactItemStack> items) {
        return ImmutableInvariantCheck.fullAudit()
                ? ROWS.project(ExactInventoryItemFacts.class, items, (before, after) -> false, ExactInventoryItemFacts::row)
                : ROWS.project(ExactInventoryItemFacts.class, items, ExactInventoryItemFacts::row);
    }
    private static Row row(SubjectId id, ExactItemStack item) {
        return new Row(item.id(), item.economicOwnerId(), item.custody());
    }
}
