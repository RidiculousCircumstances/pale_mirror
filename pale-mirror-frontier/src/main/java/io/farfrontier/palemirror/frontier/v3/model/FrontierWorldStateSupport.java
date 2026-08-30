package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    static Optional<ResidentProfile> livingResident(FrontierWorldState state, SubjectId settlementId, ResidentRole role) {
        return state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId) && resident.role() == role)
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .sorted(byRoleSkill(role)).findFirst();
    }

    static Optional<ResidentProfile> availableRouteResident(FrontierWorldState state, SubjectId settlementId, ResidentRole role) {
        return state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId) && resident.role() == role)
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(resident -> !activeOperationClaim(state, resident.id()))
                .filter(resident -> !activePatrolClaim(state, resident.id()))
                .filter(resident -> !state.humanPopulation().migrations().containsKey(resident.id()))
                .sorted(byRoleSkill(role)).findFirst();
    }

    static Optional<ResidentProfile> availableFieldResident(FrontierWorldState state, SubjectId settlementId, ResidentRole role) {
        return state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId) && resident.role() == role)
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(resident -> state.operations().values().stream().noneMatch(operation -> retainsParticipantClaim(state, operation)
                        && operation.participantIds().contains(resident.id())))
                .filter(resident -> state.sceneLeases().values().stream().noneMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                        && lease.members().stream().anyMatch(member -> member.actorId().equals(resident.id()))))
                .sorted(byRoleSkill(role)).findFirst();
    }

    static boolean retainsParticipantClaim(FrontierWorldState state, RouteOperation operation) {
        return retainsParticipantClaim(state.contracts(), operation);
    }

    static boolean retainsParticipantClaim(Map<SubjectId, SupplyContract> contracts, RouteOperation operation) {
        return switch (operation.stage()) {
            case ASSEMBLING, EN_ROUTE, RETURNING -> true;
            // Delivery acknowledgement does not release people: the same exact residents still
            // own their return journey until the operation reaches its home route point.
            case ARRIVED -> true;
            case COMPLETED, FAILED, INTERRUPTED -> false;
        };
    }

    static boolean activeOperationClaim(FrontierWorldState state, SubjectId residentId) {
        return state.operations().values().stream().anyMatch(operation -> retainsParticipantClaim(state, operation)
                && operation.participantIds().contains(residentId));
    }

    static boolean activePatrolClaim(FrontierWorldState state, SubjectId residentId) {
        return state.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.status() == RoutePatrolStatus.EN_ROUTE
                && patrol.guardId().equals(residentId));
    }

    private static Comparator<ResidentProfile> byRoleSkill(ResidentRole role) {
        return Comparator.comparingInt((ResidentProfile resident) -> resident.skill(specialistSkill(role))).reversed()
                .thenComparing(ResidentProfile::id);
    }

    private static ResidentSkill specialistSkill(ResidentRole role) {
        return switch (role) {
            case FARMER -> ResidentSkill.AGRICULTURE;
            case BUILDER -> ResidentSkill.BUILDING;
            case CRAFTER -> ResidentSkill.CRAFTING;
            case GUARD -> ResidentSkill.SECURITY;
            case MEDIC -> ResidentSkill.MEDICINE;
            case HAULER -> ResidentSkill.LOGISTICS;
        };
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
        return immutableMap(input, label, null);
    }

    static Map<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> infectionMap(
            PersistentInfectionMap input, FrontierInfectionFrontier frontier
    ) {
        return new ValidatedImmutableMap<>(Collections.unmodifiableMap(input),
                new InfectionMetadata(input, Objects.requireNonNull(frontier, "infection frontier")));
    }

    static FrontierInfectionFrontier infectionFrontier(
            Map<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> infection, WorldBounds bounds
    ) {
        if (infection instanceof ValidatedImmutableMap<?, ?> marker && marker.attachment instanceof InfectionMetadata metadata) return metadata.frontier();
        return FrontierInfectionFrontier.compile(bounds, infection);
    }

    static PersistentInfectionMap persistentInfection(Map<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> infection) {
        if (infection instanceof ValidatedImmutableMap<?, ?> marker && marker.attachment instanceof InfectionMetadata metadata) return metadata.field();
        return PersistentInfectionMap.from(infection);
    }

    static Optional<FrontierInfectionFrontier.InfectionChange> infectionChange(
            Map<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> infection, WorldBounds bounds
    ) {
        return infectionFrontier(infection, bounds).change();
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> input, String label, Object attachment) {
        Objects.requireNonNull(input, label);
        if (input instanceof ValidatedImmutableMap<?, ?>) {
            @SuppressWarnings("unchecked") ValidatedImmutableMap<K, V> trusted = (ValidatedImmutableMap<K, V>) input;
            return attachment == null || attachment == trusted.attachment ? trusted : trusted.withAttachment(attachment);
        }
        // A world-state transition replaces one owned index but passes every
        // untouched index back through this constructor.  Map.copyOf retains an
        // already immutable map.  Keep a private marker around that validated
        // copy so later strict audits can distinguish it from an arbitrary
        // externally supplied Map without repeating a full entry scan.
        input.forEach((key, value) -> {
            Objects.requireNonNull(key, label + " key");
            Objects.requireNonNull(value, label + " value");
        });
        return new ValidatedImmutableMap<>(Map.copyOf(input), attachment);
    }

    /** Immutable, validated map identity local to canonical state construction. */
    private static final class ValidatedImmutableMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> values;
        private final Object attachment;

        private ValidatedImmutableMap(Map<K, V> values, Object attachment) {
            this.values = values; this.attachment = attachment;
        }

        private ValidatedImmutableMap<K, V> withAttachment(Object nextAttachment) { return new ValidatedImmutableMap<>(values, nextAttachment); }

        @Override public Set<Entry<K, V>> entrySet() { return values.entrySet(); }
        @Override public V get(Object key) { return values.get(key); }
        @Override public boolean containsKey(Object key) { return values.containsKey(key); }
        @Override public boolean containsValue(Object value) { return values.containsValue(value); }
        @Override public int size() { return values.size(); }
        @Override public Set<K> keySet() { return values.keySet(); }
        @Override public Collection<V> values() { return values.values(); }
        @Override public boolean equals(Object other) { return values.equals(other); }
        @Override public int hashCode() { return values.hashCode(); }
        @Override public String toString() { return values.toString(); }
    }

    private record InfectionMetadata(PersistentInfectionMap field, FrontierInfectionFrontier frontier) { }
}
