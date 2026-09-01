package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveSettlementPerceptionProcessTest {
    @Test
    void scoutRetainsOnlyASettlementItHasActuallyReachedAndTheFactSurvivesItsLaterPatrol() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-settlement-local"), 451L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        state = state.withActorBody(scout.id(), FrontierTestPositions.bodyAboveSupport(settlement.anchor()));

        HiveSettlementPerceptionProcess.Refresh refresh = HiveSettlementPerceptionProcess.refresh(state, 100L);

        assertTrue(refresh.knowledge().entries().containsKey(settlement.id()));
        HiveSettlementObserved observed = refresh.events().stream().map(event -> event.payload()).filter(HiveSettlementObserved.class::isInstance)
                .map(HiveSettlementObserved.class::cast).findFirst().orElseThrow();
        state = HiveSettlementPerceptionProcess.reduce(state, state.bootstrap().hive().id(), observed);
        state = state.withActorBody(scout.id(), FrontierTestPositions.bodyAboveSupport(state.bootstrap().hive().seedNests().getFirst().anchor()));

        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(state.strategicPlans().hiveSettlementKnowledge(), restored.strategicPlans().hiveSettlementKnowledge());
        assertEquals(observed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(observed.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(observed)));
    }

    @Test
    void remoteOrForeignSettlementObservationIsRejected() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-settlement-forged"), 452L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        HiveSettlementObserved remote = new HiveSettlementObserved(new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L));

        assertThrows(IllegalArgumentException.class, () -> HiveSettlementPerceptionProcess.reduce(state, state.bootstrap().hive().id(), remote));
        HiveSettlementObserved foreign = new HiveSettlementObserved(new HiveSettlementKnowledge.Sighting(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("settlement:missing"),
                scout.id(), settlement.anchor(), 100L));
        assertThrows(IllegalArgumentException.class, () -> HiveSettlementPerceptionProcess.reduce(state, state.bootstrap().hive().id(), foreign));
    }
}
