package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FungibleWitnessedReturnTest {
    private static final SubjectId OWNER = new SubjectId("settlement:return");
    private static final SubjectId LOT = new SubjectId("lot:return");
    private static final SubjectId PLAYER_ACCOUNT = new SubjectId("custody:player-return");
    private static final SubjectId CONTAINER = new SubjectId("container:return");
    private static final SubjectId TARGET = new SubjectId("custody:return");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000009");

    @Test void causalReturnCreatesNoGiftAndRetainsPartialPlayerPortion() {
        var ledger = playerStock(); var source = ledger.bindings().values().iterator().next();
        var fact = FungiblePhysicalHandoff.returnObserved(ledger, source, 4, TARGET, CONTAINER, 7,
                List.of(stack(2, 6)));
        var result = apply(ledger, fact);
        assertEquals(10, result.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(ledger.lots(), result.lots());
        assertEquals(Map.of(LOT, 4), result.accounts().get(PLAYER_ACCOUNT).lotQuantities());
        assertEquals(Map.of(LOT, 6), result.accounts().get(TARGET).lotQuantities());
        assertThrows(IllegalArgumentException.class, () -> apply(result, fact));
        assertThrows(IllegalArgumentException.class, () -> FungiblePhysicalHandoff.returnObserved(ledger, source,
                4, TARGET, CONTAINER, 7, List.of(stack(2, 7))));
    }

    @Test void returnCanMergeAndSplitAcrossSlotsOfAnExistingContainer() {
        var first = playerStock(); var source = first.bindings().values().iterator().next();
        var deposited = apply(first, FungiblePhysicalHandoff.returnObserved(first, source, 4, TARGET, CONTAINER, 7,
                List.of(stack(2, 6))));
        var remainder = deposited.bindings().values().stream().filter(binding -> binding.accountId().equals(PLAYER_ACCOUNT))
                .findFirst().orElseThrow();
        var result = apply(deposited, FungiblePhysicalHandoff.returnObserved(deposited, remainder, 0, TARGET, CONTAINER, 7,
                List.of(stack(1, 8), stack(3, 2))));
        assertFalse(result.accounts().containsKey(PLAYER_ACCOUNT));
        assertEquals(Map.of(LOT, 10), result.accounts().get(TARGET).lotQuantities());
        assertEquals(1, result.lots().size());
        assertThrows(IllegalArgumentException.class, () -> FungiblePhysicalHandoff.returnObserved(deposited,
                new PhysicalStackBinding(remainder.id(), remainder.accountId(), remainder.address(), 99,
                        remainder.itemKind(), remainder.lotQuantities(), Map.of()), 0, TARGET, CONTAINER, 7, List.of(stack(0, 10))));
    }

    private static FungibleResourceLedger playerStock() {
        var ledger = FungibleResourceLedger.empty().issue(new ResourceLot(LOT, OWNER, "minecraft:bread", 10, "test:issued", List.of()),
                new CustodyAccount(PLAYER_ACCOUNT, new ResourceCustody.Player(PLAYER), Map.of(LOT, 10), Map.of()));
        return ledger.rebind(PLAYER_ACCOUNT, 3, FungiblePhysicalObservation.bind(ledger, PLAYER_ACCOUNT, 3,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.PlayerSlot(PLAYER, 9), "minecraft:bread", 10))));
    }
    private static FungiblePhysicalObservation.Stack stack(int slot, int quantity) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(CONTAINER, slot)), "minecraft:bread", quantity);
    }
    private static FungibleResourceLedger apply(FungibleResourceLedger ledger, FungibleResourceHandoffObserved fact) {
        return ledger.accounts().containsKey(fact.destinationAccount().id())
                ? ledger.transferObservedToExistingAccount(fact.sourceAccountId(), fact.destinationAccount().id(), fact.sourceEpoch(),
                    fact.destinationEpoch(), fact.lotQuantities(), fact.claimQuantities(), fact.remainingSource(), fact.destinationBindings())
                : ledger.transferObservedToNewAccount(fact.sourceAccountId(), fact.destinationAccount(), fact.sourceEpoch(),
                    fact.destinationEpoch(), fact.lotQuantities(), fact.claimQuantities(), fact.remainingSource(), fact.destinationBindings());
    }
}
