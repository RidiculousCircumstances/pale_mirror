package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure invariant helpers shared by the canonical frontier world state. */
public final class FrontierWorldStateSupport {
    private FrontierWorldStateSupport() { }

    static Set<SubjectId> bioformIds(FrontierBootstrap bootstrap) {
        Set<SubjectId> ids = new HashSet<>();
        bootstrap.hive().bioforms().forEach(bioform -> ids.add(bioform.id()));
        return ids;
    }

    public static Bioform bioform(FrontierBootstrap bootstrap, HiveColony colony, SubjectId id) {
        return java.util.stream.Stream.concat(bootstrap.hive().bioforms().stream(), colony.spawnedBioforms().values().stream())
                .filter(bioform -> bioform.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("unknown hive bioform"));
    }

    static Set<SubjectId> structureIds(FrontierBootstrap bootstrap) {
        Set<SubjectId> ids = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().forEach(structure -> ids.add(structure.id())));
        return ids;
    }

    public static Settlement settlement(FrontierBootstrap bootstrap, SubjectId settlementId) {
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

    public static SubjectId structureSettlement(FrontierBootstrap bootstrap, SubjectId structureId) {
        return bootstrap.settlements().stream().filter(settlement -> settlement.structures().stream()
                        .anyMatch(structure -> structure.id().equals(structureId))).map(Settlement::id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown settlement structure: " + structureId.value()));
    }

    public static boolean isHiveOrgan(FrontierBootstrap bootstrap, HiveColony colony, SubjectId ownerId) {
        return bootstrap.hive().organs().stream().anyMatch(organ -> organ.id().equals(ownerId)) || colony.addedOrgans().containsKey(ownerId);
    }

    public static SubjectId actorOwner(FrontierWorldState state, SubjectId actorId) {
        ActorLocation declaration = state.actorLocations().get(actorId);
        if (declaration == null) return null;
        // The stored nominal kind selects the owner schema. Membership only validates it.
        return switch (declaration.kind()) {
            case RESIDENT -> resident(state, actorId).settlementId();
            case BIOFORM -> bioform(state.bootstrap(), state.hiveColony(), actorId).hiveId();
            case PACK_ANIMAL -> state.transportFleet().require(actorId).homeSettlementId();
        };
    }

    static ResidentProfile resident(FrontierWorldState state, SubjectId residentId) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null) throw new IllegalArgumentException("unknown canonical resident: " + residentId.value());
        return resident;
    }

    public static Optional<ResidentProfile> availableWorkResident(FrontierWorldState state, SubjectId settlementId, HumanCapability capability) {
        return SettlementWorkforce.candidates(state, settlementId, capability).stream().findFirst();
    }

    public static Optional<ResidentProfile> availableRouteResident(FrontierWorldState state, SubjectId settlementId, HumanCapability capability) {
        return availableRouteResidents(state, settlementId, capability).stream().findFirst();
    }

    /** Deterministic exact candidates for one route owner; callers choose a bounded named formation. */
    public static List<ResidentProfile> availableRouteResidents(FrontierWorldState state, SubjectId settlementId, HumanCapability capability) {
        return SettlementWorkforce.candidates(state, settlementId, capability);
    }

    public static Optional<ResidentProfile> availableFieldResident(FrontierWorldState state, SubjectId settlementId, HumanCapability capability) {
        return SettlementWorkforce.candidates(state, settlementId, capability).stream().findFirst();
    }

    public static Optional<ResidentProfile> availableWorkResident(FrontierWorldState state, SubjectId settlementId,
                                                                 ResidentWorkKind work, HumanCapability capability) {
        return SettlementWorkforce.candidates(state, settlementId, work, capability).stream().findFirst();
    }

    /** Hunger requests a feasible meal; only actual loss of life removes work capability. */
    public static boolean workCapable(FrontierWorldState state, ResidentProfile resident) {
        return state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE;
    }

    /** New owners cannot borrow a person during retained self-care; existing work keeps its claim. */
    public static boolean availableForNewAssignment(FrontierWorldState state, ResidentProfile resident) {
        return SettlementWorkforce.availableForNewAssignment(state, resident);
    }


    public static boolean activeEmploymentClaim(FrontierWorldState state, SubjectId residentId) {
        return state.companies().employmentContracts().values().stream().anyMatch(contract -> contract.residentId().equals(residentId)
                && contract.status() == EmploymentContractStatus.ACTIVE);
    }

    public static boolean activePatrolClaim(FrontierWorldState state, SubjectId residentId) {
        return state.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.active()
                && patrol.memberIds().contains(residentId));
    }

    public static void requirePosition(WorldBounds bounds, BlockPosition position) {
        if (!bounds.contains(position)) throw new IllegalArgumentException("canonical state position is outside frontier bounds");
    }

    public static SubjectId itemOwner(FrontierWorldState state, ExactItemCustodyChanged changed) {
        ExactItemStack item = state.inventory().items().get(changed.itemId());
        if (item == null || !item.custody().equals(changed.from())) throw new IllegalArgumentException("item custody observation has no matching exact item");
        return item.economicOwnerId();
    }

    public static SubjectId itemOwner(FrontierWorldState state, ExactItemDestroyed destroyed) {
        ExactItemStack item = state.inventory().items().get(destroyed.itemId());
        if (item == null || !item.custody().equals(destroyed.source())) throw new IllegalArgumentException("destroyed item has no matching exact item");
        return item.economicOwnerId();
    }

    static void validateActorItemCustody(WorldId worldId, Map<SubjectId, ActorLocation> locations,
                                         ExactInventory inventory, FencedRecoveryState recovery) {
        var actors = locations.keySet();
        if (inventory.items().values().stream().anyMatch(item -> item.custody() instanceof InventoryCustody.Actor actor
                && !actors.contains(actor.actorId()))) throw new IllegalArgumentException("actor-held item must retain one canonical actor");
        for (CustodyAccount account : inventory.fungibleResources().accounts().values()) {
            if (account.custody() instanceof ResourceCustody.Actor actor && !actors.contains(actor.actorId())) {
                throw new IllegalArgumentException("actor-held resource must retain one canonical actor");
            }
        }
        for (PhysicalStackBinding binding : inventory.fungibleResources().bindings().values()) {
            if (binding.address() instanceof PhysicalStackAddress.ActorStack hand
                    && (!actors.contains(hand.actorId())
                    || !hand.entityId().equals(SceneLease.deterministicEntityId(worldId, hand.actorId())))) {
                throw new IllegalArgumentException("actor-hand resource binding has a foreign actor or body");
            }
            if (binding.address() instanceof PhysicalStackAddress.ActorStack hand) {
                var id = io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(hand.actorId());
                var body = recovery.current().get(id);
                var retired = recovery.tombstones().get(id);
                if (locations.get(hand.actorId()).condition().status() == ActorLifeStatus.ALIVE) {
                    if (body == null || body.asset() != FencedRecoveryAsset.BODY || body.ownerRevision() != 0L
                            || !body.ownerId().equals(hand.actorId())
                            || body.phase() != FencedRecoveryPhase.RUNNING && body.phase() != FencedRecoveryPhase.AMBIGUOUS)
                        throw new IllegalArgumentException("actor resource binding lacks its independently admitted body");
                } else if (body != null || retired == null || retired.asset() != FencedRecoveryAsset.BODY
                        || retired.ownerRevision() != 0L || !retired.ownerId().equals(hand.actorId())
                        || retired.disposition() != FencedRecoveryDisposition.REJECT_STALE) {
                    throw new IllegalArgumentException("deceased actor resource binding lacks its retained body retirement");
                }
                // Retained bindings are last resource-layout evidence, not actuation
                // authority or a declaration that a fatality consumed/dropped the stack.
            }
        }
    }

    static void validateEconomicClaims(FrontierBootstrap bootstrap, ExactInventory inventory) {
        Set<SubjectId> owners = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> owners.add(settlement.id()));
        owners.add(bootstrap.hive().id()); owners.add(FrontierRouteNetwork.OWNER);
        if (inventory.items().values().stream().anyMatch(item -> !owners.contains(item.economicOwnerId()))) throw new IllegalArgumentException("item claim owner must be a canonical frontier economy actor");
    }

    static void validateSettlementPolicies(FrontierBootstrap bootstrap, HumanPopulation population) {
        Set<SubjectId> expected = bootstrap.settlements().stream().map(Settlement::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!population.quarantines().keySet().equals(expected)) {
            throw new IllegalArgumentException("settlement quarantine index must own every and only canonical settlement");
        }
        if (population.schedules().values().stream().anyMatch(schedule -> schedule.dayTicks() != bootstrap.ruleset().residentLife().dayTicks())) {
            throw new IllegalArgumentException("settlement schedules must use the world's canonical calendar day");
        }
        var rules = bootstrap.ruleset().residentLife();
        population.nutrition().forEach((resident, need) -> {
            var condition = population.health(resident).starvation();
            if (condition.exposureRemainder() >= rules.starvation().gainTicksPerUnit()
                    || condition.recoveryRemainder() >= rules.starvation().recoveryTicksPerUnit())
                throw new IllegalArgumentException("starvation remainder contradicts world rules");
            if (!need.equals(need.accrueThrough(need.lastEvaluatedTick(), rules,
                    population.resident(resident).characteristics().effectiveMetabolismPermille(need.lastEvaluatedTick()))))
                throw new IllegalArgumentException("nutrition category or empty-stomach remainder contradicts world rules");
        });
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

    public static FrontierInfectionFrontier infectionFrontier(
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
