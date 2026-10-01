package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SceneReleaseCustodyTest {
    private static Declaration live(int actor, ActorKind kind, long epoch) {
        return new Declaration(new SubjectId("actor:release-" + actor), kind, Owner.SCENE_LEASE,
                new UUID(0, actor), Representation.LIVE_BODY, 7, epoch);
    }
    private static FrontierV3AmbientCarrierLedger.Carrier carrier(Declaration live) {
        return new FrontierV3AmbientCarrierLedger.Carrier(live.inactiveCarrier(), 7, 3);
    }

    @Test void everyFamilyDeclaresReleasePolicyAndOnlyHarvestRetainsVisibleBody() {
        for (var kind : SceneCauseKind.values()) {
            var policy = FrontierV3SceneBehaviorRegistry.bodyReleasePolicy(kind);
            assertFalse(policy.retainsLiveBody(false));
            assertEquals(kind == SceneCauseKind.RESOURCE_SITE_HARVEST, policy.retainsLiveBody(true));
            assertFalse(policy.retainsLiveBody(true, true),
                    "releasing a bound hand must fence the exact body even while observed");
            assertEquals(policy.retainsLiveBody(true), policy.retainsLiveBody(true, false));
        }
        assertThrows(IllegalArgumentException.class, () -> FrontierV3SceneBehaviorRegistry.bodyReleasePolicy(null));
    }

    @Test void entireSurvivorSetIsFencedIdempotentlyAndSurvivesReload() {
        var resident = live(1, ActorKind.RESIDENT, 2);
        var bioform = live(2, ActorKind.BIOFORM, 5);
        var request = List.of(carrier(resident), carrier(bioform));
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.canFenceAll(request));
        assertFalse(ledger.hasCarrier(resident.actorId()), "preflight must not mutate");
        assertTrue(ledger.fenceAll(request));
        assertTrue(ledger.fenceAll(request));
        var recovered = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        assertTrue(recovered.fencesBody(resident));
        assertTrue(recovered.fencesBody(bioform));
        assertTrue(recovered.fenceAll(request));
        assertFalse(recovered.fencesBody(resident.liveBody(Owner.SCENE_LEASE, 7, 3)), "new epoch is not old cleanup");
        assertFalse(recovered.fencesBody(resident.liveBody(Owner.AMBIENT_LEASE, 7, 2)), "wrong owner is not old cleanup");
        assertEquals(3, recovered.reconstructionEpoch(resident.actorId()));
    }

    @Test void invalidLaterMemberLeavesEarlierMembersUntouched() {
        var first = live(1, ActorKind.RESIDENT, 2);
        var second = live(2, ActorKind.BIOFORM, 5);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var foreignEpoch = carrier(second.liveBody(Owner.SCENE_LEASE, 7, 6));
        assertTrue(ledger.fenceAll(List.of(foreignEpoch)));
        assertFalse(ledger.fenceAll(List.of(carrier(first), carrier(second))));
        assertFalse(ledger.hasCarrier(first.actorId()));
        assertTrue(ledger.fencesBody(second.liveBody(Owner.SCENE_LEASE, 7, 6)));
    }

    @Test void duplicateActorOrUuidCannotPartiallyPublish() {
        var first = live(1, ActorKind.RESIDENT, 2);
        var other = new Declaration(new SubjectId("actor:other"), ActorKind.RESIDENT,
                Owner.SCENE_LEASE, first.entityId(), Representation.LIVE_BODY, 7, 2);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertFalse(ledger.fenceAll(List.of(carrier(first), carrier(first))));
        assertFalse(ledger.fenceAll(List.of(carrier(first), carrier(other))));
        assertFalse(ledger.hasCarrier(first.actorId()));
    }

    @Test void failedAdmissionAfterRecoveredReleaseRestoresTheSameFence() {
        var old = live(1, ActorKind.RESIDENT, 2);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.fenceAll(List.of(carrier(old))));
        ledger = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        var next = old.liveBody(Owner.AMBIENT_LEASE, 4, 3);
        assertFalse(FrontierV3ActorAdoptionAdmission.admit(ledger, FrontierV3ActorOwnerBinding.ambient(next),
                () -> {}, () -> false));
        assertTrue(ledger.fencesBody(old));
        assertFalse(ledger.fencesBody(next));
        assertTrue(ledger.pendingAdoption(old.actorId()).isEmpty());
    }
}
