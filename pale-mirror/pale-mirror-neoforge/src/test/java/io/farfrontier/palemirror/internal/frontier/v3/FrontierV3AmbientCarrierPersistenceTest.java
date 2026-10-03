package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3AmbientCarrierPersistenceTest {
    @TempDir Path directory;
    @org.junit.jupiter.api.BeforeAll static void installMinecraftVersion() {
        net.minecraft.SharedConstants.tryDetectVersion();
    }

    @Test void savesNormalMinecraftEnvelopeAndReplacesItWithoutLeavingPartialFiles() throws Exception {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        Path file = directory.resolve("carriers.dat");
        ledger.setDirty(); ledger.save(file.toFile(), null);
        assertFalse(ledger.isDirty());
        var before = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        assertTrue(before.contains("DataVersion", Tag.TAG_INT));
        assertEquals(0, FrontierV3AmbientCarrierLedger.load(before.getCompound("data"), null).inactiveCount());
        var state = FrontierV3OfflineActorRecoveryPlanTest.prepared();
        var actor = new SubjectId("resident:1-1");
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3AmbientActorExecutor.entityId(state, actor),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 2L, 1L);
        assertTrue(ledger.fence(declaration, 2L, 2L));
        ledger.save(file.toFile(), null);
        assertFalse(ledger.isDirty());
        var restored = FrontierV3AmbientCarrierLedger.load(NbtIo.readCompressed(file,
                NbtAccounter.unlimitedHeap()).getCompound("data"), null);
        assertTrue(restored.matchesCarrier(declaration, 2L, 2L));
        try (var paths = Files.list(directory)) { assertEquals(1L, paths.count()); }
        byte[] saved = Files.readAllBytes(file);
        ledger.save(file.toFile(), null);
        assertArrayEquals(saved, Files.readAllBytes(file));
    }

    @Test void failedPublicationDoesNotClearDirtyFlagOrOverwriteExistingDirectory() throws Exception {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); ledger.setDirty();
        Path blocked = Files.createDirectory(directory.resolve("carriers.dat"));
        Path sentinel = Files.writeString(blocked.resolve("preserved"), "untouched");
        assertThrows(java.io.UncheckedIOException.class, () -> ledger.save(blocked.toFile(), null));
        assertTrue(ledger.isDirty());
        assertEquals("untouched", Files.readString(sentinel));
        try (var paths = Files.list(directory)) { assertEquals(1L, paths.count()); }
    }

    @Test void savingAnotherActorsReleaseKeepsUnacknowledgedAdoptionOnDisk() throws Exception {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var old = new FrontierV3ActorCarrierComposition.Declaration(new SubjectId("resident:1-1"),
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                new java.util.UUID(0, 1), FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 2L, 1L);
        var live = old.liveBody(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, 3L, 2L);
        assertTrue(ledger.fence(old, 2L, 2L)); assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live)));
        var other = new FrontierV3ActorCarrierComposition.Declaration(new SubjectId("resident:1-2"), old.kind(), old.owner(),
                new java.util.UUID(0, 2), old.representation(), 7L, 4L);
        assertTrue(ledger.fence(other, 7L, 7L));
        Path file = directory.resolve("mixed.dat"); ledger.save(file.toFile(), null);
        var restored = FrontierV3AmbientCarrierLedger.load(NbtIo.readCompressed(file,
                NbtAccounter.unlimitedHeap()).getCompound("data"), null);
        assertEquals(1, restored.inactiveCount());
        assertTrue(restored.matchesCarrier(other, 7L, 7L));
        var pending = restored.pendingAdoption(old.actorId()).orElseThrow();
        assertEquals(old, pending.predecessor().identity()); assertEquals(live, pending.admitted());
    }
}
