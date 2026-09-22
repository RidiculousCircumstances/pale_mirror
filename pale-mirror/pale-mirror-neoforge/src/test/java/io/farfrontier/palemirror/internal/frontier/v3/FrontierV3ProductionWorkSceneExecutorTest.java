package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ProductionWorkSceneExecutorTest {
    @Test
    void ordinaryIntermediateSupportRemainsInsideTheOneRetainedWorkshopEdge() {
        SurfaceAnchor current = SurfaceAnchor.at(137, 63, 11);
        SurfaceAnchor next = SurfaceAnchor.at(138, 63, 11);

        assertTrue(FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(new BlockPos(137, 63, 10), current, next),
                "an in-flight workshop worker remains on the one retained edge until exact next-surface arrival");
        assertFalse(FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(new BlockPos(135, 63, 10), current, next),
                "the workshop executor must still fail closed outside its named current/next edge");
    }
}
