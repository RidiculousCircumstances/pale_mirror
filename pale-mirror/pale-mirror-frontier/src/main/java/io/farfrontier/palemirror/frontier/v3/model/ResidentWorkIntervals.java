package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Common personal/day boundary for admitted labour; a family still proves station and execution eligibility. */
public final class ResidentWorkIntervals {
    private ResidentWorkIntervals() { }
    public static long permittedUntil(FrontierWorldState state, SubjectId actorId, long tick) {
        if (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, actorId, tick))
            throw new IllegalArgumentException("resident has no current permitted work interval");
        var resident = state.humanPopulation().resident(actorId);
        long until = state.humanPopulation().schedule(resident.settlementId()).nextWindowBoundaryAfter(tick);
        var nutrition = state.humanPopulation().nutrition(resident.id()).accrueThrough(tick,
                state.bootstrap().ruleset().residentLife(), resident.characteristics().effectiveMetabolismPermille(tick));
        long threshold = nutrition.nextThresholdTick(state.bootstrap().ruleset().residentLife(),
                resident.characteristics().effectiveMetabolismPermille(tick));
        return threshold > tick ? Math.min(until, threshold) : until;
    }
}
