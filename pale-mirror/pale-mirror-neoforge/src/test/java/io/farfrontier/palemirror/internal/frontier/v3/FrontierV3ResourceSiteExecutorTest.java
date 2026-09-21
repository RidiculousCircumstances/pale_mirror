package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestLineage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ResourceSiteExecutorTest {
    @Test
    void canonicalPreparationDoesNotWaitForAnyNaturallyLoadedField() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:loaded-field-selection"), 77L));
        engine.advanceTo(new SimInstant(100L), new WorkBudget(64, 512));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());

        assertEquals(io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase.GROWING,
                state.resourceSites().site(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("site:2-wheat-field")).phase(),
                "an unloaded lexicographically earlier field cannot gate the canonical field clock");
    }

    @Test
    void residentChunksCannotSubstituteForTheOnePlayerLocalIngressBoundary() {
        ArrayList<BlockPosition> slots = new ArrayList<>();
        for (int x = 160; x < 168; x++) for (int z = 320; z < 328; z++) slots.add(new BlockPosition(x, 64, z));
        ResourceSite site = new ResourceSite(new SubjectId("site:99-wheat-field"), new SubjectId("settlement:99"),
                new SubjectId("structure:99-farm"), ResourceSiteKind.WHEAT_FIELD, slots);

        assertTrue(FrontierV3GrayboxExecutor.resourceSiteIngressMatches(new ChunkPos(9, 19), site),
                "a player at the adjacent farm shell is an ordinary field ingress");
        assertFalse(FrontierV3GrayboxExecutor.resourceSiteIngressMatches(new ChunkPos(2, 2), site),
                "a merely resident/generated remote chunk cannot project this COLD facility");
        assertFalse(FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(true, false),
                "remembered ingress alone cannot begin a new physical growth cursor after departure");
        assertFalse(FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(false, true),
                "a present player cannot project an unadmitted facility");
        assertTrue(FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(true, true),
                "an admitted facility can project only while its player is presently local");
    }

    @Test
    void initialProjectionPairsEveryTilledSoilCellWithItsCropBeforeTheNextTurn() {
        ArrayList<BlockPosition> slots = new ArrayList<>();
        for (int x = 160; x < 168; x++) for (int z = 320; z < 328; z++) slots.add(new BlockPosition(x, 64, z));
        ResourceSite site = new ResourceSite(new SubjectId("site:99-wheat-field"), new SubjectId("settlement:99"),
                new SubjectId("structure:99-farm"), ResourceSiteKind.WHEAT_FIELD, slots);

        var order = FrontierV3ResourceSiteExecutor.initialProjectionSlotOrder(site);
        assertEquals(132, order.size(), "the durable initial cursor retains the same exact footprint");
        for (int index = 0; index < site.irrigationSlots().size(); index++) {
            assertEquals(site.irrigationSlots().get(index), order.get(index));
        }
        for (int index = 0; index < site.cropSlots().size(); index++) {
            int first = site.irrigationSlots().size() + index * 2;
            assertEquals(site.cropSlots().get(index).offset(0, -1, 0), order.get(first),
                    "a newly tilled cell is immediately followed by its own crop write");
            assertEquals(site.cropSlots().get(index), order.get(first + 1));
        }
    }

    @Test
    void exactEarlierGrowthClaimIsARecoverablePredecessorOfColdSuccessorHarvest() {
        var intent = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-7-wheat-field");
        var growth = new FrontierV3ResourceSiteLedger.Claim(intent, FrontierV3ResourceSiteLedger.Status.ACTIVE, 0, 0);
        var maturePrefix = new FrontierV3ResourceSiteLedger.Claim(intent, FrontierV3ResourceSiteLedger.Status.ACTIVE,
                ResourceSiteLifecycle.MATURE_STAGE, 63);

        assertEquals(FrontierV3ResourceSiteExecutor.HarvestRestartPhysicalState.OWNED_BEHIND,
                FrontierV3ResourceSiteExecutor.classifyOwnedHarvestClaim(growth, 63, true),
                "a lawful saved growth field may be advanced to the later COLD successor prefix on natural ingress");
        assertEquals(FrontierV3ResourceSiteExecutor.HarvestRestartPhysicalState.OWNED_BEHIND,
                FrontierV3ResourceSiteExecutor.classifyOwnedHarvestClaim(maturePrefix, 64, true));
        assertEquals(FrontierV3ResourceSiteExecutor.HarvestRestartPhysicalState.FOREIGN_OR_DAMAGED,
                FrontierV3ResourceSiteExecutor.classifyOwnedHarvestClaim(growth, 63, false),
                "an observed foreign/damaged field remains a typed local conflict");
    }

    @Test
    void successorRegrowthRestoresOnlyThePredecessorSuffixBeyondColdProgress() {
        assertEquals(1, FrontierV3ResourceSiteExecutor.successorRegrowthRestoreSlots(64, 63),
                "a successor already advanced through 63 slots must retain that owned AIR prefix");
        assertEquals(64, FrontierV3ResourceSiteExecutor.successorRegrowthRestoreSlots(64, 0));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceSiteExecutor.successorRegrowthRestoreSlots(63, 64));
    }

    @Test
    void confirmedTerminalHarvestReceiptIsTheOnlyAllAirAdmissionToItsNextGrowthEpoch() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierV3ResourceSiteLedger.Claim terminal = new FrontierV3ResourceSiteLedger.Claim(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"),
                FrontierV3ResourceSiteLedger.Status.ACTIVE, ResourceSiteLifecycle.MATURE_STAGE, 64);
        ResourceSiteLifecycle nextEpoch = new ResourceSiteLifecycle(site, ResourceSitePhase.GROWING, 2L, 0, Optional.empty());

        assertEquals(FrontierV3ResourceSiteExecutor.ConfirmedHarvestRegrowthAdmission.ADMITTED,
                FrontierV3ResourceSiteExecutor.classifyConfirmedHarvestRegrowth(terminal, nextEpoch, 0, 0, true, true),
                "the scene's exact final receipt is the one owned input to bounded stage-zero regrowth");
        assertEquals(FrontierV3ResourceSiteExecutor.ConfirmedHarvestRegrowthAdmission.MISSING_CONFIRMED_RECEIPT,
                FrontierV3ResourceSiteExecutor.classifyConfirmedHarvestRegrowth(terminal, nextEpoch, 0, 0, true, false),
                "a look-alike all-AIR field without canonical receipt provenance remains fail-closed");
        assertEquals(FrontierV3ResourceSiteExecutor.ConfirmedHarvestRegrowthAdmission.PHYSICAL_RECEIPT_MISMATCH,
                FrontierV3ResourceSiteExecutor.classifyConfirmedHarvestRegrowth(terminal, nextEpoch, 0, 0, false, true),
                "confirmed history cannot overwrite a damaged terminal field");
        assertTrue(FrontierV3ResourceSiteExecutor.resetsTerminalHarvestClaim(terminal, 0, false, true),
                "the admitted terminal receipt may advance the mature/64 ownership witness to stage-zero regrowth");
        assertFalse(FrontierV3ResourceSiteExecutor.resetsTerminalHarvestClaim(terminal, 0, false, false),
                "a terminal-looking claim without either classified lifecycle route remains fail-closed");
        FrontierV3ResourceSiteLedger.Claim initialGrowth = new FrontierV3ResourceSiteLedger.Claim(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"),
                FrontierV3ResourceSiteLedger.Status.ACTIVE, 0, 0);
        assertTrue(FrontierV3ResourceSiteExecutor.allowsOwnedStageCatchUp(initialGrowth, ResourceSiteLifecycle.MATURE_STAGE, 0),
                "a restart may route an exact active stage-zero successor through its bounded canonical catch-up");
        assertFalse(FrontierV3ResourceSiteExecutor.allowsOwnedStageCatchUp(terminal, ResourceSiteLifecycle.MATURE_STAGE, 0),
                "a completed predecessor cannot be relabelled as an ordinary stage catch-up");
    }

    @Test
    void composedColdTerminalAllowsOnlyItsExactOwnedHotPrefixToRegrowAfterRestart() {
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        FrontierV3ResourceSiteLedger.Claim hotPrefix = new FrontierV3ResourceSiteLedger.Claim(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"),
                FrontierV3ResourceSiteLedger.Status.ACTIVE, ResourceSiteLifecycle.MATURE_STAGE, 1);
        ResourceSiteHarvestLineage composed = new ResourceSiteHarvestLineage(
                new SubjectId("job:site-harvest-1-wheat-field-2"), new SubjectId("task:settlement-1-harvest"),
                new SubjectId("resident:1-31"), new SubjectId("item:site-harvest-1-wheat-field-2-wheat"), 2L,
                new BodyPosition(0, 64, 0), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-harvest-1-wheat-field-2"),
                new InventoryCustody.ContainerSlot(new SubjectId("container:1"), 1), true, Optional.empty(), Optional.empty());
        ResourceSiteLifecycle regrowth = new ResourceSiteLifecycle(siteId, ResourceSitePhase.GROWING, 3L, 2,
                Optional.empty(), Optional.empty(), Optional.of(composed));

        assertTrue(FrontierV3ResourceSiteExecutor.isExactComposedTerminalRegrowth(
                regrowth, 2, 0, hotPrefix),
                "a COLD-composed terminal may advance only its exact retained HOT prefix into the current growth epoch");
        assertFalse(FrontierV3ResourceSiteExecutor.isExactComposedTerminalRegrowth(
                regrowth, 2, 1, hotPrefix),
                "a live harvest cursor is never relabelled as terminal regrowth");
    }

    @Test
    void lifecycleConflictTraceRetainsItsCallerAndPreConflictOwnershipTuple() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        ResourceSiteLifecycle lifecycle = new ResourceSiteLifecycle(site, ResourceSitePhase.GROWING, 2L, 0, Optional.empty());
        FrontierV3ResourceSiteLedger.Claim claim = new FrontierV3ResourceSiteLedger.Claim(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"),
                FrontierV3ResourceSiteLedger.Status.ACTIVE, ResourceSiteLifecycle.MATURE_STAGE, 64);

        String expectedTraceKind = "resource_site_conflict:ordinary_growth:owner=resource-site-lifecycle:"
                + "claim=active-s7-h64-pnone:expected=growing-e2-s0:observed=observed_managed_cell_mismatch:"
                + "admission=not_evaluated:claim_state=not_evaluated:disposition=terminal-repair-required";
        assertEquals(expectedTraceKind,
                FrontierV3ResourceSiteConflictExecutor.lifecycleConflictTraceKind(
                        FrontierV3ResourceSiteExecutor.LifecycleConflictOrigin.ORDINARY_GROWTH, lifecycle, claim,
                        io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictReason.OBSERVED_MANAGED_CELL_MISMATCH));
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.DiagnosticCategory.CANONICAL_INVARIANT_FAILURE,
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.ORDINARY_OBSERVATION_MISMATCH.diagnosticReason().category());
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.DiagnosticCategory.RECOVERY_UNKNOWN,
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.RESTART_OBSERVATION_MISMATCH.diagnosticReason().category(),
                "the named restart producer must not be relabelled as a live lifecycle invariant failure");
    }

    @Test
    void lifecycleConflictTraceRetainsTheInFlightProjectionTuple() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        ResourceSiteLifecycle lifecycle = new ResourceSiteLifecycle(site, ResourceSitePhase.GROWING, 2L, 1, Optional.empty());
        FrontierV3ResourceSiteLedger.ProjectionTransition projection = new FrontierV3ResourceSiteLedger.ProjectionTransition(
                "job:site-harvest-1-wheat-field-2", 0, 0, 1, 0, 16, 64,
                FrontierV3ResourceSiteLedger.ProjectionMode.ADVANCE);
        FrontierV3ResourceSiteLedger.Claim claim = new FrontierV3ResourceSiteLedger.Claim(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"),
                FrontierV3ResourceSiteLedger.Status.ACTIVE, 0, 0, projection);

        assertTrue(FrontierV3ResourceSiteConflictExecutor.lifecycleConflictTraceKind(
                        FrontierV3ResourceSiteExecutor.LifecycleConflictOrigin.RESTART_RECONCILIATION, lifecycle, claim,
                        io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictReason.OBSERVED_MANAGED_CELL_MISMATCH)
                .contains("claim=active-s0-h0-padvance-fs0-fh0-ts1-th0-n16-c64-srcjob_site-harvest-1-wheat-field-2"),
                "the first conflict keeps the exact bounded writer which owned its physical prefix");
    }

    @Test
    void activeClaimFencesNativeGrowthBeforeAndAfterItsPredecessorClaimIsUpdated() {
        FrontierV3ResourceSiteLedger.Claim terminal = new FrontierV3ResourceSiteLedger.Claim(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"),
                FrontierV3ResourceSiteLedger.Status.ACTIVE, ResourceSiteLifecycle.MATURE_STAGE, 64);

        assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(terminal, true, false),
                "the cursor owns freshly written stage-zero crops until the claim catches up");
        assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(terminal, false, false),
                "an already mismatched managed crop stays frozen as local conflict evidence rather than receiving further native mutation");
        assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(terminal, false, true),
                "the settled active claim keeps its normal canonical-stage guard");
    }

    @Test
    void durableProjectionTransitionRetainsTheExactOrderedRecoveryWitness() {
        FrontierV3ResourceSiteLedger.ProjectionTransition transition = new FrontierV3ResourceSiteLedger.ProjectionTransition(
                "job:site-harvest-1-wheat-field-2", 0, 0, ResourceSiteLifecycle.MATURE_STAGE, 63,
                48, 64, FrontierV3ResourceSiteLedger.ProjectionMode.ADVANCE);

        CompoundTag encoded = transition.write();
        assertEquals(transition, FrontierV3ResourceSiteLedger.ProjectionTransition.read(encoded));
        assertEquals(49, transition.advance().nextWrite());
        assertEquals(0, transition.fromStage());
        assertEquals(63, transition.targetHarvestedCropSlots(),
                "the durable fence retains the canonical cursor, rather than accepting a mixed crop surface by shape");
    }

    @Test
    void nativeGrowthFenceRetainsTheFirstPhysicalEventSeparatelyFromFacilityOwnership() {
        FrontierV3ResourceSiteLedger.NativeGrowthFence fence = new FrontierV3ResourceSiteLedger.NativeGrowthFence(
                "crop-grow-pre", new BlockPosition(144, 64, -5), 0, 0);

        assertEquals(fence, FrontierV3ResourceSiteLedger.NativeGrowthFence.read(fence.write()));
        assertEquals("crop-grow-pre", fence.source());
        assertEquals(0, fence.claimStage(), "the diagnostic observation must not advance the stable facility claim");
    }
}
