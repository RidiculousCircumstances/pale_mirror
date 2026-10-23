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
        var lot = new SubjectId("lot:collection-provisions");
        // Empty accounts retire with their last lot; an empty physical chest remains a valid destination.
        var existing = FungibleResourceCustodySupport.accountAtContainer(state, FrontierWorldState.depotId(buyer.settlementId()));
        var staging = existing.isPresent() ? new SubjectId("custody:collection-provisions")
                : ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(buyer.settlementId()));
        var destination = existing.map(CustodyAccount::id).orElse(staging);
        // Enough to provision the journey, not four days of finished food that
        // legitimately eliminate every input purchase under net requirements.
        ledger = ledger.issue(new ResourceLot(lot, buyer.party().id(), "minecraft:bread", 32, "fixture:collection-provisions", List.of()),
                new CustodyAccount(staging, new ResourceCustody.Container(FrontierWorldState.depotId(buyer.settlementId())), Map.of(lot, 32), Map.of()));
        if (!destination.equals(staging)) ledger = ledger.transfer(staging, destination, Map.of(lot, 32), Map.of());
        // Finite additional sale stock; an unused commodity does not create buyer demand.
        var export = new SubjectId("lot:collection-export-stone");
        var exportStage = new SubjectId("custody:collection-export-stone");
        var source = FungibleResourceCustodySupport.accountAtContainer(state, FrontierWorldState.depotId(seller.settlementId())).orElseThrow();
        // Keep the real 27-slot chest capacity: replace six surplus bread stacks
        // with eight stone stacks while retaining the initial individual equipment, not an over-capacity or special test container.
        ledger = ledger.destroy(source.id(), Map.of(new SubjectId("lot:trip-test/minecraft-bread"), 384), Map.of());
        ledger = ledger.issue(new ResourceLot(export, seller.party().id(), "minecraft:cobblestone", 512,
                "fixture:finite-export-stock", List.of()), new CustodyAccount(exportStage, new ResourceCustody.Container(FrontierWorldState.depotId(seller.settlementId())), Map.of(export, 512), Map.of()));
        ledger = ledger.transfer(exportStage, source.id(), Map.of(export, 512), Map.of());
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(ledger))
                .strategicPlans(state.strategicPlans().withWorkPermissions(seller.party().id(), ResidentWorkPermissions.none())));
    }
}
