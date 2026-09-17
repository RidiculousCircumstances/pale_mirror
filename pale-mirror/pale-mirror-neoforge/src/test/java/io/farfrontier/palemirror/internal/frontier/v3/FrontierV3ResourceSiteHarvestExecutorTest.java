package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void blockedHotHeadDoesNotStarveALaterNaturallyRunnableDeferredReceipt() {
        PhysicalIntent blockedHotHead = intent("1"); PhysicalIntent loadedDeferredReceipt = intent("4");

        assertEquals(loadedDeferredReceipt, FrontierV3ResourceSiteHarvestExecutor.firstRunnable(List.of(blockedHotHead, loadedDeferredReceipt),
                candidate -> candidate.equals(loadedDeferredReceipt)).orElseThrow());
    }

    @Test
    void currentProfileAdmitsCropEffectsOnlyThroughTheExactHotSceneOwner() {
        assertTrue(FrontierV3ResourceSiteHarvestExecutor.effectExecutionAdmitted(),
                "the current profile admits observed crop work through its exact HOT scene owner");
    }

    private static PhysicalIntent intent(String settlement) {
        SubjectId site = new SubjectId("site:" + settlement + "-wheat-field");
        SubjectId job = new SubjectId("job:site-harvest-" + settlement + "-wheat-field-1");
        SubjectId worker = new SubjectId("resident:" + settlement + "-1");
        SubjectId output = new SubjectId("item:site-harvest-" + settlement + "-wheat-field-1-wheat");
        return new PhysicalIntent(new PhysicalIntentId("intent:site-harvest-" + settlement + "-wheat-field-1"),
                PhysicalIntentKind.RESOURCE_SITE_HARVEST, PhysicalIntentStatus.PREPARED, site,
                List.of(site, job, worker, output), new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED);
    }
}
