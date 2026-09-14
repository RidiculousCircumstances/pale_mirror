package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.entity.Mob;

/** Public lifecycle boundary for retained HOT body motion and its vanilla-tracker observation. */
public final class FrontierV3MobMotionLifecycle {
    private FrontierV3MobMotionLifecycle() { }

    public static void advanceAtEntityBoundary(Mob mob) { FrontierV3ControlledMobMotion.advanceAtEntityBoundary(mob); }
    public static void observeVanillaTracker(Mob mob) { FrontierV3ControlledMobMotion.observeVanillaTracker(mob); }
    public static FrontierV3ControlledMobMotion.TrackerObservation trackerObservation(Mob mob) {
        return FrontierV3ControlledMobMotion.trackerObservation(mob);
    }
}
