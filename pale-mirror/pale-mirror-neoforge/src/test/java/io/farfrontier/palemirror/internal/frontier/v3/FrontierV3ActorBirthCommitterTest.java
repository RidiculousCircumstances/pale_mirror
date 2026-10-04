package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorBirthCommitterTest {
    private static final WorldId WORLD = new WorldId("frontier:birth-commit");
    private static final SubjectId ACTOR = new SubjectId("bioform:newborn");
    private static final HiveGrowthCompleted BIRTH = new HiveGrowthCompleted(new SubjectId("job:growth"),
            new ActorBirthIdentity(ACTOR, ActorBirthIdentity.Kind.BIOFORM));
    private static TransactionRecord transaction(FrontierPayload... payloads) {
        var id = new TransactionId("transaction:birth");
        var events = new java.util.ArrayList<FrontierEvent>();
        for (var payload : payloads) events.add(new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                new EventId("event:birth-" + events.size()), id, WORLD, new Revision(1), new SimInstant(1),
                new SubjectId("hive:one"), CauseChain.root(new CommandId("command:birth")), payload));
        return new TransactionRecord(id, WORLD, new Revision(1), new SimInstant(1), events);
    }
    private static FrontierV3AmbientCarrierLedger reload(FrontierV3AmbientCarrierLedger ledger) {
        return FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
    }
    @Test void permissionIsPersistedBeforeCanonicalPublicationAndUnusedRetryIsSafe() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var sequence = new java.util.ArrayList<String>();
        var committer = new FrontierV3ActorBirthCommitter(WORLD, ledger, () -> {
            assertEquals(FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED, reload(ledger).firstAdmission(ACTOR).orElseThrow().phase());
            sequence.add("permit");
        }, (tx, durability) -> sequence.add("wal"));
        committer.commit(transaction(BIRTH), Durability.DURABLE_BEFORE_EFFECT);
        committer.commit(transaction(BIRTH), Durability.DURABLE_BEFORE_EFFECT);
        assertEquals(List.of("permit", "wal", "permit", "wal"), sequence);
        assertEquals(1, ledger.firstAdmissions().size());
        var identity = ledger.firstAdmission(ACTOR).orElseThrow().identity();
        assertEquals(ActorKind.BIOFORM, identity.kind());
        assertEquals(SceneLease.deterministicEntityId(WORLD, ACTOR), identity.entityId());
    }
    @Test void failedPermissionWriteCannotPublishCanonicalBirth() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var committer = new FrontierV3ActorBirthCommitter(WORLD, ledger,
                () -> { throw new IllegalStateException("permission storage failed"); },
                (tx, durability) -> fail("canonical birth must not be published"));
        assertThrows(IllegalStateException.class, () -> committer.commit(transaction(BIRTH), Durability.DURABLE_BEFORE_EFFECT));
    }
    @Test void failedWalLeavesOnlyUnusedPermissionAndUsedHistoryCannotBeReset() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var committer = new FrontierV3ActorBirthCommitter(WORLD, ledger, () -> {},
                (tx, durability) -> { throw new IllegalStateException("wal failed"); });
        assertThrows(IllegalStateException.class, () -> committer.commit(transaction(BIRTH), Durability.DURABLE_BEFORE_EFFECT));
        var restored = reload(ledger);
        var id = restored.firstAdmission(ACTOR).orElseThrow().identity();
        assertTrue(restored.beginFirstAdmission(FrontierV3ActorOwnerBinding.body(new FrontierV3ActorCarrierComposition.Declaration(ACTOR, id.kind(),
                        FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, id.entityId(),
                        FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 1))));
        var denied = new FrontierV3ActorBirthCommitter(WORLD, restored, () -> fail("no reset"),
                (tx, durability) -> fail("no new birth"));
        assertThrows(IllegalStateException.class, () -> denied.commit(transaction(BIRTH), Durability.DURABLE_BEFORE_EFFECT));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING, restored.firstAdmission(ACTOR).orElseThrow().phase());
    }
    @Test void duplicateAndNonDurableBirthFailBeforeWriting() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var committer = new FrontierV3ActorBirthCommitter(WORLD, ledger, () -> fail("no persistence"),
                (tx, durability) -> fail("no canonical publication"));
        assertThrows(IllegalArgumentException.class, () -> committer.commit(transaction(BIRTH), Durability.BATCHABLE));
        assertThrows(IllegalArgumentException.class, () -> committer.commit(transaction(BIRTH, BIRTH), Durability.DURABLE_BEFORE_EFFECT));
        assertTrue(ledger.firstAdmissions().isEmpty());
    }
    @Test void laterUsedBirthCannotPartiallyPublishEarlierPermission() {
        var otherActor = new SubjectId("bioform:later-newborn");
        var otherBirth = new HiveGrowthCompleted(new SubjectId("job:later-growth"),
                new ActorBirthIdentity(otherActor, ActorBirthIdentity.Kind.BIOFORM));
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var identity = new FrontierV3ActorFirstAdmission.Identity(otherActor,
                ActorKind.BIOFORM,
                SceneLease.deterministicEntityId(WORLD, otherActor));
        assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(identity)));
        assertTrue(ledger.beginFirstAdmission(FrontierV3ActorOwnerBinding.body(new FrontierV3ActorCarrierComposition.Declaration(otherActor, identity.kind(),
                        FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, identity.entityId(),
                        FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 1))));
        var before = ledger.save(new CompoundTag(), null);
        var committer = new FrontierV3ActorBirthCommitter(WORLD, ledger,
                () -> fail("rejected birth batch must not persist"),
                (tx, durability) -> fail("rejected birth batch must not reach WAL"));
        assertThrows(IllegalStateException.class, () -> committer.commit(
                transaction(BIRTH, otherBirth), Durability.DURABLE_BEFORE_EFFECT));
        assertEquals(before, ledger.save(new CompoundTag(), null));
        assertTrue(ledger.firstAdmission(ACTOR).isEmpty());
    }
    @Test void twoFreshBirthsPublishOneCompletePermissionImageBeforeWal() {
        var otherActor = new SubjectId("bioform:later-newborn");
        var otherBirth = new HiveGrowthCompleted(new SubjectId("job:later-growth"),
                new ActorBirthIdentity(otherActor, ActorBirthIdentity.Kind.BIOFORM));
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var sequence = new java.util.ArrayList<String>();
        var committer = new FrontierV3ActorBirthCommitter(WORLD, ledger, () -> {
            assertEquals(2, reload(ledger).firstAdmissions().size());
            sequence.add("permit");
        }, (tx, durability) -> sequence.add("wal"));
        committer.commit(transaction(BIRTH, otherBirth), Durability.DURABLE_BEFORE_EFFECT);
        assertEquals(List.of("permit", "wal"), sequence);
    }
}
