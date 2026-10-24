package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.model.SettlementEconomicPlanningState.Change.*;

/** Stock/title/capacity owner. Native slot bindings and physical save markers are not economic inputs. */
final class InventoryEconomicInputChanges implements EconomicInputChangePort {
    @Override public void collect(FrontierWorldState before, FrontierWorldState after, Sink sink) {
        var a = before.inventory(); var b = after.inventory();
        var changedCustody = new HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        difference(before.replicaCustody().replicas(), after.replicaCustody().replicas(), (old, next) -> {
            if ((old != null && old.state() == PhysicalReplicaState.CONFLICT)
                    != (next != null && next.state() == PhysicalReplicaState.CONFLICT))
                changedCustody.add(next == null ? old.objectId() : next.objectId());
        });
        difference(before.replicaCustody().custodyByScope(), after.replicaCustody().custodyByScope(), (old, next) -> {
            if ((old != null && old.status() == PhysicalCustodyLeaseStatus.UNRESOLVED)
                    != (next != null && next.status() == PhysicalCustodyLeaseStatus.UNRESOLVED))
                changedCustody.add(next == null ? old.objectId() : next.objectId());
        });
        for (var id : changedCustody) {
            var record = b.containers().get(id);
            if (record != null && ReferenceContainerCustody.blocksCanonicalUse(before, id)
                    != ReferenceContainerCustody.blocksCanonicalUse(after, id))
                sink.changed(record.ownerId(), CAPACITY, Set.of());
        }
        if (a == b) return;
        difference(a.economics().accounts(), b.economics().accounts(), (old, next) -> {
            if (old != null) sink.changed(old.ownerId(), CASH, Set.of());
            if (next != null) sink.changed(next.ownerId(), CASH, Set.of());
        });
        difference(a.items(), b.items(), (old, next) -> {
            if (old != null) sink.resource(old.economicOwnerId(), STOCK, old.itemKind());
            if (next != null) sink.resource(next.economicOwnerId(), STOCK, next.itemKind());
        });
        difference(a.containers(), b.containers(), (old, next) -> {
            if (old != null && next != null && old.ownerId().equals(next.ownerId()) && old.slotCount() == next.slotCount()
                    && old.storage().equals(next.storage()) && old.productionStation().equals(next.productionStation())
                    && old.purpose() == next.purpose()) return;
            if (old != null) sink.changed(old.ownerId(), CAPACITY, Set.of());
            if (next != null) sink.changed(next.ownerId(), CAPACITY, Set.of());
        });
        difference(a.surfaces(), b.surfaces(), (old, next) -> {
            if (old != null && next != null && blocked(old.status()) == blocked(next.status())
                    && old.location().equals(next.location())) return;
            var surface = next == null ? old : next;
            var record = (next == null ? a : b).containers().get(surface.containerId());
            if (record != null) sink.changed(record.ownerId(), CAPACITY, Set.of());
        });
        var left = a.fungibleResources(); var right = b.fungibleResources(); if (left == right) return;
        difference(left.lots(), right.lots(), (old, next) -> {
            if (old != null) sink.resource(old.economicOwnerId(), STOCK, old.itemKind());
            if (next != null) sink.resource(next.economicOwnerId(), STOCK, next.itemKind());
        });
        difference(left.claims(), right.claims(), (old, next) -> {
            if (old != null) sink.resource(old.economicOwnerId(), COMMITMENT, old.itemKind());
            if (next != null) sink.resource(next.economicOwnerId(), COMMITMENT, next.itemKind());
        });
        difference(left.accounts(), right.accounts(), (old, next) -> {
            if (old != null) old.lotQuantities().keySet().forEach(id -> {
                var lot = left.lots().get(id); sink.resource(lot.economicOwnerId(), STOCK, lot.itemKind());
            });
            if (next != null) next.lotQuantities().keySet().forEach(id -> {
                var lot = right.lots().get(id); sink.resource(lot.economicOwnerId(), STOCK, lot.itemKind());
            });
        });
    }
    private static boolean blocked(ContainerSurfaceStatus status) {
        return status == ContainerSurfaceStatus.CONFLICT || status == ContainerSurfaceStatus.ADOPTING
                || status == ContainerSurfaceStatus.RETIRED;
    }
    private static <K, V> void difference(Map<K, V> before, Map<K, V> after, java.util.function.BiConsumer<V, V> change) {
        EconomicInputChangePort.difference(before, after, change);
    }
}
