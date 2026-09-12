package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3PlayerCustodyRecoveryTest {
    private static final FrontierV3PlayerResourceDiagnostic.Expected EXPECTED = new FrontierV3PlayerResourceDiagnostic.Expected(
            new SubjectId("custody:player-recovery"), UUID.fromString("bf39347d-cb86-3221-b6b7-7b89a1dcb4cf"), 9, 32,
            new SubjectId("binding:player-recovery"), 7L, "minecraft:wheat", 32, "bf39347d-cb86-3221-b6b7-7b89a1dcb4cf");

    @Test
    void onlyAnUnresolvedCanonicalFirstSaveGapMayMaterializeItsExactEmptySlot() {
        assertEquals(FrontierV3PlayerCustodyRecovery.Decision.MATERIALIZE,
                FrontierV3PlayerCustodyRecovery.decision(EXPECTED, new FrontierV3PlayerResourceDiagnostic.Actual("", 0), false));
        assertEquals(FrontierV3PlayerCustodyRecovery.Decision.LOCAL_AMBIGUITY,
                FrontierV3PlayerCustodyRecovery.decision(EXPECTED, new FrontierV3PlayerResourceDiagnostic.Actual("", 0), true));
        assertEquals(FrontierV3PlayerCustodyRecovery.Decision.CURRENT,
                FrontierV3PlayerCustodyRecovery.decision(EXPECTED, new FrontierV3PlayerResourceDiagnostic.Actual("minecraft:wheat", 32), true));
        assertEquals(FrontierV3PlayerCustodyRecovery.Decision.CONFLICT,
                FrontierV3PlayerCustodyRecovery.decision(EXPECTED, new FrontierV3PlayerResourceDiagnostic.Actual("minecraft:bread", 32), false));
        assertEquals(FrontierV3PlayerCustodyRecovery.Decision.CONFLICT,
                FrontierV3PlayerCustodyRecovery.decision(EXPECTED, new FrontierV3PlayerResourceDiagnostic.Actual("minecraft:wheat", 64), false));
    }
}
