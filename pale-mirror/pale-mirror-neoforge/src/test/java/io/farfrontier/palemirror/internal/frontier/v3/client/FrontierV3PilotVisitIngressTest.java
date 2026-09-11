package io.farfrontier.palemirror.internal.frontier.v3.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PilotVisitIngressTest {
    private static final String DESTINATION = "pale_mirror:frontier_graybox";
    private static final BlockPos TARGET = new BlockPos(-360, 65, -352);
    private static final FrontierV3PilotVisitIngress.Correlation CORRELATION = new FrontierV3PilotVisitIngress.Correlation(
            "00000000-0000-0000-0000-000000000031", 1, "00000000-0000-0000-0000-000000000032");

    @Test
    void requestsTheCorrelatedServerReceiptOnceEvenWhenNeitherClientFactIsReady() {
        FrontierV3PilotVisitIngress ingress = new FrontierV3PilotVisitIngress(DESTINATION);
        ingress.observe("minecraft:overworld", false, BlockPos.ZERO);

        assertTrue(ingress.requestServerReceipt(CORRELATION));
        assertFalse(ingress.requestServerReceipt(CORRELATION));
        assertFalse(ingress.targetDimensionSeen());
        assertFalse(ingress.targetChunkSeen());
    }

    @Test
    void clientFactsAreMonotonicAndChunkCannotPrecedeTheTargetDimension() {
        FrontierV3PilotVisitIngress ingress = new FrontierV3PilotVisitIngress(DESTINATION);
        ingress.observe("minecraft:overworld", true, BlockPos.ZERO);
        assertFalse(ingress.targetDimensionSeen());
        assertFalse(ingress.targetChunkSeen());

        ingress.observe(DESTINATION, false, TARGET);
        assertTrue(ingress.targetDimensionSeen());
        assertFalse(ingress.targetChunkSeen());

        ingress.observe(DESTINATION, true, TARGET);
        ingress.observe("minecraft:overworld", false, BlockPos.ZERO);
        assertTrue(ingress.targetDimensionSeen());
        assertTrue(ingress.targetChunkSeen());
    }
}
