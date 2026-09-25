package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
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
    @Test void distinctProductionJobsAtOneFacilityRetainIndependentIncidentsAcrossSnapshot() {
        var settlement = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("settlement:7");
        var facility = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("structure:7-workshop");
        var first = ProductionDiagnosticProducer.ROUTE_BLOCKED.create(settlement, facility,
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("job:production-7-1"), new io.farfrontier.palemirror.frontier.v3.api.SubjectId("task:production-7-1")).diagnostic();
        var second = ProductionDiagnosticProducer.ROUTE_BLOCKED.create(settlement, facility,
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("job:production-7-2"), new io.farfrontier.palemirror.frontier.v3.api.SubjectId("task:production-7-2")).diagnostic();
        assertNotEquals(DiagnosticIncident.idFor(first), DiagnosticIncident.idFor(second));
        var index = DiagnosticIncidentIndex.empty().retain(first, "event:1", "cause:1", 1, 10)
                .retain(second, "event:2", "cause:2", 2, 20);
        assertEquals(2, index.incidents().size());
        assertEquals(second, index.why(first.subject()).orElseThrow().diagnostic());
        var initial = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:multi-incident"), 93L).initialState();
        var codec = new FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(initial.withDiagnosticIncidents(index))).diagnosticIncidents();
        assertEquals(index, recovered);
        var repeated = recovered.retain(first, "event:3", "cause:3", 3, 30);
        assertEquals(first, repeated.why(first.subject()).orElseThrow().diagnostic());
        assertEquals(2, repeated.incident(DiagnosticIncident.idFor(first)).orElseThrow().occurrences());
        assertEquals("event:1", repeated.incident(DiagnosticIncident.idFor(first)).orElseThrow().firstEventId());
        assertEquals(1, repeated.incident(DiagnosticIncident.idFor(second)).orElseThrow().occurrences());
        assertThrows(IllegalArgumentException.class, () -> recovered.retain(DiagnosticIncident.idFor(first),
                second, "event:forged", "cause:forged", 3, 30));
    }

    @Test void multipleTerminalOwnersAtOneSubjectRemainIndividuallyAddressable() {
        var first = tuple(7);
        var second = new DiagnosticTuple(first.reason(), first.category(),
                new DiagnosticOwner(DiagnosticOwnerKind.RESOURCE_SITE,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId("site:diagnostic-other")),
                first.subject(), first.disposition());
        var index = DiagnosticIncidentIndex.empty().retain(first, "event:1", "cause:1", 1, 1)
                .retain(second, "event:2", "cause:2", 2, 2);
        assertEquals(2, index.incidents().size());
        assertTrue(index.incidents().values().stream().allMatch(DiagnosticIncident::awaitingReview));
        assertEquals(second, index.why(first.subject()).orElseThrow().diagnostic());
        assertTrue(index.bundle(DiagnosticIncident.idFor(first)).isPresent());
    }

    @Test void currentProducerInventoryHasNoUnretainedReason() {
        EnumSet<DiagnosticReason> expected = EnumSet.allOf(DiagnosticReason.class);
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
        assertTrue(bundle.context().physical().startsWith("expected=canonical_projection:"), "bounded physical context records the exact pre-event projection");
        assertTrue(bundle.context().physical().contains("observed_event=frontier.resource_site_conflict_observed"));
        assertTrue(bundle.context().claim().contains(payload.diagnostic().owner().id().value()));
        assertTrue(bundle.context().reconciliation().contains(payload.diagnostic().disposition().name()));
        assertEquals(bundle.context(), restarted.diagnosticIncidents().bundle(id).orElseThrow().context());
    }

    @Test void owningRuntimeCanSupplyCompleteArtifactAndRestartIdentityWithoutReducerInference() {
        WorldId world = new WorldId("frontier:diagnostic-runtime-identity");
        var configuration = FrontierWorldRuntimeDefinition.configuration(world, 94L);
        FrontierWorldState initial = configuration.initialState();
        var site = initial.resourceSites().sites().keySet().stream().sorted().findFirst().orElseThrow();
        var position = FrontierResourceSitePlan.compile(initial.bootstrap()).get(site).cropSlots().getFirst();
        var payload = new ResourceSiteConflictObserved(site, position, ResourceSiteDiagnosticProducer.ORDINARY_OBSERVATION_MISMATCH);
        FrontierWorldState reduced;
        try (DiagnosticCaptureScope ignored = DiagnosticCaptureScope.open(new DiagnosticRuntimeIdentity("neoforge", "tree:fixture", "jar:fixture", "restart:fresh"))) {
            reduced = configuration.reducer().apply(initial, event(world, site, payload, 1, 7));
        }
        DiagnosticIncidentContext context = reduced.diagnosticIncidents().why(payload.diagnostic().subject()).orElseThrow().context();
        assertTrue(context.complete());
        assertEquals("tree:fixture", context.sourceTree()); assertEquals("jar:fixture", context.jar());
        assertEquals("restart:fresh", context.restartIdentity());
    }

    @Test void ordinaryRoutePatrolLossRetainsItsProducerStampedTerminalIncidentAcrossRestart() {
        WorldId world = new WorldId("frontier:diagnostic-patrol-loss");
        var fixture = FrontierDevelopmentScenarios.routePatrolFixture(world, 713L);
        FrontierWorldState initial = fixture.state();
        RoutePatrol patrol = initial.strategicPlans().routePatrols().get(fixture.taskId());
        var locations = new java.util.LinkedHashMap<>(initial.actorLocations());
        var lost = patrol.memberIds().getFirst();
        locations.put(lost, new ActorLocation(locations.get(lost).body(), ActorCondition.dead()));
        FrontierWorldState withLoss = initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations));
        RoutePatrolFailed payload = RoutePatrolFailureDiagnosticProducer.memberLost(patrol.taskId());

        FrontierWorldState reduced = FrontierWorldRuntimeDefinition.configuration(world, 713L).reducer().apply(withLoss,
                event(world, patrol.settlementId(), payload, 1, 7));
        DiagnosticIncident incident = reduced.diagnosticIncidents().why(payload.diagnostic().subject()).orElseThrow();
        assertEquals(payload.diagnostic(), incident.diagnostic());
        assertEquals(RoutePatrolStatus.FAILED, reduced.strategicPlans().routePatrols().get(patrol.taskId()).status());
        FrontierWorldState restarted = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reduced));
        assertEquals(incident, restarted.diagnosticIncidents().incident(incident.id()).orElseThrow());
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
                                       FrontierPayload payload, long revision, long instant) {
        return new FrontierEvent(1, new EventId("event:diagnostic-" + revision), new TransactionId("transaction:diagnostic-" + revision),
                world, new Revision(revision), new SimInstant(instant), site, CauseChain.root(new CommandId("command:diagnostic")), payload);
    }
    private static DiagnosticTuple tuple(int ordinal) {
        var id = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("site:diagnostic-" + ordinal);
        return new DiagnosticTuple(DiagnosticReason.RESOURCE_SITE_RECOVERY_UNRESOLVED, DiagnosticCategory.RECOVERY_UNKNOWN,
                new DiagnosticOwner(DiagnosticOwnerKind.RESOURCE_SITE, id), new DiagnosticSubject(DiagnosticSubjectKind.PHYSICAL_EFFECT, id), DiagnosticDisposition.INSPECT);
    }
}
