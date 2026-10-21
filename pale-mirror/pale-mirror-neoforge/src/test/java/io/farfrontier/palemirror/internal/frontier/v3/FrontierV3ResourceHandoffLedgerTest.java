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
        var tag = new CompoundTag(); tag.putInt("format", 9); tag.putLong("nextSequence", 1);
        tag.put("entityDrops", new ListTag());
        tag.put("containerDrops", new ListTag());
        tag.put("lossOperations", new ListTag()); tag.put("handoffOperations", new ListTag());
        tag.put("pending", new ListTag()); tag.put("actorPickups", new ListTag()); tag.put("groundLosses", new ListTag());
        return FrontierV3ResourceHandoffLedger.load(tag, null);
    }
    @Test void removalAndExactToolDropRecoverTheirOwnDeclarationsAndRejectCompetingAuthority() {
        var journal = empty(); var container = new SubjectId("container:mine-original");
        var witness = new FrontierV3ContainerRemovalWitness(container, new SubjectId("settlement:6"), new BlockPosition(-77, 61, 2), 7, 3);
        var id = UUID.randomUUID();
        var drop = new ExactItemCustodyChanged(new SubjectId("item:mine-tool"), new InventoryCustody.ContainerSlot(container, 2),
                new InventoryCustody.WorldCarrier(id));
        var port = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.TERRAIN, "0", 1);
        var birth = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, id.toString(), 2);
        var operation = FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.CONTAINER_DROP, FrontierExecutionSubjects.PHYSICAL_EXECUTOR,
                UUID.randomUUID().toString(), "exact-source", Set.of(port, birth)), 1);
        var draft = new FrontierV3ContainerDropTransaction(container, 2, operation, List.of(), List.of(drop));
        journal.prepareRemoval(witness); journal.prepareDrop(draft);
        var recovered = FrontierV3ResourceHandoffLedger.load(journal.save(new CompoundTag(), null), null);
        assertEquals(List.of(witness), recovered.removals()); assertEquals(List.of(draft), recovered.containerDrops());
        assertTrue(recovered.removes(container)); assertTrue(recovered.blocks(id));
        assertTrue(recovered.dropsFrom(container)); assertFalse(recovered.canPrepareDrop(container, 2));
        assertTrue(recovered.canPrepareDrop(container, 3), "unrelated slot is not fenced by this source");
        assertThrows(IllegalArgumentException.class, () -> recovered.prepareRemoval(witness));
        assertThrows(IllegalArgumentException.class, () -> recovered.prepareDrop(draft));
        assertTrue(draft.operation().applied().isEmpty(), "a complete plan does not prove physical execution");
        var applied = draft.bind(operation.applied(operation.nativeReceipt(port)).applied(operation.nativeReceipt(birth)));
        recovered.updateDrop(draft, applied);
        assertEquals(List.of(applied), FrontierV3ResourceHandoffLedger.load(recovered.save(new CompoundTag(), null), null).containerDrops());
        assertThrows(IllegalArgumentException.class, () -> recovered.updateDrop(applied, draft));
        assertThrows(IllegalArgumentException.class, () -> recovered.retireRemoval(new FrontierV3ContainerRemovalWitness(
                container, witness.owner(), witness.position(), 8, 3)));
        var obsolete = journal.save(new CompoundTag(), null); obsolete.putInt("format", 4);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceHandoffLedger.load(obsolete, null));
        recovered.retireDrop(applied); recovered.retireRemoval(witness);
        assertFalse(recovered.removes(container)); assertFalse(recovered.blocks(id));
        assertFalse(recovered.dropsFrom(container)); assertTrue(recovered.canPrepareDrop(container, 2));
    }
    @Test void entityDropOutboxRecoversExactOutcomeAndMonotonicOperationWithoutInferringCompletion() {
        var actor = new SubjectId("resident:drop-owner"); var entity = UUID.randomUUID();
        var consequence = new UnitInventoryDispositionObserved(new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(actor, 4),
                new SubjectId("custody:drop-owner"), 2, UnitInventoryDispositionObserved.Outcome.WORLD_DROP,
                Optional.of(new ResourceCustody.Ground(entity, new BlockPosition(1, 64, 1))));
        var source = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, UUID.randomUUID().toString(), 3);
        var birth = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, entity.toString(), 1);
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.ENTITY_RESOURCE_DROP,
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR, UUID.randomUUID().toString(), FrontierV3OperationConfirmation.fingerprint(consequence), Set.of(source, birth));
        var operation = FrontierV3OperationReceipt.prepare(declaration, 4);
        var draft = new FrontierV3EntityResourceDropTransaction(source, birth, operation, consequence);
        var journal = empty(); journal.prepareEntityDrop(draft);
        var recovered = FrontierV3ResourceHandoffLedger.load(journal.save(new CompoundTag(), null), null);
        assertEquals(List.of(draft), recovered.entityDrops()); assertTrue(recovered.blocks(entity));
        assertTrue(recovered.entityDrops().getFirst().operation().applied().isEmpty(), "a retained plan is not native completion");
        assertThrows(IllegalArgumentException.class, () -> recovered.prepareEntityDrop(draft));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3EntityResourceDropTransaction(source, birth,
                FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.GROUND_MERGE,
                        declaration.owner(), declaration.operation(), declaration.fingerprint(), declaration.participants()), 4), consequence));
        var altered = new UnitInventoryDispositionObserved(consequence.body(), consequence.accountId(), 3, consequence.outcome(), consequence.ground());
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3EntityResourceDropTransaction(source, birth, operation, altered));
        var malformed = draft.save(); malformed.putString("destination", source.identity());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3EntityResourceDropTransaction.read(malformed));
        var applied = draft.bind(operation.applied(operation.nativeReceipt(source)).applied(operation.nativeReceipt(birth)));
        recovered.updateEntityDrop(draft, applied);
        var next = FrontierV3ResourceHandoffLedger.load(recovered.save(new CompoundTag(), null), null);
        assertEquals(List.of(applied), next.entityDrops());
        assertThrows(IllegalArgumentException.class, () -> next.updateEntityDrop(applied, draft));
        next.retireEntityDrop(applied); assertFalse(next.blocks(entity));
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
        var indexed = FrontierV3ResourceHandoffLedger.load(journal.save(new CompoundTag(), null), null);
        for (int i = 0; i < ids.size(); i++) {
            var id = ids.get(i); var receipt = indexed.pending().get(id);
            assertTrue(indexed.blocks(id)); assertTrue(indexed.blocksAccount(accountId));
            assertTrue(indexed.blocksAccount(receipt.destinationAccount().id()));
            indexed.retire(id, receipt);
            assertFalse(indexed.blocks(id)); assertFalse(indexed.blocksAccount(receipt.destinationAccount().id()));
            assertEquals(i < ids.size() - 1, indexed.blocksAccount(accountId),
                    "retiring one fragment cannot release the source still used by remaining fragments");
        }
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
        var nativeJournal = empty();
        var nativePort = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITY_COLUMN, "0", 2);
        var nativeOperation = FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.GROUND_MERGE, FrontierExecutionSubjects.PHYSICAL_EXECUTOR,
                UUID.randomUUID().toString(), FrontierV3OperationConfirmation.fingerprint(first), Set.of(nativePort)), 4);
        nativeJournal.prepareNative(ids.getFirst(), first, nativeOperation);
        assertThrows(IllegalArgumentException.class, () -> nativeJournal.prepareNative(UUID.randomUUID(), first, nativeOperation));
        var nativeRecovered = FrontierV3ResourceHandoffLedger.load(nativeJournal.save(new CompoundTag(), null), null);
        assertEquals(Optional.of(nativeOperation), nativeRecovered.handoffOperation(ids.getFirst()));
        assertThrows(IllegalArgumentException.class, () -> nativeRecovered.bindHandoffOperation(ids.getFirst(),
                FrontierV3OperationReceipt.prepare(nativeOperation.declaration(), 5)), "foreign operation identity cannot replace the admitted attempt");
        var nativeApplied = nativeOperation.applied(nativeOperation.nativeReceipt(nativePort));
        nativeRecovered.bindHandoffOperation(ids.getFirst(), nativeApplied);
        var nativeClosed = FrontierV3ResourceHandoffLedger.load(nativeRecovered.save(new CompoundTag(), null), null);
        assertEquals(Optional.of(nativeApplied), nativeClosed.handoffOperation(ids.getFirst()));
        var malformedLink = nativeClosed.save(new CompoundTag(), null);
        malformedLink.getList("handoffOperations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putUUID("witness", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceHandoffLedger.load(malformedLink, null));
        nativeClosed.retire(ids.getFirst(), first); assertTrue(nativeClosed.handoffOperation(ids.getFirst()).isEmpty());
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
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.PLAYER, pickup.playerId().toString(), 1);
        var operation = FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.PLAYER_GROUND_PICKUP, pickup.economicOwner(), pickup.witnessId().toString(),
                FrontierV3OperationConfirmation.fingerprint(pickup), Set.of(participant)), 1);
        assertThrows(IllegalArgumentException.class, () -> exitRecovered.bindLossOperation(pickup, operation));
        var applied = operation.applied(operation.nativeReceipt(participant));
        exitRecovered.bindLossOperation(pickup, applied);
        var linked = FrontierV3ResourceHandoffLedger.load(exitRecovered.save(new CompoundTag(), null), null);
        assertEquals(Optional.of(applied), linked.lossOperation(pickup));
        var foreign = linked.save(new CompoundTag(), null);
        foreign.getList("lossOperations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putUUID("witness", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceHandoffLedger.load(foreign, null));
        exitRecovered.retireLoss(pickup); assertFalse(exitRecovered.blocks(ids.getFirst()));
        assertFalse(exitRecovered.blocksAccount(groundAccount.id()));
        var admittedPickup = new FrontierV3ResourceHandoffLedger.Pickup(new SubjectId("work:test-pickup"), groundAccount.id(),
                ids.getFirst(), io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL, 1, "exact-pickup");
        exitRecovered.preparePickup(admittedPickup);
        var recoveredPickup = FrontierV3ResourceHandoffLedger.load(exitRecovered.save(new CompoundTag(), null), null);
        assertTrue(recoveredPickup.blocks(admittedPickup.entity())); assertTrue(recoveredPickup.blocksAccount(admittedPickup.account()));
        assertThrows(IllegalArgumentException.class, () -> recoveredPickup.prepare(ids.getFirst(), first));
        recoveredPickup.retirePickup(admittedPickup);
        assertFalse(recoveredPickup.blocks(admittedPickup.entity())); assertFalse(recoveredPickup.blocksAccount(admittedPickup.account()));
        exitRecovered.retirePickup(admittedPickup);
        assertTrue(exitRecovered.lossOperation(pickup).isEmpty());
        var destroyed = new GroundResourceDestroyed(groundAccount.id(), (ResourceCustody.Ground) groundAccount.custody(),
                1, owner, UUID.randomUUID(), "KILLED", groundAccount.lotQuantities(), Set.of());
        exitRecovered.prepareLoss(destroyed);
        var column = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITY_COLUMN, "0", 1);
        var destruction = FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.GROUND_DESTRUCTION, owner, destroyed.witnessId().toString(),
                FrontierV3OperationConfirmation.fingerprint(destroyed), Set.of(column)), 2);
        assertThrows(IllegalArgumentException.class, () -> exitRecovered.bindLossOperation(destroyed, applied),
                "player pickup authority cannot acknowledge native destruction");
        assertThrows(IllegalArgumentException.class, () -> exitRecovered.bindLossOperation(destroyed, destruction));
        var removed = destruction.applied(destruction.nativeReceipt(column)); exitRecovered.bindLossOperation(destroyed, removed);
        var durableLoss = FrontierV3ResourceHandoffLedger.load(exitRecovered.save(new CompoundTag(), null), null);
        assertEquals(Optional.of(removed), durableLoss.lossOperation(destroyed));
        durableLoss.retireLoss(destroyed); assertTrue(durableLoss.lossOperation(destroyed).isEmpty());
        var obsolete = exits.save(new CompoundTag(), null); obsolete.putInt("format", 3);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceHandoffLedger.load(obsolete, null));
        journal.finish(); // Offline readFile returns a read-only image, not a live writable store.
    }
}
