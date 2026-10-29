package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Exact inventory-owned item index. Never supplies permission, availability or custody authority. */
final class ExactItemReadIndex {
    private sealed interface Scope permits Owner, Kind { }
    private record Owner(SubjectId id) implements Scope { }
    private record Kind(SubjectId owner, String kind) implements Scope { }
    private static final ImmutableGroupView<SubjectId, ExactItemStack, Scope> ITEMS = new ImmutableGroupView<>();
    private static final ImmutableGroupView<SubjectId, ExactItemStack, SubjectId> CONTAINERS = new ImmutableGroupView<>();
    static Map<SubjectId, List<ExactItemStack>> containers(ExactInventory inventory) {
        return CONTAINERS.groupedRows(CONTAINERS, inventory.items(), item ->
                item.custody() instanceof InventoryCustody.ContainerSlot slot ? Set.of(slot.containerId()) : Set.of());
    }
    static List<ExactItemStack> owned(ExactInventory inventory, SubjectId owner) {
        return rows(inventory, new Owner(owner));
    }
    static List<ExactItemStack> owned(ExactInventory inventory, SubjectId owner, String kind) {
        return rows(inventory, new Kind(owner, kind));
    }
    private static List<ExactItemStack> rows(ExactInventory inventory, Scope scope) {
        return ITEMS.rows(ExactItemReadIndex.class, inventory.items(), item -> Set.of(
                new Owner(item.economicOwnerId()), new Kind(item.economicOwnerId(), item.itemKind())), scope);
    }
    private ExactItemReadIndex() { }
}
