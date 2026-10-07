package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3SceneStoredRecoveryTest {
    private static final ChunkPos COLUMN = new ChunkPos(0, 0);
    private static final SceneLeaseId LEASE = new SceneLeaseId("lease:stored-patrol");

    @Test void wholeMemberProofKeepsSavedInjuryAndRejectsMissingOrDuplicateBodies() {
        var first = receipt("resident:stored-patrol-one", "b771d9a2-9579-420a-8201-6b97b7df4d11", 18);
        var second = receipt("resident:stored-patrol-two", "eb739c93-148e-4910-bd34-3a7ce18605a9", 9);
        var receipts = Map.of(first.carrier().identity().entityId(), first, second.carrier().identity().entityId(), second);
        var saved = Map.of(first.carrier().identity().entityId(), saved(first), second.carrier().identity().entityId(), saved(second));
        var snapshot = new FrontierV3StoredEntityInspection.StampedSnapshot(
                new FrontierV3StoredEntityInspection.Snapshot(receipts.keySet(), saved), 0L);
        var exact = new FrontierV3SceneStoredRecovery.Proof(
                new FrontierV3StoredEntityCensus.Result(Map.of(first.carrier().identity().entityId(), Set.of(COLUMN),
                        second.carrier().identity().entityId(), Set.of(COLUMN)), 0L),
                Map.of(COLUMN, snapshot), Map.of(COLUMN, receipts.keySet()));
        assertTrue(exact.matches(receipts));
        assertFalse(exact.matches(Map.of(first.carrier().identity().entityId(), first)),
                "a partial roster cannot authorize unattended COLD release");

        var injuredDifferently = new FrontierV3SceneDeparture(second.carrier(), second.residenceGeneration(), second.leaseId(), second.sceneRevision(),
                new SceneMemberPosition(second.observed().actorId(), second.observed().body(), FixedScalar.whole(8)),
                second.canonicalHealthAtCapture());
        assertFalse(exact.matches(Map.of(first.carrier().identity().entityId(), first,
                second.carrier().identity().entityId(), injuredDifferently)),
                "saved physical health is part of the exact recovery proof");

        var duplicate = new FrontierV3SceneStoredRecovery.Proof(
                new FrontierV3StoredEntityCensus.Result(Map.of(first.carrier().identity().entityId(), Set.of(COLUMN, new ChunkPos(1, 0)),
                        second.carrier().identity().entityId(), Set.of(COLUMN)), 0L),
                Map.of(COLUMN, snapshot), Map.of(COLUMN, receipts.keySet()));
        assertFalse(duplicate.matches(receipts));
    }


    @Test void mixedLoadedAndStoredPartitionMustCoverTheWholeSceneWithoutOverlap() {
        var worker = receipt("resident:mixed-worker", "b771d9a2-9579-420a-8201-6b97b7df4d13", 17);
        var loadedMemberId = UUID.fromString("eb739c93-148e-4910-bd34-3a7ce18605ab");
        var storedWorker = new FrontierV3SceneStoredRecovery.Receipts(
                Map.of(worker.carrier().identity().entityId(), worker), Set.of(loadedMemberId));
        var full = Set.of(worker.carrier().identity().entityId(), loadedMemberId);
        assertTrue(storedWorker.covers(full), "one stored worker and one live member form a complete partition");
        assertFalse(storedWorker.covers(Set.of(worker.carrier().identity().entityId())),
                "an extra unowned loaded identity is not part of the scene");
        assertFalse(new FrontierV3SceneStoredRecovery.Receipts(
                Map.of(worker.carrier().identity().entityId(), worker), Set.of()).covers(full),
                "unproved member cannot be omitted");
        assertFalse(new FrontierV3SceneStoredRecovery.Receipts(
                Map.of(worker.carrier().identity().entityId(), worker),
                Set.of(worker.carrier().identity().entityId(), loadedMemberId)).covers(full),
                "the same UUID cannot be both live and stored");
        var workerId = worker.carrier().identity().entityId();
        var snapshot = new FrontierV3StoredEntityInspection.StampedSnapshot(
                new FrontierV3StoredEntityInspection.Snapshot(Set.of(workerId), Map.of(workerId, saved(worker))), 0L);
        var proof = new FrontierV3SceneStoredRecovery.Proof(
                new FrontierV3StoredEntityCensus.Result(Map.of(workerId, Set.of(COLUMN)), 0L),
                Map.of(COLUMN, snapshot), Map.of(COLUMN, Set.of(workerId)));
        assertTrue(proof.matches(storedWorker), "disk proof covers the unloaded subset only");
        assertFalse(new FrontierV3SceneStoredRecovery.Proof(
                new FrontierV3StoredEntityCensus.Result(Map.of(), 0L),
                Map.of(COLUMN, snapshot), Map.of(COLUMN, Set.of(workerId))).matches(storedWorker),
                "missing disk identity cannot be excused by a different loaded scene member");
    }

    @Test void crashWithoutUnloadCallbacksRequiresOneUniqueSavedColumnForEveryMissingIdentity() {
        var first = receipt("resident:crash-one", "b771d9a2-9579-420a-8201-6b97b7df4d21", 19);
        var second = receipt("resident:crash-two", "b771d9a2-9579-420a-8201-6b97b7df4d22", 17);
        var ids = Set.of(first.carrier().identity().entityId(), second.carrier().identity().entityId());
        var census = new FrontierV3StoredEntityCensus.Result(Map.of(
                first.carrier().identity().entityId(), Set.of(COLUMN),
                second.carrier().identity().entityId(), Set.of(COLUMN)), 0L);
        var snapshot = new FrontierV3StoredEntityInspection.StampedSnapshot(
                new FrontierV3StoredEntityInspection.Snapshot(ids,
                        Map.of(first.carrier().identity().entityId(), saved(first),
                                second.carrier().identity().entityId(), saved(second))), 0L);
        var exact = new FrontierV3SceneStoredRecovery.UnobservedProof(census,
                Map.of(COLUMN, ids), Map.of(COLUMN, snapshot), ids);
        assertTrue(exact.complete());
        assertTrue(exact.body(first.carrier().identity().entityId()).matches(first));
        assertTrue(exact.body(second.carrier().identity().entityId()).matches(second));
        assertFalse(new FrontierV3SceneStoredRecovery.UnobservedProof(census,
                Map.of(COLUMN, Set.of(first.carrier().identity().entityId())),
                Map.of(COLUMN, snapshot), ids).complete(), "one absent worker forbids whole-scene publication");
        assertFalse(new FrontierV3SceneStoredRecovery.UnobservedProof(new FrontierV3StoredEntityCensus.Result(Map.of(
                first.carrier().identity().entityId(), Set.of(COLUMN, new ChunkPos(1, 0)),
                second.carrier().identity().entityId(), Set.of(COLUMN)), 0L),
                Map.of(COLUMN, ids), Map.of(COLUMN, snapshot), ids).complete(),
                "a duplicate UUID in another entity column forbids publication");
    }

    @Test void decidedProofRearmsOnlyForChangedPhysicalEvidenceAfterBoundedCooldown() {
        var worker = receipt("resident:rearm-worker", "b771d9a2-9579-420a-8201-6b97b7df4d14", 19);
        var first = new FrontierV3SceneStoredRecovery.Receipts(
                Map.of(worker.carrier().identity().entityId(), worker));
        var changed = new FrontierV3SceneStoredRecovery.Receipts(first.actors(),
                Set.of(UUID.fromString("eb739c93-148e-4910-bd34-3a7ce18605ac")));
        var epoch0 = Optional.of(Map.of(COLUMN, 0L));
        var epoch1 = Optional.of(Map.of(COLUMN, 1L));
        assertFalse(FrontierV3SceneStoredRecovery.changedEvidenceAfterCooldown(first, epoch0, 100,
                first, epoch0, 500), "time alone cannot re-scan an unchanged failure");
        assertFalse(FrontierV3SceneStoredRecovery.changedEvidenceAfterCooldown(first, epoch0, 100,
                changed, epoch0, 119), "a changing scene cannot trigger a per-tick scan storm");
        assertTrue(FrontierV3SceneStoredRecovery.changedEvidenceAfterCooldown(first, epoch0, 100,
                changed, epoch0, 120), "a new loaded/stored partition may be proved independently");
        assertTrue(FrontierV3SceneStoredRecovery.changedEvidenceAfterCooldown(first, epoch0, 100,
                first, epoch1, 120), "a new exact-column write may repair a stale disk observation");
        assertFalse(FrontierV3SceneStoredRecovery.changedEvidenceAfterCooldown(first, epoch0, 100,
                first, Optional.empty(), 120), "write-index overflow cannot grant another disk proof");
    }

    private static FrontierV3SceneDeparture receipt(String actorValue, String uuid, long physicalHealth) {
        var actor = new SubjectId(actorValue);
        var id = UUID.fromString(uuid);
        var declaration = new FrontierV3ActorCarrierComposition.Declaration(actor,
                ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, id,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 0L, 2);
        return new FrontierV3SceneDeparture(new FrontierV3AmbientCarrierLedger.Carrier(declaration, 7, 3), 1L,
                LEASE, 7, new SceneMemberPosition(actor, new BodyPosition(12, 65, 10), FixedScalar.whole(physicalHealth)),
                FixedScalar.whole(20));
    }

    private static FrontierV3SceneDeparturePersistence.SavedBody saved(FrontierV3SceneDeparture receipt) {
        var identity = receipt.carrier().identity();
        return new FrontierV3SceneDeparturePersistence.SavedBody(identity.entityId(), "minecraft:villager",
                identity.actorId().value(), "RESIDENT", "ACTOR_BODY", "LIVE_BODY", 0, identity.epoch(),
                receipt.residenceGeneration(),
                receipt.observed().body(), receipt.observed().health());
    }
}
