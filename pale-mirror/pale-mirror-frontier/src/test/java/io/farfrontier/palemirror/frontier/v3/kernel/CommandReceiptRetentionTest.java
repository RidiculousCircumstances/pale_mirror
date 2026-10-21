package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommandReceiptRetentionTest {
    @Test void unorderedRecoveryKeepsInclusiveExpiryAndReadOnlyPressure() {
        var retention = new CommandReceiptRetention();
        var newest = receipt("new", 30);
        var oldest = receipt("old", 10);
        var boundary = receipt("boundary", 20);
        var peer = receipt("peer", 20);
        for (var receipt : List.of(newest, oldest, boundary, peer)) retention.add(receipt);
        var snapshot = retention.snapshot();
        assertEquals(4, retention.retainedAt(10));
        assertEquals(3, retention.retainedAt(20));
        assertEquals(snapshot, retention.snapshot());
        assertThrows(IllegalArgumentException.class, () -> retention.add(oldest));
        assertEquals(snapshot, retention.snapshot());
        retention.pruneBefore(20);
        assertEquals(List.of(newest, boundary, peer), retention.snapshot());
        assertFalse(retention.contains(oldest.commandId()));
        assertEquals(3, retention.size());
        retention.pruneBefore(21);
        assertEquals(List.of(newest), retention.snapshot());
        retention.pruneBefore(31);
        assertTrue(retention.snapshot().isEmpty());
        retention.add(receipt("old", 32));
        assertEquals(1, retention.retainedAt(32));
    }

    private static CommandReceipt receipt(String suffix, long tick) {
        return new CommandReceipt(new CommandId("command:" + suffix), new SimInstant(tick),
                new TransactionId("transaction:" + suffix), new Revision(tick));
    }

    @Test void externalObligationSurvivesTimeExpiryAndRecoveryUntilPositiveClosure() {
        var retention = new CommandReceiptRetention(); var held = receipt("physical", 10); var ordinary = receipt("ordinary", 11);
        retention.pin(held.commandId(), 2); retention.add(held); retention.add(ordinary);
        assertEquals(1, retention.retainedAt(1000)); retention.pruneBefore(1000);
        assertEquals(List.of(held), retention.snapshot());
        var recovered = new CommandReceiptRetention(); retention.snapshot().forEach(recovered::add);
        // The host restores its durable obligations before the first post-restart prune.
        recovered.pin(held.commandId(), 2); recovered.pruneBefore(2000);
        assertEquals(held, recovered.get(held.commandId()).orElseThrow());
        recovered.release(held.commandId()); assertTrue(recovered.snapshot().isEmpty());
        assertEquals(0, recovered.retainedAt(2001));
    }

    @Test void unsubmittedPinsAreBoundedAndReleasingThemDoesNotInventReceipts() {
        var retention = new CommandReceiptRetention(); var id = new CommandId("operation:pending"); retention.pin(id, 1);
        retention.pin(id, 1); assertThrows(IllegalStateException.class, () -> retention.pin(new CommandId("operation:other"), 1));
        assertTrue(retention.get(id).isEmpty()); retention.release(id);
        retention.pin(new CommandId("operation:other"), 1); assertEquals(0, retention.size());
    }
}
