package io.farfrontier.palemirror.internal.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.WorldObjectId;

class ResourceTransferLedgerTest {
    @Test
    void activeTransferSurvivesCompactionAndTerminalReceiptHasRetention() {
        ResourceTransferLedger ledger = new ResourceTransferLedger();
        ResourceTransfer active = transfer("active", 0);
        ResourceTransfer completed = transfer("completed", 0);
        completed.physicalReserved();
        completed.domainApplied();
        completed.complete();
        ledger.add(active);
        ledger.add(completed);

        assertFalse(ledger.compact(255));
        assertTrue(ledger.compact(256));
        assertTrue(ledger.find("active").isPresent());
        assertTrue(ledger.find("completed").isEmpty());
    }

    @Test
    void duplicateIdentityFailsClosed() {
        ResourceTransferLedger ledger = new ResourceTransferLedger();
        ledger.add(transfer("one", 0));
        assertThrows(IllegalStateException.class, () -> ledger.add(transfer("one", 1)));
        assertEquals(1, ledger.transfers().size());
    }

    private static ResourceTransfer transfer(String id, long step) {
        return new ResourceTransfer(id, ResourceTransferDirection.DEPOSIT, UUID.randomUUID(),
                new WorldObjectId("pale_mirror:community"), new WorldObjectId("pale_mirror:depot"),
                ResourceKind.IRON, 4, 0, ResourceTransferRuntime.MAPPING_HASH, step,
                ResourceTransferState.PREPARED, "");
    }
}
