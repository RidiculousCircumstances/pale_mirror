package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PlayerResourceDiagnosticTest {
    private static final UUID PLAYER = UUID.fromString("bf39347d-cb86-3221-b6b7-7b89a1dcb4cf");
    private static final String ACCOUNT = "custody:player-bf39347d-cb86-3221-b6b7-7b89a1dcb4cf";

    @Test
    void authenticatedPlayerSlotMustMatchItsExactCanonicalBinding() {
        CheckpointImage checkpoint = new CheckpointImage(new WorldId("frontier:player-resource-diagnostic"), new Revision(4L),
                new SimInstant(9L), new byte[] {1}, List.of(), List.of());
        FrontierV3PlayerResourceDiagnostic.Expected expected = new FrontierV3PlayerResourceDiagnostic.Expected(
                new SubjectId(ACCOUNT), PLAYER, 9, 32, new SubjectId("binding:player-wheat"), 7L, "minecraft:wheat", 32,
                "bf39347d-cb86-3221-b6b7-7b89a1dcb4cf");

        String matched = FrontierV3PlayerResourceDiagnostic.render(checkpoint, ACCOUNT, expected,
                new FrontierV3PlayerResourceDiagnostic.Actual("minecraft:wheat", 32));
        String empty = FrontierV3PlayerResourceDiagnostic.render(checkpoint, ACCOUNT, expected,
                new FrontierV3PlayerResourceDiagnostic.Actual("", 0));
        String duplicate = FrontierV3PlayerResourceDiagnostic.render(checkpoint, ACCOUNT, expected,
                new FrontierV3PlayerResourceDiagnostic.Actual("minecraft:wheat", 64));

        assertTrue(matched.contains("\"status\":\"ok\"") && matched.contains("\"slot\":9")
                && matched.contains("\"canonicalQuantity\":32") && matched.contains("\"matchesCanonical\":true"));
        assertTrue(empty.contains("\"status\":\"mismatch\"") && empty.contains("\"actual\":{\"itemKind\":\"\",\"count\":0}"));
        assertTrue(duplicate.contains("\"status\":\"mismatch\"") && duplicate.contains("\"count\":64"));
        assertFalse(empty.contains("\"status\":\"ok\""), "an empty real player slot cannot be hidden by canonical quantity");
    }
}
