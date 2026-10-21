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
        assertTrue(FrontierV3OperationConfirmation.submit(recovered, () -> { }, canonical, operation.ticket(), consequence));
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
        assertFalse(FrontierV3OperationConfirmation.submit(ledger, () -> { }, canonical, operation.ticket(), first));
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
