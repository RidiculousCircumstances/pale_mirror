package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3NavigationScopeTest {
    @Test void busyServiceRestrictsOnlyEntryNotTheWholeApproachOrItsDetours() {
        var start = SurfaceAnchor.at(1, 63, 1);
        var waiting = SurfaceAnchor.at(4, 63, 1);
        var station = SurfaceAnchor.at(5, 63, 1);
        var boundary = new ServiceAccessBoundary(java.util.Set.of(station));
        var goal = FrontierV3ServiceApproachNavigation.outsideGoal(List.of(start, waiting, station), boundary,
                new WorldBounds(0, 0, 10, 10)).orElseThrow();
        assertEquals(List.of(waiting), goal.legalStations());
        assertEquals(List.of(start, waiting), goal.routeHint());
        assertTrue(goal.scope().permits(start.support()));
        assertTrue(goal.scope().permits(new BlockPosition(3, 63, 3)));
        assertFalse(goal.scope().permits(station.support()));
        assertFalse(goal.scope().permits(new BlockPosition(-1, 63, 1)));
        assertTrue(goal.order().isEmpty(), "waiting arrival cannot be the retained owner goal arrival");
        assertTrue(FrontierV3ServiceApproachNavigation.outsideGoal(List.of(station, waiting), boundary,
                new WorldBounds(0, 0, 10, 10)).isEmpty(), "an occupant must use witnessed egress instead");
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
