package io.farfrontier.palemirror.internal.frontier.v3.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FrontierV3PilotVisitTargetTest {
    @Test
    void semanticCropBodyRemainsTheDeclaredBodyAndOnlyNormalizesItsClientObservation() {
        BlockPos body = new BlockPos(144, 64, -6);

        FrontierV3PilotVisitTarget target = FrontierV3PilotVisitTarget.fromSemanticBody(body);

        assertEquals(body, target.teleportAnchor());
        assertEquals(body, target.expectedClientFeet());
        assertEquals(body, target.normalizeObservation(body.below(), body.below()),
                "the known reported-block quirk normalizes only with the canonical support proof");
        assertNotEquals(body, target.normalizeObservation(body.below(), body.below().below()),
                "a genuinely one-block-low body has a different support and must not be normalized");
    }

    @Test
    void alreadyFeetCoordinateNeverReceivesASyntheticSecondOffset() {
        BlockPos feet = new BlockPos(144, 63, -6);

        FrontierV3PilotVisitTarget target = FrontierV3PilotVisitTarget.fromClientFeet(feet);

        assertEquals(feet, target.teleportAnchor());
        assertEquals(feet, target.expectedClientFeet());
        assertNotEquals(feet.below(), target.expectedClientFeet(), "an already-typed body is never shifted again");
    }
}
