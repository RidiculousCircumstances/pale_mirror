package io.farfrontier.palemirror.internal.integration.distanthorizons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class TravelLoadControllerTest {
    @Test
    void mediumSpeedResetsRecoveryWithoutLeavingFastMode() {
        TravelLoadController controller = new TravelLoadController(12.0D, 6.0D, 40);

        assertEquals(TravelLoadController.Mode.FAST_TRAVEL, controller.observe(20.0D, false, 20));
        assertEquals(TravelLoadController.Mode.FAST_TRAVEL, controller.observe(5.0D, false, 20));
        assertEquals(TravelLoadController.Mode.FAST_TRAVEL, controller.observe(8.0D, false, 20));
        assertEquals(TravelLoadController.Mode.FAST_TRAVEL, controller.observe(5.0D, false, 20));
        assertEquals(TravelLoadController.Mode.NORMAL, controller.observe(5.0D, false, 20));
    }
}
