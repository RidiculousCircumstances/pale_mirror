package io.farfrontier.palemirror.internal.integration.crimson;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrimsonSandboxAdapterTest {
    @Test
    void privateModelRangeIsOwnedByTheCrimsonAdapter() {
        assertTrue(CrimsonItemPolicy.isPrivateModelData(5_450_080));
        assertTrue(CrimsonItemPolicy.isPrivateModelData(5_459_999));
        assertFalse(CrimsonItemPolicy.isPrivateModelData(123));
        assertFalse(CrimsonItemPolicy.isPrivateModelData(5_460_000));
    }
}
