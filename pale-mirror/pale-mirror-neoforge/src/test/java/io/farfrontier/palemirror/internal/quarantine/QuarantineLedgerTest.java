package io.farfrontier.palemirror.internal.quarantine;

import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncidentBundle;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuarantineLedgerTest {
    @Test void terminalQuarantineRetainsItsStampedBundleByExactSubjectAcrossRepeatedObservation() {
        QuarantineLedger ledger = new QuarantineLedger();
        assertTrue(ledger.observe("legacy:source", QuarantineKind.ITEM_STACK, "fingerprint", UUID.fromString("00000000-0000-0000-0000-000000000001"), 11, "first"));
        assertFalse(ledger.observe("legacy:source", QuarantineKind.ITEM_STACK, "fingerprint", UUID.fromString("00000000-0000-0000-0000-000000000001"), 12, "later"));
        var record = ledger.records().getFirst();
        DiagnosticIncidentBundle bundle = ledger.why(record.cause().subject()).orElseThrow();
        assertEquals(record.id(), bundle.incidentId());
        assertEquals(record.cause(), bundle.diagnostic());
        assertEquals(2, bundle.occurrences());
        assertTrue(bundle.awaitingReview());
    }

    @Test void requiredQuarantineFactsFailClosedAtTheDeclaredBoundRatherThanEvictingAnOlderCause() {
        QuarantineLedger ledger = new QuarantineLedger();
        for (int i = 0; i < QuarantineLedger.MAX_RECORDS; i++) {
            assertTrue(ledger.observe("legacy:" + i, QuarantineKind.ITEM_STACK, "fingerprint:" + i, null, i, "required"));
        }
        var retained = ledger.records().getFirst();
        assertThrows(IllegalStateException.class, () -> ledger.observe("legacy:overflow", QuarantineKind.ITEM_STACK, "fingerprint:overflow", null,
                QuarantineLedger.MAX_RECORDS, "required"));
        assertTrue(ledger.incident(retained.id()).isPresent(), "capacity pressure cannot silently erase a terminal invariant cause");
    }
}
