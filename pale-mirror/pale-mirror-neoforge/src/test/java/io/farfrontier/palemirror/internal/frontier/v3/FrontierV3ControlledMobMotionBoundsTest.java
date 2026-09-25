package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
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

    @Test
    void changedRetainedEdgeSupersedesTheAlreadyDueCropTendingDirective() {
        FrontierV3ControlledMobMotion.MotionIntent cropTending = new FrontierV3ControlledMobMotion.MotionIntent(
                100L, new Vec3(137.5D, 64.0D, -5.5D), true, null, null, Double.MAX_VALUE, false);
        FrontierV3ControlledMobMotion.MotionIntent nextExactFieldEdge = new FrontierV3ControlledMobMotion.MotionIntent(
                100L, new Vec3(137.5D, 64.0D, -4.5D), false, null, null, Double.MAX_VALUE, true);

        assertFalse(FrontierV3ControlledMobMotion.sameDirective(cropTending, nextExactFieldEdge),
                "a completed crop cannot retain its old tending pose once the canonical cursor selected the next exact edge");
        assertTrue(FrontierV3ControlledMobMotion.sameDirective(nextExactFieldEdge,
                new FrontierV3ControlledMobMotion.MotionIntent(101L, new Vec3(137.5D, 64.0D, -4.5D), false,
                        null, null, Double.MAX_VALUE, true)),
                "repeated observations of the same retained edge still retain the already-due entity-pre turn");
    }

    @Test
    void exactRetainedDescentAcceptsOnlyItsThinSurfaceFootOffset() {
        var envelope = LocalNavigationEnvelope.around(new BodyPosition(-345, 65, -325),
                new BodyPosition(-345, 64, -324));
        Vec3 lowerSupportTop = new Vec3(-344.5D, 64.0D, -323.5D);
        assertTrue(FrontierV3ControlledMobMotion.walkGradeAllowed(
                new Vec3(-344.5D, 65.125D, -323.5D), lowerSupportTop, true, envelope),
                "a locally lifted farmer must be allowed to settle the retained one-grade descent");
        assertFalse(FrontierV3ControlledMobMotion.walkGradeAllowed(
                new Vec3(-344.5D, 65.125D, -323.5D), lowerSupportTop, true, null),
                "an unrelated exact goal must not inherit retained-edge latitude");
        assertFalse(FrontierV3ControlledMobMotion.walkGradeAllowed(
                new Vec3(-344.5D, 65.25D, -323.5D), lowerSupportTop, true, envelope),
                "a larger physical grade still requires a new canonical route or conflict");
    }
}
