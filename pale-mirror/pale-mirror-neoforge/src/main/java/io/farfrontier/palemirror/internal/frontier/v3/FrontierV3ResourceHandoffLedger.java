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
    private FrontierV3ResourceHandoffLedger() { this(Map.of(), Map.of(), Map.of(), 1); }
    private FrontierV3ResourceHandoffLedger(Map<UUID, Pending> initial, Map<SubjectId, Pickup> pickups, Map<UUID, GroundStockExit> losses, long nextSequence) {
        super(FrontierV3PhysicalStoreKind.RESOURCE_HANDOFFS);
        if (initial.size() + pickups.size() + losses.size() > MAX_PENDING) throw new IllegalArgumentException("resource witness bound exceeded");
        if (nextSequence < 1 || initial.values().stream().anyMatch(entry -> entry.sequence() >= nextSequence)
                || initial.values().stream().map(Pending::sequence).distinct().count() != initial.size())
            throw new IllegalArgumentException("invalid resource witness causal order");
        this.nextSequence = nextSequence;
        pending = table("pending", initial, UUID::toString, (id, receipt) -> encode(id, receipt));
        actorPickups = table("actorPickups", pickups, SubjectId::value, (id, pickup) -> pickup.save());
        groundLosses = table("groundLosses", losses, UUID::toString, (id, loss) -> encodeLoss(loss));
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
    List<GroundStockExit> losses() { return groundLosses.values().stream().sorted(Comparator.comparing(GroundStockExit::witnessId)).toList(); }
    void prepareLoss(GroundStockExit loss) {
        if (groundLosses.containsKey(loss.source().entityId()) || blocks(loss.source().entityId()) || blocksAccount(loss.sourceAccountId())
                || pending.size() + actorPickups.size() + groundLosses.size() >= MAX_PENDING)
            throw new IllegalArgumentException("ground destruction retains a competing physical witness");
        groundLosses.put(loss.source().entityId(), loss); setDirty();
    }
    void retireLoss(GroundStockExit loss) {
        if (!loss.equals(groundLosses.get(loss.source().entityId()))) throw new IllegalArgumentException("ground destruction lost its exact physical witness");
        groundLosses.remove(loss.source().entityId()); setDirty();
    }
    java.util.List<Pickup> pickups() { return actorPickups.values().stream().sorted(Comparator.comparing(Pickup::owner)).toList(); }
    boolean blocks(UUID entity) {
        return groundLosses.containsKey(entity) || pending.containsKey(entity) || pending.values().stream().anyMatch(entry ->
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
        if (blocks(pickup.entity()) || blocksAccount(pickup.account()) || pending.size() + actorPickups.size() + groundLosses.size() >= MAX_PENDING)
            throw new IllegalArgumentException("ground source retains a competing physical witness");
        actorPickups.put(pickup.owner(), pickup); setDirty();
    }
    void retirePickup(Pickup pickup) {
        if (!pickup.equals(actorPickups.get(pickup.owner()))) throw new IllegalArgumentException("ground pickup lost its exact saved witness");
        actorPickups.remove(pickup.owner()); setDirty();
    }
    void prepare(UUID id, FungibleResourceHandoffObserved receipt) {
        if (pending.size() + actorPickups.size() + groundLosses.size() >= MAX_PENDING || pending.containsKey(id)
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
    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", 4); tag.putLong("nextSequence", nextSequence); return tag; }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", 4); tag.putLong("nextSequence", nextSequence); var entries = new ListTag();
        pending.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entries.add(encode(entry.getKey(), entry.getValue())));
        tag.put("pending", entries); var pickups = new ListTag();
        actorPickups.values().stream().sorted(Comparator.comparing(Pickup::owner)).forEach(pickup -> pickups.add(pickup.save()));
        tag.put("actorPickups", pickups); var losses = new ListTag(); groundLosses.values().stream()
                .sorted(Comparator.comparing(GroundStockExit::witnessId)).forEach(loss -> losses.add(encodeLoss(loss)));
        tag.put("groundLosses", losses); return tag;
    }
    static FrontierV3ResourceHandoffLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != 4) throw new IllegalArgumentException("incompatible resource handoff journal");
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
        return new FrontierV3ResourceHandoffLedger(values, pickups, losses, tag.getLong("nextSequence"));
    }
}
