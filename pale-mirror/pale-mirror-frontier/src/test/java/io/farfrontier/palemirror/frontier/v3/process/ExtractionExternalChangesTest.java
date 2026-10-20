package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionExternalChangesTest {
    @Test void occupiedSourceAtArrivedStationSelectsOtherWorkWithoutMiningUnderAResident() {
        var admission = admitted(); var state = admission.state();
        var job = state.extractionSites().work().values().stream().min(Comparator.comparing(ExtractionWork::id)).orElseThrow();
        var actor = job.execution().actorId(); var site = ExtractionWorkAuthority.site(state, job);
        state = state.withActorBody(actor, job.toolSource().station().standingBody());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.TAKE_TOOL), admission.tick());
        job = state.extractionSites().work().get(job.id());
        var cell = site.layout().require(job.target().orElseThrow().key().cell());
        var peer = state.humanPopulation().residents().keySet().stream().filter(id -> !id.equals(actor)).sorted().findFirst().orElseThrow();
        state = state.withActorBody(actor, cell.workstation().standingBody())
                .withActorBody(peer, new SurfaceAnchor(cell.source()).standingBody());
        var before = state; var current = job;
        assertFalse(ExtractionWorkSafety.available(state, state.extractionSites().deposits().get(site.id()), cell));
        var action = ExtractionContinuation.at(job.id(), admission.tick());
        var events = ExtractionWorkProcess.progress(state, action, admission.tick());
        var selected = events.stream().map(ProposedEvent::payload).filter(ExtractionWorkProgressed.class::isInstance)
                .map(ExtractionWorkProgressed.class::cast).findFirst().orElseThrow();
        assertEquals(ExtractionWorkProgressed.Operation.SELECT_REACHABLE, selected.operation());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var replay = (ExtractionWorkProgressed) codecs.decode(selected.type(), codecs.encode(selected));
        var after = ExtractionColdWork.apply(before, job.id(), replay, admission.tick());
        assertNotEquals(current.target(), after.extractionSites().work().get(job.id()).target());
        assertEquals(before.inventory(), after.inventory()); assertEquals(before.actorLocations(), after.actorLocations());
        assertEquals(before.extractionSites().deposits(), after.extractionSites().deposits(), "selection cannot mine or award output");
        assertEquals(current.execution(), after.extractionSites().work().get(job.id()).execution());
        assertTrue(after.extractionSites().work().get(job.id()).labour().isEmpty(), "new block does not inherit old work credit");
    }
    private record Admission(FrontierWorldState state, long tick) { }
    private static Admission admitted() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:source-retarget"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r13"));
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        for (int turn = 0; turn < 500 && engine.canonicalState().state().extractionSites().work().isEmpty(); turn++) {
            var current = engine.canonicalState().state();
            var due = engine.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action))
                    .sorted().findFirst().orElseThrow();
            var advanced = engine.advanceTo(new SimInstant(Math.max(due.dueAt().ticks(), engine.checkpoint().instant().ticks())), new WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, advanced.status().kind(), advanced.status().failureDetail().orElse("active"));
            if (turn % 100 == 0) engine.compact(engine.checkpoint().revision());
        }
        return new Admission(engine.canonicalState().state(), engine.checkpoint().instant().ticks());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void changedSourcePreservesIndependentClearanceForCurrentOrSuspendedMiner(boolean suspended) {
        var admission = admitted(); var state = admission.state();
        var job = state.extractionSites().work().values().stream().min(Comparator.comparing(ExtractionWork::id)).orElseThrow();
        var actor = job.execution().actorId(); var site = ExtractionWorkAuthority.site(state, job);
        state = state.withActorBody(actor, site.layout().storagePort().standingBody());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.TAKE_TOOL), admission.tick());
        job = state.extractionSites().work().get(job.id());
        // Both paths are valid: mining clears its tool counter, or a different
        // current activity clears the home depot while this miner is suspended.
        var successor = suspended ? state.actorExecutions().next(actor,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, actor)
                : job.execution();
        if (suspended) state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, successor, admission.tick())
                .commit(state, FrontierWorldStateUpdate.begin());
        var depot = suspended ? FrontierWorldState.depotId(site.settlementId()) : QuarryStorageFixture.container(state, site);
        if (suspended) state = state.withActorBody(actor,
                SettlementServiceAccessPoints.depotPort(state, site.settlementId()).serviceSurface().standingBody());
        var clearance = ResourceAccessClearance.select(state, depot, successor, admission.tick()).orElseThrow();
        state = ActorMovementProcess.reduceStarted(state, actor, new ActorMovementStarted(clearance));
        var target = job.target().orElseThrow(); var source = site.layout().require(target.key().cell()).source();
        var region = new ExtractionRegion(site.id(), Math.floorDiv(source.x(), 16), Math.floorDiv(source.z(), 16));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(ExtractionSourceCustody.apply(state,
                region.objectId(), new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.PREPARE, 0, 0,
                    ExtractionSourceCustody.fingerprint(state.extractionSites(), region)), 100000)));
        var before = state;
        var event = new ExtractionSourceChanged(target, new BlockExtraction.Block("minecraft:air", Map.of()), 1, 1, Optional.empty());
        var changed = ExtractionExternalChanges.apply(before, site.id(), event, 100001, 100L);
        assertEquals(clearance, changed.actorMovements().get(actor));
        assertEquals(before.actorExecutions(), changed.actorExecutions());
        assertEquals(suspended ? Optional.of(job.execution()) : Optional.empty(),
                changed.actorExecutions().actors().get(actor).suspended());
        assertNotEquals(job.target(), changed.extractionSites().work().get(job.id()).target());
        assertEquals(before.actorLocations(), changed.actorLocations()); assertEquals(before.inventory(), changed.inventory());
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
    }
    @Test void actualAdmissionAndMovementRetargetTogetherWhenPlayerRemovesItsSourceWithoutAnyMiningOutput() {
        var admitted = admitted(); var state = admitted.state();
        var job = state.extractionSites().work().values().stream().min(Comparator.comparing(ExtractionWork::id)).orElseThrow();
        assertEquals(ExtractionWork.Phase.TAKE_TOOL, job.phase());
        var site = ExtractionWorkAuthority.site(state, job); var target = job.target().orElseThrow();
        var source = site.layout().require(target.key().cell()).source();
        var region = new ExtractionRegion(site.id(), Math.floorDiv(source.x(), 16), Math.floorDiv(source.z(), 16));
        var boundary = new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.PREPARE, 0, 0,
                ExtractionSourceCustody.fingerprint(state.extractionSites(), region));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(ExtractionSourceCustody.apply(state,
                region.objectId(), boundary, 100000)));
        var movement = new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(job.movementOrder(site),
                admitted.tick(), new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ExtractionLeg(job.id(), job.revision()), job.execution());
        state = ActorMovementProcess.reduceStarted(state, job.execution().actorId(), new ActorMovementStarted(movement));
        var before = state;
        var event = new ExtractionSourceChanged(target, new BlockExtraction.Block("minecraft:air", Map.of()), 1, 1, Optional.empty());
        var changed = ExtractionExternalChanges.apply(before, site.id(), event, 100001, 100L);
        var next = changed.extractionSites().work().get(job.id());
        assertEquals(job.execution(), next.execution());
        assertEquals(job.toolId(), next.toolId());
        assertEquals(job.phase(), next.phase());
        assertNotEquals(job.target(), next.target());
        assertFalse(changed.actorMovements().containsKey(job.execution().actorId()));
        assertEquals(before.actorLocations(), changed.actorLocations(), "retargeting neither teleports nor awards arrival");
        assertEquals(before.inventory(), changed.inventory(), "external change neither takes equipment nor creates output");
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
    }
    @Test void physicalGeometryChangeWithdrawsAnIssuedBlockedGoalAndKeepsMiningOtherReachableCells() throws Exception {
        var admitted = admitted(); var state = admitted.state();
        var job = state.extractionSites().work().values().stream().min(Comparator.comparing(ExtractionWork::id)).orElseThrow();
        var site = ExtractionWorkAuthority.site(state, job);
        state = state.withActorBody(job.execution().actorId(), site.layout().storagePort().standingBody());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.TAKE_TOOL), admitted.tick());
        job = state.extractionSites().work().get(job.id());
        var movement = new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(job.movementOrder(site), admitted.tick(),
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ExtractionLeg(job.id(), job.revision()), job.execution());
        state = ActorMovementProcess.reduceStarted(state, job.execution().actorId(), new ActorMovementStarted(movement));
        var before = state;
        var feet = job.movementOrder(site).legalStations().getFirst().support().offset(0, 1, 0);
        var declaration = ExtractionWorksiteBlocks.declared(state.extractionSites().deposits().get(site.id())).stream()
                .filter(value -> value.position().equals(feet)).findFirst().orElseThrow();
        assertEquals(WorksiteBlock.Role.INFRASTRUCTURE, declaration.key().role());
        var event = new ExtractionGeometryChanged(declaration, new BlockExtraction.Block("minecraft:stone", Map.of()));
        var commandId = new CommandId("command:blocked-mining-station");
        var command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, commandId, state.bootstrap().worldId(),
                new Revision(100000), new SimInstant(admitted.tick()), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(commandId), event);
        // Real command composition owns cancellation/wake, not just a helper's state change.
        var planned = new ExtractionWorkProcessModule().planCommand(state, command);
        assertInstanceOf(CommandPlan.Accepted.class, planned);
        var changed = event.apply(state, site.id()); var next = changed.extractionSites().work().get(job.id());
        assertEquals(job.target(), next.target(), "geometry observation invalidates motion, not selection of a different source");
        assertEquals(job.execution(), next.execution());
        assertEquals(before.actorLocations(), changed.actorLocations()); assertEquals(before.inventory(), changed.inventory());
        assertFalse(changed.actorMovements().containsKey(job.execution().actorId()));
        var events = ((CommandPlan.Accepted) planned).events();
        assertTrue(events.stream().anyMatch(value -> value.payload() instanceof ScheduleEffect.Cancelled));
        assertTrue(events.stream().anyMatch(value -> value.payload() instanceof ScheduleEffect.Rescheduled));
        try (var ignored = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(
                (geometry, start, goal) -> new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(
                        io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,
                        List.of(), 0, 0, "runtime calculation has not completed"))) {
            assertEquals(changed, event.apply(before, site.id()),
                    "the same retained geometry event must reduce identically before async planning and during replay");
        }
        var action = ExtractionContinuation.at(next.id(), admitted.tick());
        try (var ignored = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry, from, to) ->
                new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(
                        io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,
                        List.of(), 0, 0, "alternative extraction source pending"))) {
            var pending = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.observe(
                    () -> ExtractionWorkProcess.progress(changed, action, admitted.tick()));
            assertEquals(1, pending.requests().size());
            assertTrue(pending.result().stream().allMatch(value -> value.payload() instanceof ScheduleEffect.Rescheduled));
        }
        var selected = ExtractionWorkProcess.progress(changed, action, admitted.tick()).stream().map(ProposedEvent::payload)
                .filter(ExtractionWorkProgressed.class::isInstance).map(ExtractionWorkProgressed.class::cast).findFirst().orElseThrow();
        assertEquals(ExtractionWorkProgressed.Operation.SELECT_REACHABLE, selected.operation());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(selected, codecs.decode(selected.type(), codecs.encode(selected)));
        var beforeSelection = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed));
        FrontierWorldState retargeted;
        try (var ignored = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry, from, to) -> {
            fail("selected mining successor replay must not rediscover a route"); return null;
        })) {
            retargeted = ExtractionColdWork.apply(beforeSelection, next.id(), selected, admitted.tick());
            var afterSelection = retargeted;
            assertThrows(IllegalArgumentException.class, () -> ExtractionColdWork.apply(afterSelection, next.id(), selected, admitted.tick()));
        }
        var successor = retargeted.extractionSites().work().get(next.id());
        assertNotEquals(next.target(), successor.target());
        var knowledge = ExtractionWorkTargets.knowledge(retargeted, successor);
        knowledge.requireRoute(knowledge.plannedPath(retargeted.actorLocations().get(next.execution().actorId()).supportingSurface(), successor.movementOrder(site)));
        assertEquals(retargeted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(retargeted)));
    }
}
