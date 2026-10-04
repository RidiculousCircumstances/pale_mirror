package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SceneDepartureConsumptionTest {
    private static final FrontierWorldState STATE = preparedBody();
    private static FrontierWorldState preparedBody() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:departure-consumer"), 93L));
        var actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        return ActorBodyAuthority.demand(state, actor);
    }
    private static final SubjectId ACTOR = STATE.bootstrap().settlements().getFirst().residents().getFirst().id();
    private static final SceneMember MEMBER = new SceneMember(ACTOR, SceneLease.deterministicEntityId(STATE.bootstrap().worldId(), ACTOR));
    private static final BodyPosition BODY = STATE.actorLocations().get(ACTOR).body();
    private static final FixedScalar BASELINE = STATE.actorLocations().get(ACTOR).condition().health();
    private static final SceneLease LEASE = SceneLease.forCause(new SceneLeaseId("lease:departure-consumer"), STATE.bootstrap().worldId(),
            new ProductionWorkSceneCause(new SubjectId("job:production-departure-consumer")), BODY.supportingSurface().support(),
            new SimInstant(0), 7, SceneLeaseStatus.DRAINING, List.of(MEMBER), Set.of(), Optional.empty());

    private static FrontierV3SceneDeparture receipt(SceneLeaseId lease, long revision, FixedScalar baseline,
                                                    ActorKind kind) {
        var declaration = new FrontierV3ActorCarrierComposition.Declaration(ACTOR, kind,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, MEMBER.entityId(),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 0L,
                ActorBodyAuthority.current(STATE, ACTOR).physicalEpoch());
        return new FrontierV3SceneDeparture(new FrontierV3AmbientCarrierLedger.Carrier(declaration, revision, 0), 1L, lease, revision,
                new SceneMemberPosition(ACTOR, BODY, FixedScalar.whole(9)), baseline);
    }

    private static FrontierV3SceneDeparture valid() {
        return receipt(LEASE.id(), 7, BASELINE, ActorKind.RESIDENT);
    }

    @Test
    void readFencedUnloadWithoutCallbackSaveMarkerCanEnterOnlyIndependentDiskProof() {
        var departure = valid();
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.recordDeparture(departure));
        assertTrue(ledger.noLoadProofCandidate(departure));
        assertFalse(ledger.noLoadRecoverableDeparture(departure));
        assertTrue(FrontierV3SceneDepartureObserver.validDeparture(STATE, LEASE, MEMBER, ledger).isEmpty(),
                "an unload receipt without disk proof is not permission to release");
        assertTrue(ledger.markReturnRead(departure));
        assertFalse(ledger.noLoadProofCandidate(departure), "a loaded return revokes the no-load inspection candidate");
    }

    @Test
    void recoveredExactReceiptProvidesFinalHealthAndIdempotentFenceWithoutErasingEvidence() {
        var initial = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(initial.recordDeparture(valid()));
        assertTrue(initial.confirmSavedDeparture(valid()), "fixture supplies a completed entity-save acknowledgement");
        var ledger = FrontierV3AmbientCarrierLedger.load(initial.save(new CompoundTag(), null), null);
        assertEquals(FixedScalar.whole(9), FrontierV3SceneDepartureObserver.validDeparture(STATE, LEASE, MEMBER, ledger)
                .orElseThrow().observed().health());
        // Explicit unit fixture for the retained carrier store, not a scene-owned physical fence.
        assertTrue(ledger.fence(valid().carrier().identity(), valid().carrier().physicalRevision(), valid().carrier().ambientRevision()));
        assertTrue(ledger.fence(valid().carrier().identity(), valid().carrier().physicalRevision(), valid().carrier().ambientRevision()));
        assertTrue(ledger.hasCarrier(ACTOR));
        assertEquals(valid(), ledger.departure(ACTOR).orElseThrow(),
                "a refused canonical release must be able to retry the same receipt");
    }

    @Test
    void wrongSceneRevisionBaselineAndActorKindCannotFence() {
        var invalid = List.of(
                receipt(new SceneLeaseId("lease:other"), 7, BASELINE, ActorKind.RESIDENT),
                receipt(LEASE.id(), 8, BASELINE, ActorKind.RESIDENT),
                receipt(LEASE.id(), 7, BASELINE.plus(FixedScalar.ONE), ActorKind.RESIDENT),
                receipt(LEASE.id(), 7, BASELINE, ActorKind.BIOFORM));
        for (var receipt : invalid) {
            var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
            assertTrue(ledger.recordDeparture(receipt));
            assertTrue(ledger.confirmSavedDeparture(receipt), "negative isolates the canonical binding from storage proof");
            assertTrue(FrontierV3SceneDepartureObserver.validDeparture(STATE, LEASE, MEMBER, ledger).isEmpty());
            assertFalse(ledger.hasCarrier(ACTOR));
        }
        assertTrue(FrontierV3SceneDepartureObserver.validDeparture(STATE, LEASE, MEMBER,
                FrontierV3AmbientCarrierLedger.emptyForTest()).isEmpty());
    }

    @Test
    void exactReturnWithdrawsOnlyItsOwnProvisionalFence() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        ledger.recordDeparture(valid());
        assertTrue(ledger.confirmSavedDeparture(valid()));
        // Explicit retained-store fixture; actual body fencing is controller-owned.
        assertTrue(ledger.fence(valid().carrier().identity(), valid().carrier().physicalRevision(), valid().carrier().ambientRevision()));
        assertFalse(ledger.resumeDeparture(receipt(LEASE.id(), 8, BASELINE, ActorKind.RESIDENT)));
        assertTrue(ledger.hasCarrier(ACTOR));
        assertTrue(ledger.resumeDeparture(valid()));
        assertFalse(ledger.hasCarrier(ACTOR));
        assertTrue(ledger.departure(ACTOR).isEmpty());
    }

    @Test
    void storedReturnReadRevokesOldSceneSaveUntilANewUnloadIsSaved() {
        var receipt = valid();
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.recordDeparture(receipt)); assertTrue(ledger.confirmSavedDeparture(receipt));
        assertTrue(ledger.markReturnRead(receipt));
        assertTrue(ledger.returnRead(ACTOR)); assertFalse(ledger.savedDeparture(receipt));
        assertFalse(ledger.noLoadRecoverableDeparture(receipt));
        assertFalse(ledger.confirmSavedDeparture(receipt));
        var recovered = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        assertTrue(recovered.returnRead(ACTOR));
        assertFalse(recovered.savedDeparture(receipt));
        assertTrue(recovered.recordDeparture(receipt));
        assertTrue(recovered.returnRead(ACTOR), "identical callback cannot create a second unload");
        assertFalse(recovered.savedDeparture(receipt), "old save marker cannot certify the new unload");
        assertFalse(recovered.confirmSavedDeparture(receipt));
        assertFalse(recovered.noLoadRecoverableDeparture(receipt));
        assertTrue(recovered.resumeDeparture(receipt));
        var next = new FrontierV3SceneDeparture(receipt.carrier(), receipt.residenceGeneration() + 1L,
                receipt.leaseId(), receipt.sceneRevision(), receipt.observed(), receipt.canonicalHealthAtCapture(),
                receipt.offhand(), receipt.mainhand());
        assertTrue(recovered.recordDeparture(next));
        assertFalse(recovered.confirmSavedDeparture(receipt));
        assertTrue(recovered.confirmSavedDeparture(next));
        assertTrue(recovered.noLoadRecoverableDeparture(next));
    }

    @Test
    void exactStoredSceneBodyReadRevokesNoLoadRecoveryBeforeVanillaReturnsIt() {
        var receipt = valid();
        var actors = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(actors.recordDeparture(receipt)); assertTrue(actors.confirmSavedDeparture(receipt));
        var chunk = new ChunkPos(Math.floorDiv(BODY.x(), 16), Math.floorDiv(BODY.z(), 16));
        var stored = new CompoundTag(); stored.putIntArray("Position", new int[] {chunk.x, chunk.z});
        var entities = new ListTag(); var body = new CompoundTag();
        body.putUUID("UUID", MEMBER.entityId()); entities.add(body); stored.put("Entities", entities);
        assertArrayEquals(new boolean[] {true, false}, FrontierV3DepartureReturnReadFence.fenceStoredInventory(
                chunk, stored, actors, FrontierV3CargoDepartureLedger.emptyForTest()));
        assertTrue(actors.returnRead(ACTOR)); assertFalse(actors.noLoadRecoverableDeparture(receipt));
    }

    @Test
    void priorSavedSceneFormatIsRejectedWithoutInventingAReturnRead() {
        var receipt = valid(); var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.recordDeparture(receipt)); assertTrue(ledger.confirmSavedDeparture(receipt));
        var old = ledger.save(new CompoundTag(), null);
        old.putInt("format", 6); old.remove("returnReads");
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(old, null));
        var malformed = ledger.save(new CompoundTag(), null); malformed.remove("returnReads");
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(malformed, null));
        var unguarded = ledger.save(new CompoundTag(), null); unguarded.remove("readFencedDepartures");
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(unguarded, null));
    }

    private static FrontierV3ActorCarrierComposition.Declaration live(long epoch) {
        return new FrontierV3ActorCarrierComposition.Declaration(ACTOR, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, MEMBER.entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, epoch);
    }

    @Test
    void hotWorkIsBlockedUntilTheExactReturnedObservationResolvesItsDeparture() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        ledger.recordDeparture(valid());
        assertFalse(FrontierV3SceneDepartureObserver.permitsLiveWork(ledger, MEMBER));
        var hot = LEASE.withStatus(SceneLeaseStatus.HOT);
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, hot, MEMBER, live(2), valid().observed(), ledger));
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, hot, MEMBER, live(1),
                new SceneMemberPosition(ACTOR, BODY, FixedScalar.whole(10)), ledger));
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, hot, MEMBER, live(1),
                new SceneMemberPosition(ACTOR, new BodyPosition(BODY.x() + 1, BODY.y(), BODY.z()), FixedScalar.whole(9)), ledger));
        assertEquals(valid(), ledger.departure(ACTOR).orElseThrow());
        assertFalse(FrontierV3SceneDepartureObserver.permitsLiveWork(ledger, MEMBER));
        assertTrue(FrontierV3SceneDepartureObserver.resumeReturned(STATE, hot, MEMBER, live(1), valid().observed(), ledger));
        assertTrue(FrontierV3SceneDepartureObserver.permitsLiveWork(ledger, MEMBER));
    }

    @Test
    void secondUnloadWithDifferentHealthBlocksReleaseFenceAndExactOldReturnAfterRecovery() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var first = valid();
        ledger.recordDeparture(first);
        assertFalse(ledger.recordDeparture(new FrontierV3SceneDeparture(first.carrier(), first.residenceGeneration(), first.leaseId(),
                first.sceneRevision(), new SceneMemberPosition(ACTOR, BODY, FixedScalar.whole(8)), BASELINE)));
        var recovered = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        assertTrue(FrontierV3SceneDepartureObserver.validDeparture(STATE, LEASE, MEMBER, recovered).isEmpty());
        assertFalse(recovered.hasCarrier(ACTOR));
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, LEASE, MEMBER, live(1), first.observed(), recovered));
        assertFalse(FrontierV3SceneDepartureObserver.permitsLiveWork(recovered, MEMBER));
    }

    @Test
    void matchingBodyCannotEraseDepartureAgainstADifferentCanonicalBaseline() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var stale = receipt(LEASE.id(), 7, BASELINE.plus(FixedScalar.ONE), ActorKind.RESIDENT);
        ledger.recordDeparture(stale);
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, LEASE, MEMBER, live(1), stale.observed(), ledger));
        assertEquals(stale, ledger.departure(ACTOR).orElseThrow());
        assertFalse(FrontierV3SceneDepartureObserver.permitsLiveWork(ledger, MEMBER));
    }
}
