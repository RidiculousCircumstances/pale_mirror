package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceGrayboxPlayerBriefingTest {
    @Test
    void turnsASettlementIntoStateCauseRiskAndNextActionWithoutMutatingTheSnapshot() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot.Settlement settlement = snapshot.settlements().getFirst();

        String briefing = SourceGrayboxPlayerBriefing.at(snapshot, settlement.rectangle().centreX(), settlement.rectangle().centreZ()).orElseThrow();

        assertTrue(briefing.startsWith(settlement.name() + " — "), "the player must receive a named place, not an opaque source id");
        assertTrue(briefing.contains("State:") && briefing.contains("Cause:") && briefing.contains("Risk:") && briefing.contains("Next:"),
                "a settlement briefing must answer the player's state, cause, risk and next-action questions");
        assertEquals(snapshot, ReferenceGrayboxSimulation.create(42L).snapshot(),
                "reading a player briefing must not mutate or reinterpret the canonical source frame");
    }

    @Test
    void makesTheExactDeclaredInteractionAndItsConsequenceLegible() {
        ReferenceGrayboxSnapshot before = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot.Interaction interaction = before.interactions().stream()
                .filter(value -> value.kind().equals("route_damaged")).findFirst().orElseThrow();
        String briefing = SourceGrayboxPlayerBriefing.forIdentity(before, "interaction:" + interaction.id(), SourceGrayboxMaterializer.LABEL_KIND).orElseThrow();
        assertTrue(briefing.contains("DAMAGE ROUTE") && briefing.contains("Effect:") && briefing.contains("Action:") && briefing.contains("Result:"),
                "a marked interaction must explain its real effect before the player destroys it");

        ReferenceGrayboxSnapshot after = withReducedRouteCapacity(before, interaction.subjectId(), interaction.totalWeight() - 2.0d);
        String receipt = SourceGrayboxPlayerBriefing.acceptedReceipt(before, after, interaction);
        assertTrue(receipt.contains("World changed:") && receipt.contains("lost 2.00 capacity") && receipt.contains("endpoints may now lose supplies"),
                "an accepted interaction must produce a causal receipt rather than silently changing a technical number");
    }

    @Test
    void ignoresEmptyGroundInsteadOfInventingAStory() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(42L).snapshot();
        assertTrue(SourceGrayboxPlayerBriefing.at(snapshot, snapshot.bounds().minX(), snapshot.bounds().minZ() - 32).isEmpty(),
                "the player layer must not fabricate a cause or action for an empty non-source position");
    }

    @Test
    void retainsABoundedPlayerTimelineAndAForwardLookingReport() {
        ReferenceGrayboxSnapshot snapshot = withEvents(ReferenceGrayboxSimulation.create(42L).snapshot(), List.of(
                "D1: first event", "D2: physical route_damaged route:1:2 weight=2", "D3: third event", "D4: physical bioform killed bioform:1:1"));

        String timeline = SourceGrayboxPlayerBriefing.forIdentity(snapshot, "events:summary", SourceGrayboxMaterializer.LABEL_KIND).orElseThrow();
        String report = SourceGrayboxPlayerBriefing.forIdentity(snapshot, "dashboard:summary", SourceGrayboxMaterializer.LABEL_KIND).orElseThrow();

        assertTrue(timeline.contains("Recent canonical changes:") && timeline.contains("a hive bioform was killed"),
                "the timeline must remain bounded, human-readable and based only on retained canonical events");
        assertFalse(timeline.contains("first event"), "the timeline must omit events outside its fixed three-event window");
        assertTrue(report.contains("Forecast:") && report.contains("Priority:"),
                "the regional briefing must distinguish the current priority from a future risk");
    }

    private static ReferenceGrayboxSnapshot withReducedRouteCapacity(ReferenceGrayboxSnapshot source, String id, double capacity) {
        List<ReferenceGrayboxSnapshot.Route> routes = new ArrayList<>(source.routes().size());
        for (ReferenceGrayboxSnapshot.Route route : source.routes()) {
            routes.add(route.id().equals(id) ? new ReferenceGrayboxSnapshot.Route(route.id(), route.settlementA(), route.settlementB(),
                    route.start(), route.end(), capacity, route.risk(), route.infection(), route.quarantined(), route.disrupted(), route.colour()) : route);
        }
        return new ReferenceGrayboxSnapshot(source.day(), source.profileId(), source.stateRevision(), source.bounds(), source.cells(),
                source.settlements(), source.facilities(), source.resourceSites(), routes, source.hiveOrgans(), source.bioforms(), source.residents(),
                source.fieldPosts(), source.fieldLinks(), source.activities(), source.cargoes(), source.interactions(), source.sectors(),
                source.chrysalises(), source.readouts(), source.events());
    }

    private static ReferenceGrayboxSnapshot withEvents(ReferenceGrayboxSnapshot source, List<String> events) {
        return new ReferenceGrayboxSnapshot(source.day(), source.profileId(), source.stateRevision(), source.bounds(), source.cells(),
                source.settlements(), source.facilities(), source.resourceSites(), source.routes(), source.hiveOrgans(), source.bioforms(),
                source.residents(), source.fieldPosts(), source.fieldLinks(), source.activities(), source.cargoes(), source.interactions(),
                source.sectors(), source.chrysalises(), source.readouts(), events);
    }
}
