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

    static Set<SubjectId> bioformIds(FrontierBootstrap bootstrap) {
        Set<SubjectId> ids = new HashSet<>();
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

    static boolean isHiveOrgan(FrontierBootstrap bootstrap, HiveColony colony, SubjectId ownerId) {
        return bootstrap.hive().organs().stream().anyMatch(organ -> organ.id().equals(ownerId)) || colony.addedOrgans().containsKey(ownerId);
    }

    static SubjectId semanticOwner(FrontierBootstrap bootstrap, HiveColony colony, SubjectId ownerId) {
        if (ownerId.value().startsWith("structure:")) return structureSettlement(bootstrap, ownerId);
        if (isHiveOrgan(bootstrap, colony, ownerId)) return bootstrap.hive().id();
        if (FrontierRouteNetwork.OWNER.equals(ownerId)) return ownerId;
        throw new IllegalArgumentException("unknown repairable semantic owner: " + ownerId.value());
    }

    static SubjectId actorOwner(FrontierWorldState state, SubjectId actorId) {
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        if (resident != null) return resident.settlementId();
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .anyMatch(bioform -> bioform.id().equals(actorId)) ? state.bootstrap().hive().id() : null;
    }

    static ResidentProfile resident(FrontierWorldState state, SubjectId residentId) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null) throw new IllegalArgumentException("unknown canonical resident: " + residentId.value());
        return resident;
    }

    static Resident resident(Settlement settlement, SubjectId residentId) {
        return settlement.residents().stream().filter(value -> value.id().equals(residentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown production worker: " + residentId.value()));
    }

    static void requirePosition(WorldBounds bounds, BlockPosition position) {
        if (!bounds.contains(position)) throw new IllegalArgumentException("canonical state position is outside frontier bounds");
    }

    static SubjectId itemOwner(FrontierWorldState state, ExactItemCustodyChanged changed) {
        ExactItemStack item = state.inventory().items().get(changed.itemId());
        if (item == null || !item.custody().equals(changed.from())) throw new IllegalArgumentException("item custody observation has no matching exact item");
        return item.economicOwnerId();
    }

    static SubjectId itemOwner(FrontierWorldState state, ExactItemDestroyed destroyed) {
        ExactItemStack item = state.inventory().items().get(destroyed.itemId());
        if (item == null || !item.custody().equals(destroyed.source())) throw new IllegalArgumentException("destroyed item has no matching exact item");
        return item.economicOwnerId();
    }

    static void validateEconomicClaims(FrontierBootstrap bootstrap, ExactInventory inventory) {
        Set<SubjectId> owners = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> owners.add(settlement.id()));
        owners.add(bootstrap.hive().id()); owners.add(FrontierRouteNetwork.OWNER);
        if (inventory.items().values().stream().anyMatch(item -> !owners.contains(item.economicOwnerId()))) throw new IllegalArgumentException("item claim owner must be a canonical frontier economy actor");
    }

    static <K, V> Map<K, V> immutableMap(Map<K, V> input, String label) {
        Objects.requireNonNull(input, label);
        LinkedHashMap<K, V> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(Objects.requireNonNull(key, label + " key"), Objects.requireNonNull(value, label + " value")));
        return Map.copyOf(copy);
    }
}
