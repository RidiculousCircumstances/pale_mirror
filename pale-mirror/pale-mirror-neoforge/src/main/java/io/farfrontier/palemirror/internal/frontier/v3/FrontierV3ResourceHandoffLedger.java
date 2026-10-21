package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffObserved;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GroundStockExit;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Physical causal witnesses only. Quantities, title and claims remain in the domain ledger. */
final class FrontierV3ResourceHandoffLedger extends FrontierV3JournaledSavedData {
    private static final int MAX_PENDING = 1024;
    private record Pending(long sequence, FungibleResourceHandoffObserved receipt) {
        Pending { if (sequence < 1) throw new IllegalArgumentException("resource witness needs its causal sequence"); Objects.requireNonNull(receipt); }
    }
    private final Map<UUID, Pending> pending;
    private long nextSequence;
    record Pickup(SubjectId owner, SubjectId account, UUID entity, ActorActivityKind kind, long revision, String fingerprint) {
        Pickup {
            Objects.requireNonNull(owner); Objects.requireNonNull(account); Objects.requireNonNull(entity); Objects.requireNonNull(kind);
            if (revision < 1 || fingerprint == null || fingerprint.isBlank() || fingerprint.length() > 4096)
                throw new IllegalArgumentException("invalid ground pickup witness");
        }
        CompoundTag save() {
            var tag = new CompoundTag(); tag.putString("owner", owner.value()); tag.putString("account", account.value()); tag.putUUID("entity", entity);
            tag.putInt("kind", FrontierWireTags.tag(kind)); tag.putLong("revision", revision); tag.putString("fingerprint", fingerprint); return tag;
        }
        static Pickup read(CompoundTag tag) {
            return new Pickup(new SubjectId(tag.getString("owner")), new SubjectId(tag.getString("account")), tag.getUUID("entity"),
                    FrontierWireTags.require(ActorActivityKind.class, tag.getInt("kind")), tag.getLong("revision"), tag.getString("fingerprint"));
        }
    }
    private final Map<SubjectId, Pickup> actorPickups;
    private final Map<UUID, GroundStockExit> groundLosses;
    private final Map<SubjectId, FrontierV3ContainerRemovalWitness> containerRemovals;
    private final Map<UUID, ExactItemCustodyChanged> exactDrops;
    private final Map<UUID, FrontierV3OperationReceipt> lossOperations;
    private FrontierV3ResourceHandoffLedger() { this(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 1); }
    private FrontierV3ResourceHandoffLedger(Map<UUID, Pending> initial, Map<SubjectId, Pickup> pickups, Map<UUID, GroundStockExit> losses,
            Map<SubjectId, FrontierV3ContainerRemovalWitness> removals, Map<UUID, ExactItemCustodyChanged> drops,
            Map<UUID, FrontierV3OperationReceipt> operations, long nextSequence) {
        super(FrontierV3PhysicalStoreKind.RESOURCE_HANDOFFS);
        if (initial.size() + pickups.size() + losses.size() + removals.size() + drops.size() > MAX_PENDING) throw new IllegalArgumentException("resource witness bound exceeded");
        if (nextSequence < 1 || initial.values().stream().anyMatch(entry -> entry.sequence() >= nextSequence)
                || initial.values().stream().map(Pending::sequence).distinct().count() != initial.size())
            throw new IllegalArgumentException("invalid resource witness causal order");
        this.nextSequence = nextSequence;
        pending = table("pending", initial, UUID::toString, (id, receipt) -> encode(id, receipt));
        actorPickups = table("actorPickups", pickups, SubjectId::value, (id, pickup) -> pickup.save());
        groundLosses = table("groundLosses", losses, UUID::toString, (id, loss) -> encodeLoss(loss));
        containerRemovals = table("containerRemovals", removals, SubjectId::value, (id, removal) -> removal.save());
        exactDrops = table("exactDrops", drops, UUID::toString, (id, drop) -> encodeExact(id, drop));
        lossOperations = table("lossOperations", operations, UUID::toString, FrontierV3ResourceHandoffLedger::encodeOperation);
        operations.forEach((id, operation) -> {
            var loss = losses.values().stream().filter(value -> value.witnessId().equals(id)).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("native loss operation lacks its owner witness"));
            validateOperation(loss, operation);
        });
        if (drops.values().stream().map(ExactItemCustodyChanged::itemId).distinct().count() != drops.size()
                || drops.keySet().stream().anyMatch(id -> initial.containsKey(id) || losses.containsKey(id)))
            throw new IllegalArgumentException("competing exact container drop authority");
        drops.forEach(FrontierV3ResourceHandoffLedger::validateExact);
        // Sequential fragmented handoffs may share an account. Different physical
        // effect families must never recover authority over that same source.
        var exclusiveAccounts = new HashSet<SubjectId>(); var exclusiveEntities = new HashSet<UUID>();
        for (var pickup : pickups.values())
            if (!exclusiveAccounts.add(pickup.account()) || !exclusiveEntities.add(pickup.entity()))
                throw new IllegalArgumentException("competing recovered ground pickup authority");
        for (var loss : losses.values())
            if (!exclusiveAccounts.add(loss.sourceAccountId()) || !exclusiveEntities.add(loss.source().entityId()))
                throw new IllegalArgumentException("competing recovered ground stock exit authority");
        for (var id : drops.keySet()) if (!exclusiveEntities.add(id))
            throw new IllegalArgumentException("exact drop competes with recovered ground authority");
        if (initial.entrySet().stream().anyMatch(entry -> competingExclusive(entry.getKey(), entry.getValue().receipt())))
            throw new IllegalArgumentException("recovered handoff competes with ground pickup or exit");
    }
    static FrontierV3ResourceHandoffLedger get(ServerLevel level) {
        return FrontierV3JournaledSavedData.get(level, FrontierV3PhysicalStoreKind.RESOURCE_HANDOFFS,
                FrontierV3ResourceHandoffLedger::new, FrontierV3ResourceHandoffLedger::load);
    }
    Map<UUID, FungibleResourceHandoffObserved> pending() {
        var result = new LinkedHashMap<UUID, FungibleResourceHandoffObserved>();
        pending.entrySet().stream().sorted(Comparator.comparingLong(entry -> entry.getValue().sequence()))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue().receipt()));
        return Collections.unmodifiableMap(result);
    }
    /** Preview only: all resource authority remains with the canonical ledger/reducer. */
    FrontierWorldState projected(FrontierWorldState state) {
        for (var receipt : pending().values()) {
            var account = state.inventory().fungibleResources().accounts().get(receipt.destinationAccount().id());
            if (receipt.withoutForfeitedClaims().destinationAccount().equals(account)) continue;
            state = FungibleResourceHandoffStateSupport.apply(state, receipt);
        }
        return state;
    }
    Pickup pickup(SubjectId owner) { return actorPickups.get(owner); }
    List<FrontierV3ContainerRemovalWitness> removals() { return containerRemovals.values().stream().sorted(Comparator.comparing(FrontierV3ContainerRemovalWitness::container)).toList(); }
    boolean removes(SubjectId container) { return containerRemovals.containsKey(container); }
    Map<UUID, ExactItemCustodyChanged> exactDrops() { return Map.copyOf(exactDrops); }
    void prepareRemoval(FrontierV3ContainerRemovalWitness removal) {
        if (size() >= MAX_PENDING || containerRemovals.containsKey(removal.container())) throw new IllegalArgumentException("competing container removal witness");
        containerRemovals.put(removal.container(), removal); setDirty();
    }
    void retireRemoval(FrontierV3ContainerRemovalWitness removal) {
        if (!removal.equals(containerRemovals.get(removal.container()))) throw new IllegalArgumentException("container removal lost its witness");
        containerRemovals.remove(removal.container()); setDirty();
    }
    void prepareExact(UUID id, ExactItemCustodyChanged receipt) {
        validateExact(id, receipt);
        if (size() >= MAX_PENDING || blocks(id) || exactDrops.values().stream().anyMatch(value -> value.itemId().equals(receipt.itemId())))
            throw new IllegalArgumentException("competing exact drop witness");
        exactDrops.put(id, receipt); setDirty();
    }
    void retireExact(UUID id, ExactItemCustodyChanged receipt) {
        if (!receipt.equals(exactDrops.get(id))) throw new IllegalArgumentException("exact drop lost its witness");
        exactDrops.remove(id); setDirty();
    }
    private int size() { return pending.size() + actorPickups.size() + groundLosses.size() + containerRemovals.size() + exactDrops.size(); }
    private static void validateExact(UUID id, ExactItemCustodyChanged receipt) {
        if (!(receipt.from() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot)
                || !(receipt.to() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier carrier)
                || !carrier.carrierId().equals(id)) throw new IllegalArgumentException("exact drop lacks container/carrier declarations");
    }
    List<GroundStockExit> losses() { return groundLosses.values().stream().sorted(Comparator.comparing(GroundStockExit::witnessId)).toList(); }
    void prepareLoss(GroundStockExit loss) {
        if (groundLosses.containsKey(loss.source().entityId()) || blocks(loss.source().entityId()) || blocksAccount(loss.sourceAccountId())
                || size() >= MAX_PENDING)
            throw new IllegalArgumentException("ground destruction retains a competing physical witness");
        groundLosses.put(loss.source().entityId(), loss); setDirty();
    }
    void retireLoss(GroundStockExit loss) {
        if (!loss.equals(groundLosses.get(loss.source().entityId()))) throw new IllegalArgumentException("ground destruction lost its exact physical witness");
        groundLosses.remove(loss.source().entityId()); lossOperations.remove(loss.witnessId()); setDirty();
    }
    Optional<FrontierV3OperationReceipt> lossOperation(GroundStockExit loss) { return Optional.ofNullable(lossOperations.get(loss.witnessId())); }
    void bindLossOperation(GroundStockExit loss, FrontierV3OperationReceipt operation) {
        if (!loss.equals(groundLosses.get(loss.source().entityId()))) throw new IllegalArgumentException("native operation lost its owner witness");
        validateOperation(loss, operation);
        var prior = lossOperations.get(loss.witnessId());
        if (prior != null && (!prior.ticket().equals(operation.ticket()) || !prior.declaration().equals(operation.declaration())
                || prior.sequence() != operation.sequence())) throw new IllegalArgumentException("competing native loss operation");
        if (prior == null) { lossOperations.put(loss.witnessId(), operation); setDirty(); }
    }
    private static void validateOperation(GroundStockExit loss, FrontierV3OperationReceipt operation) {
        var declaration = operation.declaration();
        boolean compatible = loss instanceof io.farfrontier.palemirror.frontier.v3.model.GroundResourcePlayerWithdrawal
                && declaration.protocol() == FrontierV3OperationReceipt.Protocol.PLAYER_GROUND_PICKUP
                || loss instanceof io.farfrontier.palemirror.frontier.v3.model.GroundResourceDestroyed
                && declaration.protocol() == FrontierV3OperationReceipt.Protocol.GROUND_DESTRUCTION;
        if (!compatible
                || !declaration.owner().equals(loss.economicOwner())
                || !declaration.fingerprint().equals(FrontierV3OperationConfirmation.fingerprint(loss))
                || !declaration.operation().equals(loss.witnessId().toString())
                || !operation.applied().equals(declaration.participants()))
            throw new IllegalArgumentException("native loss receipt has foreign or incomplete authority");
    }
    private static CompoundTag encodeOperation(UUID witness, FrontierV3OperationReceipt operation) {
        var tag = new CompoundTag(); tag.putUUID("witness", witness); tag.put("operation", FrontierV3OperationReceiptCodec.write(operation)); return tag;
    }
    java.util.List<Pickup> pickups() { return actorPickups.values().stream().sorted(Comparator.comparing(Pickup::owner)).toList(); }
    boolean blocks(UUID entity) {
        return exactDrops.containsKey(entity) || groundLosses.containsKey(entity) || pending.containsKey(entity) || pending.values().stream().anyMatch(entry ->
                java.util.stream.Stream.concat(entry.receipt().remainingSource().stream(), entry.receipt().destinationBindings().stream())
                        .anyMatch(binding -> binding.address() instanceof PhysicalStackAddress.WorldEntity address && address.entityId().equals(entity)))
                || actorPickups.values().stream().anyMatch(pickup -> pickup.entity().equals(entity));
    }
    boolean blocksAccount(SubjectId account) {
        return pending.values().stream().anyMatch(entry -> entry.receipt().sourceAccountId().equals(account)
                || entry.receipt().destinationAccount().id().equals(account))
                || groundLosses.values().stream().anyMatch(loss -> loss.sourceAccountId().equals(account))
                || actorPickups.values().stream().anyMatch(pickup -> pickup.account().equals(account));
    }
    void preparePickup(Pickup pickup) {
        var old = actorPickups.get(pickup.owner());
        if (old != null) { if (!old.equals(pickup)) throw new IllegalArgumentException("ground pickup has a competing witness"); return; }
        if (blocks(pickup.entity()) || blocksAccount(pickup.account()) || size() >= MAX_PENDING)
            throw new IllegalArgumentException("ground source retains a competing physical witness");
        actorPickups.put(pickup.owner(), pickup); setDirty();
    }
    void retirePickup(Pickup pickup) {
        if (!pickup.equals(actorPickups.get(pickup.owner()))) throw new IllegalArgumentException("ground pickup lost its exact saved witness");
        actorPickups.remove(pickup.owner()); setDirty();
    }
    void prepare(UUID id, FungibleResourceHandoffObserved receipt) {
        if (size() >= MAX_PENDING || pending.containsKey(id) || exactDrops.containsKey(id)
                || competingExclusive(id, receipt))
            throw new IllegalArgumentException("resource handoff already prepared or witness capacity exhausted");
        pending.put(id, new Pending(nextSequence, receipt)); nextSequence = Math.addExact(nextSequence, 1);
        setDirty();
    }
    private boolean competingExclusive(UUID id, FungibleResourceHandoffObserved receipt) {
        var accounts = new HashSet<>(List.of(receipt.sourceAccountId(), receipt.destinationAccount().id()));
        var entities = new HashSet<UUID>(); entities.add(id);
        java.util.stream.Stream.concat(receipt.remainingSource().stream(), receipt.destinationBindings().stream())
                .map(binding -> binding.address()).filter(PhysicalStackAddress.WorldEntity.class::isInstance)
                .map(PhysicalStackAddress.WorldEntity.class::cast).forEach(address -> entities.add(address.entityId()));
        return actorPickups.values().stream().anyMatch(pickup -> accounts.contains(pickup.account()) || entities.contains(pickup.entity()))
                || groundLosses.values().stream().anyMatch(loss -> accounts.contains(loss.sourceAccountId()) || entities.contains(loss.source().entityId()));
    }
    void retire(UUID id, FungibleResourceHandoffObserved expected) {
        if (!pending.containsKey(id) || !expected.equals(pending.get(id).receipt())) throw new IllegalArgumentException("resource handoff retirement lost its exact witness");
        pending.remove(id); setDirty();
    }
    private static CompoundTag encode(UUID id, Pending entry) {
        var tag = new CompoundTag(); tag.putUUID("id", id); tag.putLong("sequence", entry.sequence());
        tag.putByteArray("receipt", FrontierWorldRuntimeDefinition.payloadCodecs().encode(entry.receipt())); return tag;
    }
    private static CompoundTag encodeLoss(GroundStockExit loss) {
        var tag = new CompoundTag(); tag.putString("type", loss.type()); tag.putByteArray("receipt", FrontierWorldRuntimeDefinition.payloadCodecs().encode(loss)); return tag;
    }
    private static CompoundTag encodeExact(UUID id, ExactItemCustodyChanged receipt) {
        var tag = new CompoundTag(); tag.putUUID("id", id); tag.putByteArray("receipt", FrontierWorldRuntimeDefinition.payloadCodecs().encode(receipt)); return tag;
    }
    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", 6); tag.putLong("nextSequence", nextSequence); return tag; }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(metadata()); var entries = new ListTag();
        pending.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entries.add(encode(entry.getKey(), entry.getValue())));
        tag.put("pending", entries); var pickups = new ListTag();
        actorPickups.values().stream().sorted(Comparator.comparing(Pickup::owner)).forEach(pickup -> pickups.add(pickup.save()));
        tag.put("actorPickups", pickups); var losses = new ListTag(); groundLosses.values().stream()
                .sorted(Comparator.comparing(GroundStockExit::witnessId)).forEach(loss -> losses.add(encodeLoss(loss)));
        tag.put("groundLosses", losses);
        var removals = new ListTag(); removals().forEach(removal -> removals.add(removal.save())); tag.put("containerRemovals", removals);
        var drops = new ListTag(); exactDrops.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> drops.add(encodeExact(entry.getKey(), entry.getValue())));
        tag.put("exactDrops", drops);
        var operations = new ListTag(); lossOperations.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> operations.add(encodeOperation(entry.getKey(), entry.getValue())));
        tag.put("lossOperations", operations); return tag;
    }
    static FrontierV3ResourceHandoffLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != 6) throw new IllegalArgumentException("incompatible resource handoff journal");
        var entries = tag.getList("pending", Tag.TAG_COMPOUND);
        if (entries.size() > MAX_PENDING) throw new IllegalArgumentException("resource witness bound exceeded");
        var values = new LinkedHashMap<UUID, Pending>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            var receipt = (FungibleResourceHandoffObserved) FrontierWorldRuntimeDefinition.payloadCodecs()
                    .decode("frontier.fungible_resource_handoff_observed", entry.getByteArray("receipt"));
            if (values.putIfAbsent(entry.getUUID("id"), new Pending(entry.getLong("sequence"), receipt)) != null) throw new IllegalArgumentException("duplicate resource witness");
        }
        var pickupTags = tag.getList("actorPickups", Tag.TAG_COMPOUND);
        if (values.size() + pickupTags.size() > MAX_PENDING) throw new IllegalArgumentException("resource witness bound exceeded");
        var pickups = new LinkedHashMap<SubjectId, Pickup>();
        for (int i = 0; i < pickupTags.size(); i++) {
            var pickup = Pickup.read(pickupTags.getCompound(i));
            if (pickups.putIfAbsent(pickup.owner(), pickup) != null) throw new IllegalArgumentException("duplicate ground pickup witness");
        }
        var losses = new LinkedHashMap<UUID, GroundStockExit>(); var lossTags = tag.getList("groundLosses", Tag.TAG_COMPOUND);
        if (values.size() + pickups.size() + lossTags.size() > MAX_PENDING) throw new IllegalArgumentException("resource witness bound exceeded");
        for (int i = 0; i < lossTags.size(); i++) {
            var loss = (GroundStockExit) FrontierWorldRuntimeDefinition.payloadCodecs().decode(lossTags.getCompound(i).getString("type"), lossTags.getCompound(i).getByteArray("receipt"));
            if (losses.putIfAbsent(loss.source().entityId(), loss) != null) throw new IllegalArgumentException("duplicate ground destruction witness");
        }
        var removals = new LinkedHashMap<SubjectId, FrontierV3ContainerRemovalWitness>();
        var removalTags = tag.getList("containerRemovals", Tag.TAG_COMPOUND);
        if (values.size() + pickups.size() + losses.size() + removalTags.size() > MAX_PENDING)
            throw new IllegalArgumentException("resource witness bound exceeded");
        for (int i = 0; i < removalTags.size(); i++) {
            var removal = FrontierV3ContainerRemovalWitness.read(removalTags.getCompound(i));
            if (removals.putIfAbsent(removal.container(), removal) != null) throw new IllegalArgumentException("duplicate container removal witness");
        }
        var drops = new LinkedHashMap<UUID, ExactItemCustodyChanged>(); var dropTags = tag.getList("exactDrops", Tag.TAG_COMPOUND);
        if (values.size() + pickups.size() + losses.size() + removals.size() + dropTags.size() > MAX_PENDING)
            throw new IllegalArgumentException("resource witness bound exceeded");
        for (int i = 0; i < dropTags.size(); i++) {
            var entry = dropTags.getCompound(i);
            var receipt = (ExactItemCustodyChanged) FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.exact_item_custody_changed", entry.getByteArray("receipt"));
            if (drops.putIfAbsent(entry.getUUID("id"), receipt) != null) throw new IllegalArgumentException("duplicate exact drop witness");
        }
        var operations = new HashMap<UUID, FrontierV3OperationReceipt>(); var operationTags = tag.getList("lossOperations", Tag.TAG_COMPOUND);
        if (!tag.contains("lossOperations", Tag.TAG_LIST) || !operationTags.equals(tag.get("lossOperations")) || operationTags.size() > losses.size())
            throw new IllegalArgumentException("invalid native loss operation links");
        for (int i = 0; i < operationTags.size(); i++) {
            var row = operationTags.getCompound(i); UUID id = row.getUUID("witness");
            var operation = FrontierV3OperationReceiptCodec.read(row.getCompound("operation"));
            if (!encodeOperation(id, operation).equals(row) || operations.putIfAbsent(id, operation) != null)
                throw new IllegalArgumentException("duplicate or invalid native loss operation");
        }
        return new FrontierV3ResourceHandoffLedger(values, pickups, losses, removals, drops, operations, tag.getLong("nextSequence"));
    }
}
