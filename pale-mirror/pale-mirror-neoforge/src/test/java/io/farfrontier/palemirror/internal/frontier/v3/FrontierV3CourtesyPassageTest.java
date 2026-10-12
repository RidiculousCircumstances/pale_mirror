package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CourtesyPassageTest {
    @Test void parkingIsNotPassageCompletionAndAMissingPathCannotInventCompletion() {
        assertEquals(FrontierV3CourtesyPassage.Status.WAITING,
                FrontierV3CourtesyPassage.assess(FrontierV3CourtesyPassage.Hold.empty(), true, true, 0, Optional.of(true)).status());
        var missing = FrontierV3CourtesyPassage.assess(FrontierV3CourtesyPassage.Hold.empty(), true, true, 30, Optional.empty());
        assertEquals(FrontierV3CourtesyPassage.Status.WAITING,
                missing.status());
        assertEquals(FrontierV3CourtesyPassage.Status.CLEARED,
                FrontierV3CourtesyPassage.assess(missing.hold(), true, true, 31, Optional.of(false)).status());
    }

    @Test void staleExecutionChangedCommandAndLostPhysicalHandshakeReleaseTheEphemeralHold() {
        assertEquals(FrontierV3CourtesyPassage.Status.REQUESTER_RETIRED,
                FrontierV3CourtesyPassage.assess(FrontierV3CourtesyPassage.Hold.empty(), false, true, 1, Optional.of(true)).status());
        assertEquals(FrontierV3CourtesyPassage.Status.COMMAND_CHANGED,
                FrontierV3CourtesyPassage.assess(FrontierV3CourtesyPassage.Hold.empty(), true, false, 1, Optional.of(true)).status());
        var missing = FrontierV3CourtesyPassage.assess(FrontierV3CourtesyPassage.Hold.empty(), true, true, 0, Optional.empty());
        assertEquals(FrontierV3CourtesyPassage.Status.EXPIRED,
                FrontierV3CourtesyPassage.assess(missing.hold(), true, true, FrontierV3CourtesyPassage.MAX_UNOBSERVED_TICKS, Optional.empty()).status());
        assertEquals(FrontierV3CourtesyPassage.Status.EXPIRED,
                FrontierV3CourtesyPassage.assess(missing.hold(), true, true, -1, Optional.empty()).status());
    }

    @Test void validSlowMotionUsesItsOwnNavigationDeadlineAndResetsOnlyMissingEvidenceWait() {
        var missing = FrontierV3CourtesyPassage.assess(FrontierV3CourtesyPassage.Hold.empty(), true, true, 0, Optional.empty());
        var known = FrontierV3CourtesyPassage.assess(missing.hold(), true, true, 1000, Optional.of(true));
        assertEquals(FrontierV3CourtesyPassage.Status.WAITING, known.status());
        assertTrue(known.hold().unobservedSince().isEmpty());
        var lostAgain = FrontierV3CourtesyPassage.assess(known.hold(), true, true, 1001, Optional.empty());
        assertEquals(FrontierV3CourtesyPassage.Status.WAITING, lostAgain.status());
        assertEquals(1001, lostAgain.hold().unobservedSince().orElseThrow());
        assertEquals(FrontierV3CourtesyPassage.Status.WAITING,
                FrontierV3CourtesyPassage.assess(lostAgain.hold(), true, true, 1099, Optional.empty()).status());
    }

    @Test void requesterMustActuallyClearItsObstructionAndRemainingAcceptedPath() {
        var obstruction = bodyAt(2.5, 64, 0.5);
        var path = new Path(List.of(new Node(0, 64, 0), new Node(1, 64, 0), new Node(2, 64, 0), new Node(3, 64, 0)),
                new BlockPos(3, 64, 0), true);
        assertEquals(Optional.of(true), FrontierV3PedestrianTraffic.needsPassage(bodyAt(0.5, 64, 0.5),
                new Vec3(0.5, 64, 0.5), 0.6F, path, obstruction));
        path.setNextNodeIndex(3);
        assertEquals(Optional.of(true), FrontierV3PedestrianTraffic.needsPassage(bodyAt(2.5, 64, 0.5),
                new Vec3(2.5, 64, 0.5), 0.6F, path, obstruction), "advancing a path cursor cannot move the actual body");
        assertEquals(Optional.of(false), FrontierV3PedestrianTraffic.needsPassage(bodyAt(3.5, 64, 0.5),
                new Vec3(3.5, 64, 0.5), 0.6F, path, obstruction));
        assertEquals(Optional.empty(), FrontierV3PedestrianTraffic.needsPassage(bodyAt(0.5, 64, 0.5),
                new Vec3(0.5, 64, 0.5), 0.6F, null, obstruction));
        var finalTarget = new Path(List.of(new Node(2, 64, 0)), new BlockPos(2, 64, 0), true);
        finalTarget.setNextNodeIndex(1);
        assertEquals(Optional.of(true), FrontierV3PedestrianTraffic.needsPassage(bodyAt(1.5, 64, 0.5),
                new Vec3(1.5, 64, 0.5), 0.6F, finalTarget, obstruction), "native path completion is not exact final centring");
        var oversized = new Path(java.util.Collections.nCopies(FrontierV3PhysicalPathPolicy.MAX_PATH_NODES + 1, new Node(0, 64, 0)),
                new BlockPos(0, 64, 0), true);
        assertEquals(Optional.empty(), FrontierV3PedestrianTraffic.needsPassage(bodyAt(0.5, 64, 0.5),
                new Vec3(0.5, 64, 0.5), 0.6F, oversized, obstruction), "unknown over-budget geometry cannot establish passage completion");
    }

    @Test void acceptedDetourAndElevatedPathDoNotReserveAnUnusedFootprint() {
        var obstruction = bodyAt(2.5, 64, 0.5);
        for (var path : List.of(new Path(List.of(new Node(1, 64, 2), new Node(3, 64, 2)), new BlockPos(3, 64, 2), true),
                new Path(List.of(new Node(2, 66, 0)), new BlockPos(2, 66, 0), true))) {
            assertEquals(Optional.of(false), FrontierV3PedestrianTraffic.needsPassage(bodyAt(0.5, 64, 0.5),
                    new Vec3(0.5, 64, 0.5), 0.6F, path, obstruction));
        }
    }

    @Test void nativeLegIdentityCannotSurviveAnOwnerGoalEpochOrHardScopeChange() {
        var station = SurfaceAnchor.at(4, 63, 0);
        var scope = new FrontierV3NavigationScope.ObservedWorld(new WorldBounds(-10, -10, 20, 20));
        var order = new MovementOrder(new SubjectId("test:owner"), new SubjectId("test:actor"), 0, 1,
                List.of(station), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        var demand = new FrontierV3MinecraftGoalNavigation.Demand(List.of(station), scope, Optional.of(order));
        assertTrue(demand.matches(List.of(station), scope, Optional.of(order)));
        assertFalse(demand.matches(List.of(SurfaceAnchor.at(5, 63, 0)), scope, Optional.of(order)));
        assertFalse(demand.matches(List.of(station), new FrontierV3NavigationScope.ObservedWorld(new WorldBounds(0, 0, 10, 10)), Optional.of(order)));
        assertFalse(demand.matches(List.of(station), scope, Optional.of(new MovementOrder(order.ownerId(), order.actorId(), 0, 2,
                List.of(station), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION))));
    }

    private static AABB bodyAt(double x, double y, double z) { return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3); }
}
