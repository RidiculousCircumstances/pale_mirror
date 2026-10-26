package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3NavigationScopeTest {
    @Test void serviceWaitingUsesExactGroupActuationNotParcelOwnershipAndRejectsStaleOrForeignAuthority() {
        var state = ConsolidatedTradeFixture.initial(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:group-service-wait"), 20260918065L, false);
        var seller = new SubjectId("settlement:1");
        var buyer = state.companies().goodsTrade().participants().participants().get(seller).known().getFirst().party().id();
        state = tradeReview(state, seller, 400L);
        state = tradeReview(state, buyer, 401L);
        var shipment = state.shipments().shipments().values().iterator().next();
        var execution = shipment.execution();
        var actor = execution.actorId();
        var parcel = shipment.id();
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.GROUP_MEMBER, execution.activityKind());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var authority = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body, execution);
        assertNotEquals(parcel, execution.activityOwnerId(), "service order and execution owners are separate relations");
        FrontierV3ServiceApproachNavigation.requireWaitingAuthority(state, actor, authority);
        final var exact = state;
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ServiceApproachNavigation.requireWaitingAuthority(
                exact, new SubjectId("resident:foreign"), authority));
        var forged = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(actor,
                        execution.activityKind(), parcel, execution.generation()));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ServiceApproachNavigation.requireWaitingAuthority(exact, actor, forged));
        var stale = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(actor,
                        execution.activityKind(), execution.activityOwnerId(), execution.generation() + 1L));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ServiceApproachNavigation.requireWaitingAuthority(exact, actor, stale));
        var wrongBody = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(actor, body.physicalEpoch() + 1L), execution);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ServiceApproachNavigation.requireWaitingAuthority(exact, actor, wrongBody));
    }

    private static FrontierWorldState tradeReview(FrontierWorldState state, SubjectId party, long tick) {
        for (var proposed : io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess.review(party, tick),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(tick))) {
            if (proposed.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect) continue;
            var event = new io.farfrontier.palemirror.frontier.v3.api.FrontierEvent(1,
                    new io.farfrontier.palemirror.frontier.v3.api.EventId("event:service-wait-test"),
                    new io.farfrontier.palemirror.frontier.v3.api.TransactionId("transaction:service-wait-test"),
                    state.bootstrap().worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(1),
                    new io.farfrontier.palemirror.frontier.v3.api.SimInstant(tick), proposed.subject(),
                    io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(
                            new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:service-wait-test")), proposed.payload());
            state = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.reduce(state, event);
        }
        return state;
    }

    @Test void busyServiceRestrictsOnlyEntryNotTheWholeApproachOrItsDetours() {
        var start = SurfaceAnchor.at(1, 63, 1);
        var waiting = SurfaceAnchor.at(4, 63, 1);
        var station = SurfaceAnchor.at(5, 63, 1);
        var boundary = new ServiceAccessBoundary(java.util.Set.of(station));
        var exit = SurfaceAnchor.at(4, 63, 2);
        var point = new ServiceAccessPoint(new SubjectId("test:facility"), new SubjectId("test:settlement"),
                station, boundary, List.of(waiting), java.util.Set.of(exit));
        var bounds = new WorldBounds(0, 0, 10, 10);
        var order = new MovementOrder(new SubjectId("test:owner"), new SubjectId("test:visitor"), 1, 7L,
                List.of(waiting), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        var goal = FrontierV3ServiceApproachNavigation.waitingGoal(point, waiting, bounds, order);
        assertEquals(List.of(waiting), goal.legalStations());
        assertTrue(goal.routeHint().isEmpty(), "waiting must not reuse every visitor's same ingress prefix");
        assertTrue(goal.scope().permits(start.support()));
        assertTrue(goal.scope().permits(new BlockPosition(3, 63, 3)));
        assertFalse(goal.scope().permits(station.support()));
        assertFalse(goal.scope().permits(new BlockPosition(-1, 63, 1)));
        assertEquals(order, goal.order().orElseThrow(), "waiting retains exact execution order for native continuation");
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3ServiceApproachNavigation.waitingGoal(point, waiting, bounds,
                    new MovementOrder(order.ownerId(), order.actorId(), 1, 7L, List.of(start),
                        TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION)),
                "a waiting order cannot name a different destination");
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3ServiceApproachNavigation.waitingGoal(point, exit, bounds, order), "egress is not parking");
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3ServiceApproachNavigation.waitingGoal(point, station, bounds, order), "service needs admission");
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3ServiceApproachNavigation.waitingGoal(point, start, bounds, order), "undeclared positions cannot become waiting targets");
    }
    @Test void progressAlongADetourCanIncreaseStraightLineDistanceToTheGoal() {
        var nodes = List.of(new net.minecraft.world.level.pathfinder.Node(2, 64, 4),
                new net.minecraft.world.level.pathfinder.Node(6, 64, 4),
                new net.minecraft.world.level.pathfinder.Node(6, 64, 1));
        var path = new net.minecraft.world.level.pathfinder.Path(nodes, new net.minecraft.core.BlockPos(6, 64, 1), true);
        var before = new net.minecraft.world.phys.Vec3(2.5D, 64, 1.5D);
        var after = new net.minecraft.world.phys.Vec3(2.5D, 64, 2.5D);
        SurfaceAnchor target = SurfaceAnchor.at(6, 63, 1);
        assertTrue(FrontierV3MinecraftGoalNavigation.goalDistance(after, target)
                > FrontierV3MinecraftGoalNavigation.goalDistance(before, target));
        assertTrue(FrontierV3MinecraftGoalNavigation.remainingDistance(path, after)
                < FrontierV3MinecraftGoalNavigation.remainingDistance(path, before));
    }
    @Test void observedWorldRestrictionIsNotAnInflatedKnownRouteStripe() {
        var scope = new FrontierV3NavigationScope.ObservedWorld(new WorldBounds(0, 0, 20, 20));
        assertTrue(scope.permits(new BlockPosition(10, 63, 10)));
        assertFalse(scope.permits(new BlockPosition(20, 63, 10)));
        var restricted = new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.around(
                SurfaceAnchor.at(1, 63, 1).standingBody(), SurfaceAnchor.at(2, 63, 1).standingBody()));
        assertFalse(restricted.permits(new BlockPosition(10, 63, 10)));
    }

    @Test void hintCannotReplaceTheDeclaredDestinationOrEscapeHardBounds() {
        SurfaceAnchor target = SurfaceAnchor.at(6, 63, 2);
        var order = new MovementOrder(new SubjectId("test:owner"), new SubjectId("test:actor"), 0, 1,
                List.of(target), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        WorldBounds bounds = new WorldBounds(0, 0, 10, 10);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3GoalNavigation.Goal.routed(order,
                List.of(SurfaceAnchor.at(5, 63, 2)), bounds));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3GoalNavigation.Goal.routed(order,
                List.of(SurfaceAnchor.at(-1, 63, 2), target), bounds));
        var accepted = FrontierV3GoalNavigation.Goal.routed(order, List.of(), bounds);
        assertEquals(order, accepted.order().orElseThrow());
        assertEquals(List.of(target), accepted.legalStations());
        assertEquals(ResourceSiteHarvestNavigationBlock.Reason.SEARCH_BUDGET_EXHAUSTED,
                ResourceSiteHarvestNavigationBlock.Reason.requireWireTag(11));
    }
}
