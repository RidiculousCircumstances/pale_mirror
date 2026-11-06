package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Packs fragmented warehouse stock through ordinary same-owner shipments.
 * Placement advice only: source claims, capacity, navigation and custody remain
 * with the existing shipment owners. Kitchens and production buffers keep their policy. */
final class StorageConsolidationSupply {
    private static final ImmutableSnapshotView<SubjectId, List<InternalHaulRequest>> REQUESTS = new ImmutableSnapshotView<>(4, 128);
    private record Stock(ShipmentEndpoint endpoint, int available, long quantity, int priority) { }
    private StorageConsolidationSupply() { }

    static List<InternalHaulRequest> requests(FrontierWorldState state, SubjectId home) {
        return REQUESTS.get(state, home, () -> calculate(state, home));
    }
    private static List<InternalHaulRequest> calculate(FrontierWorldState state, SubjectId home) {
        var incoming = new HashMap<SubjectId, Map<String, Long>>();
        var resources = state.inventory().fungibleResources();
        var byKind = new TreeMap<String, List<Stock>>();
        for (var id : ResourceStorageSelection.declaredContainers(state.inventory(), home, Set.of(ResourceStorageRole.WAREHOUSE))
                .stream().sorted().toList()) {
            if (ReferenceContainerCustody.blocksCanonicalUse(state, id) || ResidentDiningPlaces.pantry(state, home, id)) continue;
            var policy = state.inventory().containers().get(id).storage().orElseThrow();
            for (var shipment : state.shipments().receiving(id)) {
                if (!shipment.terminal()) incoming.computeIfAbsent(id, ignored -> new HashMap<>())
                        .merge(shipment.itemKind(), (long) shipment.quantity(), Math::addExact);
            }
            var account = FungibleResourceCustodySupport.accountAtContainer(state, id).orElse(null);
            if (account == null) continue;
            var totals = new TreeMap<String, Long>();
            account.lotQuantities().forEach((lotId, count) -> {
                var lot = resources.lots().get(lotId);
                if (lot.economicOwnerId().equals(home) && policy.filter().accepts(lot.itemKind()))
                    totals.merge(lot.itemKind(), (long) count, Math::addExact);
            });
            incoming.getOrDefault(id, Map.of()).forEach((kind, count) -> totals.merge(kind, count, Math::addExact));
            totals.forEach((kind, total) -> {
                if (ResourceStorageSelection.containers(state, home, kind, Set.of(ResourceStorageRole.WAREHOUSE)).contains(id))
                    byKind.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(
                            new Stock(policy.endpoint(), resources.unclaimedQuantity(account.id(), home, kind), total, policy.priority()));
            });
        }
        var result = new ArrayList<InternalHaulRequest>();
        for (var entry : byKind.entrySet()) {
            var stocks = entry.getValue();
            if (stocks.size() < 2) continue;
            var ranked = stocks.stream().sorted(Comparator.comparingInt(Stock::priority).reversed()
                    .thenComparing(Comparator.comparingLong(Stock::quantity).reversed())
                    .thenComparing(stock -> stock.endpoint().containerId())).toList();
            var target = ranked.getFirst();
            int room = (int) Math.max(0, 64 - target.quantity());
            if (room == 0) continue;
            for (var source : ranked.subList(1, ranked.size())) {
                // Never send an in-flight consolidation back in the opposite direction.
                if (source.available() == 0 || incoming.getOrDefault(source.endpoint().containerId(), Map.of())
                        .getOrDefault(entry.getKey(), 0L) > 0) continue;
                var request = InternalHaulRequest.select(state, home, source.endpoint(), target.endpoint(), entry.getKey(),
                        Math.min(room, source.available()));
                if (request.isPresent()) {
                    result.add(request.orElseThrow()); room -= request.orElseThrow().quantity();
                    if (room == 0) break;
                }
            }
        }
        return List.copyOf(result);
    }
}
