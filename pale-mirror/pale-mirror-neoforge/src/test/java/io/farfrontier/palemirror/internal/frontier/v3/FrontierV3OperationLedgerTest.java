package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OperationLedgerTest {
    @BeforeAll static void version() { net.minecraft.SharedConstants.tryDetectVersion(); }
    @TempDir Path directory;
    private static final WorldId WORLD = new WorldId("frontier:operation-test");
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    private static final FrontierV3OperationReceipt.Participant BODY = new FrontierV3OperationReceipt.Participant(
            FrontierV3OperationReceipt.Medium.ENTITIES, "d0da2312-c112-4b2e-9a9e-f12dc413a800", 7);
    private static final FrontierV3OperationReceipt.Participant CHEST = new FrontierV3OperationReceipt.Participant(
            FrontierV3OperationReceipt.Medium.TERRAIN, "container:source", 3);
    private static FrontierV3OperationReceipt.Declaration declaration(String operation) {
        return new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MEAL_TAKE, new SubjectId("owner:exact"), operation,
                "exact-retained-intent-" + operation, Set.of(BODY, CHEST));
    }
    private FrontierV3OperationLedger fresh(Path path) throws Exception {
        var ledger = FrontierV3OperationLedger.inMemory(); ledger.attachJournal(path, WORLD, DIMENSION, ledger.openJournal(path)); return ledger;
    }
    private FrontierV3OperationLedger reopen(Path path) throws Exception {
        var ledger = FrontierV3JournaledSavedData.readFile(path, WORLD.value(), DIMENSION.toString(),
                FrontierV3OperationLedger::inMemory, FrontierV3OperationLedger::load, null);
        ledger.attachJournal(path, WORLD, DIMENSION, ledger.openJournal(path)); return ledger;
    }
    @Test void nativeAndCanonicalAcknowledgementsAreIndependentAndBothRequiredForRetirement() throws Exception {
        Path path = directory.resolve("operations.dat"); var ledger = fresh(path);
        var operation = ledger.prepare(declaration("take"));
        assertThrows(IllegalStateException.class, () -> ledger.beginAttempt(operation.ticket()));
        ledger.save(path.toFile(), null); assertTrue(ledger.beginAttempt(operation.ticket())); assertFalse(ledger.beginAttempt(operation.ticket()));
        ledger.applied(operation.nativeReceipt(CHEST));
        assertThrows(IllegalStateException.class, () -> ledger.closeOwner(operation.ticket()));
        ledger.applied(operation.nativeReceipt(BODY)); ledger.closeOwner(operation.ticket());
        assertThrows(IllegalStateException.class, () -> ledger.retire(operation.ticket()));
        ledger.saved(operation.nativeReceipt(CHEST));
        assertThrows(IllegalStateException.class, () -> ledger.retire(operation.ticket()));
        ledger.save(path.toFile(), null); ledger.finish();
        var recovered = reopen(path);
        assertFalse(recovered.beginAttempt(operation.ticket()), "restart cannot invent permission to retry an attempted mutation");
        recovered.saved(operation.nativeReceipt(BODY)); recovered.retire(operation.ticket()); recovered.finish();
        var retired = reopen(path);
        assertEquals(0, retired.pendingCount()); assertTrue(retired.prunable(operation.nativeReceipt(BODY)));
        assertTrue(retired.prunable(operation.nativeReceipt(CHEST)));
        assertThrows(IllegalArgumentException.class, () -> retired.require(operation.ticket()));
    }
    @Test void nativeReceiptMaySurviveAppliedJournalWriteButNeverGrantsCanonicalClosure() throws Exception {
        Path path = directory.resolve("partial.dat"); var ledger = fresh(path);
        var operation = ledger.prepare(declaration("mine")); ledger.finish();
        var recovered = reopen(path); assertFalse(recovered.beginAttempt(operation.ticket()));
        recovered.saved(operation.nativeReceipt(BODY)); recovered.saved(operation.nativeReceipt(CHEST));
        assertFalse(recovered.require(operation.ticket()).ownerClosed());
        assertThrows(IllegalStateException.class, () -> recovered.retire(operation.ticket()));
        recovered.closeOwner(operation.ticket()); recovered.retire(operation.ticket()); recovered.finish();
    }
    @Test void participantIncarnationAndIntentAreNotInferredFromMatchingItemCounts() throws Exception {
        Path path = directory.resolve("identity.dat"); var ledger = fresh(path); var operation = ledger.prepare(declaration("take"));
        var wrongEpoch = new FrontierV3OperationReceipt.Participant(BODY.medium(), BODY.identity(), BODY.incarnation() + 1);
        assertThrows(IllegalArgumentException.class, () -> ledger.saved(new FrontierV3OperationReceipt.NativeReceipt(
                operation.ticket(), operation.sequence(), wrongEpoch, operation.declaration().fingerprint())));
        assertThrows(IllegalArgumentException.class, () -> ledger.applied(new FrontierV3OperationReceipt.NativeReceipt(
                operation.ticket(), operation.sequence(), BODY, "another-intent")));
        assertThrows(IllegalArgumentException.class, () -> ledger.prepare(new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.MEAL_TAKE, new SubjectId("owner:exact"), "take", "changed", Set.of(BODY, CHEST))));
    }
    @Test void retirementKeepsUnresolvedHolesWithoutRetainingUnrelatedCompletedHistory() throws Exception {
        Path path = directory.resolve("retention.dat"); var ledger = fresh(path);
        var first = ledger.prepare(declaration("first")); var second = ledger.prepare(declaration("second"));
        var markers = new CompoundTag(); FrontierV3NativeOperationMarkers.stamp(markers, second.nativeReceipt(BODY));
        FrontierV3NativeOperationMarkers.stamp(markers, first.nativeReceipt(BODY));
        for (var participant : second.declaration().participants()) ledger.saved(second.nativeReceipt(participant));
        ledger.closeOwner(second.ticket()); ledger.retire(second.ticket()); ledger.finish();
        FrontierV3NativeOperationMarkers.prune(markers, ledger);
        assertEquals(List.of(first.nativeReceipt(BODY)), FrontierV3NativeOperationMarkers.read(markers),
                "an older pending operation does not prevent pruning a positively completed successor");
        for (var participant : first.declaration().participants()) ledger.saved(first.nativeReceipt(participant));
        ledger.closeOwner(first.ticket()); ledger.retire(first.ticket());
        assertThrows(IllegalStateException.class, () -> FrontierV3NativeOperationMarkers.prune(markers, ledger));
        ledger.finish(); FrontierV3NativeOperationMarkers.prune(markers, ledger);
        assertTrue(FrontierV3NativeOperationMarkers.read(markers).isEmpty());
        var orphan = FrontierV3OperationReceipt.prepare(declaration("foreign"), 999);
        FrontierV3NativeOperationMarkers.stamp(markers, orphan.nativeReceipt(BODY));
        FrontierV3NativeOperationMarkers.prune(markers, ledger);
        assertEquals(1, FrontierV3NativeOperationMarkers.read(markers).size());
    }
}
