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
        assertEquals(ResidentNeedProcess.review(first, 15_000L),
                ((ScheduleEffect.Rescheduled) planned.get(1).payload()).replacement());
        FrontierWorldState changed = ResidentMetabolismProcess.reduce(initial, first, faster);
        assertEquals(6_000_000L, changed.humanPopulation().nutrition(first).fractionalProgress());
        assertEquals(6_000L, changed.humanPopulation().nutrition(first).lastEvaluatedTick());
        assertEquals(base, changed.humanPopulation().resident(second).characteristics());

        SubjectId source = new SubjectId("condition:resident-metabolism-test");
        ResidentCharacteristics withModifier = faster.next().withModifier(
                new ResidentCharacteristics.MetabolismModifier(source, -500));
        ResidentMetabolismChanged modifier = new ResidentMetabolismChanged(first, 10_000L, faster.next(), withModifier);
        FrontierWorldState modified = ResidentMetabolismProcess.reduce(changed, first, modifier);
        assertEquals(14_000_000L, modified.humanPopulation().nutrition(first).fractionalProgress());
        assertEquals(1_500, modified.humanPopulation().resident(first).characteristics().effectiveMetabolismPermille(10_000L));
        ResidentMetabolismChanged removed = new ResidentMetabolismChanged(first, 11_000L,
                withModifier, withModifier.withoutModifier(source));
        FrontierWorldState restored = ResidentMetabolismProcess.reduce(modified, first, removed);
        assertEquals(15_500_000L, restored.humanPopulation().nutrition(first).fractionalProgress());
        assertEquals(15_250L, restored.humanPopulation().nutrition(first)
                .nextThresholdTick(initial.bootstrap().ruleset().residentLife(), 2_000));
        FrontierWorldState reloaded = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(restored));
        assertEquals(restored.humanPopulation().resident(first).characteristics(),
                reloaded.humanPopulation().resident(first).characteristics());
        assertEquals(restored.humanPopulation().nutrition(first), reloaded.humanPopulation().nutrition(first));
        assertEquals(0, reloaded.humanPopulation().nutrition(second).hungerDeficit());
        assertThrows(IllegalArgumentException.class, () -> ResidentMetabolismProcess.reduce(restored, first, removed));

        ActorLocation body = restored.actorLocations().get(first);
        var actors = new java.util.LinkedHashMap<>(restored.actorLocations());
        actors.put(first, body.deadAt(body.body()));
        FrontierWorldState dead = restored.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        assertThrows(IllegalArgumentException.class, () -> ResidentMetabolismProcess.plan(dead,
                new ResidentMetabolismChanged(first, 12_000L, removed.next(), base)));
    }
}
