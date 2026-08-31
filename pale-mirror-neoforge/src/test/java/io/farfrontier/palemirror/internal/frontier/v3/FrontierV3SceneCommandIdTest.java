package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ensures opaque durable scene IDs cannot overflow the bounded command-identifier namespace. */
class FrontierV3SceneCommandIdTest {
    @Test
    void physicalCommandIdentityStaysBoundedForTheSerializedRevision() {
        var current = FrontierV3CommandIds.scene("explosion-prepare", 249L);
        var cargoRelease = FrontierV3CommandIds.physical("cargo-carrier-release", 273L);

        assertEquals("executor:explosion-prepare-r249", current.value());
        assertEquals("executor:cargo-carrier-release-r273", cargoRelease.value());
        assertTrue(cargoRelease.value().length() < 128);
        assertNotEquals(current, FrontierV3CommandIds.scene("explosion-prepare", 250L));
        assertNotEquals(current, FrontierV3CommandIds.scene("scene-strike-confirm", 249L));
    }
}
