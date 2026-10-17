package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransportFleetPresenceTest {
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:fleet-presence"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1")));
    }
    @Test void settlementReviewAdmitsPassiveAuthorityAndRecoveryKeepsItWithoutMovingTheAnimal() {
        var before = initial();
        var asset = before.transportFleet().assets().values().iterator().next();
        var review = StrategicObjectiveProcess.review(asset.homeSettlementId(), 1, 1);
        var events = FrontierWorldProcessCatalog.planScheduled(FrontierWorldRuntimeDefinition.processRegistry(),
                before, review, new SimInstant(20));
        var admitted = events.stream().map(ProposedEvent::payload).filter(ActorPresenceStarted.class::isInstance)
                .map(ActorPresenceStarted.class::cast).filter(value -> value.execution().actorId().equals(asset.actorId())).toList();
        assertEquals(1, admitted.size()); assertEquals(20, admitted.getFirst().atTick());
        var selected = TransportFleetPresence.select(before, asset.homeSettlementId(), 20);
        var after = selected.state();
        assertSame(before.actorLocations(), after.actorLocations());
        assertSame(before.inventory(), after.inventory());
        assertSame(before.transportFleet(), after.transportFleet());
        assertTrue(ActorSpatialCourtesy.assess(after, admitted.getFirst().execution()).ready());
        var codec = new FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(after));
        assertEquals(after.actorExecutions(), recovered.actorExecutions());
        assertTrue(TransportFleetPresence.select(recovered, asset.homeSettlementId(), 21).events().isEmpty());
        assertTrue(ActorExecutionComposition.LIFECYCLE.mayAdmitVacant(recovered, asset.actorId()),
                "passive presence must not reserve the animal against the next mission");
        var retired = recovered.withChanges(FrontierWorldStateUpdate.begin().actorExecutions(
                recovered.actorExecutions().finish(admitted.getFirst().execution())));
        var next = TransportFleetPresence.select(retired, asset.homeSettlementId(), 22).state();
        assertEquals(admitted.getFirst().execution().generation() + 1, next.actorExecutions().generation(asset.actorId()));
        assertThrows(IllegalArgumentException.class, () -> next.actorExecutions().requireCurrent(admitted.getFirst().execution()));
    }
    @Test void deadAssetIsNotReactivated() {
        var state = initial(); var asset = state.transportFleet().assets().values().iterator().next();
        var actors = new LinkedHashMap<>(state.actorLocations()); var body = actors.get(asset.actorId());
        actors.put(asset.actorId(), new ActorLocation(body.body(), ActorCondition.dead(), body.kind(), body.navigationProfile()));
        var dead = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        assertTrue(TransportFleetPresence.select(dead, asset.homeSettlementId(), 20).events().isEmpty());
    }
}
