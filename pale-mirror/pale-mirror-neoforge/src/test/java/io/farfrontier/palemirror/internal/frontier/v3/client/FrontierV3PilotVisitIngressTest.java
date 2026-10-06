package io.farfrontier.palemirror.internal.frontier.v3.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PilotVisitIngressTest {
    private static final String DESTINATION = "pale_mirror:frontier_graybox";
    private static final BlockPos TARGET = new BlockPos(-360, 65, -352);
    private static final FrontierV3PilotDemandReceiptTransition.Correlation CORRELATION = new FrontierV3PilotDemandReceiptTransition.Correlation(
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
        assertFalse(ingress.arrivedAt(TARGET), "a locally loaded target chunk is not an arrival in another dimension");

        ingress.observe(DESTINATION, false, TARGET);
        assertTrue(ingress.targetDimensionSeen());
        assertFalse(ingress.targetChunkSeen());
        assertFalse(ingress.arrivedAt(TARGET), "the exact local coordinates are insufficient until the target chunk is present");

        ingress.observe(DESTINATION, true, TARGET);
        assertTrue(ingress.arrivedAt(TARGET), "only the exact requested local player position completes an ordinary visit");
        ingress.observe(DESTINATION, false, TARGET);
        assertFalse(ingress.arrivedAt(TARGET), "a prior receipt cannot replace the currently missing target chunk");
        ingress.observe("minecraft:overworld", true, TARGET);
        assertFalse(ingress.arrivedAt(TARGET), "the same coordinates in a different dimension cannot reuse prior arrival");
        ingress.observe(DESTINATION, true, TARGET.offset(1, 0, 0));
        assertFalse(ingress.arrivedAt(TARGET), "a nearby loaded position may not masquerade as the requested first arrival");
        ingress.observe("minecraft:overworld", false, BlockPos.ZERO);
        assertTrue(ingress.targetDimensionSeen());
        assertTrue(ingress.targetChunkSeen());
    }

    @Test
    void clientVisitArmsTheExactServerDispatchBeforeAnyTeleportObservation() {
        FrontierV3PilotVisitIngress ingress = new FrontierV3PilotVisitIngress(DESTINATION);
        FrontierV3PilotDemandReceiptTransition.Arm arm = new FrontierV3PilotDemandReceiptTransition.Arm(CORRELATION,
                "settlement-assault-visit", "assault:development-settlement-assault", DESTINATION, TARGET);

        assertTrue(ingress.requestServerReceipt(arm.correlation()));
        assertFalse(ingress.targetDimensionSeen());
        assertFalse(ingress.targetChunkSeen());
        assertTrue(FrontierV3PilotDemandReceiptTransition.armCommand(arm).endsWith(" -360 65 -352"));
        assertTrue(FrontierV3PilotDemandReceiptTransition.travelCommand(arm, "pilot").equals(
                "execute in pale_mirror:frontier_graybox run tp pilot -360 65 -352"));
    }
}
