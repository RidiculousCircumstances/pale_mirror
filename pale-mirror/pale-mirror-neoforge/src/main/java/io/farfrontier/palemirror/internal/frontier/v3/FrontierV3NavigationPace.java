package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace;

/** Converts a requested walking rate to native MoveControl units; never drives or replaces a path. */
final class FrontierV3NavigationPace {
    private FrontierV3NavigationPace() { }

    static double speedModifier(TravelPace pace, double movementAttribute, double friction, double maximum) {
        if (!Double.isFinite(movementAttribute) || movementAttribute < 0
                || !Double.isFinite(friction) || friction <= 0 || friction * 0.91 >= 1
                || !Double.isFinite(maximum) || maximum <= 0)
            throw new IllegalArgumentException("invalid native walking calibration");
        if (movementAttribute == 0) return 0;
        // Minecraft 1.21 Mob.setSpeed also sets forward input. Ground acceleration
        // therefore uses speed squared, 0.98 input damping and native block friction.
        // This is a flat-ground calibration; jumps/collisions retain ordinary physics.
        double acceleration = 0.21600002 / (friction * friction * friction);
        double speed = Math.sqrt(pace.blocksPerTick() * (1 - friction * 0.91) / (0.98 * acceleration));
        return Math.min(maximum, speed / movementAttribute);
    }
}
