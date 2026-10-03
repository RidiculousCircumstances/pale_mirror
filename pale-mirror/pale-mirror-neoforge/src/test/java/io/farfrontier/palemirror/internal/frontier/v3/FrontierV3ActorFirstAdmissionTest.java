package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

class FrontierV3ActorFirstAdmissionTest {
    private static final Declaration BODY = new Declaration(new SubjectId("resident:1-1"), ActorKind.RESIDENT,
            Owner.AMBIENT_LEASE, new UUID(0, 1), Representation.LIVE_BODY, 1L, 1L);
    private static FrontierV3ActorFirstAdmission permit() {
        return FrontierV3ActorFirstAdmission.neverCreated(new FrontierV3ActorFirstAdmission.Identity(BODY.actorId(), BODY.kind(), BODY.entityId()));
    }
    @Test void knownNoCreationCanRetryWithAnotherOwnerButSavedBodyNeverBecomesFreshAgain() {
        var target = FrontierV3ActorOwnerBinding.ambient(BODY);
        var pending = permit().begin(target);
        assertThrows(IllegalStateException.class, () -> pending.begin(target));
        var retry = pending.rejectedBeforeCreation(target);
        var next = FrontierV3ActorOwnerBinding.scene(BODY.liveBody(Owner.SCENE_LEASE, 19L, 1L), new SceneLeaseId("lease:next"));
        var created = retry.begin(next).saved(next);
        assertEquals(FrontierV3ActorFirstAdmission.Phase.ESTABLISHED, created.phase());
        assertThrows(IllegalStateException.class, () -> created.begin(target));
        assertThrows(IllegalStateException.class, () -> created.rejectedBeforeCreation(next));
        assertThrows(IllegalStateException.class, () -> created.saved(next));
    }
    @Test void exactIdentityGenerationAndAttemptCannotBeReplaced() {
        var target = FrontierV3ActorOwnerBinding.scene(BODY.liveBody(Owner.SCENE_LEASE, 17L, 1L), new SceneLeaseId("lease:first"));
        var pending = permit().begin(target);
        var otherScene = FrontierV3ActorOwnerBinding.scene(target.declaration(), new SceneLeaseId("lease:foreign"));
        assertThrows(IllegalStateException.class, () -> pending.saved(otherScene));
        assertThrows(IllegalStateException.class, () -> pending.rejectedBeforeCreation(otherScene));
        for (var invalid : java.util.List.of(BODY.liveBody(Owner.AMBIENT_LEASE, 1L, 2L), BODY.inactiveCarrier(),
                new Declaration(BODY.actorId(), ActorKind.BIOFORM, BODY.owner(), BODY.entityId(), BODY.representation(), 1L, 1L),
                new Declaration(BODY.actorId(), BODY.kind(), BODY.owner(), new UUID(0, 2), BODY.representation(), 1L, 1L))) {
            assertThrows(IllegalArgumentException.class, () -> permit().begin(FrontierV3ActorOwnerBinding.ambient(invalid)));
        }
    }
    @Test void codecRetainsAllPhasesAndRejectsMissingOrInventedHistory() {
        var target = FrontierV3ActorOwnerBinding.ambient(BODY);
        for (var value : java.util.List.of(permit(), permit().begin(target), permit().begin(target).saved(target))) {
            assertEquals(value, FrontierV3ActorFirstAdmission.load(value.save()));
        }
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(new net.minecraft.nbt.CompoundTag()));
        var missing = permit().begin(target).save(); missing.remove("attempt");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(missing));
        var wrong = permit().save(); wrong.putString("phase", "absence_means_new");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(wrong));
    }
}
