package io.farfrontier.palemirror.internal.frontier.v3;

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
        var target = FrontierV3ActorOwnerBinding.ambient(new Declaration(actor, ActorKind.RESIDENT,
                Owner.AMBIENT_LEASE, id, Representation.LIVE_BODY, 2L, 1L));
        assertTrue(FrontierV3AmbientAdmissionDiagnostic.unresolvedCreationReason(ledger, actor).isEmpty());
        assertTrue(ledger.firstAdmissions().isEmpty());
        assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(actor, ActorKind.RESIDENT, id))));
        assertTrue(FrontierV3AmbientAdmissionDiagnostic.unresolvedCreationReason(ledger, actor).isEmpty());
        assertTrue(ledger.beginFirstAdmission(target));
        var before = ledger.save(new CompoundTag(), null);
        assertEquals("FIRST_CREATION_PENDING", FrontierV3AmbientAdmissionDiagnostic.unresolvedCreationReason(ledger, actor).orElseThrow());
        assertEquals(before, ledger.save(new CompoundTag(), null));
        var scene = FrontierV3ActorOwnerBinding.scene(target.declaration().liveBody(Owner.SCENE_LEASE, 1L, 1L),
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:diagnostic"));
        assertTrue(ledger.prepareHandoff(target, scene));
        assertEquals("HANDOFF_SAVE_PENDING", FrontierV3AmbientAdmissionDiagnostic.unresolvedCreationReason(ledger, actor).orElseThrow());
    }
}
