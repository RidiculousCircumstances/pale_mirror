package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3DurableNativeObservationTest {
    @BeforeAll static void version() { net.minecraft.SharedConstants.tryDetectVersion(); }
    @Test void missingOrInvalidatedSaveEvidenceNeverGrantsObservationOrMutationPermission() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITY_SNAPSHOT,
                "entity:exact/epoch:4/image:exact-saved-image", 3);
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_DEPARTURE,
                new SubjectId("resident:save"), "world/residence:3", "exact-saved-image", Set.of(participant));
        assertTrue(FrontierV3DurableNativeObservation.record(ledger, declaration, () -> false).isEmpty());
        assertTrue(ledger.find(declaration).isEmpty());
        var intent = ledger.prepare(declaration);
        assertFalse(ledger.unattempted(intent.ticket()), "observation must never become a physical execution permission");
        var recovered = FrontierV3OperationLedger.load(ledger.save(new CompoundTag(), null), null);
        assertFalse(recovered.nativeOutcomeAvailable(intent.ticket()));
        assertTrue(FrontierV3DurableNativeObservation.record(recovered, declaration, () -> false).isEmpty());
        var observed = FrontierV3DurableNativeObservation.record(recovered, declaration, () -> true).orElseThrow();
        assertEquals(Set.of(participant), observed.saved());
        assertFalse(observed.ownerClosed(), "positive native disk proof is not canonical acknowledgement");
        assertFalse(recovered.beginAttempt(intent.ticket()), "even a validated observation can never replay an unload");
        var reread = FrontierV3OperationLedger.load(recovered.save(new CompoundTag(), null), null);
        assertTrue(reread.nativeOutcomeAvailable(intent.ticket()));
    }
    @Test void savedObservationCannotBeSubstitutedForAMutableNativeParticipant() {
        var nativeBody = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, UUID.randomUUID().toString(), 1);
        var snapshot = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITY_SNAPSHOT, "saved-image", 1);
        var owner = new SubjectId("resident:save");
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_DEPARTURE,
                owner, "effect", "image", Set.of(nativeBody));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3DurableNativeObservation.record(
                FrontierV3OperationLedger.inMemory(), declaration, () -> true));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3OperationReceipt.Declaration(
                declaration.protocol(), owner, "mixed", "image", Set.of(nativeBody, snapshot)));
        var observation = new FrontierV3OperationReceipt.Declaration(declaration.protocol(), owner, "observation", "image", Set.of(snapshot));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3MinecraftOperation.Request(observation,
                List.of(new FrontierV3MinecraftOperation.Participant(snapshot, new CompoundTag(), () -> { })),
                () -> true, FrontierV3NativeEffect.observed(() -> true), () -> true));
    }
}
