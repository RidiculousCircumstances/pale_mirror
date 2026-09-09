package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Guards the real pilot checkpoint parser against diagnostic-envelope drift. */
class FrontierV3PilotLifecycleSignalTest {
    @Test
    void acceptsTheExactProductionDiagnosticEnvelope() {
        assertEquals("reference_container", FrontierV3PilotLifecycleSignal
                .diagnosticPayload("PMV3_DIAG {\"kind\":\"reference_container\",\"id\":\"f02b\"}")
                .get("kind").getAsString());
    }

    @Test
    void rejectsMissingOrForeignDiagnosticEnvelope() {
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3PilotLifecycleSignal.diagnosticPayload("{\"kind\":\"reference_container\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3PilotLifecycleSignal.diagnosticPayload("PMV3_PILOT_DIAGNOSTIC {\"kind\":\"reference_container\"}"));
    }

    @Test
    void rejectsMalformedDiagnosticPayload() {
        assertThrows(RuntimeException.class,
                () -> FrontierV3PilotLifecycleSignal.diagnosticPayload("PMV3_DIAG {not-json}"));
    }
}
