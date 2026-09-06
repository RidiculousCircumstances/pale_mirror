package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class NarratorCandidateTest {
    private static final StoryAudienceId AUDIENCE = new StoryAudienceId("pm:audience:test");

    @Test
    void selectsTheMostRelevantRegionalCandidateInStableOrder() {
        WorldState state = new WorldState();
        state.setSimulationStep(10);
        DomainEvent distant = event("pm:event:1", new WorldObjectId("pale_mirror:distant"));
        DomainEvent urgent = event("pm:event:2", new WorldObjectId("pale_mirror:urgent"));
        state.addEvent(distant);
        state.addEvent(urgent);
        DomainServices services = new DomainServices();

        List<DomainEvent> offered = services.narrator().offerBest(state, AUDIENCE, List.of(
                candidate(distant, 70, 60, 70, 75),
                candidate(urgent, 96, 95, 100, 90)));

        assertEquals(1, offered.size());
        ScenarioInstance scenario = state.scenarios().stream().findFirst().orElseThrow();
        assertEquals(urgent.subject(), scenario.target());
        assertEquals(ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS, scenario.archetype());
    }

    @Test
    void recordsNoScenarioRatherThanLeavingLowRelevanceFactUndecided() {
        WorldState state = new WorldState();
        state.setSimulationStep(10);
        DomainEvent minor = event("pm:event:minor", new WorldObjectId("pale_mirror:minor"));
        state.addEvent(minor);

        new DomainServices().narrator().offerBest(state, AUDIENCE, List.of(
                new NarrativeCandidate(minor, AUDIENCE, NarrativeCandidateType.RECOVERY_OPPORTUNITY, definition(),
                        10, 10, 10, 10, 10, 90)));

        assertTrue(state.history().stream().anyMatch(value -> value.type() == DomainEventType.NO_SCENARIO
                && minor.eventId().equals(value.correlationId())));
    }

    @Test
    void aCandidateCanBeRetriedAfterCooldownWithoutChangingItsSourceFact() {
        WorldState state = new WorldState();
        state.setSimulationStep(10);
        DomainEvent development = event("pm:event:later", new WorldObjectId("pale_mirror:community"));
        state.addEvent(development);
        state.setNarratorCooldown(AUDIENCE, 12);
        Narrator narrator = new DomainServices().narrator();

        assertTrue(narrator.offerBest(state, AUDIENCE, List.of(candidate(development, 80, 80, 80, 90))).isEmpty());
        state.setSimulationStep(12);
        assertEquals(1, narrator.offerBest(state, AUDIENCE, List.of(candidate(development, 80, 80, 80, 90))).size());
        assertEquals(1, state.scenarios().size(), "retrying a pending candidate must offer exactly one scenario");
    }

    @Test
    void acceptedDevelopmentOpportunityResolvesOnlyAfterVerifiedResult() {
        WorldState state = new WorldState();
        WorldObjectId community = new WorldObjectId("pale_mirror:community");
        DevelopmentIntent intent = new DevelopmentIntent("pm:intent:development", community,
                DevelopmentIntentType.UPGRADE_STOREHOUSE, null, null, 0, "test",
                DevelopmentIntentState.PLANNED, "");
        state.putDevelopmentIntent(intent);
        DomainEvent source = new DomainEvent("pm:event:development",
                DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED, community, 10, intent.id(), intent.id());
        state.addEvent(source);
        ScenarioInstance scenario = new ScenarioInstance("pm:scenario:development", source.eventId(), community, AUDIENCE,
                "pale_mirror:development_opportunity", "1", List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED"),
                List.of(), "", "", ScenarioArchetype.DEVELOPMENT_OPPORTUNITY, ScenarioStatus.OFFERED, null, "");
        state.putScenario(scenario);
        DomainServices services = new DomainServices();

        services.commands().execute(state, new DomainCommand.AcceptScenario(scenario.id()));
        assertEquals(ScenarioStatus.RESPOND, scenario.status());
        services.scenarios().reconcileOpportunity(state, community, ScenarioArchetype.DEVELOPMENT_OPPORTUNITY,
                "STOREHOUSE_UPGRADED");
        assertEquals(ScenarioStatus.RESOLVED, scenario.status());
        assertEquals("STOREHOUSE_UPGRADED", scenario.resolutionOutcome());
    }

    @Test
    void staleDevelopmentOpportunityCannotEnterResponseState() {
        WorldState state = new WorldState();
        WorldObjectId community = new WorldObjectId("pale_mirror:community");
        DevelopmentIntent intent = new DevelopmentIntent("pm:intent:development", community,
                DevelopmentIntentType.UPGRADE_STOREHOUSE, null, null, 0, "test",
                DevelopmentIntentState.PLANNED, "");
        state.putDevelopmentIntent(intent);
        intent.block("materialization failed");
        DomainEvent source = new DomainEvent("pm:event:development",
                DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED, community, 10, intent.id(), intent.id());
        state.addEvent(source);
        ScenarioInstance scenario = new ScenarioInstance("pm:scenario:development", source.eventId(), community, AUDIENCE,
                "pale_mirror:development_opportunity", "1", List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED"),
                List.of(), "", "", ScenarioArchetype.DEVELOPMENT_OPPORTUNITY, ScenarioStatus.OFFERED, null, "");
        state.putScenario(scenario);

        new DomainServices().commands().execute(state, new DomainCommand.AcceptScenario(scenario.id()));

        assertEquals(ScenarioStatus.BLOCKED, scenario.status());
        assertTrue(state.history().stream().anyMatch(value -> value.type() == DomainEventType.SCENARIO_BLOCKED));
    }

    @Test
    void ordinaryFreshWorldProsperityIsNotARecoveryOpportunity() {
        WorldState state = new WorldState();
        WorldObjectId community = new WorldObjectId("pale_mirror:community");
        DomainEvent planned = new DomainEvent("pm:event:1",
                DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED, community, 8,
                "pm:intent:development", "pm:intent:development");
        state.addEvent(planned);

        assertTrue(!DevelopmentOpportunityEligibility.isRecoveryOpportunity(state, planned));
    }

    @Test
    void stabilizedCrisisMakesALaterDevelopmentPlanARecoveryOpportunity() {
        WorldState state = new WorldState();
        WorldObjectId community = new WorldObjectId("pale_mirror:community");
        state.addEvent(new DomainEvent("pm:event:1", DomainEventType.SETTLEMENT_CRISIS_DETECTED,
                community, 8, "test", "test"));
        state.addEvent(new DomainEvent("pm:event:2", DomainEventType.SETTLEMENT_STABILIZED,
                community, 12, "test", "test"));
        DomainEvent planned = new DomainEvent("pm:event:3",
                DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED, community, 14,
                "pm:intent:development", "pm:intent:development");
        state.addEvent(planned);

        assertTrue(DevelopmentOpportunityEligibility.isRecoveryOpportunity(state, planned));
    }

    @Test
    void systemCancellationRetiresOnlyTheFalseOfferAndPreservesItsDevelopmentIntent() {
        WorldState state = new WorldState();
        WorldObjectId community = new WorldObjectId("pale_mirror:community");
        DevelopmentIntent intent = new DevelopmentIntent("pm:intent:development", community,
                DevelopmentIntentType.UPGRADE_STOREHOUSE, null, null, 0, "test",
                DevelopmentIntentState.PLANNED, "");
        state.putDevelopmentIntent(intent);
        DomainEvent source = new DomainEvent("pm:event:development",
                DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED, community, 10, intent.id(), intent.id());
        state.addEvent(source);
        ScenarioInstance scenario = new ScenarioInstance("pm:scenario:development", source.eventId(), community,
                AUDIENCE, "pale_mirror:development_opportunity", "1",
                List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED"), List.of(), "", "",
                ScenarioArchetype.DEVELOPMENT_OPPORTUNITY, ScenarioStatus.OFFERED, null, "");
        state.putScenario(scenario);

        List<DomainEvent> events = new DomainServices().commands().execute(state,
                new DomainCommand.CancelScenario(scenario.id(), "not a recovered crisis"));

        assertEquals(ScenarioStatus.CANCELLED, scenario.status());
        assertEquals(DevelopmentIntentState.PLANNED, intent.state());
        assertEquals(List.of(DomainEventType.SCENARIO_CANCELLED), events.stream().map(DomainEvent::type).toList());
    }

    private static NarrativeCandidate candidate(DomainEvent event, int urgency, int significance, int relevance, int novelty) {
        return new NarrativeCandidate(event, AUDIENCE, NarrativeCandidateType.SUPPLY_CRISIS, definition(),
                urgency, significance, relevance, 100, novelty, 5);
    }

    private static ScenarioDefinitionRef definition() {
        return new ScenarioDefinitionRef("pale_mirror:test", "1", List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED"),
                List.of(), 0, "", "", ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS);
    }

    private static DomainEvent event(String id, WorldObjectId subject) {
        return new DomainEvent(id, DomainEventType.SETTLEMENT_CRISIS_DETECTED, subject, 10, "test", "test");
    }
}
