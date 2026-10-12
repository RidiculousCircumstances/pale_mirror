package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Ephemeral clearance for one observed obstruction, never a service turn or canonical job. */
final class FrontierV3CourtesyPassage {
    static final long MAX_UNOBSERVED_TICKS = 100; // Lost physical evidence bound, never a travel duration.
    enum Status { WAITING, CLEARED, REQUESTER_RETIRED, COMMAND_CHANGED, EXPIRED }
    record Hold(OptionalLong unobservedSince) {
        Hold { Objects.requireNonNull(unobservedSince); }
        static Hold empty() { return new Hold(OptionalLong.empty()); }
    }
    record Observation(Status status, Hold hold) { }

    record Claim(FrontierV3PedestrianYieldRequests.Request request, FrontierV3PedestrianYieldRequests.Blocker obstruction) {
        Claim {
            Objects.requireNonNull(request); Objects.requireNonNull(obstruction);
            if (!request.blockers().contains(obstruction)) throw new IllegalArgumentException("passage claim has a foreign observed blocker");
        }
        Observation observe(ServerLevel level, Hold hold) {
            var requester = request.source().get();
            boolean current = requester != null && requester.level() == level && requester.isAlive() && !requester.isRemoved()
                    && request.permission().current(requester);
            boolean sameCommand = current && FrontierV3MinecraftGoalNavigation.hasDemand(requester, request.demand());
            return assess(hold, current, sameCommand, level.getGameTime(),
                    sameCommand ? FrontierV3PedestrianTraffic.needsPassage(requester,
                            FrontierV3MinecraftGoalNavigation.acceptedPath(requester, request.demand()), obstruction.observedBounds()) : Optional.empty());
        }
    }

    private FrontierV3CourtesyPassage() { }

    static Observation assess(Hold previous, boolean requesterCurrent, boolean sameCommand, long atTick, Optional<Boolean> needed) {
        Objects.requireNonNull(previous); Objects.requireNonNull(needed);
        if (!requesterCurrent) return new Observation(Status.REQUESTER_RETIRED, Hold.empty());
        if (!sameCommand) return new Observation(Status.COMMAND_CHANGED, Hold.empty());
        // Actual accepted motion has the navigator's progress deadline, not a second courtesy clock.
        if (needed.isPresent()) return new Observation(needed.orElseThrow() ? Status.WAITING : Status.CLEARED, Hold.empty());
        long since = previous.unobservedSince().orElse(atTick);
        long elapsed = atTick - since;
        return new Observation(elapsed < 0 || elapsed >= MAX_UNOBSERVED_TICKS ? Status.EXPIRED : Status.WAITING,
                new Hold(OptionalLong.of(since)));
    }
}
