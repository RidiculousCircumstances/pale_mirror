package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mechanical recurrence coverage for the no-inference diagnostic authority boundary. */
class DiagnosticTupleTest {
    private static final SubjectId SITE = new SubjectId("site:diagnostic-tuple");

    @Test
    void everyCurrentReasonAndResourceSiteProducerHasOneStableNonReusedWireTag() {
        assertEquals(DiagnosticReason.values().length, Arrays.stream(DiagnosticReason.values())
                .map(DiagnosticReason::wireTag).collect(Collectors.toSet()).size());
        assertEquals(ResourceSiteDiagnosticProducer.values().length, Arrays.stream(ResourceSiteDiagnosticProducer.values())
                .map(ResourceSiteDiagnosticProducer::wireTag).collect(Collectors.toSet()).size());
        assertTrue(Arrays.stream(DiagnosticReason.values()).allMatch(reason -> reason.wireTag() > 0));
    }

    @Test
    void stampedProducerCarriesEveryAuthoritativeDimensionBeforeAnyIncidentExists() {
        DiagnosticTuple tuple = ResourceSiteDiagnosticProducer.PLAYER_REMOVED.stamp(SITE, SITE);
        assertEquals(DiagnosticReason.RESOURCE_SITE_PLAYER_REMOVED, tuple.reason());
        assertEquals(DiagnosticCategory.DOMAIN_DISRUPTION, tuple.category());
        assertEquals(DiagnosticOwnerKind.RESOURCE_SITE, tuple.owner().kind());
        assertEquals(DiagnosticSubjectKind.RESOURCE_SITE_CELL, tuple.subject().kind());
        assertEquals(DiagnosticDisposition.REPAIR, tuple.disposition());
    }

    @Test
    void missingOrForgedTupleDimensionFailsBeforeAReasonCanDriveVerdictOrPersistence() {
        DiagnosticTuple stamped = ResourceSiteDiagnosticProducer.PLAYER_REMOVED.stamp(SITE, SITE);
        assertThrows(NullPointerException.class, () -> new DiagnosticTuple(null, stamped.category(), stamped.owner(), stamped.subject(), stamped.disposition()));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticTuple(stamped.reason(), DiagnosticCategory.RECOVERY_UNKNOWN,
                stamped.owner(), stamped.subject(), stamped.disposition()));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticTuple(stamped.reason(), stamped.category(),
                new DiagnosticOwner(DiagnosticOwnerKind.PHYSICAL_INTENT, SITE), stamped.subject(), stamped.disposition()));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticTuple(stamped.reason(), stamped.category(), stamped.owner(),
                new DiagnosticSubject(DiagnosticSubjectKind.PHYSICAL_EFFECT, SITE), stamped.disposition()));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticTuple(stamped.reason(), stamped.category(), stamped.owner(),
                stamped.subject(), DiagnosticDisposition.INSPECT));
    }

    @Test
    void unknownProducerAndTupleWireIdentitiesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteDiagnosticProducer.requireWireTag(255));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticWireTags.reason(65_535));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticWireTags.category(255));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticWireTags.ownerKind(255));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticWireTags.subjectKind(255));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticWireTags.disposition(255));
    }
}
