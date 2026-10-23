package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OperationConfirmationTest {
    @BeforeAll static void version() { net.minecraft.SharedConstants.tryDetectVersion(); }
    @TempDir Path directory;
    @Test void deferredPhysicalBindingAndClosureNeverBlockOrReleaseReceiptEarly() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS, "1", 9);
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_WORK,
                new SubjectId("owner:test"), "effect", "exact", Set.of(participant)));
        ledger.saved(operation.nativeReceipt(participant));
        var consequence = new PhysicalDeltaObserved(new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:async-confirmation"));
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        var closed = new java.util.concurrent.CompletableFuture<Long>();
        var delivery = new FrontierV3DurableContinuations();
        var durability = new FrontierV3OperationConfirmation.Durability() {
            public boolean prepared(UUID ticket) { assertEquals(operation.ticket(), ticket); return ready.get(); }
            public void closed(Runnable release) { delivery.add(closed, release); }
        };
        var pins = new HashSet<CommandId>(); var receipts = new HashMap<CommandId, CommandReceipt>();
        var submissions = new AtomicInteger();
        var canonical = new FrontierV3OperationConfirmation.Canonical() {
            public void pin(CommandId id) { pins.add(id); }
            public void release(CommandId id) { pins.remove(id); }
            public Optional<CommandReceipt> receipt(CommandId id) { return Optional.ofNullable(receipts.get(id)); }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload payload) {
                submissions.incrementAndGet();
                var result = new CommandResult.Accepted(id, new TransactionId("test:async"), new Revision(1));
                receipts.put(id, new CommandReceipt(id, SimInstant.ZERO, result.transactionId(), result.revision()));
                return result;
            }
        };
        assertEquals(FrontierV3OperationConfirmation.Outcome.AWAITING_PHYSICAL_DURABILITY,
                FrontierV3OperationConfirmation.submitOutcome(ledger, durability, canonical, operation.ticket(), consequence));
        assertEquals(0, submissions.get()); assertTrue(pins.isEmpty());
        // Crash before the later closure flush recovers this exact open owner binding.
        var beforeClosure = ledger.save(new net.minecraft.nbt.CompoundTag(), null);
        ready.set(true);
        assertEquals(FrontierV3OperationConfirmation.Outcome.CONFIRMED,
                FrontierV3OperationConfirmation.submitOutcome(ledger, durability, canonical, operation.ticket(), consequence));
        delivery.advance(); assertEquals(1, pins.size()); assertEquals(1, submissions.get());
        closed.complete(1L);
        assertEquals(1, pins.size(), "the writer cannot run owner callbacks");
        delivery.advance(); assertTrue(pins.isEmpty());
        var recovered = FrontierV3OperationLedger.load(beforeClosure, null);
        FrontierV3OperationConfirmation.recover(recovered, () -> { }, canonical);
        assertTrue(recovered.find(operation.ticket()).isEmpty());
        assertEquals(1, submissions.get(), "recovery must never submit the accepted consequence twice");
    }
    @Test void failedClosureReceiptRetainsProtection() {
        var delivery = new FrontierV3DurableContinuations();
        var receipt = new java.util.concurrent.CompletableFuture<Long>();
        var released = new java.util.concurrent.atomic.AtomicBoolean();
        delivery.add(receipt, () -> released.set(true));
        delivery.advance(); assertFalse(released.get());
        receipt.completeExceptionally(new java.io.IOException("disk failed"));
        assertThrows(java.util.concurrent.CompletionException.class, delivery::advance);
        assertFalse(released.get());
    }

    @Test void deferredCanonicalDurabilityKeepsTheOwnerOpenAndPinnedUntilPublication() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, UUID.randomUUID().toString(), 1);
        var consequence = new PhysicalDeltaObserved(new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:deferred-owner"));
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_FATALITY,
                new SubjectId("resident:deferred"), "fatality", FrontierV3OperationConfirmation.fingerprint(consequence), Set.of(participant)));
        ledger.saved(operation.nativeReceipt(participant));
        var pins = new HashSet<CommandId>();
        var receipts = new HashMap<CommandId, CommandReceipt>();
        var completion = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var canonical = new FrontierV3OperationConfirmation.Canonical() {
            public void pin(CommandId id) { pins.add(id); }
            public void release(CommandId id) { pins.remove(id); }
            public Optional<CommandReceipt> receipt(CommandId id) { return Optional.ofNullable(receipts.get(id)); }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload payload) {
                return new CommandResult.Pending(id, new Revision(1), CommandResult.Pending.Reason.DURABILITY);
            }
            public void afterCommit(CommandResult result, Runnable continuation) { completion.set(continuation); }
        };
        assertEquals(FrontierV3OperationConfirmation.Outcome.AWAITING_CANONICAL_COMMIT,
                FrontierV3OperationConfirmation.submitOutcome(ledger, () -> ledger.setDirty(false), canonical, operation.ticket(), consequence));
        var command = ledger.ownerCommand(operation.ticket()).orElseThrow();
        assertEquals(Set.of(command.id()), pins);
        assertFalse(ledger.require(operation.ticket()).ownerClosed());
        var recovered = FrontierV3OperationLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        FrontierV3OperationConfirmation.recover(recovered, () -> recovered.setDirty(false), canonical);
        assertFalse(recovered.require(operation.ticket()).ownerClosed(), "a retained submission is not a successful receipt");
        // The runtime dispatches the retained continuation only after exact receipt publication.
        receipts.put(command.id(), new CommandReceipt(command.id(), SimInstant.ZERO, new TransactionId("test:deferred"), new Revision(1)));
        completion.get().run();
        assertTrue(ledger.find(operation.ticket()).isEmpty());
        assertTrue(pins.isEmpty());
        FrontierV3OperationConfirmation.recover(recovered, () -> recovered.setDirty(false), canonical);
        assertTrue(recovered.find(operation.ticket()).isEmpty(), "restart closes the same operation without a second native effect");
    }
    @Test void explicitAftermathSurvivesAcceptanceAndRestartUntilItsOwnerClosesIt() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, UUID.randomUUID().toString(), 7);
        var consequence = new PhysicalDeltaObserved(new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:aftermath"));
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_FATALITY,
                new SubjectId("resident:aftermath"), "fatality", FrontierV3OperationConfirmation.fingerprint(consequence), Set.of(participant)));
        var evidence = new byte[] { 1, 2, 3 };
        var command = new FrontierV3OperationLedger.OwnerCommand(new CommandId("physical-operation:" + operation.ticket()), consequence,
                FrontierV3OperationLedger.OwnerCommand.Closure.EXPLICIT_OWNER, evidence);
        evidence[0] = 9; command.context()[1] = 9;
        assertArrayEquals(new byte[] { 1, 2, 3 }, command.context(), "retained owner evidence cannot be mutated by its producer");
        ledger.retainOwnerIntent(operation.ticket(), command);
        var pins = new HashSet<CommandId>(); var accepted = new HashMap<CommandId, CommandReceipt>();
        var calls = new AtomicInteger();
        var canonical = new FrontierV3OperationConfirmation.Canonical() {
            public void pin(CommandId id) { pins.add(id); }
            public void release(CommandId id) { pins.remove(id); }
            public Optional<CommandReceipt> receipt(CommandId id) { assertTrue(pins.contains(id)); return Optional.ofNullable(accepted.get(id)); }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload payload) {
                calls.incrementAndGet();
                var result = new CommandResult.Accepted(id, new TransactionId("test:aftermath"), new Revision(1));
                accepted.put(id, new CommandReceipt(id, SimInstant.ZERO, result.transactionId(), result.revision()));
                return result;
            }
        };
        assertFalse(FrontierV3OperationConfirmation.completeOwner(ledger, () -> ledger.setDirty(false), canonical, operation.ticket()));
        assertTrue(pins.isEmpty(), "owner completion cannot invent canonical acceptance");
        ledger.saved(operation.nativeReceipt(participant));
        assertTrue(FrontierV3OperationConfirmation.submit(ledger, () -> ledger.setDirty(false), canonical, operation.ticket(), consequence));
        assertFalse(ledger.require(operation.ticket()).ownerClosed());
        assertEquals(Set.of(command.id()), pins, "canonical receipt remains pinned while the owner has outstanding consequences");
        assertThrows(IllegalStateException.class, () -> ledger.retire(operation.ticket()));
        var recovered = FrontierV3OperationLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(command, recovered.ownerCommand(operation.ticket()).orElseThrow());
        pins.clear();
        FrontierV3OperationConfirmation.recover(recovered, () -> recovered.setDirty(false), canonical);
        assertEquals(Set.of(command.id()), pins); assertFalse(recovered.require(operation.ticket()).ownerClosed());
        assertEquals(1, calls.get(), "recovery must not reapply the already accepted parent consequence");
        assertTrue(FrontierV3OperationConfirmation.completeOwner(recovered, () -> recovered.setDirty(false), canonical, operation.ticket()));
        assertTrue(recovered.find(operation.ticket()).isEmpty()); assertTrue(pins.isEmpty());
        assertTrue(recovered.prunable(operation.nativeReceipt(participant)));
        var invalid = ledger.save(new net.minecraft.nbt.CompoundTag(), null); invalid.putInt("format", 3);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3OperationLedger.load(invalid, null));
    }
    @Test void preEffectConsequenceSurvivesRestartButCannotConfirmWithoutNativeProof() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, UUID.randomUUID().toString(), 1);
        var consequence = new PhysicalDeltaObserved(new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:pre-effect-intent"));
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_FATALITY,
                new SubjectId("resident:intent"), "fatality", FrontierV3OperationConfirmation.fingerprint(consequence), Set.of(participant)));
        var command = new FrontierV3OperationLedger.OwnerCommand(new CommandId("physical-operation:" + operation.ticket()), consequence);
        ledger.retainOwnerIntent(operation.ticket(), command);
        var recovered = FrontierV3OperationLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(command, recovered.ownerCommand(operation.ticket()).orElseThrow());
        assertFalse(recovered.beginAttempt(operation.ticket()), "recovery cannot replay even an exact retained intent");
        var calls = new AtomicInteger();
        var canonical = new FrontierV3OperationConfirmation.Canonical() {
            public void pin(CommandId id) { }
            public void release(CommandId id) { }
            public Optional<CommandReceipt> receipt(CommandId id) { return Optional.empty(); }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload payload) {
                assertEquals(consequence, payload); calls.incrementAndGet();
                return new CommandResult.Accepted(id, new TransactionId("test:intent"), new Revision(1));
            }
        };
        assertFalse(FrontierV3OperationConfirmation.submit(recovered, () -> recovered.setDirty(false), canonical, operation.ticket(), consequence));
        assertEquals(0, calls.get());
        recovered.saved(operation.nativeReceipt(participant));
        assertTrue(FrontierV3OperationConfirmation.submit(recovered, () -> recovered.setDirty(false), canonical, operation.ticket(), consequence));
        assertEquals(1, calls.get());
        assertTrue(recovered.find(operation.ticket()).isEmpty());
        var wrong = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_FATALITY,
                new SubjectId("resident:intent"), "other", "different", Set.of(participant)));
        assertThrows(IllegalArgumentException.class, () -> ledger.retainOwnerIntent(wrong.ticket(), command));
    }
    @Test void mixedNativeGenerationsCannotUseHistoricalAppliedAsCurrentEvidence() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var source = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.TERRAIN, "source", 1);
        var target = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, UUID.randomUUID().toString(), 2);
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.NATIVE_INVENTORY_TRANSFER,
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR, "mixed-save", "exact", Set.of(source, target)));
        ledger.applied(operation.nativeReceipt(source)); ledger.applied(operation.nativeReceipt(target));
        ledger.saved(operation.nativeReceipt(source));
        assertTrue(ledger.nativeOutcomeAvailable(operation.ticket()), "the running callback has both actual native participants");
        var recovered = FrontierV3OperationLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(operation.declaration().participants(), recovered.require(operation.ticket()).applied());
        assertEquals(Set.of(target), recovered.unverifiedNative(operation.ticket()), "only the independently unsaved side needs native evidence");
        var consequence = new PhysicalDeltaObserved(new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:mixed-native-save"));
        var calls = new AtomicInteger(); var forces = new AtomicInteger();
        var canonical = new FrontierV3OperationConfirmation.Canonical() {
            public void pin(CommandId id) { }
            public void release(CommandId id) { }
            public Optional<CommandReceipt> receipt(CommandId id) { return Optional.empty(); }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload payload) {
                calls.incrementAndGet(); return new CommandResult.Accepted(id, new TransactionId("test:mixed"), new Revision(1));
            }
        };
        assertFalse(FrontierV3OperationConfirmation.submit(recovered, forces::incrementAndGet, canonical, operation.ticket(), consequence));
        assertFalse(FrontierV3OperationConfirmation.submitRecovery(recovered, forces::incrementAndGet, canonical, operation.ticket(), consequence));
        assertEquals(0, calls.get()); assertEquals(0, forces.get()); assertTrue(recovered.ownerCommand(operation.ticket()).isEmpty());
        recovered.applied(operation.nativeReceipt(target));
        assertTrue(recovered.nativeOutcomeAvailable(operation.ticket()), "a validated current native marker fills only its own gap");
        var restarted = FrontierV3OperationLedger.load(recovered.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertFalse(restarted.nativeOutcomeAvailable(operation.ticket()), "live observation is not itself disk durability");
        restarted.saved(operation.nativeReceipt(target));
        var durable = FrontierV3OperationLedger.load(restarted.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertTrue(FrontierV3OperationConfirmation.submit(durable, forces::incrementAndGet, canonical, operation.ticket(), consequence));
        assertEquals(1, calls.get()); assertTrue(durable.find(operation.ticket()).isEmpty());
    }
    @Test void canonicalClosureDoesNotAuthorizeRetiringAnUnverifiedNativeOwnerWitness() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS, "1", 1);
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_WORK,
                new SubjectId("owner:closed-test"), "effect", "exact", Set.of(participant)));
        ledger.applied(operation.nativeReceipt(participant)); ledger.closeOwner(operation.ticket());
        var recovered = FrontierV3OperationLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertTrue(recovered.require(operation.ticket()).ownerClosed());
        assertFalse(recovered.nativeOutcomeAvailable(operation.ticket()), "canonical acceptance and native recovery are separate acknowledgements");
        recovered.applied(operation.nativeReceipt(participant));
        assertTrue(recovered.nativeOutcomeAvailable(operation.ticket()));
        assertFalse(recovered.require(operation.ticket()).retirable(), "fresh live evidence still cannot retire the disk-save obligation");
    }
    @Test void sealedCancellationAndItsTypedConsequenceRecoverWithoutInventingNativeApplication() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, UUID.randomUUID().toString(), 1);
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MEAL_TAKE,
                new SubjectId("resident:cancelled"), "take", "exact", Set.of(participant)));
        var recoveredBefore = FrontierV3OperationLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertFalse(recoveredBefore.sealUnattemptedCancellation(operation.ticket()), "a recovered PREPARED record is not proof that no attempt occurred");
        assertTrue(ledger.sealUnattemptedCancellation(operation.ticket()));
        var sealed = ledger.require(operation.ticket()); assertTrue(sealed.cancelled()); assertTrue(sealed.applied().isEmpty());
        assertThrows(IllegalStateException.class, () -> ledger.applied(operation.nativeReceipt(participant)));
        var consequence = new PhysicalDeltaObserved(new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:cancel-consequence"));
        var command = new FrontierV3OperationLedger.OwnerCommand(new CommandId("test:cancelled-native"), consequence);
        ledger.bindOwnerCommand(operation.ticket(), command);
        var recovered = FrontierV3OperationLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(consequence, recovered.ownerCommand(operation.ticket()).orElseThrow().consequence());
        assertTrue(recovered.sealUnattemptedCancellation(operation.ticket()), "a persisted cancellation is positive evidence across restart");
        var calls = new AtomicInteger();
        var canonical = new FrontierV3OperationConfirmation.Canonical() {
            public void pin(CommandId id) { }
            public void release(CommandId id) { }
            public Optional<CommandReceipt> receipt(CommandId id) { return Optional.empty(); }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload payload) {
                assertEquals(consequence, payload); calls.incrementAndGet();
                return new CommandResult.Accepted(id, new TransactionId("test:cancelled"), new Revision(1));
            }
        };
        assertTrue(FrontierV3OperationConfirmation.submit(recovered, () -> recovered.setDirty(false), canonical, operation.ticket(), consequence));
        assertEquals(1, calls.get()); assertTrue(recovered.find(operation.ticket()).isEmpty());
        assertTrue(recovered.prunable(operation.nativeReceipt(participant)), "cancelled action retires only after its canonical consequence");
    }
    @Test void ownerRecoveryCanReplaceOnlyAnUnacceptedPinnedConsequence() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS, "1", 9);
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_WORK,
                new SubjectId("owner:test"), "effect", "exact", Set.of(participant)));
        ledger.applied(operation.nativeReceipt(participant));
        var first = new PhysicalDeltaObserved(new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:initial"));
        var recovery = new PhysicalDeltaObserved(new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:recovery"));
        var pins = new HashSet<CommandId>(); var calls = new AtomicInteger();
        var canonical = new FrontierV3OperationConfirmation.Canonical() {
            public void pin(CommandId id) { pins.add(id); }
            public void release(CommandId id) { pins.remove(id); }
            public Optional<CommandReceipt> receipt(CommandId id) { assertTrue(pins.contains(id)); return Optional.empty(); }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload payload) {
                if (calls.incrementAndGet() == 1) return new CommandResult.Rejected(id, new Revision(1),
                        new CommandRejection(RejectionCode.REJECTED_BY_POLICY, "scene needs explicit reconciliation"));
                assertEquals(recovery, payload);
                return new CommandResult.Accepted(id, new TransactionId("test:accepted"), new Revision(2));
            }
        };
        assertEquals(FrontierV3OperationConfirmation.Outcome.REJECTED,
                FrontierV3OperationConfirmation.submitOutcome(ledger, () -> { }, canonical, operation.ticket(), first));
        assertTrue(pins.isEmpty(), "a rejected command has no receipt to retain while awaiting owner recovery");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3OperationConfirmation.submit(ledger, () -> { }, canonical, operation.ticket(), recovery));
        assertTrue(FrontierV3OperationConfirmation.submitRecovery(ledger, () -> { }, canonical, operation.ticket(), recovery));
        assertTrue(ledger.require(operation.ticket()).ownerClosed()); assertTrue(pins.isEmpty()); assertEquals(2, calls.get());
    }
    @Test void canonicalCommitSurvivesCrashBeforePhysicalOwnerClosureWithoutReplayingTheConsequence() throws Exception {
        var world = new WorldId("frontier:operation-confirmation");
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var config = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var canonicalPath = directory.resolve("canonical"); var physicalPath = directory.resolve("physical.dat");
        var store = new FrontierFileStore(canonicalPath, FrontierWorldRuntimeDefinition.payloadCodecs());
        var runtime = FrontierV3ServerRuntime.start(config, store, 10_000);
        var ledger = FrontierV3OperationLedger.inMemory();
        ledger.attachJournal(physicalPath, world, dimension, ledger.openJournal(physicalPath));
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS, "1", 9);
        var consequence = new PhysicalDeltaObserved(new PhysicalDelta(new BlockPosition(1, 64, 1),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:physical-operation"));
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.EXTRACTION_BLOCK, new SubjectId("owner:test"),
                "effect", "exact-before-after", Set.of(participant)));
        ledger.applied(operation.nativeReceipt(participant)); ledger.save(physicalPath.toFile(), null);
        var forces = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> FrontierV3OperationConfirmation.submit(ledger, () -> {
            if (forces.incrementAndGet() == 2) throw new IllegalStateException("injected loss before physical closure force");
            ledger.save(physicalPath.toFile(), null);
        }, canonical(runtime), operation.ticket(), consequence));
        var command = new CommandId("physical-operation:" + operation.ticket());
        var accepted = runtime.commandReceipt(command).orElseThrow();
        assertEquals(consequence.delta(), runtime.decodedState().orElseThrow().physicalDeltas().get(consequence.delta().position()));
        store.close(); // No runtime shutdown/checkpoint, and do NOT flush the dirty physical closure.
        var recoveredLedger = FrontierV3JournaledSavedData.readFile(physicalPath, world.value(), dimension.toString(),
                FrontierV3OperationLedger::inMemory, FrontierV3OperationLedger::load, null);
        recoveredLedger.attachJournal(physicalPath, world, dimension, recoveredLedger.openJournal(physicalPath));
        assertFalse(recoveredLedger.require(operation.ticket()).ownerClosed());
        var recoveredStore = new FrontierFileStore(canonicalPath, FrontierWorldRuntimeDefinition.payloadCodecs());
        var recovered = FrontierV3ServerRuntime.start(config, recoveredStore, 10_000);
        assertEquals(accepted, recovered.commandReceipt(command).orElseThrow());
        var revision = recovered.canonicalState().orElseThrow().revision();
        FrontierV3OperationConfirmation.recover(recoveredLedger, () -> recoveredLedger.save(physicalPath.toFile(), null), canonical(recovered));
        assertTrue(recoveredLedger.require(operation.ticket()).ownerClosed());
        assertEquals(revision, recovered.canonicalState().orElseThrow().revision(), "recovery only observes the exact receipt; it must not reapply the command");
        assertFalse(recoveredLedger.require(operation.ticket()).retirable(), "canonical acceptance is not native-save evidence");
        assertEquals(consequence, recoveredLedger.ownerCommand(operation.ticket()).orElseThrow().consequence(),
                "retain the actual typed outcome, not just its hash, until native participants save");
        recoveredLedger.saved(operation.nativeReceipt(participant)); recoveredLedger.retire(operation.ticket()); recoveredLedger.finish();
        recovered.shutdown();
    }
    private static FrontierV3OperationConfirmation.Canonical canonical(FrontierV3ServerRuntime<?, ?> runtime) {
        return new FrontierV3OperationConfirmation.Canonical() {
            public void pin(CommandId id) { runtime.pinCommandReceipt(id); }
            public void release(CommandId id) { runtime.releaseCommandReceipt(id); }
            public Optional<CommandReceipt> receipt(CommandId id) { return runtime.commandReceipt(id); }
            public CommandResult submit(CommandId id, SubjectId owner, FrontierPayload payload) {
                return FrontierV3CommandSubmission.submitOperation(runtime, id, owner.value(), payload);
            }
        };
    }
}
