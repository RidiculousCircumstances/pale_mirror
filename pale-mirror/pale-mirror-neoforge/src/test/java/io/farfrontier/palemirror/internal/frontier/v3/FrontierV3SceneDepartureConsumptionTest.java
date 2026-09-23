package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SceneDepartureConsumptionTest {
    private static final FrontierWorldState STATE = FrontierWorldState.initial(
            FrontierBootstrapper.create(new WorldId("frontier:departure-consumer"), 93L));
    private static final SubjectId ACTOR = STATE.bootstrap().settlements().getFirst().residents().getFirst().id();
    private static final SceneMember MEMBER = new SceneMember(ACTOR, SceneLease.deterministicEntityId(STATE.bootstrap().worldId(), ACTOR));
    private static final BodyPosition BODY = STATE.actorLocations().get(ACTOR).body();
    private static final FixedScalar BASELINE = STATE.actorLocations().get(ACTOR).condition().health();
    private static final SceneLease LEASE = SceneLease.forCause(new SceneLeaseId("lease:departure-consumer"), STATE.bootstrap().worldId(),
            new ProductionWorkSceneCause(new SubjectId("job:production-departure-consumer")), BODY.supportingSurface().support(),
            new SimInstant(0), 7, SceneLeaseStatus.DRAINING, List.of(MEMBER), Map.of(ACTOR, BODY), Set.of(), Optional.empty());

    private static FrontierV3SceneDeparture receipt(SceneLeaseId lease, long revision, FixedScalar baseline,
                                                    FrontierV3ActorCarrierComposition.ActorKind kind) {
        var declaration = new FrontierV3ActorCarrierComposition.Declaration(ACTOR, kind,
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, MEMBER.entityId(),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, revision, 4);
        return new FrontierV3SceneDeparture(new FrontierV3AmbientCarrierLedger.Carrier(declaration, revision, 0), lease, revision,
                new SceneMemberPosition(ACTOR, BODY, FixedScalar.whole(9)), baseline);
    }

    private static FrontierV3SceneDeparture valid() {
        return receipt(LEASE.id(), 7, BASELINE, FrontierV3ActorCarrierComposition.ActorKind.RESIDENT);
    }

    @Test
    void recoveredExactReceiptProvidesFinalHealthAndIdempotentFenceWithoutErasingEvidence() {
        var initial = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(initial.recordDeparture(valid()));
        var ledger = FrontierV3AmbientCarrierLedger.load(initial.save(new CompoundTag(), null), null);
        assertEquals(FixedScalar.whole(9), FrontierV3SceneDepartureObserver.validDeparture(STATE, LEASE, MEMBER, ledger)
                .orElseThrow().observed().health());
        assertTrue(FrontierV3SceneDepartureObserver.fenceDeparture(STATE, LEASE, MEMBER, ledger));
        assertTrue(FrontierV3SceneDepartureObserver.fenceDeparture(STATE, LEASE, MEMBER, ledger));
        assertTrue(ledger.hasCarrier(ACTOR));
        assertEquals(valid(), ledger.departure(ACTOR).orElseThrow(),
                "a refused canonical release must be able to retry the same receipt");
    }

    @Test
    void wrongSceneRevisionBaselineAndActorKindCannotFence() {
        var invalid = List.of(
                receipt(new SceneLeaseId("lease:other"), 7, BASELINE, FrontierV3ActorCarrierComposition.ActorKind.RESIDENT),
                receipt(LEASE.id(), 8, BASELINE, FrontierV3ActorCarrierComposition.ActorKind.RESIDENT),
                receipt(LEASE.id(), 7, BASELINE.plus(FixedScalar.ONE), FrontierV3ActorCarrierComposition.ActorKind.RESIDENT),
                receipt(LEASE.id(), 7, BASELINE, FrontierV3ActorCarrierComposition.ActorKind.BIOFORM));
        for (var receipt : invalid) {
            var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
            assertTrue(ledger.recordDeparture(receipt));
            assertTrue(FrontierV3SceneDepartureObserver.validDeparture(STATE, LEASE, MEMBER, ledger).isEmpty());
            assertFalse(FrontierV3SceneDepartureObserver.fenceDeparture(STATE, LEASE, MEMBER, ledger));
            assertFalse(ledger.hasCarrier(ACTOR));
        }
        assertTrue(FrontierV3SceneDepartureObserver.validDeparture(STATE, LEASE, MEMBER,
                FrontierV3AmbientCarrierLedger.emptyForTest()).isEmpty());
    }

    @Test
    void exactReturnWithdrawsOnlyItsOwnProvisionalFence() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        ledger.recordDeparture(valid());
        assertTrue(FrontierV3SceneDepartureObserver.fenceDeparture(STATE, LEASE, MEMBER, ledger));
        assertFalse(ledger.resumeDeparture(receipt(LEASE.id(), 8, BASELINE, FrontierV3ActorCarrierComposition.ActorKind.RESIDENT)));
        assertTrue(ledger.hasCarrier(ACTOR));
        assertTrue(ledger.resumeDeparture(valid()));
        assertFalse(ledger.hasCarrier(ACTOR));
        assertTrue(ledger.departure(ACTOR).isEmpty());
    }

    private static FrontierV3ActorCarrierComposition.Declaration live(long epoch) {
        return new FrontierV3ActorCarrierComposition.Declaration(ACTOR, FrontierV3ActorCarrierComposition.ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, MEMBER.entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, LEASE.revision(), epoch);
    }

    @Test
    void hotWorkIsBlockedUntilTheExactReturnedObservationResolvesItsDeparture() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        ledger.recordDeparture(valid());
        assertFalse(FrontierV3SceneDepartureObserver.permitsLiveWork(ledger, MEMBER));
        var hot = LEASE.withStatus(SceneLeaseStatus.HOT);
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, hot, MEMBER, live(5), valid().observed(), ledger));
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, hot, MEMBER, live(4),
                new SceneMemberPosition(ACTOR, BODY, FixedScalar.whole(10)), ledger));
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, hot, MEMBER, live(4),
                new SceneMemberPosition(ACTOR, new BodyPosition(BODY.x() + 1, BODY.y(), BODY.z()), FixedScalar.whole(9)), ledger));
        assertEquals(valid(), ledger.departure(ACTOR).orElseThrow());
        assertFalse(FrontierV3SceneDepartureObserver.permitsLiveWork(ledger, MEMBER));
        assertTrue(FrontierV3SceneDepartureObserver.resumeReturned(STATE, hot, MEMBER, live(4), valid().observed(), ledger));
        assertTrue(FrontierV3SceneDepartureObserver.permitsLiveWork(ledger, MEMBER));
    }

    @Test
    void secondUnloadWithDifferentHealthBlocksReleaseFenceAndExactOldReturnAfterRecovery() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var first = valid();
        ledger.recordDeparture(first);
        assertFalse(ledger.recordDeparture(new FrontierV3SceneDeparture(first.carrier(), first.leaseId(),
                first.sceneRevision(), new SceneMemberPosition(ACTOR, BODY, FixedScalar.whole(8)), BASELINE)));
        var recovered = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        assertTrue(FrontierV3SceneDepartureObserver.validDeparture(STATE, LEASE, MEMBER, recovered).isEmpty());
        assertFalse(FrontierV3SceneDepartureObserver.fenceDeparture(STATE, LEASE, MEMBER, recovered));
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, LEASE, MEMBER, live(4), first.observed(), recovered));
        assertFalse(FrontierV3SceneDepartureObserver.permitsLiveWork(recovered, MEMBER));
    }

    @Test
    void matchingBodyCannotEraseDepartureAgainstADifferentCanonicalBaseline() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var stale = receipt(LEASE.id(), 7, BASELINE.plus(FixedScalar.ONE), FrontierV3ActorCarrierComposition.ActorKind.RESIDENT);
        ledger.recordDeparture(stale);
        assertFalse(FrontierV3SceneDepartureObserver.resumeReturned(STATE, LEASE, MEMBER, live(4), stale.observed(), ledger));
        assertEquals(stale, ledger.departure(ACTOR).orElseThrow());
        assertFalse(FrontierV3SceneDepartureObserver.permitsLiveWork(ledger, MEMBER));
    }
}
