package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductiveResourceStockTest {
    @Test void actualTitleQuantityAndCustodyRemainIndependentAcrossOwnerTransfersAndBranches() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:productive-stock"), 41));
        var home = state.bootstrap().settlements().getFirst().id(); var other = state.bootstrap().settlements().get(1).id();
        var actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var lot = new ResourceLot(new SubjectId("lot:productive-title"), home, "minecraft:gold_ingot", 10, "fixture:stock", List.of());
        var account = new SubjectId("custody:productive-title");
        var ledger = state.inventory().fungibleResources().issue(lot,
                new CustodyAccount(account, new ResourceCustody.Actor(actor), Map.of(lot.id(), 10), Map.of()));
        var foreign = new ResourceLot(new SubjectId("lot:productive-foreign"), other, lot.itemKind(), 7, "fixture:foreign", List.of());
        ledger = ledger.issue(foreign, new CustodyAccount(new SubjectId("custody:productive-foreign"),
                new ResourceCustody.Actor(actor), Map.of(foreign.id(), 7), Map.of()));
        var excluded = List.of(new ResourceCustody.Player(new UUID(0, 1)), new ResourceCustody.WorldCarrier(new UUID(0, 2)),
                new ResourceCustody.Ground(new UUID(0, 3), new BlockPosition(1, 64, 1)));
        for (int i = 0; i < excluded.size(); i++) {
            var outside = new ResourceLot(new SubjectId("lot:productive-outside-" + i), home, lot.itemKind(), 5, "fixture:outside", List.of());
            ledger = ledger.issue(outside, new CustodyAccount(new SubjectId("custody:productive-outside-" + i),
                    excluded.get(i), Map.of(outside.id(), 5), Map.of()));
        }
        var depot = FrontierWorldState.depotId(home);
        var item = new ExactItemStack(new SubjectId("item:productive-exact"), home, lot.itemKind(), 3,
                new InventoryCustody.ContainerSlot(depot, state.inventory().firstFreeSlot(depot).orElseThrow()));
        var before = state.inventory().withFungibleResources(ledger).store(item);
        assertEquals(13, ProductiveResourceStock.held(before, home, lot.itemKind()));
        assertEquals(7, ProductiveResourceStock.held(before, other, lot.itemKind()));
        // A legal title-only ledger replacement need not touch its holder row.
        var transferred = new FungibleResourceLedger(ImmutableLookupMap.withEntry(ledger.lots(), lot.id(), lot.withEconomicOwner(other)),
                ledger.claims(), ledger.accounts(), ledger.bindings());
        var after = before.withFungibleResources(transferred);
        assertSame(before.fungibleResources().accounts(), after.fungibleResources().accounts());
        assertEquals(3, ProductiveResourceStock.held(after, home, lot.itemKind()));
        assertEquals(17, ProductiveResourceStock.held(after, other, lot.itemKind()));
        var consumed = after.withFungibleResources(transferred.destroy(account, Map.of(lot.id(), 4), Map.of()));
        assertEquals(13, ProductiveResourceStock.held(consumed, other, lot.itemKind()));
        var removed = consumed.withFungibleResources(consumed.fungibleResources().destroy(account, Map.of(lot.id(), 6), Map.of()));
        assertEquals(7, ProductiveResourceStock.held(removed, other, lot.itemKind()));
        assertEquals(13, ProductiveResourceStock.held(before, home, lot.itemKind()));
        assertEquals(7, ProductiveResourceStock.held(before, other, lot.itemKind()));
        assertEquals(17, ProductiveResourceStock.held(after, other, lot.itemKind()));
    }
}
