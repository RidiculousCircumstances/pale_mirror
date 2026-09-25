package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ActorHandObservationTest {
    private static final SubjectId FARMER = new SubjectId("resident:1-1");
    private static final UUID BODY = UUID.fromString("00000000-0000-0000-0000-000000000125");

    @Test
    void onlyBoundedWheatCanBecomeAnExactActorOffhandObservation() {
        assertEquals(FrontierV3ActorHandObservation.Disposition.EMPTY,
                FrontierV3ActorHandObservation.classify(FARMER, BODY, "", 0).disposition());
        assertEquals(FrontierV3ActorHandObservation.Disposition.FOREIGN_ITEM,
                FrontierV3ActorHandObservation.classify(FARMER, BODY, "minecraft:iron_hoe", 1).disposition());
        var wheat = FrontierV3ActorHandObservation.classify(FARMER, BODY, "minecraft:wheat", 37);
        assertEquals(FrontierV3ActorHandObservation.Disposition.WHEAT, wheat.disposition());
        var stack = wheat.stack().orElseThrow();
        assertEquals(new PhysicalStackAddress.ActorHand(FARMER, BODY), stack.address());
        assertEquals("minecraft:wheat", stack.itemKind());
        assertEquals(37, stack.quantity());
        assertEquals(FrontierV3ActorHandObservation.Disposition.FOREIGN_ITEM,
                FrontierV3ActorHandObservation.classify(FARMER, BODY, "minecraft:wheat", 37, false).disposition(),
                "wheat with foreign components is not the farmer's ordinary fungible cargo");
        assertTrue(FrontierV3ActorHandObservation.classify(FARMER, BODY, "minecraft:wheat", 65)
                .stack().isEmpty(), "one physical hand cannot prove an overfull harvest part");
    }
}
