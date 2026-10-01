package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/** Captures a pedestrian's actual supporting block, not the floor of its fractional feet Y. */
final class FrontierV3SupportedBodyCapture {
    private FrontierV3SupportedBodyCapture() { }

    static Optional<BodyPosition> observe(ServerLevel level, Mob actor) {
        return observe(level, actor, false);
    }

    static Optional<BodyPosition> observeDeparting(ServerLevel level, Mob actor) {
        return observe(level, actor, true);
    }

    private static Optional<BodyPosition> observe(ServerLevel level, Mob actor, boolean departing) {
        if (actor.level() != level || actor.getHealth() <= 0.0F
                || !departing && actor.isRemoved())
            return Optional.empty();
        return FrontierV3BodyObservation.capture(actor).supportedBody();
    }
}
