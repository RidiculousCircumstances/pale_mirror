package io.farfrontier.palemirror.internal.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Pure ledger tests: combat authority must survive without a live Minecraft entity. */
class ThreatCombatLedgerTest {
    @Test
    void actorHealthAndSchedulingStayInThePmLedger() {
        ThreatCombatLedger ledger = new ThreatCombatLedger();
        UUID actorId = UUID.randomUUID();
        ThreatActorControlState actor = ledger.attachActor("crimson", "mine:1", "encounter", "human",
                "pale_mirror:crimsonified_human", actorId, 21);

        String key = actor.key();
        ledger.scheduleActorAction(key, 40L);
        ledger.scheduleActorMovement(key, 12L, 3);
        assertEquals(14, ledger.applyActorDamage(key, 7));
        assertEquals(40L, actor.nextActionTick());
        assertEquals(12L, actor.nextMovementTick());
        assertEquals(3, actor.routeCursor());
        assertEquals(0, ledger.applyActorDamage(key, 99));
        assertEquals(ThreatActorControlState.Status.DEFEATED, actor.status());
    }

    @Test
    void activeProjectileIsNeverReplayedAfterRestartAndKeepsItsSingleTarget() {
        ThreatCombatLedger ledger = new ThreatCombatLedger();
        UUID targetId = UUID.randomUUID();
        PmProjectileRef proposed = new PmProjectileRef("pm:spore:projectile:mine:1:spitter:5:1", "spore", "mine:1",
                "spore:mine:1:encounter:spitter", targetId, "spore:acid_ball", "launch", 5.0F,
                5L, 105L, null, "", PmProjectileRef.State.PLANNED, 0L, "");

        PmProjectileRef ref = ledger.planProjectile(proposed);
        assertEquals(targetId, ref.targetId());
        assertTrue(ledger.activateProjectile(ref.id(), UUID.randomUUID()));
        assertTrue(ledger.recoverAfterRestart(20L));
        assertEquals(PmProjectileRef.State.UNKNOWN_AFTER_RESTART, ref.state());
        assertFalse(ledger.recoverAfterRestart(21L));
        assertTrue(ref.terminal());
    }
}
