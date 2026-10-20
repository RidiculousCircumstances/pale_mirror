package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentMealGroundingTest {
    @Test void airborneColdMealLandsThenConsumesWithoutLosingItsPortionOrAuthority() {
        var fixture = FrontierV3FixtureCatalog.configuration("resident-meal-after-cold-take",
                new WorldId("frontier:meal-gravity"), 41L);
        var id = new SubjectId("resident:6-1");
        var state = fixture.initialState();
        var meal = state.humanPopulation().meals().get(id);
        long now = fixture.initialInstant().ticks() + 1;
        var floor = meal.clearingSurface();
        state = state.withActorBody(id, new BodyPosition(floor.x(), floor.y() + 2, floor.z()));
        var events = ResidentMealProcess.planProgress(state, ResidentMealProcess.progress(meal, now));
        var event = assertInstanceOf(ResidentMealGrounded.class, events.getFirst().payload());
        assertEquals(floor, event.landing());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, codecs.decode(event.type(), codecs.encode(event)));
        DiagnosticProducerContract.requireAdmitted(event);
        var prior = state;
        var hot = ModeledActorBodyFacts.present(prior, id);
        assertTrue(ResidentMealGrounding.plan(hot, meal, event.atTick()).isEmpty(),
                "a physical custodian retains gravity; COLD cannot move its body");
        assertThrows(IllegalArgumentException.class, () -> ResidentMealGrounding.reduce(hot, id, event));
        assertThrows(IllegalArgumentException.class, () -> ResidentMealGrounding.reduce(prior, id,
                new ResidentMealGrounded(event.execution(), event.atTick(), event.from(),
                        SurfaceAnchor.at(floor.x(), floor.y() - 1, floor.z()))));
        state = FrontierWorldRuntimeDefinition.reduce(state, new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                new EventId("event:meal-grounded"), new TransactionId("transaction:meal-grounded"),
                state.bootstrap().worldId(), new Revision(1), new SimInstant(event.atTick()), id,
                CauseChain.root(new CommandId("test:meal-grounded")), event));
        assertEquals(prior.inventory(), state.inventory());
        assertEquals(prior.humanPopulation(), state.humanPopulation(), "gravity cannot count as food or access completion");
        assertEquals(floor.standingBody(), state.actorLocations().get(id).body());
        assertTrue(ResidentMealGrounding.plan(state, meal, now + 1).isEmpty());
        var codec = new FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        for (int turn = 0; state.humanPopulation().meals().containsKey(id) && turn < 16; turn++) {
            var current = state.humanPopulation().meals().get(id);
            now = current.coldTravel().map(route -> route.arrivalTick()).orElse(now + 1);
            state = ResidentMealProcess.reduceColdStep(state, id, ResidentMealProcess.planColdStep(state, id, now).orElseThrow());
        }
        assertFalse(state.humanPopulation().meals().containsKey(id));
        assertFalse(state.inventory().fungibleResources().claims().containsKey(meal.claimId()));
    }
}
