package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionServiceWaitTest {
    @Test void serviceReleaseWakesLoadingAfterRecoveryWithoutWaitingForTheTradeReviewClock() {
        var base = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:supply-service-wake"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var initialEngine = FrontierEngines.createCanonicalStateAccess(base);
        for (int step = 0; step < 64 && initialEngine.canonicalState().state().shipments().missions().isEmpty(); step++) {
            var due = initialEngine.nextExecutionBoundary().orElseThrow();
            assertEquals(EngineStatus.Kind.ACTIVE, initialEngine.advanceTo(due, new WorkBudget(1, 1024)).status().kind());
        }
        var state = initialEngine.canonicalState().state();
        var mission = state.shipments().missions().values().stream().findFirst().orElseThrow();
        var load = mission.supplies().orElseThrow();
        var allocation = load.next().orElseThrow();
        var group = state.unitGroups().groups().get(mission.groupId());
        long now = initialEngine.checkpoint().instant().ticks();
        // Fixture precondition: an admitted delivery waits while another resident clears the port.
        for (var member : group.members()) state = state.withActorBody(member.actorId(),
                load.assemblyStations().get(member.actorId()).standingBody());
        var loadedPart = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.coldLoaded(
                state.withActorBody(allocation.actorId(), mission.sender().station().standingBody()), mission.id(), allocation.claimId());
        var occupied = state;
        var blocker = state.humanPopulation().residents().keySet().stream().sorted()
                .filter(id -> group.members().stream().noneMatch(member -> member.actorId().equals(id)))
                .filter(id -> !occupied.actorExecutions().actors().containsKey(id)).findFirst().orElseThrow();
        var boundary = ServiceAccessCoordinator.boundary(state, mission.sender().containerId());
        state = state.withActorBody(blocker, mission.sender().station().standingBody());
        var execution = state.actorExecutions().next(blocker, ActorActivityKind.SERVICE_EXIT, blocker);
        var exit = KnownServiceExitNavigation.clearancePathFrom(state, mission.sender().settlementId(),
                mission.sender().containerId(), blocker, blocker, 0, 1,
                state.actorLocations().get(blocker).supportingSurface()).getLast();
        assertTrue(boundary.cleared(exit.standingBody()));
        var movement = new ActorMovement(new MovementOrder(blocker, blocker, 0, 1, List.of(exit),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION), now,
                new ActorMovementContext.ServiceExit(mission.sender().settlementId(), mission.sender().containerId()), execution);
        state = ActorMovementProcess.reduceStarted(state, blocker, new ActorMovementStarted(movement));
        var assemblyReady = ActorMovementProcess.reduceStarted(
                loadedPart.withActorBody(blocker, mission.sender().station().standingBody()), blocker, new ActorMovementStarted(movement));
        assertFalse(ExpeditionSupplyProcess.serviceHeld(assemblyReady, assemblyReady.shipments().missions().get(mission.id())),
                "another loading wait must not park the already provisioned member's independent assembly");
        var action = TransportMissionProcess.progress(mission.id(), now + 1);
        assertTrue(FrontierWorldRuntimeDefinition.scheduledHeld(state, action));
        assertTrue(FrontierWorldProcessCatalog.holdWakeKeys(state, action).containsAll(Set.of(
                mission.sender().containerId(), allocation.actorId(), mission.id(), mission.groupId())));
        var retry = ExpeditionSupplyProcess.plan(state, mission, action, now).stream()
                .map(ProposedEvent::payload).filter(ScheduleEffect.Rescheduled.class::isInstance)
                .map(ScheduleEffect.Rescheduled.class::cast).filter(e -> e.scheduleId().equals(action.id())).findFirst().orElseThrow();
        assertEquals(now + 1, retry.replacement().dueAt().ticks(), "service wait must not become a 6000-tick review delay");
        var schedules = new ArrayList<>(initialEngine.checkpoint().schedules());
        schedules.removeIf(a -> a.id().equals(action.id())); schedules.add(action);
        schedules.add(ActorMovementProcess.progress(movement, now + 1));
        var config = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(now), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), schedules,
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        assertEquals(EngineStatus.Kind.ACTIVE, engine.advanceTo(new SimInstant(now + 1), new WorkBudget(32, 1024)).status().kind());
        assertFalse(engine.canonicalState().state().shipments().missions().get(mission.id()).supplies().orElseThrow().allocations().getFirst().loaded());
        var checkpoint = engine.checkpoint();
        engine = FrontierEngines.recoverCanonicalStateAccess(config, new RecoveryImage(base.worldId(),
                Optional.of(new SnapshotRecord(checkpoint, 0)), List.of()));
        int quantityBefore = total(engine.canonicalState().state());
        for (int step = 0; step < 32; step++) {
            if (engine.canonicalState().state().shipments().missions().get(mission.id()).supplies().orElseThrow().allocations().getFirst().loaded()) break;
            var current = engine.canonicalState().state();
            var due = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, a))
                    .sorted().findFirst().orElseThrow().dueAt();
            assertTrue(due.ticks() < now + state.bootstrap().ruleset().cadence().transportReviewInterval(),
                    "loading waited for the strategic review clock after the service exit: " + due);
            var result = engine.advanceTo(new SimInstant(Math.max(engine.executionView().instant().ticks(), due.ticks())), new WorkBudget(32, 4096));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        var received = engine.canonicalState().state();
        assertTrue(received.shipments().missions().get(mission.id()).supplies().orElseThrow().allocations().getFirst().loaded(),
                "ordinary exit must wake and execute the supply handoff well before the fallback review timer: tick="
                    + engine.executionView().instant() + " blocker=" + received.actorLocations().get(blocker)
                    + " movements=" + received.actorMovements());
        assertEquals(quantityBefore, total(received), "wake-up changes custody, not total resources");
    }

    private static int total(FrontierWorldState state) {
        return state.inventory().fungibleResources().accounts().values().stream()
                .mapToInt(a -> a.lotQuantities().values().stream().mapToInt(Integer::intValue).sum()).sum();
    }
}
