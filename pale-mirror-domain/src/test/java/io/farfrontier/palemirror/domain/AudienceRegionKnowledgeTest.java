package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AudienceRegionKnowledgeTest {
    private static final StoryAudienceId AUDIENCE = new StoryAudienceId("pale_mirror:test_audience");
    private static final StoryAudienceId OTHER = new StoryAudienceId("pale_mirror:other_audience");

    @Test
    void introductoryIncidentWaitsForAudienceSupplyChainKnowledge() {
        WorldState state = state();
        DomainCommandProcessor commands = new DomainServices().commands();
        LivingRegionState region = state.livingRegion("test-region").orElseThrow();

        commands.execute(state, new DomainCommand.DiscoverLivingRegion(region.id(), AUDIENCE, "test:settlement"));
        state.setSimulationStep(100);
        assertFalse(region.incidentDue(state.simulationStep(),
                state.regionKnowledge(AUDIENCE, region.id()).orElseThrow()));

        commands.execute(state, new DomainCommand.DiscoverRegionalFeature(region.id(), AUDIENCE,
                KnownRegionalFeature.DEPOT, "test:depot"));
        commands.execute(state, new DomainCommand.DiscoverRegionalFeature(region.id(), AUDIENCE,
                KnownRegionalFeature.PRIMARY_ROUTE, "test:route"));
        AudienceRegionKnowledge knowledge = state.regionKnowledge(AUDIENCE, region.id()).orElseThrow();
        assertFalse(region.incidentDue(104, knowledge));
        assertTrue(region.incidentDue(105, knowledge));
    }

    @Test
    void anotherAudienceCannotRevealAClaimedRegion() {
        WorldState state = state();
        DomainCommandProcessor commands = new DomainServices().commands();
        LivingRegionState region = state.livingRegion("test-region").orElseThrow();
        commands.execute(state, new DomainCommand.DiscoverLivingRegion(region.id(), AUDIENCE, "test:settlement"));

        assertTrue(commands.execute(state, new DomainCommand.DiscoverRegionalFeature(region.id(), OTHER,
                KnownRegionalFeature.DEPOT, "test:foreign")).isEmpty());
        assertTrue(state.regionKnowledge(OTHER, region.id()).isEmpty());
    }

    private static WorldState state() {
        WorldState state = new WorldState();
        WorldObjectId place = new WorldObjectId("pale_mirror:test_place");
        state.putPlace(new SettlementPlace(place));
        state.putLivingRegion(new LivingRegionState("test-region",
                new WorldObjectId("pale_mirror:test_community"), place,
                new WorldObjectId("pale_mirror:test_mine"), new WorldObjectId("pale_mirror:test_alternate"),
                new WorldObjectId("pale_mirror:test_route"), new WorldObjectId("pale_mirror:test_alt_route"),
                5, null, RecognitionState.DISCOVERED, -1, true));
        return state;
    }
}
