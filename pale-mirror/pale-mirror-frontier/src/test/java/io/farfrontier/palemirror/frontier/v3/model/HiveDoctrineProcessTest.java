package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HiveDoctrineProcessTest {


    @Test
    void ownedBiomassConsolidatesAndReducerRejectsForeignOrBackdatedDoctrine() {
        FrontierWorldState state = initial("frontier:hive-doctrine-rejection", 802L);
        SubjectId hive = state.bootstrap().hive().id();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
        BlockPosition position = FrontierTestPositions.supportOf(state.actorLocations().get(scout.id()));
        HiveTerritoryKnowledge.Belief belief = new HiveTerritoryKnowledge.Belief(InfectionCell.at(position),
                new FixedRatio(new FixedScalar(500_000L)), scout.id(), position, 100L);
        state = state.withStrategicPlans(state.strategicPlans().withHiveTerritoryKnowledge(HiveTerritoryKnowledge.empty().observe(belief)));

        assertEquals(HiveDoctrine.CONSOLIDATE, HiveDoctrineProcess.select(state, 100L).doctrine());
        FrontierWorldState selected = HiveDoctrineProcess.reduce(state, hive, new HiveDoctrineSelected(new HiveDoctrineState(HiveDoctrine.EXPAND, 100L)));
        assertThrows(IllegalArgumentException.class, () -> HiveDoctrineProcess.reduce(selected, new SubjectId("settlement:1"),
                new HiveDoctrineSelected(new HiveDoctrineState(HiveDoctrine.INTERDICT, 101L))));
        assertThrows(IllegalArgumentException.class, () -> HiveDoctrineProcess.reduce(selected, hive,
                new HiveDoctrineSelected(new HiveDoctrineState(HiveDoctrine.CONSOLIDATE, 99L))));
    }

    private static FrontierWorldState initial(String world, long seed) {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(world), seed));
    }
}
