package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateDiagnosticsTest {
    @Test
    void restoresEveryRetainedDiagnosticRowWithoutDerivingANewHistory() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        source.run(30);
        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));

        ReferenceWorld restored = new ReferenceWorld(source.config());
        ReferenceGrayboxStateDiagnostics.read(reference.get("diagnostics"), source.settlements()).applyTo(restored.diagnostics());

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateDiagnostics.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateDiagnostics.capture(restored)));
    }

    @Test
    void rejectsAnIncompleteDiagnosticOwner() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateDiagnostics.read(Map.of(), Map.of()));
    }
}
