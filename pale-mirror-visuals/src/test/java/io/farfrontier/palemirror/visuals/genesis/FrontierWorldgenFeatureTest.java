package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrontierWorldgenFeatureTest {
    @Test void railEarthworkUsesOnlyTheCurrentColumnSurface() {
        assertEquals(RailEarthwork.CUT, RailEarthwork.resolve(82, 70));
        assertEquals(RailEarthwork.GROUND, RailEarthwork.resolve(68, 70));
        assertEquals(RailEarthwork.BRIDGE, RailEarthwork.resolve(60, 70));
    }
}
