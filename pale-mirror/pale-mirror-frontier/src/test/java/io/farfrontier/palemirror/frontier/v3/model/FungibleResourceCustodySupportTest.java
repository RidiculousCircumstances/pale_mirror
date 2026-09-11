package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FungibleResourceCustodySupportTest {
    @Test
    void canonicalContainerQuerySelectsTheExactLotWithoutInspectingAnyPhysicalStack() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:fungible-query"), 91L));
        SubjectId container = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        SubjectId lotId = new SubjectId("lot:fungible-query"); SubjectId accountId = new SubjectId("custody:fungible-query");
        FungibleResourceLedger resources = FungibleResourceLedger.empty().issue(
                new ResourceLot(lotId, new SubjectId("settlement:1"), "minecraft:wheat", 64, "test", List.of()),
                new CustodyAccount(accountId, new ResourceCustody.Container(container), Map.of(lotId, 64), Map.of()));
        FrontierWorldState state = baseline.withInventory(baseline.inventory().withFungibleResources(resources));

        FungibleResourceCustodySupport.LotAtContainer found = FungibleResourceCustodySupport
                .firstAtContainer(state, container, "minecraft:wheat", 64).orElseThrow();

        assertEquals(accountId, found.accountId());
        assertEquals(lotId, found.lot().id());
        assertTrue(FungibleResourceCustodySupport.firstAtContainer(state, container, "minecraft:wheat", 65).isEmpty());
        assertTrue(FungibleResourceCustodySupport.firstAtContainer(state, container, "minecraft:bread", 1).isEmpty());
    }
}
