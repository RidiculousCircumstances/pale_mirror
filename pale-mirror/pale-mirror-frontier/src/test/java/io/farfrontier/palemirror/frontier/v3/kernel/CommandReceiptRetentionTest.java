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
}
