package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FungibleObservedTransformationTest {
    private static final SubjectId OWNER = new SubjectId("settlement:1");
    private static final SubjectId DEPOT = new SubjectId("container:1-depot");
    private static final SubjectId ACCOUNT = new SubjectId("custody:recipe");
    private static final SubjectId WHEAT = new SubjectId("lot:recipe-wheat");
    private static final SubjectId CLAIM = new SubjectId("claim:recipe");
    private static final ResourceLot OUTPUT = new ResourceLot(new SubjectId("lot:recipe-bread"), OWNER,
            "minecraft:bread", 32, "recipe:bread", List.of(WHEAT));

    @Test
    void observedRecipePreservesUnspentStockAndEpochAndConsumesClaimExactlyOnce() {
        FungibleResourceLedger before = fixture();
        var after = before.transformObserved(ACCOUNT, 7L, Map.of(WHEAT, 32), Map.of(CLAIM, 32), OUTPUT, result());
        assertEquals(32, after.totalQuantity(OWNER, "minecraft:wheat"));
        assertEquals(32, after.totalQuantity(OWNER, "minecraft:bread"));
        assertFalse(after.claims().containsKey(CLAIM));
        assertEquals(Map.of(WHEAT, 32, OUTPUT.id(), 32), after.accounts().get(ACCOUNT).lotQuantities());
        assertEquals(2, after.bindings().size());
        assertTrue(after.bindings().values().stream().allMatch(binding -> binding.authorityEpoch() == 7L));
        assertEquals(List.of(WHEAT), after.lots().get(OUTPUT.id()).lineage());
        assertThrows(IllegalArgumentException.class, () -> after.transformObserved(ACCOUNT, 7L,
                Map.of(WHEAT, 32), Map.of(CLAIM, 32), OUTPUT, result()));
        assertEquals(64, before.totalQuantity(OWNER, "minecraft:wheat"));
        assertEquals(32, before.claims().get(CLAIM).quantity());
    }

    @Test
    void wrongEpochIncompleteLayoutAndUnclaimedRecipeCannotPublishAnyResult() {
        FungibleResourceLedger before = fixture();
        assertThrows(IllegalArgumentException.class, () -> before.transformObserved(ACCOUNT, 8L,
                Map.of(WHEAT, 32), Map.of(CLAIM, 32), OUTPUT, result()));
        assertThrows(IllegalArgumentException.class, () -> before.transformObserved(ACCOUNT, 7L,
                Map.of(WHEAT, 32), Map.of(CLAIM, 32), OUTPUT, List.of(stack(1, "minecraft:bread", 32))));
        assertThrows(IllegalArgumentException.class, () -> before.transformObserved(ACCOUNT, 7L,
                Map.of(WHEAT, 32), Map.of(), OUTPUT, result()));
        assertEquals(fixture(), before);
    }

    private static FungibleResourceLedger fixture() {
        var resources = FungibleResourceLedger.empty().issue(new ResourceLot(WHEAT, OWNER, "minecraft:wheat", 64, "test", List.of()),
                new CustodyAccount(ACCOUNT, new ResourceCustody.Container(DEPOT), Map.of(WHEAT, 64), Map.of()))
                .reserve(new ClaimAllocation(CLAIM, new SubjectId("job:recipe"), OWNER, "minecraft:wheat", 32), ACCOUNT);
        return resources.rebind(ACCOUNT, 7L, FungiblePhysicalObservation.bind(resources, ACCOUNT, 7L,
                List.of(stack(0, "minecraft:wheat", 64))));
    }

    private static List<FungiblePhysicalObservation.Stack> result() {
        return List.of(stack(0, "minecraft:wheat", 32), stack(1, "minecraft:bread", 32));
    }

    private static FungiblePhysicalObservation.Stack stack(int slot, String kind, int quantity) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(DEPOT, slot)), kind, quantity);
    }
}
