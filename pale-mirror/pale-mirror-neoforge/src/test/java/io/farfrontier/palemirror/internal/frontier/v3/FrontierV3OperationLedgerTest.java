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
    @Test void nativeSaveAcknowledgementCannotEraseMarkersBeforeItsAsyncRetirementIsDurable() throws Exception {
        var tasks = new ArrayDeque<Runnable>();
        var path = directory.resolve("async-retirement.dat");
        var ledger = FrontierV3OperationLedger.inMemory();
        ledger.attachJournal(path, WORLD, DIMENSION,
                FrontierV3JournalStore.open(path, tasks::add, FrontierV3JournalStore.PHYSICAL));
        var operation = ledger.prepare(declaration("async-retirement"));
        ledger.persistAsync(); tasks.remove().run();
        var markers = new CompoundTag();
        FrontierV3NativeOperationMarkers.stamp(markers, operation.nativeReceipt(BODY));
        for (var participant : operation.declaration().participants()) {
            ledger.applied(operation.nativeReceipt(participant)); ledger.saved(operation.nativeReceipt(participant));
        }
        ledger.closeOwner(operation.ticket()); ledger.retire(operation.ticket());
        var receipt = ledger.persistAsync();
        assertFalse(receipt.isDone());
        assertFalse(FrontierV3NativeOperationMarkers.prune(markers, ledger));
        assertTrue(FrontierV3NativeOperationMarkers.contains(markers, operation.nativeReceipt(BODY)),
                "native saved evidence survives until the journal acknowledges retirement");
        tasks.remove().run();
        assertTrue(FrontierV3NativeOperationMarkers.prune(markers, ledger));
        ledger.finish();
        assertTrue(reopen(path).prunable(operation.nativeReceipt(BODY)));
    }
    @Test void conditionalCancellationRetainsTheLivingCommandAndNeedsPositiveParentOutcomeAfterRestart() throws Exception {
        conditionalCancellation(true);
        conditionalCancellation(false);
    }
    private void conditionalCancellation(boolean sharedParticipant) throws Exception {
        var path = directory.resolve("conditional-" + sharedParticipant + ".dat"); var ledger = fresh(path);
        var payload = new io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaObserved(
                new io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta(
                        new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(1, 64, 1),
                        io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind.UNKNOWN_SCAR,
                        Optional.empty(), Optional.empty(), "test:conditional"));
        var fingerprint = FrontierV3OperationConfirmation.fingerprint(payload);
        var child = ledger.prepare(new FrontierV3OperationReceipt.Declaration(sharedParticipant
                ? FrontierV3OperationReceipt.Protocol.MEAL_TAKE : FrontierV3OperationReceipt.Protocol.FIELD_WORK,
                new SubjectId("owner:conditional-child"), "take", fingerprint, sharedParticipant ? Set.of(BODY, CHEST) : Set.of(CHEST)));
        var normal = new FrontierV3OperationLedger.OwnerCommand(new CommandId("test:normal-child"), payload);
        ledger.retainOwnerIntent(child.ticket(), normal);
        var parent = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_FATALITY,
                new SubjectId("resident:conditional"), "death", fingerprint, Set.of(BODY)));
        var aftermath = new FrontierV3OperationAftermath(child, FrontierV3OperationAftermath.Outcome.CANCEL_UNUSED, payload);
        var context = FrontierV3JournalNbt.encode(aftermath.save());
        ledger.retainOwnerIntent(parent.ticket(), new FrontierV3OperationLedger.OwnerCommand(new CommandId("test:conditional-parent"), payload,
                FrontierV3OperationLedger.OwnerCommand.Closure.EXPLICIT_OWNER, context));
        aftermath.retain(ledger, parent.ticket()); ledger.finish();
        assertFalse(ledger.beginAttempt(child.ticket()), "parent admission positively excludes the still-unused child");
        assertFalse(ledger.sealUnattemptedCancellation(child.ticket()), "parent preparation is not death");
        assertFalse(ledger.cancelUnattempted(parent.ticket()), "parent cannot erase its conditional outcome");
        assertEquals(normal, ledger.ownerCommand(child.ticket()).orElseThrow(), "living owner was not replaced by a death-only command");
        var recovered = reopen(path);
        assertEquals(aftermath, FrontierV3OperationAftermath.load(FrontierV3JournalNbt.decode(
                recovered.ownerCommand(parent.ticket()).orElseThrow().context())), "typed child outcome survives the actual journal boundary");
        assertFalse(recovered.beginAttempt(parent.ticket())); assertFalse(recovered.beginAttempt(child.ticket()));
        assertFalse(recovered.sealUnattemptedCancellation(child.ticket()));
        assertThrows(IllegalStateException.class, () -> recovered.applied(child.nativeReceipt(CHEST)));
        recovered.applied(parent.nativeReceipt(BODY));
        assertThrows(IllegalStateException.class, () -> recovered.closeOwner(parent.ticket()));
        var historical = FrontierV3OperationLedger.load(recovered.save(new CompoundTag(), null), null);
        assertFalse(historical.sealUnattemptedCancellation(child.ticket()), "historical APPLIED is not the recovered parent's native image");
        historical.saved(parent.nativeReceipt(BODY));
        assertTrue(historical.sealUnattemptedCancellation(child.ticket()));
        assertTrue(historical.require(child.ticket()).cancelled());
        assertEquals(normal, historical.ownerCommand(child.ticket()).orElseThrow());
        var afterCut = FrontierV3OperationLedger.load(historical.save(new CompoundTag(), null), null);
        assertTrue(afterCut.sealUnattemptedCancellation(child.ticket()), "sealed cancellation survives before child outcome submission");
        afterCut.closeOwner(child.ticket()); afterCut.retire(child.ticket());
        afterCut.closeOwner(parent.ticket()); afterCut.retire(parent.ticket());
        assertEquals(0, afterCut.pendingCount());
        var orphan = recovered.save(new CompoundTag(), null);
        orphan.getList("cancellationParents", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putUUID("parent", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3OperationLedger.load(orphan, null));
    }
    @Test void recoveryProbesAreBoundedFairAndDerivedAfterReload() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var first = ledger.prepare(declaration("blocked"));
        var second = ledger.prepare(declaration("available"));
        assertEquals(List.of(first), ledger.probe(first.declaration().protocol(), 1));
        assertEquals(List.of(second), ledger.probe(first.declaration().protocol(), 1));
        assertTrue(ledger.cancelUnattempted(first.ticket()));
        assertEquals(List.of(second), ledger.probe(first.declaration().protocol(), 1));
        var recovered = FrontierV3OperationLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(List.of(second), recovered.probe(first.declaration().protocol(), 1));
        assertTrue(recovered.probe(FrontierV3OperationReceipt.Protocol.BODY_FATALITY, 1).isEmpty());
    }
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
    @Test void asyncSubmissionDoesNotGrantAttemptOrRetirementBeforeTheActualForce() throws Exception {
        Path path = directory.resolve("async.dat"); var work = new ArrayDeque<Runnable>();
        var store = FrontierV3JournalStore.open(path, work::add, FrontierV3JournalStore.PHYSICAL);
        var ledger = FrontierV3OperationLedger.inMemory(); ledger.attachJournal(path, WORLD, DIMENSION, store);
        var first = ledger.prepare(declaration("async-first"));
        var firstForce = ledger.persistAsync();
        assertFalse(ledger.isDirty()); assertFalse(firstForce.isDone()); assertFalse(ledger.durableReady());
        assertThrows(IllegalStateException.class, () -> ledger.beginAttempt(first.ticket()));
        var second = ledger.prepare(declaration("async-second")); var secondForce = ledger.persistAsync();
        assertEquals(1, work.size(), "one writer drains both immutable submitted prefixes");
        work.remove().run(); assertTrue(firstForce.isDone()); assertTrue(secondForce.isDone());
        assertTrue(ledger.durableReady());
        var later = ledger.prepare(declaration("async-later")); ledger.persistAsync();
        assertFalse(ledger.durableReady()); assertFalse(ledger.durableOperation(later.ticket()));
        assertTrue(ledger.beginAttempt(first.ticket()), "unrelated later writes cannot delay this already durable permit");
        ledger.finishAttempt(first.ticket());
        for (var participant : first.declaration().participants()) ledger.saved(first.nativeReceipt(participant));
        ledger.closeOwner(first.ticket()); ledger.retire(first.ticket()); ledger.persistAsync();
        var markers = new CompoundTag(); FrontierV3NativeOperationMarkers.stamp(markers, first.nativeReceipt(BODY));
        assertFalse(ledger.prunable(first.nativeReceipt(BODY)));
        assertFalse(FrontierV3NativeOperationMarkers.prune(markers, ledger), "submitted retirement cannot erase native evidence");
        work.remove().run(); assertTrue(FrontierV3NativeOperationMarkers.prune(markers, ledger));
        var recovered = reopen(path);
        assertEquals(second.declaration(), recovered.require(second.ticket()).declaration());
        assertFalse(recovered.beginAttempt(second.ticket()), "unattempted memory permission does not survive restart");
        assertTrue(recovered.prunable(first.nativeReceipt(BODY)));
    }
    @Test void onlyTheLiveAttemptMaySealPositiveNativeRefusal() throws Exception {
        Path path = directory.resolve("refused.dat"); var ledger = fresh(path);
        var refused = ledger.prepare(declaration("refused")); ledger.finish();
        assertFalse(ledger.rejectCurrentAttempt(refused.ticket()));
        assertTrue(ledger.beginAttempt(refused.ticket())); assertTrue(ledger.rejectCurrentAttempt(refused.ticket()));
        ledger.finishAttempt(refused.ticket()); ledger.finish();
        var recovered = reopen(path);
        assertTrue(recovered.require(refused.ticket()).cancelled());
        assertFalse(recovered.rejectCurrentAttempt(refused.ticket()));
        assertThrows(IllegalStateException.class, () -> recovered.retire(refused.ticket()), "refusal still needs owner rollback");
        recovered.closeOwner(refused.ticket()); recovered.retire(refused.ticket()); recovered.finish();
        var unknown = recovered.prepare(declaration("unknown")); recovered.finish();
        assertTrue(recovered.beginAttempt(unknown.ticket())); recovered.finishAttempt(unknown.ticket());
        assertFalse(recovered.rejectCurrentAttempt(unknown.ticket()), "a later callback cannot turn an unknown effect into absence");
        assertFalse(reopen(path).rejectCurrentAttempt(unknown.ticket()));
    }
}
