package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FungibleResourceLedgerTest {
    private static final SubjectId OWNER = new SubjectId("settlement:one");
    private static final SubjectId LOT = new SubjectId("lot:bread-genesis");
    private static final SubjectId DEPOT = new SubjectId("container:depot");
    private static final SubjectId DEPOT_ACCOUNT = new SubjectId("custody:depot");

    @Test
    void splitPartialMoveAndMergePreserveOneExactFungibleTotalWithoutStackIdentity() {
        FungibleResourceLedger issued = issue(10);
        ResourceLot child = new ResourceLot(new SubjectId("lot:bread-child"), OWNER, "minecraft:bread", 4, "bootstrap", List.of(LOT));
        FungibleResourceLedger split = issued.split(DEPOT_ACCOUNT, LOT, child, 4);
        CustodyAccount player = new CustodyAccount(new SubjectId("custody:player"), new ResourceCustody.Player(uuid(2)),
                Map.of(child.id(), 4), Map.of());
        FungibleResourceLedger moved = split.transferToNewAccount(DEPOT_ACCOUNT, player);

        assertEquals(10, moved.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(6, moved.accounts().get(DEPOT_ACCOUNT).lotQuantities().get(LOT));
        assertEquals(4, moved.accounts().get(player.id()).lotQuantities().get(child.id()));

        ResourceLot merged = new ResourceLot(new SubjectId("lot:bread-merged"), OWNER, "minecraft:bread", 10, "bootstrap", List.of(LOT, child.id()));
        FungibleResourceLedger returned = moved.transfer(player.id(), DEPOT_ACCOUNT, Map.of(child.id(), 4), Map.of()).merge(DEPOT_ACCOUNT, LOT, child.id(), merged);
        assertEquals(10, returned.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(Map.of(merged.id(), 10), returned.accounts().get(DEPOT_ACCOUNT).lotQuantities());
    }

    @Test
    void reservedPortionMovesExactlyAndCannotBeDoubleAllocatedOrOverConsumed() {
        FungibleResourceLedger reserved = issue(10).reserve(new ClaimAllocation(new SubjectId("claim:provision"), new SubjectId("process:provision"), OWNER,
                "minecraft:bread", 4), DEPOT_ACCOUNT);
        CustodyAccount player = new CustodyAccount(new SubjectId("custody:player"), new ResourceCustody.Player(uuid(3)), Map.of(LOT, 4),
                Map.of(new SubjectId("claim:provision"), 4));
        FungibleResourceLedger stolen = reserved.transferToNewAccount(DEPOT_ACCOUNT, player);

        assertEquals(10, stolen.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(4, stolen.accounts().get(player.id()).claimQuantities().get(new SubjectId("claim:provision")));
        assertThrows(IllegalArgumentException.class, () -> reserved.reserve(new ClaimAllocation(new SubjectId("claim:duplicate"), new SubjectId("process:other"), OWNER,
                "minecraft:bread", 8), DEPOT_ACCOUNT));
        assertThrows(IllegalArgumentException.class, () -> stolen.destroy(player.id(), Map.of(LOT, 5), Map.of(new SubjectId("claim:provision"), 4)));
    }

    @Test
    void transientPhysicalBindingsAcceptARealSplitButFenceStaleOrDuplicateStackEvidence() {
        FungibleResourceLedger issued = issue(10);
        PhysicalStackBinding first = binding("binding:one", 7L, 6, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)));
        PhysicalStackBinding second = binding("binding:two", 7L, 4, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 1)));
        FungibleResourceLedger split = issued.rebind(DEPOT_ACCOUNT, 7L, List.of(first, second));

        assertEquals(10, split.bindings().values().stream().mapToInt(PhysicalStackBinding::quantity).sum());
        assertThrows(IllegalStateException.class, () -> split.destroy(DEPOT_ACCOUNT, Map.of(LOT, 1), Map.of()),
                "an observer-free HOT hopper/container binding must fence concurrent COLD spending");
        assertThrows(IllegalArgumentException.class, () -> split.releaseBindings(DEPOT_ACCOUNT, 6L));
        FungibleResourceLedger released = split.releaseBindings(DEPOT_ACCOUNT, 7L);
        assertEquals(9, released.destroy(DEPOT_ACCOUNT, Map.of(LOT, 1), Map.of()).totalQuantity(OWNER, "minecraft:bread"));
        assertThrows(IllegalArgumentException.class, () -> split.rebind(DEPOT_ACCOUNT, 8L, List.of(first)));
        assertThrows(IllegalArgumentException.class, () -> issued.rebind(DEPOT_ACCOUNT, 7L, List.of(first,
                binding("binding:duplicate-address", 7L, 4, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0))))));
        assertThrows(IllegalArgumentException.class, () -> issued.rebind(DEPOT_ACCOUNT, 7L, List.of(binding("binding:over", 7L, 11,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0))))));
    }

    @Test
    void snapshotRoundTripPreservesLotsClaimsAccountsAndTransientBindingsExactly() {
        WorldId world = new WorldId("frontier:fungible-ledger-round-trip");
        FrontierWorldState baseline = new FrontierWorldStateCodec().decode(FrontierEngines
                .create(FrontierWorldRuntimeDefinition.configuration(world, 17L)).checkpoint().canonicalState());
        SubjectId containerId = new SubjectId("container:1-depot"); SubjectId owner = new SubjectId("settlement:1");
        SubjectId lotId = new SubjectId("lot:snapshot"); SubjectId accountId = new SubjectId("custody:snapshot");
        ResourceLot lot = new ResourceLot(lotId, owner, "minecraft:bread", 10, "snapshot", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Container(containerId), Map.of(lotId, 10), Map.of());
        PhysicalStackBinding binding = new PhysicalStackBinding(new SubjectId("binding:snapshot"), accountId,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(containerId, 1)), 3L, "minecraft:bread", Map.of(lotId, 10), Map.of());
        FungibleResourceLedger resources = FungibleResourceLedger.empty().issue(lot, account)
                .reserve(new ClaimAllocation(new SubjectId("claim:snapshot"), new SubjectId("process:snapshot"), owner, "minecraft:bread", 4), accountId)
                .rebind(accountId, 3L, List.of(binding));
        FrontierWorldState retained = baseline.withInventory(baseline.inventory().withFungibleResources(resources));

        assertEquals(resources, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(retained)).inventory().fungibleResources());
    }

    private static FungibleResourceLedger issue(int quantity) {
        ResourceLot lot = new ResourceLot(LOT, OWNER, "minecraft:bread", quantity, "bootstrap", List.of());
        CustodyAccount account = new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT), Map.of(LOT, quantity), Map.of());
        return FungibleResourceLedger.empty().issue(lot, account);
    }

    private static PhysicalStackBinding binding(String id, long epoch, int quantity, PhysicalStackAddress address) {
        return new PhysicalStackBinding(new SubjectId(id), DEPOT_ACCOUNT, address, epoch, "minecraft:bread", Map.of(LOT, quantity), Map.of());
    }

    private static UUID uuid(int tail) { return UUID.fromString("00000000-0000-0000-0000-00000000000" + tail); }
}
