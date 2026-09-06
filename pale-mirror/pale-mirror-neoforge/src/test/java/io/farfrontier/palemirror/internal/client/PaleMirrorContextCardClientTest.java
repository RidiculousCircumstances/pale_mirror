package io.farfrontier.palemirror.internal.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaleMirrorContextCardClientTest {
    @Test
    void cardNeverConsumesMoreThanItsResponsiveSafeZone() {
        assertEquals(260, PaleMirrorContextCardClient.layout(1920, 8_000).width());
        assertEquals(163, PaleMirrorContextCardClient.layout(480, 8_000).width());
        assertEquals(76, PaleMirrorContextCardClient.layout(100, 8_000).width());
    }

    @Test
    void cardKeepsAUsableMinimumWhenTheScreenAllowsIt() {
        int width = PaleMirrorContextCardClient.layout(640, 10).width();
        assertEquals(154, width);
        assertTrue(width >= 154);
    }
}
