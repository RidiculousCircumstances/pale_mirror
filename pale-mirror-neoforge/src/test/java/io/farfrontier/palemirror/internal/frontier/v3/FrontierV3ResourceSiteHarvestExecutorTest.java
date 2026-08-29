package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FrontierV3ResourceSiteHarvestExecutorTest {
    @Test
    void crashWindowRetryUsesTheRecoveredRevisionRatherThanReusingThePriorReceiptIdentity() {
        PhysicalIntentId intent = new PhysicalIntentId("intent:site-harvest-1-wheat-field-1");

        var beforeCrash = FrontierV3ResourceSiteHarvestExecutor.commandId("confirmed", intent, 1_456L);
        var recovered = FrontierV3ResourceSiteHarvestExecutor.commandId("confirmed", intent, 1_457L);

        assertEquals("executor:resource-site-harvest-confirmed-intent-site-harvest-1-wheat-field-1-r1456", beforeCrash.value());
        assertNotEquals(beforeCrash, recovered,
                "a loaded-world postcondition retry must not collide with a retained pre-crash executor receipt");
    }
}
