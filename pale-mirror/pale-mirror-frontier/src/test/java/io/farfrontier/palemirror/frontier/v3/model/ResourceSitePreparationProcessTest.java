package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSitePreparationProcessTest {
    @Test
    void freshWorldCanonicallyPreparesEveryFieldBeforeTheFirstNaturalChunkVisit() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:resource-site-schedule"), 77L);
        assertEquals(12, configuration.initialSchedules().stream()
                .filter(action -> action.kind().equals(ResourceSiteProcess.PREPARATION_ACTION))
                .count());
        assertTrue(configuration.initialSchedules().stream()
                .filter(action -> action.kind().equals(ResourceSiteProcess.PREPARATION_ACTION))
                .allMatch(action -> action.dueAt().ticks() == configuration.initialState().bootstrap().ruleset().cadence().resourceInitialPreparationTick()));

        var engine = FrontierEngines.create(configuration);
        engine.advanceTo(new SimInstant(100L), new WorkBudget(4_096, 32_768));

        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
        assertEquals(0, state.physicalIntents().values().stream().filter(intent -> intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_PREPARATION).count());
        assertTrue(state.resourceSites().sites().values().stream().allMatch(site -> site.phase() == ResourceSitePhase.GROWING && site.growthStage() == 0));
        assertEquals(12, engine.checkpoint().schedules().stream().filter(action -> action.kind().equals("frontier.resource_site.growth")).count());
    }

    /** FREE is a preference, not a ban on starting useful ordinary work. */
    @Test
    void readyFieldCanAdmitItsFarmerInFreeWithoutCompetingFood() {
        FrontierWorldState state = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial(47L));
        var farmer = FrontierWorldStateSupport.availableFieldResident(state, new SubjectId("settlement:1"),
                HumanCapability.AGRICULTURE).orElseThrow();
        assertEquals(ResourceSitePhase.READY, state.resourceSites().site(new SubjectId("site:1-wheat-field")).phase());
        assertEquals(SettlementDailySchedule.Window.FREE,
                state.humanPopulation().schedule(new SubjectId("settlement:1")).windowAt(21_140L));
        assertTrue(ResidentActivityCoordinator.mayStartOrdinaryWork(state, farmer.id(), 21_140L));
    }

    @Test
    void canonicalPreparationPayloadRoundTripsAndCannotCompleteTheWrongJob() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-command"), 77L));
        SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteProcess.planPreparation(initial, ResourceSiteProcess.preparation(site, 4_000L));
        ResourceSitePrepared prepared = (ResourceSitePrepared) planned.get(1).payload();
        assertEquals(prepared, FrontierWorldRuntimeDefinition.payloadCodecs().decode(prepared.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(prepared)));
        FrontierWorldState preparing = ResourceSiteProcess.reducePreparationStarted(initial, site, (ResourceSitePreparationStarted) planned.getFirst().payload());
        assertEquals(ResourceSitePhase.GROWING, ResourceSiteProcess.reducePrepared(preparing, site, prepared).resourceSites().site(site).phase());
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reducePrepared(preparing, site,
                new ResourceSitePrepared(new ResourceSitePreparationJob(new SubjectId("job:site-prepare-2-wheat-field"), new SubjectId("site:2-wheat-field"),
                        new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-prepare-2-wheat-field")))));
    }

    @Test
    void canonicalPreparationSchedulesOneFirstColdStage() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-prepared"), 77L));
        SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 8_000L));
        assertEquals(3, planned.size()); assertTrue(planned.get(2).payload() instanceof ScheduleEffect.Created);
        assertEquals(8_000L + state.bootstrap().ruleset().cadence().resourceGrowthStageInterval(),
                ((ScheduleEffect.Created) planned.get(2).payload()).action().dueAt().ticks());
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) planned.getFirst().payload());
        FrontierWorldState prepared = ResourceSiteProcess.reducePrepared(state, site, (ResourceSitePrepared) planned.get(1).payload());
        assertEquals(ResourceSitePhase.GROWING, prepared.resourceSites().site(site).phase());
        assertEquals(prepared, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(prepared)));
    }

    @Test
    void oneObservedOwnedCropLossIsDurablySiteSpecificAndCannotNameForeignGeometry() {
        FrontierWorldState growing = prepared(); BlockPosition crop = FrontierResourceSitePlan.compile(growing.bootstrap()).get(new SubjectId("site:1-wheat-field")).cropSlots().getFirst();
        ResourceSiteConflictObserved loss = new ResourceSiteConflictObserved(new SubjectId("site:1-wheat-field"), crop, ResourceSiteDiagnosticProducer.PLAYER_REMOVED);

        assertEquals(ResourceSitePhase.CONFLICT, ResourceSiteProcess.reduceConflict(growing, loss.siteId(), loss).resourceSites().site(loss.siteId()).phase());
        assertEquals(loss, FrontierWorldRuntimeDefinition.payloadCodecs().decode(loss.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(loss)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteProcess.reduceConflict(growing, loss.siteId(),
                new ResourceSiteConflictObserved(loss.siteId(), new BlockPosition(crop.x() - 1, crop.y(), crop.z()), ResourceSiteDiagnosticProducer.PLAYER_REMOVED)));
    }

    @Test
    void firstAcceptedConflictRetainsItsImmutableIncidentAcrossSnapshotAndEquivalentRepeats() {
        FrontierWorldState growing = prepared(); SubjectId siteId = new SubjectId("site:1-wheat-field");
        BlockPosition crop = FrontierResourceSitePlan.compile(growing.bootstrap()).get(siteId).cropSlots().getFirst();
        ResourceSiteConflictObserved first = new ResourceSiteConflictObserved(siteId, crop, ResourceSiteDiagnosticProducer.PLAYER_REMOVED);
        FrontierWorldState accepted = ResourceSiteProcess.reduceConflict(growing, siteId, first);
        ConflictIncident incident = accepted.resourceSites().site(siteId).conflictDisposition().orElseThrow().incident();

        assertEquals("incident:resource-site:1-wheat-field", incident.id());
        assertEquals(DiagnosticCategory.DOMAIN_DISRUPTION, incident.category());
        assertEquals(siteId, incident.ownerId()); assertEquals(siteId, incident.subjectId());
        assertEquals("conflict:incident:resource-site:1-wheat-field", incident.traceCorrelation());
        assertTrue(incident.expectedFact().contains("phase=GROWING"));
        assertTrue(incident.observedFact().contains("PLAYER_REMOVED_MANAGED_CELL"));
        assertEquals(accepted, ResourceSiteProcess.reduceConflict(accepted, siteId,
                new ResourceSiteConflictObserved(siteId, crop, ResourceSiteDiagnosticProducer.EXPLOSION_DAMAGE)), "an equivalent later observation cannot overwrite the first source");
        FrontierWorldState reloaded = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(accepted));
        assertEquals(incident, reloaded.resourceSites().site(siteId).conflictDisposition().orElseThrow().incident(),
                "snapshot/WAL state retains the canonical incident-to-trace link");
        assertEquals(first, FrontierWorldRuntimeDefinition.payloadCodecs().decode(first.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(first)));
    }

    private static FrontierWorldState prepared() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-preparation"), 77L));
        SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) planned.getFirst().payload());
        return ResourceSiteProcess.reducePrepared(state, site, (ResourceSitePrepared) planned.get(1).payload());
    }
}
