package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumSet;

class DiagnosticIncidentIndexTest {
    @Test void currentProducerInventoryHasNoUnretainedReason() {
        EnumSet<DiagnosticReason> expected = EnumSet.allOf(DiagnosticReason.class);
        expected.remove(DiagnosticReason.FRONTIER_QUARANTINE);
        assertEquals(expected, DiagnosticIncidentExtractor.retainedReasons());
    }
    @Test void reducerAtomicallyRetainsExactOwnerLinkAndSnapshotRoundTripsIt() {
        WorldId world = new WorldId("frontier:diagnostic-index");
        var configuration = FrontierWorldRuntimeDefinition.configuration(world, 93L);
        FrontierWorldState initial = configuration.initialState();
        var site = initial.resourceSites().sites().keySet().stream().sorted().findFirst().orElseThrow();
        var position = FrontierResourceSitePlan.compile(initial.bootstrap()).get(site).cropSlots().getFirst();
        var payload = new ResourceSiteConflictObserved(site, position, ResourceSiteDiagnosticProducer.ORDINARY_OBSERVATION_MISMATCH);
        FrontierWorldState reduced = configuration.reducer().apply(initial, event(world, site, payload, 1, 7));
        String id = reduced.resourceSites().site(site).conflictDisposition().orElseThrow().incident().id();
        DiagnosticIncident incident = reduced.diagnosticIncidents().incident(id).orElseThrow();
        assertEquals(payload.diagnostic(), incident.diagnostic());
        assertEquals("event:diagnostic-1", incident.firstEventId());
        assertEquals("command:diagnostic", incident.firstCauseId());
        assertTrue(incident.awaitingReview());
        FrontierWorldState restarted = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reduced));
        assertEquals(incident, restarted.diagnosticIncidents().incident(id).orElseThrow());
        assertEquals(incident, restarted.diagnosticIncidents().why(payload.diagnostic().subject()).orElseThrow());
        DiagnosticIncidentBundle bundle = restarted.diagnosticIncidents().bundle(id).orElseThrow();
        assertEquals(id, bundle.incidentId());
        assertEquals(payload.diagnostic(), bundle.diagnostic());
        assertEquals("event:diagnostic-1", bundle.eventId());
        assertEquals("command:diagnostic", bundle.causeId());
        assertEquals(world.value(), bundle.context().world());
        assertEquals("frontier-v3", bundle.context().runtime());
        assertFalse(bundle.context().complete());
        assertEquals("runtime_source_tree_jar_restart_identity_unavailable", bundle.context().degradation());
        assertEquals(bundle.context(), restarted.diagnosticIncidents().bundle(id).orElseThrow().context());
    }

    @Test void oneIdentityProducesOneBoundedBundleEvenWhenTheSameFactIsRepeated() {
        DiagnosticTuple tuple = tuple(7);
        DiagnosticIncidentIndex retained = DiagnosticIncidentIndex.empty().retain(tuple, "event:first", "cause:first", 3, 5)
                .retain(tuple, "event:later", "cause:later", 4, 6);
        DiagnosticIncidentBundle bundle = retained.bundle(DiagnosticIncident.idFor(tuple)).orElseThrow();
        assertEquals(1, retained.incidents().size());
        assertEquals("event:first", bundle.eventId(), "bundle identity retains its first event rather than a sampled later event");
        assertEquals("cause:first", bundle.causeId());
        assertEquals(2, bundle.occurrences());
    }

    @Test void terminalAdmissionFailsClosedInsteadOfEvictingAnUnresolvedExplanation() {
        DiagnosticIncidentIndex index = DiagnosticIncidentIndex.empty();
        for (int i = 0; i < DiagnosticIncidentIndex.MAX_INCIDENTS; i++) {
            index = index.retain(tuple(i), "event:" + i, "cause:" + i, i, i);
        }
        DiagnosticIncident retained = index.incidents().values().iterator().next();
        DiagnosticIncidentIndex full = index;
        assertThrows(IllegalStateException.class, () -> full.retain(tuple(9_999), "event:next", "cause:next", 9_999, 9_999));
        assertEquals(retained, full.incident(retained.id()).orElseThrow());
    }

    private static FrontierEvent event(WorldId world, io.farfrontier.palemirror.frontier.v3.api.SubjectId site,
                                       ResourceSiteConflictObserved payload, long revision, long instant) {
        return new FrontierEvent(1, new EventId("event:diagnostic-" + revision), new TransactionId("transaction:diagnostic-" + revision),
                world, new Revision(revision), new SimInstant(instant), site, CauseChain.root(new CommandId("command:diagnostic")), payload);
    }
    private static DiagnosticTuple tuple(int ordinal) {
        var id = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("site:diagnostic-" + ordinal);
        return new DiagnosticTuple(DiagnosticReason.RESOURCE_SITE_RECOVERY_UNRESOLVED, DiagnosticCategory.RECOVERY_UNKNOWN,
                new DiagnosticOwner(DiagnosticOwnerKind.RESOURCE_SITE, id), new DiagnosticSubject(DiagnosticSubjectKind.PHYSICAL_EFFECT, id), DiagnosticDisposition.INSPECT);
    }
}
