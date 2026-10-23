package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Retained transport promise. Authoritative objects and lots stay in the shared inventory. */
public sealed interface ShipmentCargo permits ShipmentCargo.Fungible, ShipmentCargo.Individual {
    String itemKind();
    Map<SubjectId, Integer> portions();
    ShipmentCargo without(Map<SubjectId, Integer> unloaded);
    default int quantity() { return portions().values().stream().mapToInt(Integer::intValue).sum(); }
    record Fungible(SubjectId sourceAccountId, SubjectId carriedAccountId, SubjectId receivingAccountId,
                    String itemKind, Map<SubjectId, Integer> portions) implements ShipmentCargo {
        public Fungible {
            Objects.requireNonNull(sourceAccountId); Objects.requireNonNull(carriedAccountId); Objects.requireNonNull(receivingAccountId);
            Objects.requireNonNull(itemKind); portions = Map.copyOf(portions);
            if (sourceAccountId.equals(carriedAccountId) || sourceAccountId.equals(receivingAccountId)
                    || carriedAccountId.equals(receivingAccountId)) throw new IllegalArgumentException("cargo accounts must be distinct");
        }
        @Override public ShipmentCargo without(Map<SubjectId, Integer> unloaded) {
            FungibleResourceLedger.requireSubset(portions, unloaded, "unloaded cargo");
            var remaining = new HashMap<>(portions);
            unloaded.forEach((id, quantity) -> { int left = remaining.get(id) - quantity; if (left == 0) remaining.remove(id); else remaining.put(id, left); });
            return new Fungible(sourceAccountId, carriedAccountId, receivingAccountId, itemKind, remaining);
        }
        public Fungible receiving(SubjectId account) { return new Fungible(sourceAccountId, carriedAccountId, account, itemKind, portions); }
        public Fungible carried(SubjectId account) { return new Fungible(sourceAccountId, account, receivingAccountId, itemKind, portions); }
    }
    /** Individual cargo cannot be split, consumed or silently replaced during a trip. */
    record Individual(SubjectId itemId, SubjectId ownerId, String itemKind, ItemCondition condition, boolean remaining) implements ShipmentCargo {
        public Individual {
            Objects.requireNonNull(itemId); Objects.requireNonNull(ownerId); Objects.requireNonNull(itemKind); Objects.requireNonNull(condition);
            if (!condition.durable()) throw new IllegalArgumentException("individual cargo requires its condition contract");
        }
        public Individual(IndividualResourceAllocation allocation) {
            this(allocation.itemId(), allocation.economicOwnerId(), allocation.itemKind(), allocation.condition(), true);
        }
        @Override public Map<SubjectId, Integer> portions() { return remaining ? Map.of(itemId, 1) : Map.of(); }
        public boolean matches(ExactItemStack item) {
            return item != null && item.id().equals(itemId) && item.count() == 1 && item.economicOwnerId().equals(ownerId)
                    && item.itemKind().equals(itemKind) && item.condition().equals(condition);
        }
        @Override public ShipmentCargo without(Map<SubjectId, Integer> unloaded) {
            if (!remaining || !unloaded.equals(portions())) throw new IllegalArgumentException("individual cargo is indivisible");
            return new Individual(itemId, ownerId, itemKind, condition, false);
        }
        public Individual ownedBy(SubjectId owner) { return new Individual(itemId, owner, itemKind, condition, remaining); }
    }
}
