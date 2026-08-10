package io.farfrontier.palemirror.internal.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectLeaseLedgerTest {
    @Test
    void sameKeyNeverBeginsTwice() {
        EffectLeaseLedger ledger = new EffectLeaseLedger();
        EffectLease first = EffectLease.planned("pm:effect:1", "effect-key", "spore", "pm:mine", "guard",
                "direct_attack", 100L, 120L);
        EffectLease duplicate = EffectLease.planned("pm:effect:2", "effect-key", "spore", "pm:mine", "guard",
                "direct_attack", 100L, 120L);

        assertEquals(first, ledger.plan(first));
        assertEquals(first, ledger.plan(duplicate));
        assertTrue(ledger.begin(first.id()));
        ledger.complete(first.id(), 100L);
        assertFalse(ledger.begin(first.id()));
        assertEquals(EffectLeaseState.COMPLETED, ledger.find(first.id()).orElseThrow().state());
    }

    @Test
    void runningEffectIsNotReplayedAfterRestart() {
        EffectLeaseLedger ledger = new EffectLeaseLedger();
        EffectLease lease = EffectLease.planned("pm:effect:restart", "restart-key", "crimson", "pm:mine", "boss",
                "direct_attack", 100L, 130L);
        ledger.plan(lease);
        assertTrue(ledger.begin(lease.id()));

        assertTrue(ledger.recoverAfterRestart(101L));
        assertEquals(EffectLeaseState.UNKNOWN_AFTER_RESTART, lease.state());
        assertFalse(ledger.begin(lease.id()));
    }

    @Test
    void unstartedLeaseExpiresAndCompactsAfterRetention() {
        EffectLeaseLedger ledger = new EffectLeaseLedger();
        EffectLease lease = EffectLease.planned("pm:effect:expired", "expired-key", "spore", "pm:mine", "spitter",
                "projectile", 10L, 20L);
        ledger.plan(lease);

        assertTrue(ledger.expireDue(21L));
        assertEquals(EffectLeaseState.EXPIRED, lease.state());
        assertTrue(ledger.compact(72_021L));
        assertTrue(ledger.leases().isEmpty());
    }
}
