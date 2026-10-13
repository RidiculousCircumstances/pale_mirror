package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Finite preconditions: real buyer provisions and seller labour disabled, never a pre-created sale or trip. */
public final class BuyerCollectionFixture {
    private BuyerCollectionFixture() { }
    public static FrontierWorldState initial(WorldId world, long seed) {
        var state = ConsolidatedTradeFixture.initial(world, seed, false, FrontierRulesets.marketQuarryGraybox());
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var buyer = state.companies().goodsTrade().participants().participants().get(seller.known().getFirst().party().id());
        var ledger = state.inventory().fungibleResources();
        var destination = FungibleResourceCustodySupport.accountAtContainer(state, buyer.endpoint().containerId()).orElseThrow();
        var lot = new SubjectId("lot:collection-provisions"); var staging = new SubjectId("custody:collection-provisions");
        ledger = ledger.issue(new ResourceLot(lot, buyer.party().id(), "minecraft:bread", 384, "fixture:collection-provisions", List.of()),
                new CustodyAccount(staging, buyer.endpoint().custody(), Map.of(lot, 384), Map.of()));
        ledger = ledger.transfer(staging, destination.id(), Map.of(lot, 384), Map.of());
        // Source stock must exceed the actual protected stone reserve; do not wait for hunger to create a different sale.
        var export = new SubjectId("lot:collection-export-stone");
        var exportStage = new SubjectId("custody:collection-export-stone");
        var source = FungibleResourceCustodySupport.accountAtContainer(state, seller.endpoint().containerId()).orElseThrow();
        // Keep the real 27-slot chest capacity: replace four surplus bread stacks
        // with eight stone stacks, not an over-capacity or special test container.
        ledger = ledger.destroy(source.id(), Map.of(new SubjectId("lot:trip-test/minecraft-bread"), 256), Map.of());
        ledger = ledger.issue(new ResourceLot(export, seller.party().id(), "minecraft:cobblestone", 512,
                "fixture:finite-export-stock", List.of()), new CustodyAccount(exportStage, seller.endpoint().custody(), Map.of(export, 512), Map.of()));
        ledger = ledger.transfer(exportStage, source.id(), Map.of(export, 512), Map.of());
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(ledger))
                .strategicPlans(state.strategicPlans().withWorkPermissions(seller.party().id(), ResidentWorkPermissions.none())));
    }
}
