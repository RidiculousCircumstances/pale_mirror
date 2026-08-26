package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.farfrontier.palemirror.frontier.FrontierProfile;
import io.farfrontier.palemirror.frontier.FrontierSnapshots;
import io.farfrontier.palemirror.frontier.FrontierWorldFactory;
import org.junit.jupiter.api.Test;

class FrontierProjectionMapperTest {
    @Test
    void mapsOnlyImmutableFrontierViewsAcrossTheVisualBoundary() {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 5L);
        var projection = FrontierProjectionMapper.map(FrontierSnapshots.snapshot(state));

        assertEquals("graybox-10", projection.profileId());
        assertEquals(10, projection.settlements().size());
        assertEquals(state.residents().size(), projection.residents().size());
        assertEquals(state.facilities().size(), projection.facilities().size());
        assertEquals(state.operations().size(), projection.operations().size());
        assertEquals(state.hives().size(), projection.hives().size());
        assertEquals(state.hiveOrgans().size(), projection.hiveOrgans().size());
        assertEquals(0, projection.morphogenesisProjects().size());
        assertEquals(0, projection.harvesterRuns().size());
        assertEquals(state.bioforms().size(), projection.bioforms().size());
        assertEquals(0, projection.fieldOperations().size());
    }
}
