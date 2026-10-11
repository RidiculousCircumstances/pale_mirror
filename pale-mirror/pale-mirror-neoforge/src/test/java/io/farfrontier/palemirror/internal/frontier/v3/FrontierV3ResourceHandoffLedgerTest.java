package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A split Vanilla drop needs its causal order, not UUID order, including after journal recovery. */
class FrontierV3ResourceHandoffLedgerTest {
    @BeforeAll static void version() { net.minecraft.SharedConstants.tryDetectVersion(); }
    @TempDir Path directory;
    private static FrontierV3ResourceHandoffLedger empty() {
        var tag = new CompoundTag(); tag.putInt("format", 4); tag.putLong("nextSequence", 1);
        tag.put("pending", new ListTag()); tag.put("actorPickups", new ListTag()); tag.put("groundLosses", new ListTag());
        return FrontierV3ResourceHandoffLedger.load(tag, null);
    }
    @Test void fragmentedDropsRetainExactlyOneStockBalanceAndReplayInRecordedOrder() throws Exception {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:drop-fragments"), 20260918065L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
        var owner = initial.bootstrap().settlements().getFirst().id();
        var container = FrontierWorldState.depotId(owner); var accountId = new SubjectId("custody:fragment-source");
        var lot = new ResourceLot(new SubjectId("lot:fragment-source"), owner, "minecraft:cobblestone", 64, "test:bound-source", List.of());
        var resources = FungibleResourceLedger.empty().issue(lot,
                new CustodyAccount(accountId, new ResourceCustody.Container(container), Map.of(lot.id(), 64), Map.of()));
        var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 26));
        resources = resources.rebind(accountId, 7, FungiblePhysicalObservation.bind(resources, accountId, 7,
                List.of(new FungiblePhysicalObservation.Stack(address, lot.itemKind(), 64))));
        var state = initial.withInventory(initial.inventory().withFungibleResources(resources));
        var journal = empty(); var ids = List.of(new UUID(0, 30), new UUID(0, 20), new UUID(0, 10));
        var sizes = List.of(17, 23, 24);
        for (int index = 0; index < ids.size(); index++) {
            var projected = journal.projected(state).inventory().fungibleResources();
            var binding = projected.bindings().values().stream().filter(b -> b.accountId().equals(accountId)).findFirst().orElseThrow();
            var id = ids.get(index);
            var receipt = FungiblePhysicalHandoff.departToNew(projected, accountId, 7, binding, binding.quantity() - sizes.get(index),
                    new SubjectId("custody:drop-" + id), new ResourceCustody.Ground(id, new BlockPosition(1, 65, 1)), 1,
                    new PhysicalStackAddress.WorldEntity(id));
            journal.prepare(id, receipt);
        }
        assertEquals(ids, new ArrayList<>(journal.pending().keySet()));
        var finalResources = journal.projected(state).inventory().fungibleResources();
        assertFalse(finalResources.accounts().containsKey(accountId));
        assertEquals(64, finalResources.accounts().values().stream().filter(a -> a.custody() instanceof ResourceCustody.Ground)
                .flatMap(a -> a.lotQuantities().values().stream()).mapToInt(Integer::intValue).sum());
        assertEquals(lot, finalResources.lots().get(lot.id()));
        assertEquals(64, state.inventory().fungibleResources().accounts().get(accountId).lotQuantities().get(lot.id()), "preview must not publish its result");
        Path path = directory.resolve("handoffs.dat");
        var store = FrontierV3JournalStore.open(path, FrontierV3JournalStore.PHYSICAL);
        var world = new WorldId("frontier:drop-fragments"); var dimension = ResourceLocation.parse("minecraft:overworld");
        journal.attachJournal(path, world, dimension, store); journal.setDirty(); journal.save(path.toFile(), null);
        var recovered = FrontierV3JournaledSavedData.readFile(path, world.value(), dimension.toString(),
                FrontierV3ResourceHandoffLedgerTest::empty, FrontierV3ResourceHandoffLedger::load, null);
        assertEquals(ids, new ArrayList<>(recovered.pending().keySet()));
        assertEquals(journal.projected(state), recovered.projected(state));
        var first = recovered.pending().get(ids.getFirst());
        var afterFirst = FungibleResourceHandoffStateSupport.apply(state, first);
        assertEquals(journal.projected(state), recovered.projected(afterFirst), "canonical-first recovery must not replay the first physical effect");
        assertThrows(IllegalArgumentException.class, () -> recovered.prepare(ids.getFirst(), first));
        var invalid = journal.save(new CompoundTag(), null); invalid.getList("pending", 10).getCompound(1).putLong("sequence", 1);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceHandoffLedger.load(invalid, null));
        var groundState = journal.projected(state);
        var groundAccount = groundState.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody() instanceof ResourceCustody.Ground ground && ground.entityId().equals(ids.getFirst())).findFirst().orElseThrow();
        var pickup = GroundResourcePlayerWithdrawal.prepare(groundState, groundAccount.id(), UUID.randomUUID(), UUID.randomUUID(), 3);
        var exits = empty(); exits.prepareLoss(pickup);
        assertTrue(exits.blocks(ids.getFirst())); assertTrue(exits.blocksAccount(groundAccount.id()));
        assertThrows(IllegalArgumentException.class, () -> exits.prepareLoss(pickup));
        assertThrows(IllegalArgumentException.class, () -> exits.prepare(ids.getFirst(), first),
                "a handoff cannot borrow authority retained by a player withdrawal");
        var overlapping = journal.save(new CompoundTag(), null);
        overlapping.put("groundLosses", exits.save(new CompoundTag(), null).getList("groundLosses", 10));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceHandoffLedger.load(overlapping, null),
                "recovery must reject competing effect families just as live prepare does");
        var exitRecovered = FrontierV3ResourceHandoffLedger.load(exits.save(new CompoundTag(), null), null);
        assertEquals(List.of(pickup), exitRecovered.losses());
        exitRecovered.retireLoss(pickup); assertFalse(exitRecovered.blocks(ids.getFirst()));
        var obsolete = exits.save(new CompoundTag(), null); obsolete.putInt("format", 3);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceHandoffLedger.load(obsolete, null));
        journal.finish(); // Offline readFile returns a read-only image, not a live writable store.
    }
}
