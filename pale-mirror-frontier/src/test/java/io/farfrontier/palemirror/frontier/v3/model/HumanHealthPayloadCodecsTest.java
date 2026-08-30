package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HumanHealthPayloadCodecsTest {
    @Test void healthAndQuarantineFactsRoundTripThroughTheInstalledWalRegistry() {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        ResidentHealthTransition health = new ResidentHealthTransition(new SubjectId("resident:1-1"), ResidentHealthStatus.INFECTED, 1_200L);
        SettlementQuarantineTransition quarantine = new SettlementQuarantineTransition(new SubjectId("settlement:1"), SettlementQuarantineStatus.QUARANTINED, 1_200L);
        assertEquals(health, codecs.decode(health.type(), codecs.encode(health)));
        assertEquals(quarantine, codecs.decode(quarantine.type(), codecs.encode(quarantine)));
    }
}
