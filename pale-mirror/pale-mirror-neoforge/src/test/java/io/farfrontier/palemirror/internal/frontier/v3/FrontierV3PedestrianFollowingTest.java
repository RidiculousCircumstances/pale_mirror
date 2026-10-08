package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PedestrianFollowingTest {
    @Test void aHeadwayPauseIsExcludedAfterReleaseWithoutClaimingBodyProgress() {
        var wait = FrontierV3MinecraftGoalNavigation.FollowingWait.empty().at(true, 10);
        assertEquals(100, wait.duration(110));
        wait = wait.at(false, 110);
        assertEquals(100, wait.duration(120));
        assertEquals(20, 130 - 10 - wait.duration(130));
        wait = wait.at(true, 130).at(false, 150);
        assertEquals(120, wait.duration(160));
    }
    @Test void movingPoputchikIsFollowingButStationaryAndCrossingBodiesStillNeedAvoidance() {
        var origin = Vec3.ZERO; var direction = new Vec3(1, 0, 0); var ahead = new Vec3(2, 0, 0);
        assertTrue(FrontierV3PedestrianFollowing.coFlow(origin, direction, ahead, new Vec3(.05, 0, 0)));
        assertFalse(FrontierV3PedestrianFollowing.coFlow(origin, direction, ahead, Vec3.ZERO));
        assertFalse(FrontierV3PedestrianFollowing.coFlow(origin, direction, ahead, new Vec3(0, 0, .05)));
        assertFalse(FrontierV3PedestrianFollowing.coFlow(origin, direction, ahead, new Vec3(-.05, 0, 0)));
        assertFalse(FrontierV3PedestrianFollowing.coFlow(origin, direction, new Vec3(-2, 0, 0), new Vec3(.05, 0, 0)));
        assertEquals(1, FrontierV3PedestrianFollowing.paceFraction(2, .6, .6));
        assertEquals(0, FrontierV3PedestrianFollowing.paceFraction(.6, .6, .6));
        assertEquals(.5, FrontierV3PedestrianFollowing.paceFraction(.9, .6, .6), 1e-10);
        assertTrue(Math.abs(FrontierV3PedestrianFollowing.paceFraction(.8999, .6, .6)
                - FrontierV3PedestrianFollowing.paceFraction(.9001, .6, .6)) < .001);
    }
}
