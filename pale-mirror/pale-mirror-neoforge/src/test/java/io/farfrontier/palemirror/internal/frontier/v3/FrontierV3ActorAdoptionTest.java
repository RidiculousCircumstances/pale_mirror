package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorAdoptionTest {
    private static final UUID UUID_1 = UUID.fromString("c511b610-5bb8-42b5-a09f-578a6888d9e3");
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");
    private static final Declaration OLD = new Declaration(ACTOR, ActorKind.RESIDENT, Owner.SCENE_LEASE,
            UUID_1, Representation.INACTIVE_CARRIER, 7L, 3L);
    private static final FrontierV3AmbientCarrierLedger.Carrier CARRIER =
            new FrontierV3AmbientCarrierLedger.Carrier(OLD, 7L, 12L);

    @Test void preservesExplicitEndpointsAndIndependentOwnerClocks() {
        for (Owner owner : Owner.values()) {
            long revision = owner == Owner.SCENE_LEASE ? 8L : 13L;
            var live = OLD.liveBody(owner, revision, 4L);
            var receipt = new FrontierV3ActorAdoption(CARRIER, FrontierV3ActorAdoptionFixture.binding(live));
            assertEquals(receipt, FrontierV3ActorAdoption.load(receipt.save()));
            assertTrue(receipt.matches(FrontierV3ActorAdoptionFixture.binding(live)));
            assertFalse(receipt.matches(FrontierV3ActorAdoptionFixture.binding(OLD.liveBody(owner, revision + 1L, 4L))));
            assertFalse(receipt.matches(FrontierV3ActorAdoptionFixture.binding(OLD.liveBody(owner, revision, 5L))));
        }
    }

    @Test void rejectsDifferentIdentityRepresentationStaleRevisionAndRetiredEpoch() {
        for (var invalid : java.util.List.of(
                new Declaration(new SubjectId("resident:1-2"), ActorKind.RESIDENT, Owner.AMBIENT_LEASE,
                        UUID_1, Representation.LIVE_BODY, 13L, 4L),
                new Declaration(ACTOR, ActorKind.RESIDENT, Owner.AMBIENT_LEASE, new UUID(0, 1),
                        Representation.LIVE_BODY, 13L, 4L),
                new Declaration(ACTOR, ActorKind.BIOFORM, Owner.AMBIENT_LEASE, UUID_1,
                        Representation.LIVE_BODY, 13L, 4L),
                OLD.liveBody(Owner.AMBIENT_LEASE, 13L, 4L).inactiveCarrier(),
                OLD.liveBody(Owner.AMBIENT_LEASE, 12L, 4L),
                OLD.liveBody(Owner.SCENE_LEASE, 7L, 4L),
                OLD.liveBody(Owner.AMBIENT_LEASE, 13L, 3L),
                OLD.liveBody(Owner.AMBIENT_LEASE, 13L, 2L))) {
            assertThrows(IllegalArgumentException.class, () -> new FrontierV3ActorAdoption(CARRIER, FrontierV3ActorAdoptionFixture.binding(invalid)));
        }
    }
    @Test void skippedUnattemptedCanonicalEpochDoesNotRequireASecondPhysicalAllocator() {
        var live = OLD.liveBody(Owner.AMBIENT_LEASE, 13L, 5L);
        var receipt = new FrontierV3ActorAdoption(CARRIER, FrontierV3ActorAdoptionFixture.binding(live));
        assertEquals(5L, receipt.admitted().epoch());
        assertEquals(receipt, FrontierV3ActorAdoption.load(receipt.save()));
    }

    @Test void rejectsMissingForgedAndWrongTypedPersistedDimensions() {
        var receipt = new FrontierV3ActorAdoption(CARRIER, FrontierV3ActorAdoptionFixture.binding(OLD.liveBody(Owner.AMBIENT_LEASE, 13L, 4L)));
        for (String endpoint : java.util.List.of("predecessor", "admitted")) {
            for (String key : java.util.List.of("actor", "uuid", "kind", "owner", "representation", "revision", "epoch")) {
                var incomplete = receipt.save(); incomplete.getCompound(endpoint).remove(key);
                assertThrows(IllegalStateException.class, () -> FrontierV3ActorAdoption.load(incomplete));
            }
            for (String key : java.util.List.of("kind", "owner", "representation")) {
                var forged = receipt.save(); forged.getCompound(endpoint).putString(key, "UNREGISTERED");
                assertThrows(IllegalStateException.class, () -> FrontierV3ActorAdoption.load(forged));
            }
        }
        var stale = receipt.save(); stale.getCompound("admitted").putLong("revision", 12L);
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorAdoption.load(stale));
        var wrongType = receipt.save(); wrongType.putString("physicalRevision", "7");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorAdoption.load(wrongType));
        var wrongFormat = receipt.save(); wrongFormat.putInt("format", 1);
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorAdoption.load(wrongFormat));
    }
    @Test void retainedSceneIdCannotBeOmittedOrReplacedByMatchingRevision() {
        var live = OLD.liveBody(Owner.SCENE_LEASE, 8L, 4L);
        var scene = FrontierV3ActorOwnerBinding.scene(live, new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:original"));
        var receipt = new FrontierV3ActorAdoption(CARRIER, scene);
        assertEquals(receipt, FrontierV3ActorAdoption.load(receipt.save()));
        assertFalse(receipt.matches(FrontierV3ActorOwnerBinding.scene(live,
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:impostor"))));
        var missing = receipt.save(); missing.getCompound("admitted").remove("sceneLease");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorAdoption.load(missing));
        var old = receipt.save(); old.putInt("format", 1);
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorAdoption.load(old));
    }
}
