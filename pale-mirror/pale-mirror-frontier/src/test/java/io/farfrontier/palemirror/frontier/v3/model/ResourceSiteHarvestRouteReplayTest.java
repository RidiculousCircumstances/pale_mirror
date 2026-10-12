package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Retained route acceptance is not a second search for an optimal path. */
class ResourceSiteHarvestRouteReplayTest {
    @Test void terminalRefusalReplaysWithoutSearchAndDoesNotPermanentlyFreezeColdWork() throws Exception {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var state = fixture.state(); var job = fixture.job();
        var action = ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
        ResourceSiteHarvestColdGoalHeld refusal;
        try (var binding = PedestrianRoutePlanning.bind((geometry, from, to) ->
                new PedestrianRouteResult(PedestrianRouteResult.Status.NO_PATH, List.of(), 2, 1, "isolated terminal refusal"))) {
            refusal = ResourceSiteHarvestProcess.planColdProgress(state, action).stream().map(ProposedEvent::payload)
                    .filter(ResourceSiteHarvestColdGoalHeld.class::isInstance)
                    .map(ResourceSiteHarvestColdGoalHeld.class::cast).findFirst().orElseThrow();
        }
        assertEquals(PedestrianRouteResult.Status.NO_PATH, refusal.routeStatus());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(refusal, codecs.decode(refusal.type(), codecs.encode(refusal)));
        var codec = new FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(state));
        FrontierWorldState held;
        try (var binding = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            fail("refusal replay cannot search again for a negative answer"); return null;
        })) {
            held = ResourceSiteHarvestProcess.reduceColdGoalHeld(recovered, fixture.site(), refusal);
            assertEquals(recovered.inventory(), held.inventory());
            assertEquals(recovered.actorLocations(), held.actorLocations());
            assertTrue(held.resourceSites().site(fixture.site()).harvestJob(job.id()).orElseThrow()
                    .navigationBlock().orElseThrow().reroutable());
            var duplicate = held;
            assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceColdGoalHeld(duplicate, fixture.site(), refusal));
            var id = refusal.executionId();
            var stale = new ResourceSiteHarvestColdGoalHeld(refusal.siteId(), refusal.jobId(), refusal.workerId(),
                    refusal.layoutRevision(), refusal.nextWorkSlot(), refusal.kind(), refusal.target(), refusal.reason(),
                    refusal.coldScheduleId(), refusal.coldDueAt(), refusal.goalRevision(), refusal.origin(),
                    new ActorExecutionId(id.actorId(), id.activityKind(), id.activityOwnerId(), id.generation() + 1),
                    refusal.routeStatus(), refusal.routeDetail());
            assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceColdGoalHeld(recovered, fixture.site(), stale));
            assertThrows(IllegalArgumentException.class, () -> new ResourceSiteHarvestColdGoalHeld(refusal.siteId(), refusal.jobId(),
                    refusal.workerId(), refusal.layoutRevision(), refusal.nextWorkSlot(), refusal.kind(), refusal.target(), refusal.reason(),
                    refusal.coldScheduleId(), refusal.coldDueAt(), refusal.goalRevision(), refusal.origin(), id,
                    PedestrianRouteResult.Status.PLANNING, "not a terminal outcome"));
        }
        held = codec.decode(codec.encode(held));
        assertFalse(ResourceSiteHarvestProcess.coldProgressHeld(held, action), "a calculation miss cannot park the farmer until HOT");
        var movement = ResourceSiteHarvestProcess.planColdProgress(held, action).stream().map(ProposedEvent::payload)
                .filter(ResourceSiteHarvestColdGoalAdvanced.class::isInstance)
                .map(ResourceSiteHarvestColdGoalAdvanced.class::cast).findFirst().orElseThrow();
        var resumed = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(held, fixture.site(), movement);
        assertTrue(resumed.resourceSites().site(fixture.site()).harvestJob(job.id()).orElseThrow().navigationBlock().isEmpty());
        assertEquals(held.inventory(), resumed.inventory());

        // Isolated recovered initial condition: a supported capture has placed the retained
        // worker at the same semantic goal, while the prior calculation hold still exists.
        var goal = ResourceSiteHarvestGoal.current(held, held.resourceSites().site(fixture.site()).harvestJob(job.id()).orElseThrow());
        var actors = new java.util.LinkedHashMap<>(held.actorLocations());
        actors.put(job.workerId(), actors.get(job.workerId()).withBody(goal.representative().standingBody()));
        var atGoal = held.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        var clearance = ResourceSiteHarvestProcess.planColdProgress(atGoal, action).stream().map(ProposedEvent::payload)
                .filter(ResourceSiteHarvestColdGoalAdvanced.class::isInstance)
                .map(ResourceSiteHarvestColdGoalAdvanced.class::cast).findFirst().orElseThrow();
        assertTrue(clearance.plannedRoute().isEmpty());
        var cleared = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(atGoal, fixture.site(), clearance);
        assertEquals(atGoal.actorLocations(), cleared.actorLocations());
        assertEquals(atGoal.inventory(), cleared.inventory());
        assertTrue(cleared.resourceSites().site(fixture.site()).harvestJob(job.id()).orElseThrow().navigationBlock().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceColdGoalAdvanced(cleared, fixture.site(), clearance));
    }

    @Test void acceptedAlternativeColdRouteReplaysWithoutRediscoveringThePreferredNextSupport() throws Exception {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var state = fixture.state(); var job = fixture.job();
        var usual = ResourceSiteHarvestKnownNavigation.plannedPath(state, job);
        var start = usual.getFirst();
        var knowledge = KnownPedestrianRouteKnowledge.forField(state, state.resourceSite(job.siteId()),
                state.resourceSites().cycle(job.siteId()), ResourceSiteHarvestGoal.depotPort(state, job), start);
        SurfaceAnchor alternative = null;
        for (int[] delta : List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1})) {
            var candidate = KnownPedestrianGround.forFrontier(state).at(start.x() + delta[0], start.z() + delta[1]);
            if (candidate == null || candidate.equals(usual.get(1))) continue;
            try {
                new PedestrianRouteReceipt(List.of(start, candidate));
                knowledge.requireRoute(List.of(start, candidate));
                alternative = candidate; break;
            } catch (IllegalArgumentException unavailable) { /* Try another actual supported neighbour. */ }
        }
        assertNotNull(alternative, "fixture needs two legal first edges to the same work goal");
        var route = new java.util.ArrayList<SurfaceAnchor>();
        route.add(start); route.add(alternative); route.addAll(usual);
        knowledge.requireRoute(route);
        var acceptedPath = List.copyOf(route);
        var action = ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
        ResourceSiteHarvestColdGoalAdvanced accepted;
        try (var binding = PedestrianRoutePlanning.bind((geometry, from, to) ->
                new PedestrianRouteResult(PedestrianRouteResult.Status.FOUND, acceptedPath, 0, 0, "isolated legal alternative"))) {
            accepted = ResourceSiteHarvestProcess.planColdProgress(state, action).stream().map(ProposedEvent::payload)
                    .filter(ResourceSiteHarvestColdGoalAdvanced.class::isInstance)
                    .map(ResourceSiteHarvestColdGoalAdvanced.class::cast).findFirst().orElseThrow();
        }
        assertNotEquals(usual.get(1).standingBody(), accepted.nextBody());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(accepted, codecs.decode(accepted.type(), codecs.encode(accepted)));
        var codec = new FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(state));
        try (var binding = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            fail("replay must validate accepted geometry without invoking a planner"); return null;
        })) {
            var advanced = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(recovered, fixture.site(), accepted);
            assertEquals(accepted.nextBody(), advanced.actorLocations().get(job.workerId()).body());
            assertEquals(recovered.inventory(), advanced.inventory());
            assertEquals(recovered.resourceSites(), advanced.resourceSites(), "movement cannot harvest a cell");
            assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceColdGoalAdvanced(advanced, fixture.site(), accepted));
            var id = accepted.executionId();
            var stale = new ResourceSiteHarvestColdGoalAdvanced(job.id(), job.workerId(), accepted.layoutRevision(),
                    accepted.nextWorkSlot(), accepted.kind(), accepted.nextBody(), action.id(), action.dueAt().ticks(),
                    accepted.goalRevision(), new ActorExecutionId(id.actorId(), id.activityKind(), id.activityOwnerId(), id.generation() + 1), accepted.plannedRoute());
            assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceColdGoalAdvanced(recovered, fixture.site(), stale));
            var truncated = new ResourceSiteHarvestColdGoalAdvanced(job.id(), job.workerId(), accepted.layoutRevision(),
                    accepted.nextWorkSlot(), accepted.kind(), accepted.nextBody(), action.id(), action.dueAt().ticks(),
                    accepted.goalRevision(), id, Optional.of(new PedestrianRouteReceipt(acceptedPath.subList(0, 2))));
            assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceColdGoalAdvanced(recovered, fixture.site(), truncated));
        }
    }
}
