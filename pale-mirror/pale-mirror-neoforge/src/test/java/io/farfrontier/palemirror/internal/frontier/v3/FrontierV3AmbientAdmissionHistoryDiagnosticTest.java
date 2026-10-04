package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

class FrontierV3AmbientAdmissionHistoryDiagnosticTest {
    @Test void pendingFirstCreationIsExplainedWithoutMutatingOrIssuingPermission() {
        var actor = new SubjectId("resident:diagnostic");
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var id = java.util.UUID.randomUUID();
        var target = FrontierV3ActorOwnerBinding.body(new Declaration(actor, ActorKind.RESIDENT,
                Owner.ACTOR_BODY, id, Representation.LIVE_BODY, 0L, 1L));
        assertTrue(FrontierV3AmbientAdmissionDiagnostic.unresolvedCreationReason(ledger, actor).isEmpty());
        assertTrue(ledger.firstAdmissions().isEmpty());
        assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(actor, ActorKind.RESIDENT, id))));
        assertTrue(FrontierV3AmbientAdmissionDiagnostic.unresolvedCreationReason(ledger, actor).isEmpty());
        assertTrue(ledger.beginFirstAdmission(target));
        var before = ledger.save(new CompoundTag(), null);
        assertEquals("FIRST_CREATION_PENDING", FrontierV3AmbientAdmissionDiagnostic.unresolvedCreationReason(ledger, actor).orElseThrow());
        assertEquals(before, ledger.save(new CompoundTag(), null));
        var restored = FrontierV3AmbientCarrierLedger.load(before, null);
        assertEquals("FIRST_CREATION_PENDING", FrontierV3AmbientAdmissionDiagnostic.unresolvedCreationReason(restored, actor).orElseThrow());
        assertEquals(before, restored.save(new CompoundTag(), null));
    }
}
