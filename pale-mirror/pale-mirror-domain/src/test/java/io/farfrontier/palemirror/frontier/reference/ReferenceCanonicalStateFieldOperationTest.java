package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceCanonicalStateFieldOperationTest {
    private static final RootCheckpoint OPERATIONS = new RootCheckpoint(
            158, "bf8b7a045f29053784d92c0b132cbeaa32c9e15bc930494a3c95599686894a9c");
    private static final RootCheckpoint FIELD = new RootCheckpoint(
            330, "ed6c954ab944e37d7fc9f4368b016a9f4641e79036f403c4eec6e6c77c6841bc");

    @Test
    void operationAndFieldOwnersMatchPinnedPythonAtEveryCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());

        for (int day = 0; day <= 30; day++) {
            if (day == 0 || day == 1 || day == 2 || day == 3 || day == 5 || day % 5 == 0) {
                assertCheckpoint(OPERATIONS, ReferenceCanonicalStateOperations.capture(world), "operations", day);
                assertCheckpoint(FIELD, ReferenceCanonicalStateField.capture(world), "field", day);
            }
            if (day < 30) world.tick();
        }
    }

    @Test
    void ownersRejectV2DisabledWorld() {
        ReferenceWorld legacy = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2));

        assertEquals("canonical state requires V2-enabled reference world", assertThrows(IllegalStateException.class,
                () -> ReferenceCanonicalStateOperations.capture(legacy)).getMessage());
        assertEquals("canonical state requires V2-enabled reference world", assertThrows(IllegalStateException.class,
                () -> ReferenceCanonicalStateField.capture(legacy)).getMessage());
    }

    private static void assertCheckpoint(RootCheckpoint expected, Map<String, Object> state, String owner, int day) {
        String json = ReferenceV2PublicSnapshot.canonicalJson(state);
        assertEquals(expected.bytes(), json.getBytes(StandardCharsets.UTF_8).length, owner + " bytes day " + day);
        assertEquals(expected.digest(), ReferenceV2PublicSnapshot.sha256(state), owner + " digest day " + day);
    }

    private record RootCheckpoint(int bytes, String digest) { }
}
