package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxActorExecutionStateTest {
    @Test
    void exactActorsReceiveOneLeaseAndHotPositionSurvivesSourceRefresh() {
        ReferenceGrayboxSimulation simulation = ReferenceGrayboxSimulation.create(42L);
        ReferenceGrayboxSnapshot before = simulation.snapshot();
        ReferenceGrayboxActorExecutionState state = ReferenceGrayboxActorExecutionState.bootstrap(before);
        String actor = before.residents().getFirst().id();

        assertEquals(before.residents().size() + before.bioforms().size(), state.actors().size());
        assertEquals(ReferenceGrayboxActorExecutionState.Mode.COLD, state.actor(actor).orElseThrow().mode());
        assertTrue(state.prepare(actor, "materializer:chunk:0_0", 20L));
        var prepared = state.actor(actor).orElseThrow();
        assertEquals(ReferenceGrayboxActorExecutionState.Mode.PREPARING, prepared.mode());
        assertFalse(state.prepare(actor, "materializer:chunk:0_0", 21L), "a second executor must not receive a second lease");
        assertTrue(state.activate(actor, prepared.leaseId(), "materializer:chunk:0_0", 22L));
        assertTrue(state.capture(actor, prepared.leaseId(), "materializer:chunk:0_0", 321, -77, 30L));
        assertTrue(state.touchDemand(actor, prepared.leaseId(), "materializer:chunk:0_0", 35L));

        simulation.tick();
        assertFalse(state.reconcile(simulation.snapshot()),
                "an unchanged exact body must not be rewritten merely because another source process advanced the day");
        var hot = state.actor(actor).orElseThrow();
        assertEquals(ReferenceGrayboxActorExecutionState.Mode.HOT, hot.mode());
        assertEquals(321, hot.actualXSixteenths());
        assertEquals(-77, hot.actualZSixteenths());
        assertEquals(35L, hot.demandedAtGameTick());
        assertEquals(actorRevision(simulation.snapshot(), actor), hot.sourceRevision());
    }

    @Test
    void restartRequiresLeaseCheckedRecoveryInsteadOfBlindRespawn() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxActorExecutionState state = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        String actor = snapshot.residents().getFirst().id();
        assertTrue(state.prepare(actor, "materializer:chunk:0_0", 1L));
        String lease = state.actor(actor).orElseThrow().leaseId();
        assertTrue(state.activate(actor, lease, "materializer:chunk:0_0", 2L));

        ReferenceGrayboxActorExecutionState restored = ReferenceGrayboxActorExecutionState.restore(state.actors());
        assertTrue(restored.enterRecovery(3L));
        assertEquals(ReferenceGrayboxActorExecutionState.Mode.RECOVERING, restored.actor(actor).orElseThrow().mode());
        assertFalse(restored.recoverHot(actor, lease, "materializer:other", 4L), "a stale executor cannot adopt a body");
        assertTrue(restored.recoverCold(actor, lease, "materializer:chunk:0_0", 4L));
        assertEquals(ReferenceGrayboxActorExecutionState.Mode.COLD, restored.actor(actor).orElseThrow().mode());
        assertTrue(restored.actor(actor).orElseThrow().leaseId().isEmpty());
        assertTrue(restored.prepare(actor, "materializer:chunk:0_0", 5L));
        assertFalse(lease.equals(restored.actor(actor).orElseThrow().leaseId()),
                "a new physical incarnation must never reuse a settled lease ID");
    }

    @Test
    void physicallyBlockedHotBodyReleasesOneLeaseWithoutLosingItsExactHandoff() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxActorExecutionState state = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        String actor = snapshot.residents().getFirst().id();
        assertTrue(state.prepare(actor, "materializer:chunk:0_0", 1L));
        String lease = state.actor(actor).orElseThrow().leaseId();
        assertTrue(state.activate(actor, lease, "materializer:chunk:0_0", 2L));
        assertTrue(state.capture(actor, lease, "materializer:chunk:0_0", 321, -77, 3L));

        assertFalse(state.deferBlockedHotActor(actor, lease, "materializer:other", 4L),
                "a foreign executor may not release a live actor after an obstruction");
        assertTrue(state.deferBlockedHotActor(actor, lease, "materializer:chunk:0_0", 4L));
        var deferred = state.actor(actor).orElseThrow();
        assertEquals(ReferenceGrayboxActorExecutionState.Mode.COLD, deferred.mode());
        assertEquals(321, deferred.actualXSixteenths());
        assertEquals(-77, deferred.actualZSixteenths());
        assertTrue(deferred.leaseId().isEmpty(), "the rejected body must not retain a live executor claim");
    }

    @Test
    void blockedPreparationReleasesItsLeaseWithoutInventingAPhysicalBody() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxActorExecutionState state = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        String actor = snapshot.residents().getFirst().id();
        int sourceX = state.actor(actor).orElseThrow().actualXSixteenths();
        int sourceZ = state.actor(actor).orElseThrow().actualZSixteenths();

        assertTrue(state.prepare(actor, "materializer:chunk:0_0", 10L));
        String lease = state.actor(actor).orElseThrow().leaseId();
        assertFalse(state.cancelPreparation(actor, lease, "materializer:other", 11L),
                "a foreign executor cannot release a preparation lease");
        assertTrue(state.cancelPreparation(actor, lease, "materializer:chunk:0_0", 11L));

        var released = state.actor(actor).orElseThrow();
        assertEquals(ReferenceGrayboxActorExecutionState.Mode.COLD, released.mode());
        assertTrue(released.leaseId().isEmpty(), "a blocked preparation has no body and no retained lease");
        assertEquals(sourceX, released.actualXSixteenths());
        assertEquals(sourceZ, released.actualZSixteenths());
    }

    @Test
    void combatActionHasOneDurableEpochAndCannotBypassItsCooldownOrLease() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxActorExecutionState state = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        String actor = snapshot.residents().getFirst().id();
        assertTrue(state.prepare(actor, "materializer:chunk:0_0", 10L));
        String lease = state.actor(actor).orElseThrow().leaseId();
        assertTrue(state.activate(actor, lease, "materializer:chunk:0_0", 11L));

        var action = state.reserveCombatAction(actor, lease, "materializer:chunk:0_0", 12L, 20L).orElseThrow();
        assertEquals(1L, action.actionEpoch());
        assertFalse(state.reserveCombatAction(actor, lease, "materializer:chunk:0_0", 13L, 20L).isPresent(),
                "a tick-loop must not manufacture a second hit before the persisted cooldown expires");
        assertFalse(state.reserveCombatAction(actor, lease, "materializer:other", 32L, 20L).isPresent(),
                "a different executor must not use the live body's combat authority");
        var next = state.reserveCombatAction(actor, lease, "materializer:chunk:0_0", 32L, 20L).orElseThrow();
        assertEquals(2L, next.actionEpoch());
        assertFalse(action.id().equals(next.id()), "a physical effect key must never be reused across actions");
    }

    @Test
    void malformedOrBackwardStateFailsClosed() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxActorExecutionState state = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        String actor = snapshot.residents().getFirst().id();
        assertThrows(IllegalArgumentException.class, () -> state.prepare(actor, "Bad Holder", 1L));
        assertTrue(state.prepare(actor, "materializer:chunk:0_0", 10L));
        var prepared = state.actor(actor).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> state.activate(actor, prepared.leaseId(), "materializer:chunk:0_0", 9L));
        assertThrows(IllegalArgumentException.class, () -> new ReferenceGrayboxActorExecutionState.ActorState(
                "resident:bad", ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT,
                ReferenceGrayboxActorExecutionState.Mode.HOT, actorRevision(snapshot, actor), 0, 0, 0, 0,
                0L, "", "", 0L, 0L, 0L, 0L));
    }

    @Test
    void globalProjectionRevisionDoesNotInvalidateUnchangedActorLeases() {
        ReferenceGrayboxSnapshot original = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot presentationRefresh = copyOf(original, "a".repeat(64), original.residents());
        ReferenceGrayboxActorExecutionState state = ReferenceGrayboxActorExecutionState.bootstrap(original);

        assertFalse(original.stateRevision().equals(presentationRefresh.stateRevision()));
        assertEquals(ReferenceGrayboxActorExecutionState.descriptors(original),
                ReferenceGrayboxActorExecutionState.descriptors(presentationRefresh),
                "a changed complete-frame revision alone must not stale every individual physical actor");
        assertFalse(state.reconcile(presentationRefresh),
                "an unchanged actor ledger must not churn simply because an unrelated projection field changed");
    }

    @Test
    void changedResidentSemanticsRefreshOnlyThatActorRevision() {
        ReferenceGrayboxSnapshot original = ReferenceGrayboxSimulation.create(42L).snapshot();
        String changedId = original.residents().getFirst().id();
        List<ReferenceGrayboxSnapshot.Resident> changedResidents = new ArrayList<>(original.residents());
        ReferenceGrayboxSnapshot.Resident prior = changedResidents.getFirst();
        changedResidents.set(0, new ReferenceGrayboxSnapshot.Resident(prior.id(), prior.homeSettlementId(), prior.occupation(),
                prior.economicClass(), prior.location(), prior.locationId(), "wounded", prior.deploymentRole(),
                prior.position(), prior.colour()));
        ReferenceGrayboxSnapshot changed = copyOf(original, "b".repeat(64), changedResidents);
        ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(original);

        assertFalse(actorRevision(original, changedId).equals(actorRevision(changed, changedId)),
                "a changed source condition must refresh the physical body's semantic revision");
        String untouched = original.residents().get(1).id();
        assertEquals(actorRevision(original, untouched), actorRevision(changed, untouched),
                "an unrelated resident must retain its own stable source revision");
        assertTrue(execution.reconcile(changed), "the execution ledger must refresh when its exact actor semantics change");
        assertEquals(actorRevision(changed, changedId), execution.actor(changedId).orElseThrow().sourceRevision());
        assertEquals(actorRevision(original, untouched), execution.actor(untouched).orElseThrow().sourceRevision());
    }

    private static String actorRevision(ReferenceGrayboxSnapshot snapshot, String actorId) {
        return ReferenceGrayboxActorExecutionState.descriptors(snapshot).stream()
                .filter(descriptor -> descriptor.id().equals(actorId))
                .findFirst()
                .orElseThrow()
                .sourceRevision();
    }

    private static ReferenceGrayboxSnapshot copyOf(ReferenceGrayboxSnapshot source, String stateRevision,
                                                    List<ReferenceGrayboxSnapshot.Resident> residents) {
        return new ReferenceGrayboxSnapshot(source.day(), source.profileId(), stateRevision, source.bounds(), source.cells(),
                source.settlements(), source.facilities(), source.warehouses(), source.resourceSites(), source.routes(),
                source.hiveOrgans(), source.bioforms(), residents, source.fieldPosts(), source.fieldLinks(), source.activities(),
                source.effects(), source.cargoes(), source.interactions(), source.sectors(), source.chrysalises(), source.readouts(),
                source.events());
    }
}
