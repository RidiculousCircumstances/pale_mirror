package io.farfrontier.palemirror.internal.integration.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExcludedSourceItemFirewallTest {
    @Test
    void crimsonPrivateModelRangeIsRecognised() {
        assertTrue(ExcludedSourceItemFirewall.isCrimsonPrivateModelData(5_450_080));
        assertTrue(ExcludedSourceItemFirewall.isCrimsonPrivateModelData(5_459_999));
    }

    @Test
    void modelOutsidePrivateRangeIsNotClaimed() {
        assertFalse(ExcludedSourceItemFirewall.isCrimsonPrivateModelData(123));
        assertFalse(ExcludedSourceItemFirewall.isCrimsonPrivateModelData(5_460_000));
    }
}
