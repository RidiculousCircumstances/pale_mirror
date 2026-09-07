package io.farfrontier.palemirror.internal.frontier.v3.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PersistentPilotTransportTest {
    @Test
    void reconnectAdmissionUsesTheAuthoritativeTransportNotARetainedPresentationObject() {
        assertTrue(FrontierV3PersistentPilotReconnectAdmission.eligible(true, false),
                "a disconnected transport must advance the one authenticated resume even if Minecraft has not retired its last LocalPlayer yet");
        assertFalse(FrontierV3PersistentPilotReconnectAdmission.eligible(true, true),
                "a live predecessor connection may not be reinterpreted as a replacement request");
        assertFalse(FrontierV3PersistentPilotReconnectAdmission.eligible(false, false),
                "a normal disconnected client without a runner-authorized resume remains unmanaged");
    }
}
