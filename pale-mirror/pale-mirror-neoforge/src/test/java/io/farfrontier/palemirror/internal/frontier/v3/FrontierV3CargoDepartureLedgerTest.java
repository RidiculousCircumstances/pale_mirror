package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
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
        var recovered = FrontierV3CargoDepartureLedger.load(ledger.save(new CompoundTag(), null), null);
        assertFalse(recovered.isDirty());
        assertFalse(recovered.resolveExact(receipt(1, 2)));
        assertEquals(first, recovered.observation(first.entityId()).orElseThrow());
        assertTrue(recovered.resolveExact(first)); assertFalse(recovered.resolveExact(first));
        assertTrue(recovered.observation(first.entityId()).isEmpty());
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
