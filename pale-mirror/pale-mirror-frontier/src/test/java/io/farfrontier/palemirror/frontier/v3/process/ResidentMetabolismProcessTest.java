package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResidentMetabolismProcessTest {
    @Test void freshSettlementRetainsDistinctBoundedPersonalHungerRates() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-personal-baselines"), 419L));
        FrontierRuleset.ResidentLife rules = state.bootstrap().ruleset().residentLife();
        var rates = state.bootstrap().settlements().getFirst().residents().stream()
                .map(resident -> state.humanPopulation().resident(resident.id()))
                .map(profile -> {
                    assertEquals(ResidentCharacteristics.initial(rules, profile.id()), profile.characteristics());
                    return profile.characteristics().baseMetabolismPermille();
                }).toList();
        assertTrue(rates.stream().distinct().count() > 1, "neighbors must not all become hungry on one tick");
        assertTrue(rates.stream().allMatch(rate -> Math.abs(rate - rules.metabolismDefaultPermille())
                <= rules.metabolismBaselineSpreadPermille()));
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(state.humanPopulation().residents(), restored.humanPopulation().residents());
    }

    @Test void oldRateFractionAndExactNeedWakeSurviveChangeModifierRemovalAndRestart() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-metabolism"), 419L));
        SubjectId first = initial.bootstrap().settlements().getFirst().residents().get(0).id();
        SubjectId second = initial.bootstrap().settlements().getFirst().residents().get(1).id();
        ResidentCharacteristics base = initial.humanPopulation().resident(first).characteristics();
        ResidentMetabolismChanged faster = new ResidentMetabolismChanged(first, 6_000L, base,
                base.withBaseMetabolism(2_000));
        assertEquals(faster, FrontierWorldRuntimeDefinition.payloadCodecs().decode(faster.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(faster)));
        var planned = ResidentMetabolismProcess.plan(initial, faster);
        assertEquals(3, planned.size());
        long oldProgress = 6_000L * base.effectiveMetabolismPermille(0L);
        long unit = initial.bootstrap().ruleset().residentLife().satietyUnitTicks() * 1_000L;
        long boundaryUnits = initial.bootstrap().ruleset().residentLife().satietyCapacityUnits()
                - initial.bootstrap().ruleset().residentLife().eatBelowUnits() + 1L;
        long expectedFirstDue = 6_000L + Math.ceilDiv(boundaryUnits * unit - oldProgress, 2_000L);
        assertEquals(ResidentNeedProcess.review(first, expectedFirstDue),
                ((ScheduleEffect.Rescheduled) planned.get(1).payload()).replacement());
        FrontierWorldState changed = ResidentMetabolismProcess.reduce(initial, first, faster);
        assertEquals(oldProgress % unit, changed.humanPopulation().nutrition(first).fractionalProgress());
        assertEquals(6_000L, changed.humanPopulation().nutrition(first).lastEvaluatedTick());
        assertEquals(initial.humanPopulation().resident(second).characteristics(),
                changed.humanPopulation().resident(second).characteristics());

        SubjectId source = new SubjectId("condition:resident-metabolism-test");
        ResidentCharacteristics withModifier = faster.next().withModifier(
                new ResidentCharacteristics.MetabolismModifier(source, -500));
        ResidentMetabolismChanged modifier = new ResidentMetabolismChanged(first, 10_000L, faster.next(), withModifier);
        FrontierWorldState modified = ResidentMetabolismProcess.reduce(changed, first, modifier);
        assertEquals((oldProgress + 8_000_000L) % unit, modified.humanPopulation().nutrition(first).fractionalProgress());
        assertEquals(1_500, modified.humanPopulation().resident(first).characteristics().effectiveMetabolismPermille(10_000L));
        ResidentMetabolismChanged removed = new ResidentMetabolismChanged(first, 11_000L,
                withModifier, withModifier.withoutModifier(source));
        FrontierWorldState restored = ResidentMetabolismProcess.reduce(modified, first, removed);
        assertEquals((oldProgress + 9_500_000L) % unit, restored.humanPopulation().nutrition(first).fractionalProgress());
        assertEquals(11_000L + Math.ceilDiv(boundaryUnits * unit - (oldProgress + 9_500_000L), 2_000L),
                restored.humanPopulation().nutrition(first)
                        .nextThresholdTick(initial.bootstrap().ruleset().residentLife(), 2_000));
        FrontierWorldState reloaded = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(restored));
        assertEquals(restored.humanPopulation().resident(first).characteristics(),
                reloaded.humanPopulation().resident(first).characteristics());
        assertEquals(restored.humanPopulation().nutrition(first), reloaded.humanPopulation().nutrition(first));
        assertEquals(initial.bootstrap().ruleset().residentLife().satietyCapacityUnits(),
                reloaded.humanPopulation().nutrition(second).satietyUnits());
        assertThrows(IllegalArgumentException.class, () -> ResidentMetabolismProcess.reduce(restored, first, removed));

        ActorLocation body = restored.actorLocations().get(first);
        var actors = new java.util.LinkedHashMap<>(restored.actorLocations());
        actors.put(first, body.deadAt(body.body()));
        FrontierWorldState dead = restored.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        assertThrows(IllegalArgumentException.class, () -> ResidentMetabolismProcess.plan(dead,
                new ResidentMetabolismChanged(first, 12_000L, removed.next(), base)));
    }
}
