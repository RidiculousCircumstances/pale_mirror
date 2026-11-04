package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.entity.Mob;

/** Public lifecycle boundary for retained HOT body motion and its vanilla-tracker observation. */
public final class FrontierV3MobMotionLifecycle {
    private FrontierV3MobMotionLifecycle() { }

    /** Steering remains exclusive to the current navigation permission. */
    public static boolean usesMinecraftGoalNavigation(Mob mob) {
        return FrontierV3GoalNavigation.controls(mob);
    }

    /** Passive native physics belongs to the body, including while idle, hurt or path-blocked. */
    public static boolean usesMinecraftPhysics(Mob mob) {
        return !mob.level().isClientSide() && (FrontierV3ControlledMobMotion.ordinaryPhysicsRegistered(mob)
                || usesMinecraftGoalNavigation(mob));
    }

    /** Clear expired steering inputs, never the physical velocity imparted by a hit or collision. */
    public static void suppressUnownedSteering(Mob mob) {
        if (!usesMinecraftGoalNavigation(mob)) {
            mob.setXxa(0); mob.setYya(0); mob.setZza(0); mob.setJumping(false);
        }
    }

    public static void advanceAtEntityBoundary(Mob mob) {
        if (!FrontierV3GoalNavigation.advanceAtEntityBoundary(mob))
            FrontierV3ControlledMobMotion.advanceAtEntityBoundary(mob);
    }
    public static void observeVanillaTracker(Mob mob) { FrontierV3ControlledMobMotion.observeVanillaTracker(mob); }
    public static FrontierV3ControlledMobMotion.TrackerObservation trackerObservation(Mob mob) {
        return FrontierV3ControlledMobMotion.trackerObservation(mob);
    }
}
