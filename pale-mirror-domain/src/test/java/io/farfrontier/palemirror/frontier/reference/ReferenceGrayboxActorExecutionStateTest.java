package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(state.reconcile(simulation.snapshot()));
        var hot = state.actor(actor).orElseThrow();
        assertEquals(ReferenceGrayboxActorExecutionState.Mode.HOT, hot.mode());
        assertEquals(321, hot.actualXSixteenths());
        assertEquals(-77, hot.actualZSixteenths());
        assertEquals(35L, hot.demandedAtGameTick());
        assertEquals(simulation.snapshot().stateRevision(), hot.sourceRevision());
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
                ReferenceGrayboxActorExecutionState.Mode.HOT, snapshot.stateRevision(), 0, 0, 0, 0,
                0L, "", "", 0L, 0L, 0L, 0L));
    }
}
