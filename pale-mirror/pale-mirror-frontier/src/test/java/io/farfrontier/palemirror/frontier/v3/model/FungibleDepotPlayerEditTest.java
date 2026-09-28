package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FungibleDepotPlayerEditTest {
    private static final SubjectId OWNER = new SubjectId("settlement:one");
    private static final SubjectId DEPOT = FrontierWorldState.depotId(OWNER);
    private static final SubjectId ACCOUNT = new SubjectId("custody:settlement-one-depot");
    private static final SubjectId FREE = new SubjectId("lot:free-bread");
    private static final SubjectId HELD = new SubjectId("lot:held-bread");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000141");
    private static final UUID INTERACTION = UUID.fromString("00000000-0000-0000-0000-000000000142");

    @Test void multiStackWithdrawalNeedsNoPlayerSlotAndCannotSilentlyConsumeClaim() {
        FungibleResourceLedger cold = new FungibleResourceLedger(Map.of(
                FREE, new ResourceLot(FREE, OWNER, "minecraft:bread", 64, "bootstrap", List.of()),
                HELD, new ResourceLot(HELD, OWNER, "minecraft:bread", 8, "bootstrap", List.of())),
                Map.of(), Map.of(ACCOUNT, new CustodyAccount(ACCOUNT, new ResourceCustody.Container(DEPOT),
                        Map.of(FREE, 64, HELD, 8), Map.of())), Map.of());
        SubjectId claimId = new SubjectId("claim:held-bread");
        cold = cold.reserve(new ClaimAllocation(claimId, new SubjectId("job:held-bread"), OWNER,
                "minecraft:bread", 8, Map.of(HELD, 8), ClaimPurpose.EXTERNAL_RESERVATION), ACCOUNT);
        FungibleResourceLedger hot = cold.rebind(ACCOUNT, 2,
                FungiblePhysicalObservation.bind(cold, ACCOUNT, 2, List.of(stack(0, 64), stack(1, 8))));

        FungibleStockDepartureObserved exit = assertInstanceOf(FungibleStockDepartureObserved.class,
                FungibleDepotPlayerEdit.classify(hot, ACCOUNT, DEPOT, OWNER, 2, PLAYER, INTERACTION,
                        List.of(stack(0, 32), stack(1, 8))));
        assertEquals(Map.of(FREE, 32), exit.departedLots());
        assertEquals(40, hot.departObserved(ACCOUNT, 2, exit.departedLots(), exit.remaining())
                .totalQuantity(OWNER, "minecraft:bread"));
        assertThrows(IllegalArgumentException.class, () -> FungibleDepotPlayerEdit.classify(hot, ACCOUNT, DEPOT,
                OWNER, 2, PLAYER, INTERACTION, List.of(stack(0, 1))));
    }

    @Test void depositBecomesFreshLotOwnedBySettlement() {
        ResourceLot existing = new ResourceLot(FREE, OWNER, "minecraft:bread", 32, "bootstrap", List.of());
        FungibleResourceLedger cold = FungibleResourceLedger.empty().issue(existing,
                new CustodyAccount(ACCOUNT, new ResourceCustody.Container(DEPOT), Map.of(FREE, 32), Map.of()));
        FungibleResourceLedger hot = cold.rebind(ACCOUNT, 2,
                FungiblePhysicalObservation.bind(cold, ACCOUNT, 2, List.of(stack(0, 32))));

        FungibleStockContributionObserved gift = assertInstanceOf(FungibleStockContributionObserved.class,
                FungibleDepotPlayerEdit.classify(hot, ACCOUNT, DEPOT, OWNER, 2, PLAYER, INTERACTION,
                        List.of(stack(0, 48))));
        assertEquals(OWNER, gift.contribution().economicOwnerId());
        assertEquals(16, gift.contribution().quantity());
        assertEquals(48, hot.contributeObserved(ACCOUNT, 2, gift.contribution(), gift.observed())
                .totalQuantity(OWNER, "minecraft:bread"));
    }

    @Test void firstGiftMayCreateTheVacantDepotAccount() {
        SubjectId canonicalAccount = ReferenceContainerCustody.scopeId(DEPOT);
        var observed = List.of(stack(0, 16));
        var gift = assertInstanceOf(FungibleStockContributionObserved.class,
                FungibleDepotPlayerEdit.classify(FungibleResourceLedger.empty(), canonicalAccount,
                        DEPOT, OWNER, 3, PLAYER, INTERACTION, observed));
        var credited = FungibleResourceLedger.empty().contributeObserved(canonicalAccount, 3,
                gift.contribution(), observed);
        assertEquals(16, credited.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(observed, gift.observed());
    }

    private static FungiblePhysicalObservation.Stack stack(int slot, int count) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(DEPOT, slot)), "minecraft:bread", count);
    }
}
