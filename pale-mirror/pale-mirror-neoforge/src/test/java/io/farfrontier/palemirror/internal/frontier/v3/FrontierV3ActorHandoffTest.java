package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorHandoffTest {
    // Test-only fixture producer; production callers must supply the real scene ID.
    private static FrontierV3ActorOwnerBinding binding(Declaration value) {
        return value.owner() == Owner.AMBIENT_LEASE ? FrontierV3ActorOwnerBinding.ambient(value)
                : FrontierV3ActorOwnerBinding.scene(value,
                    new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:test-" + value.authorityRevision()));
    }
    private static final Declaration SCENE = new Declaration(new SubjectId("resident:1-1"), ActorKind.RESIDENT,
            Owner.SCENE_LEASE, new UUID(0, 1), Representation.LIVE_BODY, 17L, 4L);
    private static final Declaration AMBIENT = SCENE.liveBody(Owner.AMBIENT_LEASE, 3L, 4L);

    @Test void sceneIdentityIsRequiredAndCannotBeSubstitutedAtTheSameRevision() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorOwnerBinding.ambient(SCENE));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorOwnerBinding.scene(AMBIENT,
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:wrong-owner")));
        var chain = FrontierV3ActorHandoff.begin(binding(AMBIENT), binding(SCENE));
        var impostor = FrontierV3ActorOwnerBinding.scene(SCENE,
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:other-scene"));
        assertFalse(chain.contains(impostor));
        assertThrows(IllegalArgumentException.class, () -> chain.extend(binding(AMBIENT), impostor));
        var missing = chain.save();
        missing.getList("declarations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(1).remove("sceneLease");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorHandoff.load(missing));
        var olderFormat = chain.save(); olderFormat.putInt("format", 1);
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorHandoff.load(olderFormat));
    }

    @Test void ledgerRetainsReconstructionAndHandoffsUntilExactLatestSave() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var predecessor = SCENE.liveBody(Owner.SCENE_LEASE, 16L, 3L).inactiveCarrier();
        assertTrue(ledger.fence(predecessor, 16L, 2L)); assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(SCENE)));
        var adoption = ledger.pendingAdoption(SCENE.actorId()).orElseThrow();
        assertTrue(ledger.prepareHandoff(binding(SCENE), binding(AMBIENT)));
        var first = ledger.pendingHandoff(SCENE.actorId()).orElseThrow();
        assertFalse(ledger.acknowledgeAdoption(adoption, FrontierV3ActorAdoptionFixture.binding(SCENE)), "old body save cannot remove handoff recovery evidence");
        var nextScene = SCENE.liveBody(Owner.SCENE_LEASE, 18L, 4L);
        assertTrue(ledger.prepareHandoff(binding(AMBIENT), binding(nextScene)));
        assertFalse(ledger.acknowledgeHandoff(first, AMBIENT), "late save of intermediate owner is not current");
        var restored = FrontierV3AmbientCarrierLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        var current = restored.pendingHandoff(SCENE.actorId()).orElseThrow();
        assertEquals(3, current.declarations().size()); assertTrue(current.contains(binding(SCENE)));
        assertTrue(restored.prepareHandoff(binding(SCENE), binding(nextScene)), "saved oldest body can resume durable current target");
        assertTrue(restored.acknowledgeHandoff(current, nextScene));
        assertTrue(restored.pendingAdoption(SCENE.actorId()).isEmpty());
        assertTrue(restored.pendingHandoff(SCENE.actorId()).isEmpty());
    }

    @Test void onlyCurrentHandoffOwnerCanFenceAndLateSaveCannotUndoFence() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.prepareHandoff(binding(SCENE), binding(AMBIENT)));
        var receipt = ledger.pendingHandoff(SCENE.actorId()).orElseThrow();
        assertFalse(ledger.fence(SCENE.inactiveCarrier(), 17L, 2L));
        assertTrue(ledger.fence(AMBIENT.inactiveCarrier(), 3L, 3L));
        assertFalse(ledger.acknowledgeHandoff(receipt, AMBIENT));
        assertTrue(ledger.matchesCarrier(AMBIENT.inactiveCarrier(), 3L, 3L));
    }

    @Test void retainsEveryPossiblySavedOwnerAndUsesIndependentRevisionClocks() {
        var nextScene = SCENE.liveBody(Owner.SCENE_LEASE, 18L, 4L);
        var handoff = FrontierV3ActorHandoff.begin(binding(SCENE), binding(AMBIENT)).extend(binding(AMBIENT), binding(nextScene));
        assertEquals(nextScene, handoff.current());
        var restored = FrontierV3ActorHandoff.load(handoff.save());
        assertEquals(handoff, restored);
        assertTrue(restored.contains(binding(SCENE))); assertTrue(restored.contains(binding(AMBIENT)));
        assertEquals(handoff, restored.extend(binding(SCENE), binding(nextScene)), "old saved body resumes only exact durable target");
        assertThrows(IllegalArgumentException.class, () -> restored.extend(binding(SCENE), binding(SCENE.liveBody(Owner.AMBIENT_LEASE, 4L, 4L))));
    }

    @Test void identityGenerationRepresentationAndRevisionCannotBeGuessedOrReversed() {
        for (var invalid : java.util.List.of(
                new Declaration(new SubjectId("resident:1-2"), SCENE.kind(), AMBIENT.owner(), SCENE.entityId(), Representation.LIVE_BODY, 3L, 4L),
                new Declaration(SCENE.actorId(), ActorKind.BIOFORM, AMBIENT.owner(), SCENE.entityId(), Representation.LIVE_BODY, 3L, 4L),
                new Declaration(SCENE.actorId(), SCENE.kind(), AMBIENT.owner(), new UUID(0, 2), Representation.LIVE_BODY, 3L, 4L),
                AMBIENT.liveBody(AMBIENT.owner(), 3L, 5L), AMBIENT.inactiveCarrier(), SCENE)) {
            assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorHandoff.begin(binding(SCENE), binding(invalid)));
        }
        var chain = FrontierV3ActorHandoff.begin(binding(SCENE), binding(AMBIENT));
        assertThrows(IllegalArgumentException.class, () -> chain.extend(binding(AMBIENT), binding(SCENE)));
    }

    @Test void boundDoesNotDropOldRecoveryEvidenceAndCodecRejectsIncompleteTags() {
        var chain = FrontierV3ActorHandoff.begin(binding(SCENE), binding(AMBIENT));
        while (chain.declarations().size() < FrontierV3ActorHandoff.MAX_DECLARATIONS) {
            var current = chain.current();
            chain = chain.extend(binding(current), binding(current.liveBody(current.owner(), current.authorityRevision() + 1L, current.epoch())));
        }
        var bounded = chain;
        assertThrows(IllegalArgumentException.class, () -> bounded.extend(binding(bounded.current()),
                binding(bounded.current().liveBody(Owner.AMBIENT_LEASE, 100L, 4L))));
        assertTrue(bounded.contains(binding(SCENE))); assertTrue(bounded.contains(binding(AMBIENT)));
        var malformed = bounded.save();
        malformed.getList("declarations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("owner");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorHandoff.load(malformed));
    }
}
