package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorAdoptionAdmissionTest {
    @TempDir Path directory;
    @BeforeAll static void version() { net.minecraft.SharedConstants.tryDetectVersion(); }
    private static final Declaration OLD = new Declaration(new SubjectId("resident:1-1"), ActorKind.RESIDENT,
            Owner.ACTOR_BODY, new UUID(0, 1), Representation.INACTIVE_CARRIER, 0L, 1L);
    private static final Declaration LIVE = OLD.liveBody(Owner.ACTOR_BODY, 0L, 2L);
    private FrontierV3AmbientCarrierLedger ledger() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); assertTrue(ledger.fence(OLD, 2L, 2L)); return ledger;
    }
    private FrontierV3AmbientCarrierLedger read(Path file) {
        try { return FrontierV3AmbientCarrierLedger.readFile(file, null); }
        catch (java.io.UncheckedIOException failure) { throw failure; }
    }
    @Test void exactTransferIsOnDiskBeforePhysicalEffect() {
        var ledger = ledger(); var file = directory.resolve("ledger.dat"); var effects = new AtomicInteger();
        assertTrue(FrontierV3AdoptionCrashFixture.admit(ledger, FrontierV3ActorAdoptionFixture.binding(LIVE), () -> ledger.save(file.toFile(), null), () -> {
            var stored = read(file);
            assertFalse(stored.hasCarrier(OLD.actorId()));
            assertEquals(LIVE, stored.pendingAdoption(OLD.actorId()).orElseThrow().admitted());
            effects.incrementAndGet(); return true;
        }));
        assertEquals(1, effects.get()); assertTrue(read(file).pendingAdoption(OLD.actorId()).isPresent());
        assertFalse(FrontierV3AdoptionCrashFixture.admit(ledger, FrontierV3ActorAdoptionFixture.binding(LIVE),
                () -> fail("must not republish"), () -> { fail("must not duplicate"); return true; }));
    }
    @Test void explicitFailedCreationRestoresAndPersistsPredecessor() {
        var ledger = ledger(); var file = directory.resolve("ledger.dat"); var saves = new AtomicInteger();
        assertFalse(FrontierV3AdoptionCrashFixture.admit(ledger, FrontierV3ActorAdoptionFixture.binding(LIVE), () -> {
            ledger.save(file.toFile(), null); saves.incrementAndGet();
        }, () -> false));
        assertEquals(2, saves.get());
        assertTrue(read(file).matchesCarrier(OLD, 2L, 2L));
        assertTrue(read(file).pendingAdoptions().isEmpty());
    }
    @Test void failedIntentPublicationNeverInvokesPhysicalEffect() {
        var ledger = ledger(); var file = directory.resolve("ledger.dat"); ledger.save(file.toFile(), null);
        assertThrows(IllegalStateException.class, () -> FrontierV3AdoptionCrashFixture.admit(ledger, FrontierV3ActorAdoptionFixture.binding(LIVE),
                () -> { throw new IllegalStateException("injected publication failure"); },
                () -> { fail("effect after failed publication"); return true; }));
        assertTrue(read(file).matchesCarrier(OLD, 2L, 2L));
        assertTrue(ledger.pendingAdoption(OLD.actorId()).isPresent());
    }
    @Test void unknownPhysicalOutcomeAndFailedRollbackKeepDurablePendingEvidence() {
        for (boolean unknownEffect : new boolean[]{true, false}) {
            var ledger = ledger(); var file = directory.resolve("ledger-" + unknownEffect + ".dat");
            var writes = new AtomicInteger();
            assertThrows(IllegalStateException.class, () -> FrontierV3AdoptionCrashFixture.admit(ledger, FrontierV3ActorAdoptionFixture.binding(LIVE), () -> {
                if (writes.incrementAndGet() > 1) throw new IllegalStateException("rollback publication failure");
                ledger.save(file.toFile(), null);
            }, () -> {
                if (unknownEffect) throw new IllegalStateException("unknown add result");
                return false;
            }));
            assertEquals(LIVE, read(file).pendingAdoption(OLD.actorId()).orElseThrow().admitted());
        }
    }
}
