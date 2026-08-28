package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable exact custody ledger. Any dangling, duplicate or mixed custody is rejected. */
public record ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                             Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems) {
    public ExactInventory {
        containers = Map.copyOf(containers); items = Map.copyOf(items); cargo = Map.copyOf(cargo);
        playerItems = playerItems.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        Map<InventoryCustody.ContainerSlot, SubjectId> slots = new HashMap<>();
        for (ExactItemStack item : items.values()) switch (item.custody()) {
            case InventoryCustody.ContainerSlot slot -> {
                ContainerRecord container = containers.get(slot.containerId());
                if (container == null || slot.slot() >= container.slotCount() || slots.put(slot, item.id()) != null) throw new IllegalArgumentException("invalid or duplicate container slot custody");
            }
            case InventoryCustody.Cargo batch -> {
                CargoBatch value = cargo.get(batch.cargoId());
                if (value == null || !value.itemIds().contains(item.id())) throw new IllegalArgumentException("dangling or conflicting cargo custody");
            }
            case InventoryCustody.Player player -> {
                List<SubjectId> value = playerItems.get(player.playerId());
                if (value == null || !value.contains(item.id())) throw new IllegalArgumentException("dangling or conflicting player custody");
            }
        };
        for (CargoBatch batch : cargo.values()) {
            for (SubjectId item : batch.itemIds()) require(items.get(item), new InventoryCustody.Cargo(batch.id()), "cargo reverse custody");
        }
        for (Map.Entry<UUID, List<SubjectId>> player : playerItems.entrySet()) {
            for (SubjectId item : player.getValue()) require(items.get(item), new InventoryCustody.Player(player.getKey()), "player reverse custody");
        }
    }
    public static ExactInventory empty() { return new ExactInventory(Map.of(), Map.of(), Map.of(), Map.of()); }
    private static void require(Object value, Object expected, String label) {
        if (value instanceof ExactItemStack item && item.custody().equals(expected)) return;
        throw new IllegalArgumentException("dangling or conflicting " + label);
    }
}
