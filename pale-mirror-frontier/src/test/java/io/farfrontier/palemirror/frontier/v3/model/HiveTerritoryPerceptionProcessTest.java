package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveTerritoryPerceptionProcessTest {
    @Test
    void livingScoutPersistsOnlyItsLocallyObservedInfection() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-territory-local"), 401L));
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        InfectionCell local = InfectionCell.at(state.actorLocations().get(scout.id()).position());
        InfectionCell remote = new InfectionCell(local.x() + (local.x() > 0 ? -20 : 20), local.z() + (local.z() > 0 ? -20 : 20));
        state = state.withInfection(local, new FixedRatio(new FixedScalar(500_000L)))
                .withInfection(remote, new FixedRatio(new FixedScalar(750_000L)));

        HiveTerritoryPerceptionProcess.Refresh refresh = HiveTerritoryPerceptionProcess.refresh(state, 100L);

        assertTrue(refresh.knowledge().entries().containsKey(local));
        assertTrue(!refresh.knowledge().entries().containsKey(remote));
        assertTrue(refresh.events().stream().map(event -> event.payload()).filter(HiveTerritoryObserved.class::isInstance)
                .map(HiveTerritoryObserved.class::cast).anyMatch(observed -> observed.belief().cell().equals(local)));
    }

    @Test
    void forgedRemoteObservationIsRejectedEvenWhenTheCellExistsCanonically() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-territory-forged"), 402L));
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        InfectionCell remote = InfectionCell.at(state.actorLocations().get(scout.id()).position()).equals(new InfectionCell(90, 90))
                ? new InfectionCell(-90, -90) : new InfectionCell(90, 90);
        state = state.withInfection(remote, new FixedRatio(new FixedScalar(500_000L)));
        HiveTerritoryObserved forged = new HiveTerritoryObserved(new HiveTerritoryKnowledge.Belief(remote,
                state.infection().get(remote), scout.id(), state.actorLocations().get(scout.id()).position(), 100L));

        FrontierWorldState finalState = state;
        assertThrows(IllegalArgumentException.class, () -> HiveTerritoryPerceptionProcess.reduce(finalState, finalState.bootstrap().hive().id(), forged));
    }

    @Test
    void beliefSurvivesCodecButExpiresFromStrategicTargeting() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-territory-codec"), 403L));
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        InfectionCell local = InfectionCell.at(state.actorLocations().get(scout.id()).position());
        state = state.withInfection(local, new FixedRatio(new FixedScalar(500_000L)));
        HiveTerritoryObserved observed = new HiveTerritoryObserved(new HiveTerritoryKnowledge.Belief(local, state.infection().get(local), scout.id(),
                state.actorLocations().get(scout.id()).position(), 10L));
        state = HiveTerritoryPerceptionProcess.reduce(state, state.bootstrap().hive().id(), observed);

        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));

        assertEquals(state.strategicPlans().hiveTerritoryKnowledge(), restored.strategicPlans().hiveTerritoryKnowledge());
        assertEquals(observed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(observed.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(observed)));
        assertTrue(restored.strategicPlans().hiveTerritoryKnowledge().freshInfection(restored.bootstrap().ruleset(), 10L).containsKey(local));
        assertTrue(restored.strategicPlans().hiveTerritoryKnowledge().freshInfection(restored.bootstrap().ruleset(),
                10L + restored.bootstrap().ruleset().cadence().hiveTerritoryKnowledgeMaxAge() + 1L).isEmpty());
    }
}
