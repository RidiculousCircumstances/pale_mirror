package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
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

    @Test
    void releasedReferenceDepotIsNotAnEligibleHarvestWriter() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:harvest-custody"), 71L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:7"));

        assertTrue(!FrontierV3ResourceSiteHarvestExecutor.hasOperationalDepotCustody(state, depot),
                "a released reference replica is not permission to write a COLD harvest receipt");
        assertTrue(FrontierV3ResourceSiteHarvestExecutor.hasOperationalDepotCustody(state, new SubjectId("container:non-reference")),
                "the reference custody fence must not change legacy non-reference container eligibility");
    }

    @Test
    void receiptRequiresTheSameTickingOwnedDepotBodyAsReferenceCustody() {
        assertTrue(!FrontierV3ResourceSiteHarvestExecutor.physicallyGroundedDepot(false, true, true),
                "a retained but non-ticking chunk is serialized evidence, not a harvest writer");
        assertTrue(!FrontierV3ResourceSiteHarvestExecutor.physicallyGroundedDepot(true, false, true),
                "a lease without its owned chest body is not a harvest writer");
        assertTrue(!FrontierV3ResourceSiteHarvestExecutor.physicallyGroundedDepot(true, true, false),
                "a physical chest without current custody is not a harvest writer");
        assertTrue(FrontierV3ResourceSiteHarvestExecutor.physicallyGroundedDepot(true, true, true),
                "only the shared ticking body plus exact custody admits the receipt");
    }

    @Test
    void exactCanonicalOutputOnACompleteOwnedFieldIsAcknowledgedRatherThanQuarantined() {
        assertEquals(FrontierV3ResourceSiteHarvestExecutor.ReceiptDisposition.ACKNOWLEDGE_EXISTING_OUTPUT,
                FrontierV3ResourceSiteHarvestExecutor.receiptDisposition(true, false, true, false),
                "ordinary depot ingress may materialize COLD's exact canonical output before the harvest receipt observer");
        assertEquals(FrontierV3ResourceSiteHarvestExecutor.ReceiptDisposition.APPLY_NEW_OUTPUT,
                FrontierV3ResourceSiteHarvestExecutor.receiptDisposition(true, false, false, true));
        assertEquals(FrontierV3ResourceSiteHarvestExecutor.ReceiptDisposition.CONFLICT,
                FrontierV3ResourceSiteHarvestExecutor.receiptDisposition(true, false, false, false),
                "a foreign occupied slot never borrows the canonical output identity");
        assertEquals(FrontierV3ResourceSiteHarvestExecutor.ReceiptDisposition.CONFLICT,
                FrontierV3ResourceSiteHarvestExecutor.receiptDisposition(false, false, true, false),
                "even an exact output cannot confirm without the complete owned terminal field");
    }

    private static PhysicalIntent intent(String settlement) {
        SubjectId site = new SubjectId("site:" + settlement + "-wheat-field");
        SubjectId job = new SubjectId("job:site-harvest-" + settlement + "-wheat-field-1");
        SubjectId worker = new SubjectId("resident:" + settlement + "-1");
        SubjectId output = new SubjectId("item:site-harvest-" + settlement + "-wheat-field-1-wheat");
        return new PhysicalIntent(new PhysicalIntentId("intent:site-harvest-" + settlement + "-wheat-field-1"),
                PhysicalIntentKind.RESOURCE_SITE_HARVEST, PhysicalIntentStatus.PREPARED, site,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.siteHarvest(site, job, worker, output), new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST);
    }
}
