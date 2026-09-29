package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResidentLifeScheduleRetirementTest {
    @Test void deathRetiresOnlyTheExactResidentsNeedAndActivityTimers() {
        FrontierWorldState before = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-life-death-timers"), 91L));
        SubjectId dead = before.bootstrap().settlements().getFirst().residents().getFirst().id();
        SubjectId alive = before.bootstrap().settlements().getFirst().residents().get(1).id();
        var actors = new LinkedHashMap<>(before.actorLocations());
        ActorLocation prior = actors.get(dead);
        actors.put(dead, prior.deadAt(prior.body()));
        FrontierWorldState after = before.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        var deadNeed = ResidentNeedProcess.firstReviewAfter(dead, 0L, before.bootstrap().ruleset().residentLife());
        var deadActivity = ResidentActivityProcess.review(dead, 1L);
        var aliveNeed = ResidentNeedProcess.firstReviewAfter(alive, 0L, before.bootstrap().ruleset().residentLife());
        assertEquals(List.of(deadNeed, deadActivity), ResidentLifeScheduleRetirement.afterDeath(before, after,
                () -> List.of(deadNeed, aliveNeed, deadActivity)));
        assertEquals(List.of(), ResidentLifeScheduleRetirement.afterDeath(before, before,
                () -> { throw new AssertionError("unchanged state must not enumerate schedules"); }));
    }
}
