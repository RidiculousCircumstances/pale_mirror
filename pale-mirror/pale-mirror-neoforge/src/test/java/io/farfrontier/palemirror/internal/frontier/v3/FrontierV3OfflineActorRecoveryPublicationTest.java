package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OfflineActorRecoveryPublicationTest {
    @TempDir Path directory;
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");

    @Test void everyDurablePublicationBoundaryResumesFromDiskWithoutDuplicateCommands() throws Exception {
        for (var boundary : FrontierV3OfflineActorRecoveryPublication.Boundary.values()) {
            Path world = Files.createDirectory(directory.resolve(boundary.name())).toRealPath();
            Path ledgerFile = world.resolve("carriers.dat"), receipt = world.resolve("repair.dat");
            var root = new CompoundTag(); root.putInt("DataVersion", 3955);
            root.put("data", FrontierV3AmbientCarrierLedger.emptyForTest().save(new CompoundTag(), null));
            NbtIo.writeCompressed(root, ledgerFile);
            byte[] original = Files.readAllBytes(ledgerFile);
            var state = FrontierV3OfflineActorRecoveryPlanTest.prepared();
            var config = FrontierV3OfflineActorRecoveryPlanTest.configuration(state);
            var proof = proof(world, state, "original-entity-input");
            var runtime = FrontierV3ServerRuntime.start(config,
                    new FrontierFileStore(world, FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
            runtime.checkpoint().orElseThrow();
            assertThrows(IOException.class, () -> FrontierV3OfflineActorRecoveryPublication.publish(runtime, proof,
                    ACTOR, ledgerFile, receipt, reached -> {
                        if (reached == boundary) throw new IOException("simulated process interruption at " + boundary);
                    }));
            assertTrue(Files.isRegularFile(receipt));
            assertArrayEquals(original, NbtIo.readCompressed(receipt, NbtAccounter.unlimitedHeap()).getByteArray("beforeLedger"));
            if (boundary == FrontierV3OfflineActorRecoveryPublication.Boundary.RECEIPT_DURABLE)
                assertArrayEquals(original, Files.readAllBytes(ledgerFile));
            else assertTrue(FrontierV3AmbientCarrierLedger.load(NbtIo.readCompressed(ledgerFile,
                    NbtAccounter.unlimitedHeap()).getCompound("data"), null).hasCarrier(ACTOR));
            // No graceful shutdown: recover from the actual persisted snapshot/WAL only.
            var recovered = FrontierV3ServerRuntime.start(config,
                    new FrontierFileStore(world, FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
            var beforeRetry = recovered.checkpointImage().orElseThrow().revision();
            assertThrows(IOException.class, () -> FrontierV3OfflineActorRecoveryPublication.publish(recovered,
                    proof(world, state, "changed-entity-input"), ACTOR, ledgerFile, receipt));
            assertEquals(beforeRetry, recovered.checkpointImage().orElseThrow().revision());
            FrontierV3OfflineActorRecoveryPublication.publish(recovered, proof, ACTOR, ledgerFile, receipt);
            var finalState = recovered.decodedState().orElseThrow();
            assertEquals(AmbientLeaseStatus.CLOSED, finalState.ambientLeases().get(ACTOR).status());
            assertEquals(state, finalState.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(state.ambientLeases())));
            assertEquals(2L, recovered.checkpointImage().orElseThrow().revision().value());
            assertEquals(10L, recovered.checkpointImage().orElseThrow().instant().ticks());
            byte[] published = Files.readAllBytes(ledgerFile), retainedReceipt = Files.readAllBytes(receipt);
            FrontierV3OfflineActorRecoveryPublication.publish(recovered, proof, ACTOR, ledgerFile, receipt);
            assertEquals(2L, recovered.checkpointImage().orElseThrow().revision().value());
            assertArrayEquals(published, Files.readAllBytes(ledgerFile));
            assertArrayEquals(retainedReceipt, Files.readAllBytes(receipt));
            // Format 2 appends multiple transactions to one bounded segment.
            // Idempotency concerns recovered transactions, not the file count.
            var disk = new FrontierFileStore(world, FrontierWorldRuntimeDefinition.payloadCodecs());
            assertEquals(List.of(1L, 2L), disk.recover(state.bootstrap().worldId()).walTail().stream()
                    .map(transaction -> transaction.revision().value()).toList());
        }
    }

    private static FrontierV3OfflineActorAbsence.Proof proof(Path world, FrontierWorldState state, String digest) {
        return new FrontierV3OfflineActorAbsence.Proof(world,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), ACTOR), 1,
                List.of(new FrontierV3OfflineActorAbsence.FileProof("entities/r.0.0.mca", digest)));
    }
}
