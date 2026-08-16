package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MultiAudienceScenarioTest {
    private static final StoryAudienceId FIRST = new StoryAudienceId("pm:audience:first");
    private static final StoryAudienceId SECOND = new StoryAudienceId("pm:audience:second");
    private static final WorldObjectId COMMUNITY = new WorldObjectId("pale_mirror:shared_community");

    @Test
    void oneGlobalEventCreatesIndependentAudienceScenarios() {
        WorldState state = new WorldState();
        DomainServices services = new DomainServices();
        DomainEvent crisis = new DomainEvent("pm:event:1", DomainEventType.SETTLEMENT_CRISIS_DETECTED,
                COMMUNITY, 0, "test:crisis", "test:crisis");
        ScenarioDefinitionRef definition = definition();

        services.commands().execute(state, new DomainCommand.OfferScenario(crisis, FIRST, definition));
        services.commands().execute(state, new DomainCommand.OfferScenario(crisis, SECOND, definition));

        ScenarioInstance first = scenario(state, FIRST);
        ScenarioInstance second = scenario(state, SECOND);
        assertNotEquals(first.id(), second.id());
        services.commands().execute(state, new DomainCommand.AcceptScenario(first.id()));
        services.commands().execute(state, new DomainCommand.DeclineScenario(second.id()));
        assertEquals(ScenarioStatus.RESPOND, first.status());
        assertEquals(ScenarioStatus.DECLINED, second.status());
    }

    @Test
    void oneCanonicalOutcomeResolvesEveryNonTerminalAudienceScenarioExactlyOnce() {
        WorldState state = crisisState();
        ScenarioInstance first = responding("pm:scenario:first", FIRST);
        ScenarioInstance second = responding("pm:scenario:second", SECOND);
        state.putScenario(first);
        state.putScenario(second);
        DomainServices services = new DomainServices();

        List<DomainEvent> events = services.settlementCrises().reconcile(state);

        assertEquals(ScenarioStatus.RESOLVED, first.status());
        assertEquals(ScenarioStatus.RESOLVED, second.status());
        assertEquals(SettlementCrisisRuntime.PRIMARY_OUTCOME, first.resolutionOutcome());
        assertEquals(SettlementCrisisRuntime.PRIMARY_OUTCOME, second.resolutionOutcome());
        assertEquals(1, events.stream().filter(value -> value.type() == DomainEventType.PRIMARY_SUPPLY_RESTORED).count());
        assertEquals(2, events.stream().filter(value -> value.type() == DomainEventType.SCENARIO_RESOLVED).count());
        assertEquals(List.of(), services.settlementCrises().reconcile(state));
    }

    @Test
    void canonicalOutcomePersistsWithoutAnActivePrivateScenario() {
        WorldState state = crisisState();
        DomainServices services = new DomainServices();

        List<DomainEvent> events = services.settlementCrises().reconcile(state);

        assertEquals(1, events.stream().filter(value -> value.type() == DomainEventType.PRIMARY_SUPPLY_RESTORED).count());
        assertEquals(SettlementCrisisRuntime.PRIMARY_OUTCOME,
                state.livingRegion("shared-region").orElseThrow().incidentOutcome());
        assertEquals(List.of(), services.settlementCrises().reconcile(state));
    }

    private static WorldState crisisState() {
        WorldState state = new WorldState();
        WorldObjectId place = new WorldObjectId("pale_mirror:shared_place");
        WorldObjectId mine = new WorldObjectId("pale_mirror:shared_mine");
        state.putCommunity(new SettlementCommunity(COMMUNITY));
        state.putPlace(new SettlementPlace(place));
        state.putCommunityPlaceBinding(new CommunityPlaceBinding(COMMUNITY, place));
        state.putEconomy(new SettlementEconomy(COMMUNITY,
                Map.of(ResourceKind.IRON, new ResourceAccount(64, 32, 0, 4, 2))));
        state.putFacility(new FacilityState(mine, new InfectionSourceId("pale_mirror:test"), 8, 10, 0));
        state.putLivingRegion(new LivingRegionState("shared-region", COMMUNITY, place, mine,
                new WorldObjectId("pale_mirror:alternate"), new WorldObjectId("pale_mirror:primary_route"),
                new WorldObjectId("pale_mirror:alternate_route"), 0, false, 0, 0));
        state.addEvent(new DomainEvent("pm:event:infected", DomainEventType.MINE_INFECTED,
                mine, 0, "test:infected", "test:infected"));
        return state;
    }

    private static ScenarioInstance responding(String id, StoryAudienceId audience) {
        return new ScenarioInstance(id, "pm:event:crisis", COMMUNITY, audience,
                "pale_mirror:settlement_supply_crisis", "1", List.of("RESPOND"), List.of(), "", "",
                ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS, ScenarioStatus.RESPOND, null, "");
    }

    private static ScenarioInstance scenario(WorldState state, StoryAudienceId audience) {
        return state.scenarios().stream().filter(value -> value.audience().equals(audience)).findFirst().orElseThrow();
    }

    private static ScenarioDefinitionRef definition() {
        return new ScenarioDefinitionRef("pale_mirror:settlement_supply_crisis", "1",
                List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED"), List.of(), 0, "", "",
                ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS);
    }
}
