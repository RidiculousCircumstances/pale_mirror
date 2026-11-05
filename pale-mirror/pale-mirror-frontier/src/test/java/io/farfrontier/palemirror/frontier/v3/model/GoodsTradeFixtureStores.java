package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Ledger test preconditions: explicit custom mixed warehouses, independent of kitchen defaults. */
final class GoodsTradeFixtureStores {
    static FrontierWorldState prepare(FrontierWorldState state, List<SubjectId> stores) {
        var inventory = state.inventory(); var containers = new HashMap<>(inventory.containers());
        for (var id : stores) {
            var record = containers.get(id); var policy = record.storage().orElseThrow();
            containers.put(id, record.withStorage(new ResourceStoragePolicy(policy.endpoint(), ResourceStorageFilter.all(), policy.priority(), policy.role())));
        }
        return state.withInventory(new ExactInventory(containers, inventory.items(), inventory.cargo(), inventory.playerItems(),
                inventory.worldCarrierItems(), inventory.conflicts(), inventory.surfaces(), inventory.economics(), inventory.fungibleResources()));
    }
    static FrontierWorldState seed(FrontierWorldState state, SubjectId owner, SubjectId container, String kind, int count, SubjectId lot) {
        var resources = state.inventory().fungibleResources();
        var target = FungibleResourceCustodySupport.accountAtContainer(state, container);
        var staging = new SubjectId("custody:fixture-" + lot.value().replace(':', '-'));
        var account = target.map(CustodyAccount::id).orElse(ReferenceContainerCustody.scopeId(container));
        var source = target.isPresent() ? staging : account;
        resources = resources.issue(new ResourceLot(lot, owner, kind, count, "fixture:warehouse-stock", List.of()),
                new CustodyAccount(source, new ResourceCustody.Container(container), Map.of(lot, count), Map.of()));
        if (target.isPresent()) resources = resources.transfer(source, account, Map.of(lot, count), Map.of());
        return state.withInventory(state.inventory().withFungibleResources(resources));
    }
    private GoodsTradeFixtureStores() { }
}
