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
    private static final Declaration OLD = new Declaration(ACTOR, ActorKind.RESIDENT, Owner.ACTOR_BODY,
            UUID_1, Representation.INACTIVE_CARRIER, 0L, 3L);
    private static final FrontierV3AmbientCarrierLedger.Carrier CARRIER =
            new FrontierV3AmbientCarrierLedger.Carrier(OLD, 7L, 12L);

    @Test void preservesExplicitIncarnationsWithoutAnActivityOwnerClock() {
        for (Owner owner : Owner.values()) {
            var live = OLD.liveBody(owner, 0L, 4L);
            var receipt = new FrontierV3ActorAdoption(CARRIER, FrontierV3ActorAdoptionFixture.binding(live));
            assertEquals(receipt, FrontierV3ActorAdoption.load(receipt.save()));
            assertTrue(receipt.matches(FrontierV3ActorAdoptionFixture.binding(live)));
            assertFalse(receipt.matches(FrontierV3ActorAdoptionFixture.binding(OLD.liveBody(owner, 0L, 5L))));
            assertEquals(0L, receipt.admitted().authorityRevision());
        }
    }

    @Test void rejectsDifferentIdentityRepresentationAndRetiredEpoch() {
        for (var invalid : java.util.List.of(
                new Declaration(new SubjectId("resident:1-2"), ActorKind.RESIDENT, Owner.ACTOR_BODY,
                        UUID_1, Representation.LIVE_BODY, 0L, 4L),
                new Declaration(ACTOR, ActorKind.RESIDENT, Owner.ACTOR_BODY, new UUID(0, 1),
                        Representation.LIVE_BODY, 0L, 4L),
                new Declaration(ACTOR, ActorKind.BIOFORM, Owner.ACTOR_BODY, UUID_1,
                        Representation.LIVE_BODY, 0L, 4L),
                OLD.liveBody(Owner.ACTOR_BODY, 0L, 4L).inactiveCarrier(),
                OLD.liveBody(Owner.ACTOR_BODY, 0L, 3L),
                OLD.liveBody(Owner.ACTOR_BODY, 0L, 2L))) {
            assertThrows(IllegalArgumentException.class, () -> new FrontierV3ActorAdoption(CARRIER, FrontierV3ActorAdoptionFixture.binding(invalid)));
        }
    }
    @Test void skippedUnattemptedCanonicalEpochDoesNotRequireASecondPhysicalAllocator() {
        var live = OLD.liveBody(Owner.ACTOR_BODY, 0L, 5L);
        var receipt = new FrontierV3ActorAdoption(CARRIER, FrontierV3ActorAdoptionFixture.binding(live));
        assertEquals(5L, receipt.admitted().epoch());
        assertEquals(receipt, FrontierV3ActorAdoption.load(receipt.save()));
    }

    @Test void rejectsMissingForgedAndWrongTypedPersistedDimensions() {
        var receipt = new FrontierV3ActorAdoption(CARRIER, FrontierV3ActorAdoptionFixture.binding(OLD.liveBody(Owner.ACTOR_BODY, 0L, 4L)));
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
    @Test void activityOwnershipCannotBeInjectedIntoPhysicalAdoption() {
        var live = OLD.liveBody(Owner.ACTOR_BODY, 0L, 4L);
        var scene = FrontierV3ActorOwnerBinding.body(live);
        var receipt = new FrontierV3ActorAdoption(CARRIER, scene);
        assertEquals(receipt, FrontierV3ActorAdoption.load(receipt.save()));
        assertTrue(receipt.matches(FrontierV3ActorOwnerBinding.body(live)));
        assertFalse(receipt.save().getCompound("admitted").contains("sceneLease"));
        var injected = receipt.save(); injected.getCompound("admitted").putString("sceneLease", "lease:foreign");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorAdoption.load(injected));
        var old = receipt.save(); old.putInt("format", 1);
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorAdoption.load(old));
    }
}
