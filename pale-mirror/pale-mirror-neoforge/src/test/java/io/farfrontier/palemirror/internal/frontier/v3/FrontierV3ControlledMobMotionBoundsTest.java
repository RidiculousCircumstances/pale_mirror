package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ControlledMobMotionBoundsTest {
    private static final WorldBounds BOUNDS = new WorldBounds(-512, -512, 1024, 1024);

    @Test
    void hotAssaultActuatorCannotHandBackAWorldEscapePosition() {
        assertTrue(FrontierV3ControlledMobMotion.insideWorldBounds(new Vec3(-511.01D, 65.0D, -511.01D), BOUNDS));
        assertTrue(FrontierV3ControlledMobMotion.insideWorldBounds(new Vec3(511.99D, 65.0D, 511.99D), BOUNDS));
        assertFalse(FrontierV3ControlledMobMotion.insideWorldBounds(new Vec3(-512.01D, 65.0D, 0.0D), BOUNDS));
        assertFalse(FrontierV3ControlledMobMotion.insideWorldBounds(new Vec3(512.0D, 65.0D, 0.0D), BOUNDS));
    }
}
