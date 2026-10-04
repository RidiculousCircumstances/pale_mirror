package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

class FrontierV3ActorFirstAdmissionTest {
    private static final Declaration BODY = new Declaration(new SubjectId("resident:1-1"), ActorKind.RESIDENT,
            Owner.ACTOR_BODY, new UUID(0, 1), Representation.LIVE_BODY, 0L, 1L);
    private static FrontierV3ActorFirstAdmission permit() {
        return FrontierV3ActorFirstAdmission.neverCreated(new FrontierV3ActorFirstAdmission.Identity(BODY.actorId(), BODY.kind(), BODY.entityId()));
    }
    @Test void knownNoCreationCanRetrySameBodyWithNewAttemptButSavedBodyNeverBecomesFreshAgain() {
        var target = FrontierV3ActorOwnerBinding.body(BODY);
        var pending = permit().begin(target);
        assertThrows(IllegalStateException.class, () -> pending.begin(target));
        var retry = pending.rejectedBeforeCreation(target);
        assertEquals(pending.attemptGeneration(), retry.attemptGeneration());
        var next = FrontierV3ActorOwnerBinding.body(BODY.liveBody(Owner.ACTOR_BODY, 0L, 1L));
        var retried = retry.begin(next);
        assertEquals(pending.attemptGeneration() + 1L, retried.attemptGeneration());
        var created = retried.saved(next);
        assertEquals(FrontierV3ActorFirstAdmission.Phase.ESTABLISHED, created.phase());
        assertThrows(IllegalStateException.class, () -> created.begin(target));
        assertThrows(IllegalStateException.class, () -> created.rejectedBeforeCreation(next));
        assertThrows(IllegalStateException.class, () -> created.saved(next));
        // Cancelled never-inserted incarnations do not consume the actor's first
        // physical creation permission. The canonical body owner supplies epoch 3.
        var later = FrontierV3ActorOwnerBinding.body(BODY.liveBody(Owner.ACTOR_BODY, 0L, 3L));
        var firstPhysical = permit().begin(later);
        assertEquals(firstPhysical, FrontierV3ActorFirstAdmission.load(firstPhysical.save()));
        var rearmed = firstPhysical.rearmAfterProvenAbsence(later, "c".repeat(64));
        assertEquals(rearmed, FrontierV3ActorFirstAdmission.load(rearmed.save()));
        assertThrows(IllegalStateException.class, () -> rearmed.begin(target));
        assertEquals(firstPhysical.attemptGeneration() + 1L, rearmed.begin(later).attemptGeneration());
        assertThrows(IllegalStateException.class, () -> firstPhysical.saved(target));
        assertThrows(IllegalStateException.class, () -> firstPhysical.fencedAsInactive(BODY.inactiveCarrier()));
        assertThrows(IllegalStateException.class, () -> firstPhysical.fencedAsInactive(later.declaration()));
        var established = firstPhysical.fencedAsInactive(later.declaration().inactiveCarrier());
        assertEquals(FrontierV3ActorFirstAdmission.Phase.ESTABLISHED, established.phase());
        assertEquals(firstPhysical.attempt(), established.attempt());
        assertEquals(established, FrontierV3ActorFirstAdmission.load(established.save()));
        assertThrows(IllegalStateException.class, () -> established.begin(later));
    }
    @Test void exactIdentityGenerationAndAttemptCannotBeReplaced() {
        var target = FrontierV3ActorOwnerBinding.body(BODY.liveBody(Owner.ACTOR_BODY, 0L, 1L));
        var pending = permit().begin(target);
        var otherIncarnation = FrontierV3ActorOwnerBinding.body(BODY.liveBody(Owner.ACTOR_BODY, 0L, 2L));
        assertThrows(IllegalStateException.class, () -> pending.saved(otherIncarnation));
        assertThrows(IllegalStateException.class, () -> pending.rejectedBeforeCreation(otherIncarnation));
        for (var invalid : java.util.List.of(BODY.inactiveCarrier(),
                new Declaration(BODY.actorId(), ActorKind.BIOFORM, BODY.owner(), BODY.entityId(), BODY.representation(), 0L, 1L),
                new Declaration(BODY.actorId(), BODY.kind(), BODY.owner(), new UUID(0, 2), BODY.representation(), 0L, 1L))) {
            assertThrows(IllegalArgumentException.class, () -> permit().begin(FrontierV3ActorOwnerBinding.body(invalid)));
        }
    }
    @Test void codecRetainsAllPhasesAndRejectsMissingOrInventedHistory() {
        var target = FrontierV3ActorOwnerBinding.body(BODY);
        for (var value : java.util.List.of(permit(), permit().begin(target), permit().begin(target).saved(target))) {
            assertEquals(value, FrontierV3ActorFirstAdmission.load(value.save()));
        }
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(new net.minecraft.nbt.CompoundTag()));
        var missing = permit().begin(target).save(); missing.remove("attempt");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(missing));
        var missingGeneration = permit().begin(target).save(); missingGeneration.remove("attemptGeneration");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(missingGeneration));
        var wrong = permit().save(); wrong.putString("phase", "absence_means_new");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(wrong));
    }
}
