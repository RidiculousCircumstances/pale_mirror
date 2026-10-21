package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Derived resource-witness address index. No persistence, permission or custody
 * authority: the ledger adds/removes exactly its already-validated records. */
final class FrontierV3ResourceHandoffIndex {
    record Footprint(Set<UUID> entities, Set<SubjectId> accounts, boolean exclusive,
                     Optional<InventoryCustody.ContainerSlot> source) {
        Footprint { entities = Set.copyOf(entities); accounts = Set.copyOf(accounts); Objects.requireNonNull(source); }
    }
    private final Map<UUID, Integer> entities = new HashMap<>(), exclusiveEntities = new HashMap<>();
    private final Map<SubjectId, Integer> accounts = new HashMap<>(), exclusiveAccounts = new HashMap<>(), containers = new HashMap<>();
    private final Map<InventoryCustody.ContainerSlot, Integer> slots = new HashMap<>();

    boolean blocks(UUID entity) { return entities.containsKey(entity); }
    boolean blocksAccount(SubjectId account) { return accounts.containsKey(account); }
    boolean dropsFrom(SubjectId container) { return containers.containsKey(container); }
    boolean dropsFrom(SubjectId container, int slot) { return slots.containsKey(new InventoryCustody.ContainerSlot(container, slot)); }
    boolean competingExclusive(Footprint footprint) {
        return footprint.entities().stream().anyMatch(exclusiveEntities::containsKey)
                || footprint.accounts().stream().anyMatch(exclusiveAccounts::containsKey);
    }
    void add(Footprint footprint) { change(footprint, 1); }
    void remove(Footprint footprint) { change(footprint, -1); }
    private void change(Footprint footprint, int delta) {
        footprint.entities().forEach(key -> change(entities, key, delta));
        footprint.accounts().forEach(key -> change(accounts, key, delta));
        if (footprint.exclusive()) {
            footprint.entities().forEach(key -> change(exclusiveEntities, key, delta));
            footprint.accounts().forEach(key -> change(exclusiveAccounts, key, delta));
        }
        footprint.source().ifPresent(source -> { change(slots, source, delta); change(containers, source.containerId(), delta); });
    }
    private static <K> void change(Map<K, Integer> counts, K key, int delta) {
        int next = Math.addExact(counts.getOrDefault(key, 0), delta);
        if (next < 0) throw new IllegalStateException("resource witness index lost its retained reference");
        if (next == 0) counts.remove(key); else counts.put(key, next);
    }
    static Footprint handoff(UUID id, FungibleResourceHandoffObserved receipt) {
        var entities = new HashSet<UUID>(); entities.add(id);
        java.util.stream.Stream.concat(receipt.remainingSource().stream(), receipt.destinationBindings().stream())
                .map(PhysicalStackBinding::address).filter(PhysicalStackAddress.WorldEntity.class::isInstance)
                .map(PhysicalStackAddress.WorldEntity.class::cast).forEach(address -> entities.add(address.entityId()));
        return new Footprint(entities, new HashSet<>(List.of(receipt.sourceAccountId(), receipt.destinationAccount().id())), false, Optional.empty());
    }
    static Footprint pickup(FrontierV3ResourceHandoffLedger.Pickup pickup) {
        return new Footprint(Set.of(pickup.entity()), Set.of(pickup.account()), true, Optional.empty());
    }
    static Footprint loss(GroundStockExit loss) {
        return new Footprint(Set.of(loss.source().entityId()), Set.of(loss.sourceAccountId()), true, Optional.empty());
    }
    static Footprint containerDrop(FrontierV3ContainerDropTransaction drop) {
        var entities = new HashSet<UUID>(); var accounts = new HashSet<SubjectId>();
        for (var resource : drop.resources()) {
            entities.add(((ResourceCustody.Ground) resource.destinationAccount().custody()).entityId());
            accounts.add(resource.sourceAccountId()); accounts.add(resource.destinationAccount().id());
        }
        for (var item : drop.exactItems()) entities.add(((InventoryCustody.WorldCarrier) item.to()).carrierId());
        return new Footprint(entities, accounts, false, Optional.of(new InventoryCustody.ContainerSlot(drop.container(), drop.slot())));
    }
    static Footprint entityDrop(FrontierV3EntityResourceDropTransaction drop) {
        return new Footprint(Set.of(drop.entity()), Set.of(), false, Optional.empty());
    }
}
