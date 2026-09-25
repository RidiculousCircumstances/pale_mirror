package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoDepartureLedgerTest {
    private static FrontierV3CargoDeparture receipt(int identity, long epoch) {
        return new FrontierV3CargoDeparture(new SceneLeaseId("lease:cargo-" + identity), new SubjectId("cargo:test-" + identity),
                new UUID(0, identity), 0, epoch, new BodyPosition(1, 65, 2),
                IntStream.range(0, 27).mapToObj(ignored -> new CompoundTag()).toList());
    }

    @Test void exactReturnSurvivesRecoveryAndCannotRemoveAnotherObservation() {
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest(); var first = receipt(1, 1);
        assertTrue(ledger.record(first)); assertTrue(ledger.record(first));
        assertFalse(ledger.savedObservation(first));
        assertTrue(ledger.noLoadProofCandidate(first), "read-fenced receipt may enter disk proof, not release");
        assertTrue(ledger.confirmSavedObservation(first));
        var recovered = FrontierV3CargoDepartureLedger.load(ledger.save(new CompoundTag(), null), null);
        assertFalse(recovered.isDirty());
        assertTrue(recovered.savedObservation(first));
        assertTrue(recovered.noLoadRecoverableObservation(first));
        assertFalse(recovered.resolveExact(receipt(1, 2)));
        assertEquals(first, recovered.observation(first.entityId()).orElseThrow());
        assertTrue(recovered.resolveExact(first)); assertFalse(recovered.resolveExact(first));
        assertTrue(recovered.observation(first.entityId()).isEmpty());
        assertFalse(recovered.savedObservation(first));
    }

    @Test void legacyObservationIsUnconfirmedAndMalformedSavedMarkerCannotGrantRelease() {
        var first = receipt(1, 1);
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest();
        assertTrue(ledger.record(first));
        var legacy = ledger.save(new CompoundTag(), null);
        legacy.putInt("format", 1); legacy.remove("savedDepartures");
        assertFalse(FrontierV3CargoDepartureLedger.load(legacy, null).savedObservation(first));
        var missing = ledger.save(new CompoundTag(), null); missing.remove("savedDepartures");
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDepartureLedger.load(missing, null));
        var orphan = ledger.save(new CompoundTag(), null);
        var marker = new CompoundTag(); marker.putUUID("entity", receipt(2, 1).entityId());
        orphan.getList("savedDepartures", Tag.TAG_COMPOUND).add(marker);
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDepartureLedger.load(orphan, null));
        assertFalse(ledger.confirmSavedObservation(receipt(1, 2)));
        assertFalse(ledger.record(receipt(1, 2)));
        assertFalse(ledger.confirmSavedObservation(first), "contradictory observation must revoke confirmation");
    }

    @Test void storedReturnReadRevokesOldProofUntilANewUnloadIsSaved() {
        var receipt = receipt(1, 1);
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest();
        assertTrue(ledger.record(receipt)); assertTrue(ledger.confirmSavedObservation(receipt));
        assertTrue(ledger.markReturnRead(receipt.entityId()));
        assertTrue(ledger.returnRead(receipt.entityId()));
        assertFalse(ledger.savedObservation(receipt));
        assertFalse(ledger.noLoadRecoverableObservation(receipt));
        assertFalse(ledger.noLoadProofCandidate(receipt));
        assertFalse(ledger.confirmSavedObservation(receipt));
        var recovered = FrontierV3CargoDepartureLedger.load(ledger.save(new CompoundTag(), null), null);
        assertTrue(recovered.returnRead(receipt.entityId()));
        assertFalse(recovered.savedObservation(receipt));
        assertTrue(recovered.record(receipt), "new exact unload withdraws the old return-read fence");
        assertFalse(recovered.returnRead(receipt.entityId()));
        assertFalse(recovered.savedObservation(receipt), "old save marker cannot certify the new unload");
        assertTrue(recovered.confirmSavedObservation(receipt));
        assertTrue(recovered.savedObservation(receipt));
        assertTrue(recovered.noLoadRecoverableObservation(receipt));
    }

    @Test void returnReadFencesOnlyTheReceiptChunkAndExactSerializedEntity() {
        var receipt = receipt(1, 1);
        var cargo = FrontierV3CargoDepartureLedger.emptyForTest();
        var actors = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(cargo.record(receipt)); assertTrue(cargo.confirmSavedObservation(receipt));
        var unrelated = FrontierV3DepartureReturnReadFence.fenceStoredInventory(
                new ChunkPos(1, 0), new CompoundTag(), actors, cargo);
        assertArrayEquals(new boolean[] {false, false}, unrelated);
        assertTrue(cargo.savedObservation(receipt));

        var stored = new CompoundTag(); stored.putIntArray("Position", new int[] {0, 0});
        var entities = new ListTag();
        var other = new CompoundTag(); other.putUUID("UUID", new UUID(0, 2)); entities.add(other);
        stored.put("Entities", entities);
        assertArrayEquals(new boolean[] {false, false}, FrontierV3DepartureReturnReadFence.fenceStoredInventory(
                new ChunkPos(0, 0), stored, actors, cargo));
        assertTrue(cargo.savedObservation(receipt));
        assertThrows(IllegalStateException.class, () -> FrontierV3DepartureReturnReadFence.fenceStoredInventory(
                new ChunkPos(0, 0), new CompoundTag(), actors, cargo));

        var exact = new CompoundTag(); exact.putUUID("UUID", receipt.entityId()); entities.add(exact);
        assertArrayEquals(new boolean[] {false, true}, FrontierV3DepartureReturnReadFence.fenceStoredInventory(
                new ChunkPos(0, 0), stored, actors, cargo));
        assertTrue(cargo.returnRead(receipt.entityId()));
        assertFalse(cargo.noLoadRecoverableObservation(receipt));
    }

    @Test void priorSavedFormatLoadsWithoutInventingAReturnRead() {
        var receipt = receipt(1, 1);
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest();
        assertTrue(ledger.record(receipt)); assertTrue(ledger.confirmSavedObservation(receipt));
        var old = ledger.save(new CompoundTag(), null);
        old.putInt("format", 2); old.remove("returnReads");
        var recovered = FrontierV3CargoDepartureLedger.load(old, null);
        assertTrue(recovered.savedObservation(receipt));
        assertFalse(recovered.returnRead(receipt.entityId()));
        assertFalse(recovered.noLoadRecoverableObservation(receipt), "old format did not fence pre-load returns");
        var malformed = ledger.save(new CompoundTag(), null); malformed.remove("returnReads");
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDepartureLedger.load(malformed, null));
        var unguarded = ledger.save(new CompoundTag(), null); unguarded.remove("readFencedDepartures");
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDepartureLedger.load(unguarded, null));
    }

    @Test void savedConfirmationIsWrittenToTheActualSavedDataFile(@TempDir Path directory) throws java.io.IOException {
        net.minecraft.SharedConstants.tryDetectVersion();
        var receipt = receipt(1, 1);
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest();
        assertTrue(ledger.record(receipt));
        assertTrue(ledger.confirmSavedObservation(receipt));
        var file = directory.resolve("cargo-departures.dat");
        ledger.save(file.toFile(), null);
        assertTrue(Files.isRegularFile(file));
        assertTrue(Files.size(file) > 0);
        assertFalse(ledger.isDirty());
        var published = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        assertTrue(FrontierV3CargoDepartureLedger.load(published.getCompound("data"), null).savedObservation(receipt));
    }

    @Test void firstContradictionIsBoundedPersistentAndNeverHiddenByAnOldMatchingReturn() {
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest(); var first = receipt(1, 1);
        assertTrue(ledger.record(first)); assertFalse(ledger.record(receipt(1, 2))); assertFalse(ledger.record(receipt(1, 3)));
        var saved = ledger.save(new CompoundTag(), null);
        assertEquals(1, saved.getList("conflicts", Tag.TAG_COMPOUND).size());
        var recovered = FrontierV3CargoDepartureLedger.load(saved, null);
        assertTrue(recovered.conflicted(first.entityId()));
        assertFalse(recovered.record(first)); assertFalse(recovered.resolveExact(first));
        assertEquals(first, recovered.observation(first.entityId()).orElseThrow());
    }

    @Test void malformedInventoriesDuplicateIdentitiesAndOrphanConflictsReject() {
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest(); ledger.record(receipt(1, 1));
        var missing = ledger.save(new CompoundTag(), null); missing.remove("conflicts");
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDepartureLedger.load(missing, null));
        var wrong = ledger.save(new CompoundTag(), null); var strings = new ListTag(); strings.add(StringTag.valueOf("not an observation"));
        wrong.put("departures", strings);
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDepartureLedger.load(wrong, null));
        var duplicate = ledger.save(new CompoundTag(), null); duplicate.getList("departures", Tag.TAG_COMPOUND).add(receipt(1, 1).save());
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDepartureLedger.load(duplicate, null));
        var orphan = ledger.save(new CompoundTag(), null); orphan.getList("conflicts", Tag.TAG_COMPOUND).add(receipt(2, 2).save());
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDepartureLedger.load(orphan, null));
    }

    @Test void retentionHasAHardBoundWithoutEvictingUnresolvedEvidence() {
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest();
        for (int i = 0; i < FrontierV3CargoDepartureLedger.MAX_ENTRIES; i++) assertTrue(ledger.record(receipt(i, 1)));
        assertFalse(ledger.record(receipt(FrontierV3CargoDepartureLedger.MAX_ENTRIES, 1)));
        assertTrue(ledger.observation(receipt(0, 1).entityId()).isPresent());
    }
}
