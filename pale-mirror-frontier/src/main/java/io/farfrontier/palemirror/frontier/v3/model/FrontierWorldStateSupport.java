package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure invariant helpers shared by the canonical frontier world state. */
final class FrontierWorldStateSupport {
    private FrontierWorldStateSupport() { }

    static Set<SubjectId> actorIds(FrontierBootstrap bootstrap) {
        Set<SubjectId> ids = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> settlement.residents().forEach(resident -> ids.add(resident.id())));
        bootstrap.hive().bioforms().forEach(bioform -> ids.add(bioform.id()));
        return ids;
    }

    static Set<SubjectId> structureIds(FrontierBootstrap bootstrap) {
        Set<SubjectId> ids = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().forEach(structure -> ids.add(structure.id())));
        return ids;
    }

    static Settlement settlement(FrontierBootstrap bootstrap, SubjectId settlementId) {
        return bootstrap.settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown production settlement: " + settlementId.value()));
    }

    static SettlementStructure structure(Settlement settlement, SubjectId structureId) {
        return settlement.structures().stream().filter(value -> value.id().equals(structureId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown production facility: " + structureId.value()));
    }

    static SettlementStructure structureById(FrontierBootstrap bootstrap, SubjectId structureId) {
        return bootstrap.settlements().stream().flatMap(settlement -> settlement.structures().stream())
                .filter(structure -> structure.id().equals(structureId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown structure: " + structureId.value()));
    }

    static SubjectId structureSettlement(FrontierBootstrap bootstrap, SubjectId structureId) {
        return bootstrap.settlements().stream().filter(settlement -> settlement.structures().stream()
                        .anyMatch(structure -> structure.id().equals(structureId))).map(Settlement::id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown settlement structure: " + structureId.value()));
    }

    static Resident resident(Settlement settlement, SubjectId residentId) {
        return settlement.residents().stream().filter(value -> value.id().equals(residentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown production worker: " + residentId.value()));
    }

    static void requirePosition(WorldBounds bounds, BlockPosition position) {
        if (!bounds.contains(position)) throw new IllegalArgumentException("canonical state position is outside frontier bounds");
    }

    static SubjectId itemOwner(FrontierWorldState state, ExactItemCustodyChanged changed) {
        InventoryCustody.ContainerSlot slot = changed.from() instanceof InventoryCustody.ContainerSlot source ? source
                : (InventoryCustody.ContainerSlot) changed.to();
        ContainerRecord container = state.inventory().containers().get(slot.containerId());
        if (container == null) throw new IllegalArgumentException("item custody observation references an unknown container");
        return container.ownerId();
    }

    static <K, V> Map<K, V> immutableMap(Map<K, V> input, String label) {
        Objects.requireNonNull(input, label);
        LinkedHashMap<K, V> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(Objects.requireNonNull(key, label + " key"), Objects.requireNonNull(value, label + " value")));
        return Map.copyOf(copy);
    }
}
