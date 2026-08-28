package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierWorldRuntimeDefinitionTest {
    @Test
    void concreteProfileStartsWithTheRequiredExactWorldPopulation() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:definition"), 91L));

        FrontierWorldProjection projection = engine.projection(ProjectionQuery.summary());

        assertEquals(12, projection.settlementCount());
        assertEquals(48, projection.bioformCount());
        org.junit.jupiter.api.Assertions.assertTrue(projection.residentCount() >= 240 && projection.residentCount() <= 480);
        assertEquals(0, projection.itemStackCount());
        assertEquals(2, projection.infectedCellCount());
    }

    @Test
    void infectionPulseIsPersistedDeterministicScheduledWorldWork() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:pulse"), 91L));
        engine.advanceTo(new SimInstant(100L), new WorkBudget(8, 64));

        FrontierWorldProjection projection = engine.projection(ProjectionQuery.summary());
        assertEquals(3, projection.infectedCellCount());
        assertEquals(1L, projection.revision().value());
    }
}
