package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
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
        assertEquals(0, projection.infectedCellCount());
    }
}
