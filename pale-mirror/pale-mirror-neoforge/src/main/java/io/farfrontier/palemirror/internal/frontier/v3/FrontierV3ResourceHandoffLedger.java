package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffObserved;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GroundStockExit;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
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
    private final Map<UUID, FrontierV3OperationReceipt> lossOperations;
    private final Map<UUID, FrontierV3OperationReceipt> handoffOperations;
    private final Map<UUID, FrontierV3ContainerDropTransaction> containerDrops;
    private final Map<UUID, FrontierV3EntityResourceDropTransaction> entityDrops;
    private FrontierV3ResourceHandoffLedger() { this(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 1); }
    private FrontierV3ResourceHandoffLedger(Map<UUID, Pending> initial, Map<SubjectId, Pickup> pickups, Map<UUID, GroundStockExit> losses,
            Map<SubjectId, FrontierV3ContainerRemovalWitness> removals,
            Map<UUID, FrontierV3OperationReceipt> operations, Map<UUID, FrontierV3OperationReceipt> handoffs,
            Map<UUID, FrontierV3ContainerDropTransaction> nativeDrops, Map<UUID, FrontierV3EntityResourceDropTransaction> bodyDrops, long nextSequence) {
        super(FrontierV3PhysicalStoreKind.RESOURCE_HANDOFFS);
        if (initial.size() + pickups.size() + losses.size() + removals.size() + nativeDrops.size() + bodyDrops.size() > MAX_PENDING) throw new IllegalArgumentException("resource witness bound exceeded");
        if (nextSequence < 1 || initial.values().stream().anyMatch(entry -> entry.sequence() >= nextSequence)
                || initial.values().stream().map(Pending::sequence).distinct().count() != initial.size())
            throw new IllegalArgumentException("invalid resource witness causal order");
        this.nextSequence = nextSequence;
        pending = table("pending", initial, UUID::toString, (id, receipt) -> encode(id, receipt));
        actorPickups = table("actorPickups", pickups, SubjectId::value, (id, pickup) -> pickup.save());
        groundLosses = table("groundLosses", losses, UUID::toString, (id, loss) -> encodeLoss(loss));
        containerRemovals = table("containerRemovals", removals, SubjectId::value, (id, removal) -> removal.save());
        lossOperations = table("lossOperations", operations, UUID::toString, FrontierV3ResourceHandoffLedger::encodeOperation);
        handoffOperations = table("handoffOperations", handoffs, UUID::toString, FrontierV3ResourceHandoffLedger::encodeOperation);
        containerDrops = table("containerDrops", nativeDrops, UUID::toString, (id, drop) -> drop.save());
        entityDrops = table("entityDrops", bodyDrops, UUID::toString, (id, drop) -> drop.save());
        if (bodyDrops.values().stream().map(FrontierV3EntityResourceDropTransaction::entity).distinct().count() != bodyDrops.size()
                || bodyDrops.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id())
                    || nativeDrops.values().stream().anyMatch(drop -> drop.blocks(entry.getValue().entity()))
                    || pickups.values().stream().anyMatch(pickup -> pickup.entity().equals(entry.getValue().entity()))
                    || losses.containsKey(entry.getValue().entity()) || pending.containsKey(entry.getValue().entity())
                    || pending.values().stream().flatMap(value -> value.receipt().destinationBindings().stream())
                        .anyMatch(binding -> binding.address().equals(new PhysicalStackAddress.WorldEntity(entry.getValue().entity())))))
            throw new IllegalArgumentException("competing native entity resource drops");
        var tickets = new HashSet<UUID>();
        java.util.stream.Stream.of(operations.values().stream(), handoffs.values().stream(),
                nativeDrops.values().stream().map(FrontierV3ContainerDropTransaction::operation),
                bodyDrops.values().stream().map(FrontierV3EntityResourceDropTransaction::operation))
                .flatMap(stream -> stream).forEach(operation -> {
                    if (!tickets.add(operation.ticket())) throw new IllegalArgumentException("native operation has competing resource owners");
                });
        if (nativeDrops.values().stream().map(drop -> new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot(drop.container(), drop.slot()))
                .distinct().count() != nativeDrops.size()) throw new IllegalArgumentException("competing native container drops");
        if (handoffs.values().stream().map(FrontierV3OperationReceipt::ticket).distinct().count() != handoffs.size())
            throw new IllegalArgumentException("one native handoff has competing owner witnesses");
        handoffs.forEach((id, operation) -> {
            if (!initial.containsKey(id)) throw new IllegalArgumentException("native handoff has no exact owner witness");
            validateHandoffOperation(initial.get(id).receipt(), operation);
        });
        operations.forEach((id, operation) -> {
            var loss = losses.values().stream().filter(value -> value.witnessId().equals(id)).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("native loss operation lacks its owner witness"));
            validateOperation(loss, operation);
        });
        // Sequential fragmented handoffs may share an account. Different physical
        // effect families must never recover authority over that same source.
        var exclusiveAccounts = new HashSet<SubjectId>(); var exclusiveEntities = new HashSet<UUID>();
        for (var pickup : pickups.values())
            if (!exclusiveAccounts.add(pickup.account()) || !exclusiveEntities.add(pickup.entity()))
                throw new IllegalArgumentException("competing recovered ground pickup authority");
        for (var loss : losses.values())
            if (!exclusiveAccounts.add(loss.sourceAccountId()) || !exclusiveEntities.add(loss.source().entityId()))
                throw new IllegalArgumentException("competing recovered ground stock exit authority");
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
    void prepareRemoval(FrontierV3ContainerRemovalWitness removal) {
        if (size() >= MAX_PENDING || containerRemovals.containsKey(removal.container())) throw new IllegalArgumentException("competing container removal witness");
        containerRemovals.put(removal.container(), removal); setDirty();
    }
    void retireRemoval(FrontierV3ContainerRemovalWitness removal) {
        if (!removal.equals(containerRemovals.get(removal.container()))) throw new IllegalArgumentException("container removal lost its witness");
        containerRemovals.remove(removal.container()); setDirty();
    }
    List<FrontierV3ContainerDropTransaction> containerDrops() {
        return containerDrops.values().stream().sorted(Comparator.comparingLong(d -> d.operation().sequence())).toList();
    }
    boolean dropsFrom(SubjectId container) { return containerDrops.values().stream().anyMatch(d -> d.container().equals(container)); }
    boolean canPrepareDrop(SubjectId container, int slot) {
        return size() < MAX_PENDING && containerDrops.values().stream().noneMatch(d -> d.container().equals(container) && d.slot() == slot);
    }
    void prepareDrop(FrontierV3ContainerDropTransaction drop) {
        if (!canPrepareDrop(drop.container(), drop.slot()) || containerDrops.containsKey(drop.id()))
            throw new IllegalArgumentException("container drop has competing authority or exhausted capacity");
        containerDrops.put(drop.id(), drop); setDirty();
    }
    void updateDrop(FrontierV3ContainerDropTransaction expected, FrontierV3ContainerDropTransaction next) {
        if (!expected.equals(containerDrops.get(expected.id())) || !expected.container().equals(next.container()) || expected.slot() != next.slot()
                || !expected.bind(next.operation()).operation().equals(next.operation())
                || !next.outcome().equals(expected.outcome()))
            throw new IllegalArgumentException("container drop changed its exact causal witness");
        containerDrops.put(next.id(), next); setDirty();
    }
    void retireDrop(FrontierV3ContainerDropTransaction expected) {
        if (!expected.equals(containerDrops.get(expected.id()))) throw new IllegalArgumentException("container drop lost its witness");
        containerDrops.remove(expected.id()); setDirty();
    }
    List<FrontierV3EntityResourceDropTransaction> entityDrops() {
        return entityDrops.values().stream().sorted(Comparator.comparingLong(d -> d.operation().sequence())).toList();
    }
    boolean canPrepareEntityDrop(UUID entity) { return size() < MAX_PENDING && !blocks(entity); }
    void prepareEntityDrop(FrontierV3EntityResourceDropTransaction drop) {
        if (!canPrepareEntityDrop(drop.entity()) || entityDrops.containsKey(drop.id()) || containerDrops.containsKey(drop.id())
                || java.util.stream.Stream.concat(lossOperations.values().stream(), handoffOperations.values().stream())
                    .anyMatch(operation -> operation.ticket().equals(drop.id())))
            throw new IllegalArgumentException("entity drop has competing authority or exhausted capacity");
        entityDrops.put(drop.id(), drop); setDirty();
    }
    void updateEntityDrop(FrontierV3EntityResourceDropTransaction expected, FrontierV3EntityResourceDropTransaction next) {
        if (!expected.equals(entityDrops.get(expected.id())) || !expected.bind(next.operation()).equals(next))
            throw new IllegalArgumentException("entity drop changed its exact causal witness");
        entityDrops.put(next.id(), next); setDirty();
    }
    void retireEntityDrop(FrontierV3EntityResourceDropTransaction expected) {
        if (!expected.equals(entityDrops.get(expected.id()))) throw new IllegalArgumentException("entity drop lost its witness");
        entityDrops.remove(expected.id()); setDirty();
    }
    private int size() { return pending.size() + actorPickups.size() + groundLosses.size() + containerRemovals.size() + containerDrops.size() + entityDrops.size(); }
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
        return entityDrops.values().stream().anyMatch(drop -> drop.entity().equals(entity))
                || containerDrops.values().stream().anyMatch(drop -> drop.blocks(entity))
                || groundLosses.containsKey(entity) || pending.containsKey(entity) || pending.values().stream().anyMatch(entry ->
                java.util.stream.Stream.concat(entry.receipt().remainingSource().stream(), entry.receipt().destinationBindings().stream())
                        .anyMatch(binding -> binding.address() instanceof PhysicalStackAddress.WorldEntity address && address.entityId().equals(entity)))
                || actorPickups.values().stream().anyMatch(pickup -> pickup.entity().equals(entity));
    }
    boolean blocksAccount(SubjectId account) {
        return containerDrops.values().stream().flatMap(drop -> drop.resources().stream()).anyMatch(r -> r.sourceAccountId().equals(account)
                || r.destinationAccount().id().equals(account))
                || pending.values().stream().anyMatch(entry -> entry.receipt().sourceAccountId().equals(account)
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
        if (size() >= MAX_PENDING || pending.containsKey(id)
                || competingExclusive(id, receipt))
            throw new IllegalArgumentException("resource handoff already prepared or witness capacity exhausted");
        pending.put(id, new Pending(nextSequence, receipt)); nextSequence = Math.addExact(nextSequence, 1);
        setDirty();
    }
    Optional<FrontierV3OperationReceipt> handoffOperation(UUID id) { return Optional.ofNullable(handoffOperations.get(id)); }
    void prepareNative(UUID id, FungibleResourceHandoffObserved receipt, FrontierV3OperationReceipt operation) {
        validateHandoffOperation(receipt, operation);
        if (handoffOperations.values().stream().anyMatch(existing -> existing.ticket().equals(operation.ticket())))
            throw new IllegalArgumentException("native handoff already belongs to another witness");
        prepare(id, receipt); handoffOperations.put(id, operation); setDirty();
    }
    void bindHandoffOperation(UUID id, FrontierV3OperationReceipt operation) {
        var prior = handoffOperations.get(id);
        if (prior == null || !prior.ticket().equals(operation.ticket()) || prior.sequence() != operation.sequence()
                || !prior.declaration().equals(operation.declaration()) || !operation.applied().containsAll(prior.applied()))
            throw new IllegalArgumentException("handoff changed its exact native operation");
        validateHandoffOperation(pending.get(id).receipt(), operation);
        if (!prior.equals(operation)) { handoffOperations.put(id, operation); setDirty(); }
    }
    private static void validateHandoffOperation(FungibleResourceHandoffObserved receipt, FrontierV3OperationReceipt operation) {
        var declaration = operation.declaration();
        if ((declaration.protocol() != FrontierV3OperationReceipt.Protocol.GROUND_MERGE
                    && declaration.protocol() != FrontierV3OperationReceipt.Protocol.NATIVE_INVENTORY_TRANSFER)
                || !declaration.owner().equals(io.farfrontier.palemirror.frontier.v3.model.FrontierExecutionSubjects.PHYSICAL_EXECUTOR)
                || !declaration.fingerprint().equals(FrontierV3OperationConfirmation.fingerprint(receipt)))
            throw new IllegalArgumentException("handoff has a foreign native declaration");
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
        pending.remove(id); handoffOperations.remove(id); setDirty();
    }
    private static CompoundTag encode(UUID id, Pending entry) {
        var tag = new CompoundTag(); tag.putUUID("id", id); tag.putLong("sequence", entry.sequence());
        tag.putByteArray("receipt", FrontierWorldRuntimeDefinition.payloadCodecs().encode(entry.receipt())); return tag;
    }
    private static CompoundTag encodeLoss(GroundStockExit loss) {
        var tag = new CompoundTag(); tag.putString("type", loss.type()); tag.putByteArray("receipt", FrontierWorldRuntimeDefinition.payloadCodecs().encode(loss)); return tag;
    }
    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", 9); tag.putLong("nextSequence", nextSequence); return tag; }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(metadata()); var entries = new ListTag();
        pending.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entries.add(encode(entry.getKey(), entry.getValue())));
        tag.put("pending", entries); var pickups = new ListTag();
        actorPickups.values().stream().sorted(Comparator.comparing(Pickup::owner)).forEach(pickup -> pickups.add(pickup.save()));
        tag.put("actorPickups", pickups); var losses = new ListTag(); groundLosses.values().stream()
                .sorted(Comparator.comparing(GroundStockExit::witnessId)).forEach(loss -> losses.add(encodeLoss(loss)));
        tag.put("groundLosses", losses);
        var removals = new ListTag(); removals().forEach(removal -> removals.add(removal.save())); tag.put("containerRemovals", removals);
        var operations = new ListTag(); lossOperations.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> operations.add(encodeOperation(entry.getKey(), entry.getValue())));
        tag.put("lossOperations", operations);
        var handoffs = new ListTag(); handoffOperations.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> handoffs.add(encodeOperation(entry.getKey(), entry.getValue())));
        tag.put("handoffOperations", handoffs);
        var nativeDrops = new ListTag(); containerDrops().forEach(drop -> nativeDrops.add(drop.save())); tag.put("containerDrops", nativeDrops);
        var bodyDrops = new ListTag(); entityDrops().forEach(drop -> bodyDrops.add(drop.save())); tag.put("entityDrops", bodyDrops); return tag;
    }
    static FrontierV3ResourceHandoffLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != 9) throw new IllegalArgumentException("incompatible resource handoff journal");
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
        var operations = new HashMap<UUID, FrontierV3OperationReceipt>(); var operationTags = tag.getList("lossOperations", Tag.TAG_COMPOUND);
        if (!tag.contains("lossOperations", Tag.TAG_LIST) || !operationTags.equals(tag.get("lossOperations")) || operationTags.size() > losses.size())
            throw new IllegalArgumentException("invalid native loss operation links");
        for (int i = 0; i < operationTags.size(); i++) {
            var row = operationTags.getCompound(i); UUID id = row.getUUID("witness");
            var operation = FrontierV3OperationReceiptCodec.read(row.getCompound("operation"));
            if (!encodeOperation(id, operation).equals(row) || operations.putIfAbsent(id, operation) != null)
                throw new IllegalArgumentException("duplicate or invalid native loss operation");
        }
        var handoffs = new HashMap<UUID, FrontierV3OperationReceipt>(); var handoffTags = tag.getList("handoffOperations", Tag.TAG_COMPOUND);
        if (!tag.contains("handoffOperations", Tag.TAG_LIST) || !handoffTags.equals(tag.get("handoffOperations")) || handoffTags.size() > values.size())
            throw new IllegalArgumentException("invalid native handoff operation links");
        for (int i = 0; i < handoffTags.size(); i++) {
            var row = handoffTags.getCompound(i); UUID id = row.getUUID("witness");
            var operation = FrontierV3OperationReceiptCodec.read(row.getCompound("operation"));
            if (!encodeOperation(id, operation).equals(row) || handoffs.putIfAbsent(id, operation) != null)
                throw new IllegalArgumentException("duplicate or invalid native handoff operation");
        }
        var nativeDrops = new LinkedHashMap<UUID, FrontierV3ContainerDropTransaction>(); var nativeTags = tag.getList("containerDrops", Tag.TAG_COMPOUND);
        if (!tag.contains("containerDrops", Tag.TAG_LIST) || !nativeTags.equals(tag.get("containerDrops")) || nativeTags.size() > MAX_PENDING)
            throw new IllegalArgumentException("invalid native container drop witnesses");
        for (int i = 0; i < nativeTags.size(); i++) {
            var drop = FrontierV3ContainerDropTransaction.read(nativeTags.getCompound(i));
            if (nativeDrops.putIfAbsent(drop.id(), drop) != null) throw new IllegalArgumentException("duplicate native container drop");
        }
        var bodyDrops = new LinkedHashMap<UUID, FrontierV3EntityResourceDropTransaction>(); var bodyTags = tag.getList("entityDrops", Tag.TAG_COMPOUND);
        if (!tag.contains("entityDrops", Tag.TAG_LIST) || !bodyTags.equals(tag.get("entityDrops")) || bodyTags.size() > MAX_PENDING)
            throw new IllegalArgumentException("invalid native entity drop witnesses");
        for (int i = 0; i < bodyTags.size(); i++) {
            var drop = FrontierV3EntityResourceDropTransaction.read(bodyTags.getCompound(i));
            if (bodyDrops.putIfAbsent(drop.id(), drop) != null) throw new IllegalArgumentException("duplicate native entity drop");
        }
        return new FrontierV3ResourceHandoffLedger(values, pickups, losses, removals, operations, handoffs, nativeDrops, bodyDrops, tag.getLong("nextSequence"));
    }
}
