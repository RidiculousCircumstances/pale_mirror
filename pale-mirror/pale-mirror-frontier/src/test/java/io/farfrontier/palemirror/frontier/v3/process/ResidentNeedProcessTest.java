package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResidentNeedProcessTest {
    @Test void deadResidentsConsumeTheirOwnPendingNeedReviewWithoutRescheduling() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-need-death"), 419L));
        SubjectId resident = initial.bootstrap().settlements().getFirst().residents().getFirst().id();
        var actors = new java.util.LinkedHashMap<>(initial.actorLocations());
        ActorLocation body = actors.get(resident);
        actors.put(resident, body.deadAt(body.body()));
        FrontierWorldState dead = initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        var review = ResidentNeedProcess.review(resident, 24_000L);
        var events = ResidentNeedProcess.plan(dead, review);
        assertEquals(1, events.size());
        assertEquals(new ScheduleEffect.Consumed(review.id()), events.getFirst().payload());
    }

    @Test void oneResidentNeedClockIntegratesAndRequeuesWithoutPopulationScan() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-need-clock"), 419L));
        SubjectId resident = initial.bootstrap().settlements().getFirst().residents().getFirst().id();
        ResidentProfile profile = initial.humanPopulation().resident(resident);
        var rules = initial.bootstrap().ruleset().residentLife();
        int rate = profile.characteristics().effectiveMetabolismPermille(0L);
        var due = ResidentNeedProcess.review(resident,
                initial.humanPopulation().nutrition(resident).nextThresholdTick(rules, rate));
        assertEquals(10, due.priority());
        assertEquals(due, ResidentNeedProcess.firstReviewAfter(profile, 0L, rules));
        assertEquals(ResidentNeedProcess.review(resident,
                        ResidentNutrition.nourishedAtTick(24_100L).nextThresholdTick(rules, rate)),
                ResidentNeedProcess.firstReviewAfter(profile, 24_100L, rules));
        var events = ResidentNeedProcess.plan(initial, due);
        assertEquals(2, events.size());
        ResidentNeedIntegrated integrated = assertInstanceOf(ResidentNeedIntegrated.class, events.getFirst().payload());
        assertEquals(integrated, FrontierWorldRuntimeDefinition.payloadCodecs().decode(integrated.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(integrated)));
        FrontierWorldState hungry = ResidentNeedProcess.reduce(initial, resident, integrated);
        assertEquals(rules.eatBelowUnits() - 1, hungry.humanPopulation().nutrition(resident).satietyUnits());
        assertEquals(due.dueAt().ticks() / 24_000L,
                hungry.humanPopulation().nutrition(resident).lastIntegratedDay());
        assertEquals(ResidentNeedProcess.review(resident,
                        hungry.humanPopulation().nutrition(resident).nextThresholdTick(rules, rate)),
                ((ScheduleEffect.Created) events.get(1).payload()).action());
        assertThrows(IllegalArgumentException.class, () -> ResidentNeedProcess.reduce(hungry, resident, integrated));
        assertThrows(IllegalArgumentException.class, () -> ResidentNeedProcess.plan(hungry, due));
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(hungry));
        assertEquals(hungry.humanPopulation().nutrition(resident), recovered.humanPopulation().nutrition(resident));
    }

    @Test void confirmedBreadReplacesAnOverdueThresholdBeforeItsQueuedReviewCanRun() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-need-meal-before-review"), 419L));
        SubjectId resident = initial.bootstrap().settlements().getFirst().residents().getFirst().id();
        var rules = initial.bootstrap().ruleset().residentLife();
        int rate = initial.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(24_001L);
        long oldDue = initial.humanPopulation().nutrition(resident).nextThresholdTick(rules, rate);
        var replacement = assertInstanceOf(ScheduleEffect.Rescheduled.class,
                ResidentNeedProcess.requeueAfterConfirmedBread(initial, resident, 24_001L).payload());
        assertEquals(ResidentNeedProcess.review(resident, oldDue).id(), replacement.scheduleId());
        long nextDue = initial.humanPopulation().nutrition(resident).consumeBreadAt(24_001L, rules, rate)
                .nextThresholdTick(rules, rate);
        assertEquals(ResidentNeedProcess.review(resident, nextDue), replacement.replacement());
        FrontierWorldState afterMeal = initial.withHumanPopulation(initial.humanPopulation()
                .consumeResidentBread(resident, 24_001L, initial.bootstrap().ruleset().residentLife()));
        assertThrows(IllegalArgumentException.class, () -> ResidentNeedProcess.plan(afterMeal,
                ResidentNeedProcess.review(resident, oldDue)), "the old due action must be retired atomically");
        assertEquals(2, ResidentNeedProcess.plan(afterMeal, replacement.replacement()).size());
    }
}
