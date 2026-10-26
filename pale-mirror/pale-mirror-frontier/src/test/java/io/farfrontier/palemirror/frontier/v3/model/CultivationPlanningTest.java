package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CultivationPlanningTest {
    @Test void unchangedLandReusesItsSurveyButChangedGroundAndAdmissionStateRemainFenced() {
        var state = prepared(); var home = new SubjectId("settlement:1");
        var fields = state.resourceSiteDescriptors().values().stream().filter(f -> f.settlementId().equals(home)).toList();
        var first = FieldDevelopmentLand.survey(state, home, fields, 3);
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var moved = state.withActorBody(actor, state.actorLocations().get(actor).body().offset(1, 0, 0));
        var again = FieldDevelopmentLand.survey(moved, home, fields, 3);
        assertSame(first.cells().getFirst(), again.cells().getFirst(), "unit motion does not require another land flood");
        assertThrows(IllegalArgumentException.class, () -> first.require(moved, home, fields),
                "reused immutable candidate facts cannot admit an old state's plan");
        var cell = first.cells().getFirst();
        var changed = moved.withChanges(FrontierWorldStateUpdate.begin().extractionSites(
                moved.extractionSites().invalidateGeology(cell.support().support())));
        assertFalse(FieldDevelopmentLand.survey(changed, home, fields, 3).cells().contains(cell),
                "actual revoked ground must be re-evaluated before land can be developed");
    }

    @Test void separatedSoilPatchesShareOneBoundedReachableWorkAreaAndRejectStaleSurveys() {
        var state = prepared(); var home = new SubjectId("settlement:1");
        for (int turn = 0; turn < 3; turn++) {
            var fields = state.resourceSiteDescriptors().values().stream().filter(f -> f.settlementId().equals(home))
                    .sorted(Comparator.comparing(ResourceSite::id)).toList();
            var survey = FieldDevelopmentLand.survey(state, home, fields, 64);
            assertEquals(64, survey.cells().size());
            var before = state;
            assertThrows(IllegalArgumentException.class, () -> FieldDevelopmentLand.survey(before,
                    new SubjectId("settlement:2"), fields, 64));
            var result = FieldLandAdmission.admit(state, state.resourceSites(), home, fields, survey,
                    state.bootstrap().ruleset().fields().cultivation().orElseThrow());
            assertFalse(result.deferred());
            state = state.withResourceSites(result.sites());
            var next = state;
            assertThrows(IllegalArgumentException.class, () -> FieldLandAdmission.admit(next, next.resourceSites(), home,
                    fields, survey, next.bootstrap().ruleset().fields().cultivation().orElseThrow()));
        }
        var fields = state.resourceSiteDescriptors().values().stream().filter(f -> f.settlementId().equals(home)).toList();
        assertEquals(1, fields.size(), "soil islands are not separate worksite declarations");
        assertEquals(256, fields.getFirst().layout().cells().size());
    }

    @Test void miningSettlementCanAdmitRealCoarseGroundWithoutPretendingItIsAlreadyTilled() {
        var state = prepared(); var home = new SubjectId("settlement:1");
        var fields = state.resourceSiteDescriptors().values().stream().filter(f -> f.settlementId().equals(home)).toList();
        var survey = FieldDevelopmentLand.survey(state, home, fields, 3);
        var land = survey.cells();
        assertFalse(land.isEmpty(), "rocky territory has native-preparable soil");
        assertTrue(land.stream().allMatch(cell -> cell.soil().block().kind().equals("minecraft:coarse_dirt")));
        var admitted = FieldLandAdmission.admit(state, state.resourceSites(), home, fields, survey,
                state.bootstrap().ruleset().fields().cultivation().orElseThrow());
        long coarse = admitted.sites().cycles().values().stream().flatMap(c -> c.layout().cells().stream()
                .map(cell -> c.cell(cell.id()))).filter(cell -> cell.soil() == ResourceFieldCycle.Soil.fromBlock("minecraft:coarse_dirt")).count();
        assertEquals(land.size(), coarse);
        var decoded = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state.withResourceSites(admitted.sites())));
        assertEquals(admitted.sites().declarations(), decoded.resourceSites().declarations());
    }

    @Test void registeredReviewExecutesInTheRealEngineAndRetainsItsContinuation() {
        var configuration = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:cultivation-engine"), 125L, FrontierRulesets.dynamicFarmingGraybox());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        for (int turn = 0; engine.canonicalState().instant().ticks() < 6000; turn++) {
            assertTrue(turn < 10000, "bounded engine integration must advance");
            var now = engine.canonicalState().instant();
            var next = engine.nextScheduledInstantAfter(now).orElse(new SimInstant(6000));
            var result = engine.advanceTo(new SimInstant(Math.min(6000, next.ticks())), new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().toString());
            engine.compact(result.revision());
        }
        assertFalse(engine.canonicalState().state().resourceSites().cultivation().isEmpty());
        assertEquals(12, engine.checkpoint().schedules().stream().filter(a -> a.kind().equals("frontier.cultivation.review")).count());
        FrontierReadabilityPlan.compile(engine.canonicalState().state(), 0L);
    }
    @Test void economicReviewAdmitsBoundedLandWithoutGrowingPlantsOrAwardingStockAndSurvivesRecovery() {
        var state = prepared(); var home = new SubjectId("settlement:7");
        state = AcceptedEconomyFixture.publish(state, home, 5000);
        var initialSites = state.resourceSites();
        var beforeInventory = state.inventory();
        var plan = CultivationPlanning.read(state, home, 5000);
        assertNotSame(initialSites, plan.sites());
        var review = plan.sites().cultivation().get(home);
        assertNotNull(review);
        var next = state.withResourceSites(plan.sites());
        assertSame(beforeInventory, next.inventory());
        int added = 0;
        for (var site : next.resourceSiteDescriptors().values()) {
            var old = initialSites.cycles().get(site.id());
            var cycle = next.resourceSites().cycle(site.id());
            for (var cell : cycle.layout().cells()) {
                if (old != null && old.layout().cells().contains(cell)) {
                    assertEquals(old.cell(cell.id()), cycle.cell(cell.id()), "planning never destroys or ages existing plants");
                } else {
                    added++; assertEquals(ResourceFieldCycle.Crop.ABSENT, cycle.cell(cell.id()).crop());
                    assertTrue(cycle.cell(cell.id()).soil().cultivable());
                }
            }
        }
        assertTrue(added <= 64);
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(next));
        assertEquals(review, recovered.resourceSites().cultivation().get(home));
        assertEquals(next.resourceSites().declarations(), recovered.resourceSites().declarations());
        for (var field : next.resourceSiteDescriptors().values())
            assertEquals(next.resourceSites().cycle(field.id()).sowing(), recovered.resourceSites().cycle(field.id()).sowing());
    }

    @Test void runtimeLandAdmissionExtendsOnlyRealSurveyedTerrainAndRetainsExistingCellIdentities() {
        var state = prepared(); var home = new SubjectId("settlement:7");
        var fields = state.resourceSiteDescriptors().values().stream().filter(f -> f.settlementId().equals(home)).toList();
        var survey = FieldDevelopmentLand.survey(state, home, fields, 3);
        var land = survey.cells();
        assertFalse(land.isEmpty(), "graybox must expose real cultivable adjacent terrain");
        var outcome = FieldLandAdmission.admit(state, state.resourceSites(), home, fields, survey,
                state.bootstrap().ruleset().fields().cultivation().orElseThrow());
        assertTrue(outcome.sites().cycles().values().stream().mapToInt(c -> c.layout().cells().size()).sum()
                > state.resourceSites().cycles().values().stream().mapToInt(c -> c.layout().cells().size()).sum());
        var next = state.withResourceSites(outcome.sites());
        new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(next));
    }

    static FrontierWorldState prepared() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:cultivation"), 125L,
                FrontierRulesets.dynamicFarmingGraybox()));
        for (var id : state.resourceSites().sites().keySet().stream().sorted().toList()) {
            var events = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(id, 4000));
            state = ResourceSiteProcess.reducePreparationStarted(state, id, (ResourceSitePreparationStarted) events.getFirst().payload());
            state = ResourceSiteProcess.reducePrepared(state, id, (ResourceSitePrepared) events.get(1).payload());
        }
        return state;
    }
}
