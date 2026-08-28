package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSitePreparationProcessTest {
    @Test
    void initialWorldSchedulesEveryFieldAsOneDurablePreparationIntent() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:resource-site-schedule"), 77L));
        for (long tick = 100L; tick <= 4_100L; tick += 100L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));

        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
        assertEquals(12, state.physicalIntents().values().stream().filter(intent -> intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.RESOURCE_SITE_PREPARATION).count());
        assertTrue(state.resourceSites().sites().values().stream().allMatch(site -> site.phase() == ResourceSitePhase.UNPREPARED && site.activeWork().isPresent()));
    }

    @Test
    void confirmedPreparationMakesOneGrowingFieldAndPersistsItsFirstColdStage() {
        Prepared prepared = prepared();
        FrontierWorldState running = prepared.state().transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ResourceSitePreparationObservation receipt = receipt(prepared.site(), prepared.intent());
        PhysicalIntentTransition confirmed = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteProcess.planPreparationTransition(running, prepared.intent(), confirmed, 8_000L);

        assertEquals(2, planned.size());
        assertTrue(planned.get(1).payload() instanceof ScheduleEffect.Created);
        assertEquals(8_000L + ResourceSiteProcess.WHEAT_STAGE_INTERVAL,
                ((ScheduleEffect.Created) planned.get(1).payload()).action().dueAt().ticks());
        FrontierWorldState confirmedState = running.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(ResourceSitePhase.GROWING, confirmedState.resourceSites().site(prepared.site()).phase());
        assertEquals(1L, confirmedState.resourceSites().site(prepared.site()).growthEpoch());
        assertEquals(receipt, confirmedState.physicalObservations().get(receipt.id()));
        assertEquals(confirmedState, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(confirmedState)));
        assertEquals(confirmed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(confirmed.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(confirmed)));
    }

    @Test
    void foreignReceiptCannotAdvanceAFieldAndUnknownPreparationBecomesVisibleConflict() {
        Prepared prepared = prepared();
        FrontierWorldState running = prepared.state().transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ResourceSitePreparationObservation foreign = new ResourceSitePreparationObservation(new PhysicalObservationId("observation:site-prepare-foreign"),
                prepared.intent().id(), new SubjectId("site:2-wheat-field"), 64, 64);

        assertThrows(IllegalArgumentException.class, () -> running.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(foreign)));
        FrontierWorldState conflicted = running.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        assertEquals(ResourceSitePhase.CONFLICT, conflicted.resourceSites().site(prepared.site()).phase());
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, conflicted.physicalIntents().get(prepared.intent().id()).status());
        assertEquals(conflicted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(conflicted)));
        assertThrows(IllegalArgumentException.class, () -> prepared.state().withResourceSites(prepared.state().resourceSites()
                .replace(prepared.state().resourceSites().site(prepared.site()).prepared())));
    }

    @Test
    void destroyedFarmRetiresItsPendingPreparationWithoutReclassifyingTheFieldAsConflict() {
        Prepared prepared = prepared();
        FrontierWorldState destroyed = prepared.state().withStructureCondition(new SubjectId("structure:1-farm"), StructureCondition.DESTROYED);
        PhysicalIntentTransition unknown = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());

        assertEquals(ResourceSitePhase.DESTROYED, destroyed.resourceSites().site(prepared.site()).phase());
        assertEquals(1, ResourceSiteProcess.planPreparationTransition(destroyed, prepared.intent(), unknown, 9_000L).size());
        FrontierWorldState resolved = destroyed.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        assertEquals(ResourceSitePhase.DESTROYED, resolved.resourceSites().site(prepared.site()).phase());
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, resolved.physicalIntents().get(prepared.intent().id()).status());
    }

    private static Prepared prepared() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-preparation"), 77L));
        SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) planned.getFirst().payload());
        PhysicalIntent intent = ((PhysicalIntentPrepared) planned.get(1).payload()).intent();
        return new Prepared(ResourceSiteProcess.reducePrepared(state, site, intent), site, intent);
    }

    private static ResourceSitePreparationObservation receipt(SubjectId site, PhysicalIntent intent) {
        return new ResourceSitePreparationObservation(new PhysicalObservationId("observation:site-prepare-1"), intent.id(), site, 64, 64);
    }

    private record Prepared(FrontierWorldState state, SubjectId site, PhysicalIntent intent) { }
}
