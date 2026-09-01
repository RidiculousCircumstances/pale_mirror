package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HiveDoctrineProcessTest {
    @Test
    void freshScoutFactSelectsInterdictionButStaleFactCannotKeepTheHiveOmniscient() {
        FrontierWorldState state = initial("frontier:hive-doctrine-freshness", 801L);
        SubjectId hive = state.bootstrap().hive().id();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        BlockPosition scoutPosition = FrontierTestPositions.supportOf(state.actorLocations().get(scout.id()));
        HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(new SubjectId("operation:seen"), scout.id(), scoutPosition, 100L);
        HiveTerritoryKnowledge.Belief belief = new HiveTerritoryKnowledge.Belief(InfectionCell.at(scoutPosition),
                new FixedRatio(new FixedScalar(500_000L)), scout.id(), scoutPosition, 100L);
        state = state.withInventory(state.inventory().withoutItem(new SubjectId("item:bootstrap-hive-biomass")))
                .withStrategicPlans(state.strategicPlans().withHiveOperationKnowledge(HiveOperationKnowledge.empty().observe(sighting))
                        .withHiveTerritoryKnowledge(HiveTerritoryKnowledge.empty().observe(belief)));

        HiveDoctrineState interdict = HiveDoctrineProcess.select(state, 100L, true);
        assertEquals(HiveDoctrine.INTERDICT, interdict.doctrine());

        HiveDoctrineState expand = HiveDoctrineProcess.select(state, 100L + state.bootstrap().ruleset().cadence().hivePerceptionRefreshInterval() + 1L, true);
        assertEquals(HiveDoctrine.EXPAND, expand.doctrine(), "an expired scout fact must not retain an interception posture");
        FrontierWorldState reduced = HiveDoctrineProcess.reduce(state, hive, new HiveDoctrineSelected(expand));
        assertEquals(expand, reduced.strategicPlans().hiveDoctrine());
        assertEquals(reduced, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reduced)));
    }

    @Test
    void ownedBiomassConsolidatesAndReducerRejectsForeignOrBackdatedDoctrine() {
        FrontierWorldState state = initial("frontier:hive-doctrine-rejection", 802L);
        SubjectId hive = state.bootstrap().hive().id();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        BlockPosition position = FrontierTestPositions.supportOf(state.actorLocations().get(scout.id()));
        HiveTerritoryKnowledge.Belief belief = new HiveTerritoryKnowledge.Belief(InfectionCell.at(position),
                new FixedRatio(new FixedScalar(500_000L)), scout.id(), position, 100L);
        state = state.withStrategicPlans(state.strategicPlans().withHiveTerritoryKnowledge(HiveTerritoryKnowledge.empty().observe(belief)));

        assertEquals(HiveDoctrine.CONSOLIDATE, HiveDoctrineProcess.select(state, 100L, true).doctrine());
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
