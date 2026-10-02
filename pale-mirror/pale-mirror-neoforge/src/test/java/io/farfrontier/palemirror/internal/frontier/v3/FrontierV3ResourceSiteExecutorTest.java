package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
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
    void deferredRecoveryCannotMonopolizeOtherLoadedFieldsOrDiscardItsOwnRecord() {
        var a = new SubjectId("site:1-wheat-field");
        var b = new SubjectId("site:2-wheat-field");
        var pending = new java.util.HashSet<>(java.util.Set.of(a, b));
        var turns = new FrontierV3FairTurn<SubjectId>();
        assertEquals(a, FrontierV3ResourceSiteRestartDispatcher.nextRecoverySite(pending, turns, id -> true).orElseThrow());
        // A returned DEFERRED: its durable projection and pending membership remain.
        assertEquals(b, FrontierV3ResourceSiteRestartDispatcher.nextRecoverySite(pending, turns, id -> true).orElseThrow());
        assertEquals(java.util.Set.of(a, b), pending);
        pending.remove(b); // B finished normally, A subsequently loses observer demand.
        assertTrue(FrontierV3ResourceSiteRestartDispatcher.nextRecoverySite(pending, turns, id -> false).isEmpty());
        assertEquals(a, FrontierV3ResourceSiteRestartDispatcher.nextRecoverySite(pending, turns, id -> true).orElseThrow());
        assertEquals(java.util.Set.of(a), pending);
    }

    @Test
    void projectionSelectionFollowsCurrentEligibilityWithoutReservingTheTurnForDepartedWork() {
        var a = new ResourceSiteLifecycle(new SubjectId("site:1-wheat-field"), ResourceSitePhase.GROWING, 1L, 2, Optional.empty());
        var b = new ResourceSiteLifecycle(new SubjectId("site:2-wheat-field"), ResourceSitePhase.GROWING, 1L, 3, Optional.empty());
        var sites = java.util.List.of(b, a);
        // Start A, leave its bounded writer unfinished, then enter B. The retained writer
        // is not a candidate-selection input: its cursor belongs to A, not the whole queue.
        assertEquals(java.util.List.of(a), FrontierV3ResourceSiteExecutor.projectionCandidates(sites, java.util.Set.of(), a::equals));
        assertEquals(java.util.List.of(b), FrontierV3ResourceSiteExecutor.projectionCandidates(sites, java.util.Set.of(), b::equals));
        assertEquals(java.util.List.of(a, b), FrontierV3ResourceSiteExecutor.projectionCandidates(sites, java.util.Set.of(), value -> true));
        assertEquals(java.util.List.of(b), FrontierV3ResourceSiteExecutor.projectionCandidates(sites, java.util.Set.of(a.siteId()), value -> true),
                "recovery of A does not reserve B's ordinary projection turn");
        assertEquals(java.util.List.of(), FrontierV3ResourceSiteExecutor.projectionCandidates(sites, java.util.Set.of(), value -> false));
    }

    @Test
    void matureSuccessorUsesRecoverableReverseCursorRatherThanAnEmptySameStageAdvance() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        var ledger = FrontierV3ResourceSiteLedger.fixture();
        ledger.reserveComposedTerminalSuccessor(site, new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"));
        ledger.activate(site);
        assertTrue(FrontierV3ResourceSiteProjectionAdmission.restoresMaturePredecessor(ledger.claim(site), 7, 0, true));
        assertFalse(FrontierV3ResourceSiteProjectionAdmission.restoresMaturePredecessor(ledger.claim(site), 7, 0, false),
                "a lower cursor alone grants no regrowth authority");
        assertFalse(FrontierV3ResourceSiteProjectionAdmission.restoresMaturePredecessor(ledger.claim(site), 2, 0, true),
                "nonmature regrowth uses the existing whole-stage transition");
        int writes = FrontierV3ResourceSiteProjectionAdmission.successorRegrowthRestoreSlots(64, 0);
        ledger.beginProjection(site, new FrontierV3ResourceSiteLedger.ProjectionTransition("growth:site:1-wheat-field:e2",
                7, 64, 7, 0, 0, writes, FrontierV3ResourceSiteLedger.ProjectionMode.SUCCESSOR_RESTORE));
        for (int index = 1; index <= 8; index++) { ledger.restoreOne(site, 64 - index); ledger.advanceProjection(site, index); }
        var resumed = FrontierV3ResourceSiteLedger.ProjectionTransition.read(ledger.claim(site).projection().write());
        assertEquals(8, resumed.nextWrite());
        assertEquals(64, resumed.fromHarvestedCropSlots());
        assertEquals(0, resumed.targetHarvestedCropSlots());
        assertEquals(FrontierV3ResourceSiteLedger.ProjectionMode.SUCCESSOR_RESTORE, resumed.mode());
        for (int index = resumed.nextWrite() + 1; index <= resumed.writeCount(); index++) {
            ledger.restoreOne(site, 64 - index); ledger.advanceProjection(site, index);
        }
        ledger.completeProjection(site);
        assertEquals(0, ledger.claim(site).harvestedCropSlots());
        assertEquals(7, ledger.claim(site).stage());
    }

    @Test
    void canonicalPreparationDoesNotWaitForAnyNaturallyLoadedField() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:loaded-field-selection"), 77L));
        // Initial exact-resident schedule actions share this bounded queue with
        // the twelve fields; drain the same due instant before asserting phase.
        engine.advanceTo(new SimInstant(100L), new WorkBudget(4_096, 32_768));
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
                new SubjectId("structure:99-farm"), ResourceSiteKind.WHEAT_FIELD, io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan.initialGrayboxLayout(slots));

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
                new SubjectId("structure:99-farm"), ResourceSiteKind.WHEAT_FIELD, io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan.initialGrayboxLayout(slots));

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
        assertEquals(1, FrontierV3ResourceSiteProjectionAdmission.successorRegrowthRestoreSlots(64, 63),
                "a successor already advanced through 63 slots must retain that owned AIR prefix");
        assertEquals(64, FrontierV3ResourceSiteProjectionAdmission.successorRegrowthRestoreSlots(64, 0));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceSiteProjectionAdmission.successorRegrowthRestoreSlots(63, 64));
    }

    @Test
    void confirmedTerminalHarvestReceiptIsTheOnlyAllAirAdmissionToItsNextGrowthEpoch() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierV3ResourceSiteLedger.Claim terminal = new FrontierV3ResourceSiteLedger.Claim(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"),
                FrontierV3ResourceSiteLedger.Status.ACTIVE, ResourceSiteLifecycle.MATURE_STAGE, 64);
        ResourceSiteLifecycle nextEpoch = new ResourceSiteLifecycle(site, ResourceSitePhase.GROWING, 2L, 0, Optional.empty());

        assertEquals(FrontierV3ResourceSiteExecutor.ConfirmedHarvestRegrowthAdmission.ADMITTED,
                FrontierV3ResourceSiteProjectionAdmission.classifyConfirmedHarvestRegrowth(terminal, nextEpoch, 0, 0, true, true),
                "the scene's exact final receipt is the one owned input to bounded stage-zero regrowth");
        assertEquals(FrontierV3ResourceSiteExecutor.ConfirmedHarvestRegrowthAdmission.MISSING_CONFIRMED_RECEIPT,
                FrontierV3ResourceSiteProjectionAdmission.classifyConfirmedHarvestRegrowth(terminal, nextEpoch, 0, 0, true, false),
                "a look-alike all-AIR field without canonical receipt provenance remains fail-closed");
        assertEquals(FrontierV3ResourceSiteExecutor.ConfirmedHarvestRegrowthAdmission.PHYSICAL_RECEIPT_MISMATCH,
                FrontierV3ResourceSiteProjectionAdmission.classifyConfirmedHarvestRegrowth(terminal, nextEpoch, 0, 0, false, true),
                "confirmed history cannot overwrite a damaged terminal field");
        assertTrue(FrontierV3ResourceSiteProjectionAdmission.resetsTerminalHarvestClaim(terminal, 0, false, true),
                "the admitted terminal receipt may advance the mature/64 ownership witness to stage-zero regrowth");
        assertFalse(FrontierV3ResourceSiteProjectionAdmission.resetsTerminalHarvestClaim(terminal, 0, false, false),
                "a terminal-looking claim without either classified lifecycle route remains fail-closed");
        FrontierV3ResourceSiteLedger.Claim initialGrowth = new FrontierV3ResourceSiteLedger.Claim(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"),
                FrontierV3ResourceSiteLedger.Status.ACTIVE, 0, 0);
        assertTrue(FrontierV3ResourceSiteProjectionAdmission.allowsOwnedStageCatchUp(initialGrowth, ResourceSiteLifecycle.MATURE_STAGE, 0),
                "a restart may route an exact active stage-zero successor through its bounded canonical catch-up");
        assertFalse(FrontierV3ResourceSiteProjectionAdmission.allowsOwnedStageCatchUp(terminal, ResourceSiteLifecycle.MATURE_STAGE, 0),
                "a completed predecessor cannot be relabelled as an ordinary stage catch-up");
        ResourceSiteLifecycle ready = new ResourceSiteLifecycle(site, ResourceSitePhase.GROWING, 2L, 6, Optional.empty()).advanceGrowth();
        assertEquals(FrontierV3ResourceSiteExecutor.ConfirmedHarvestRegrowthAdmission.ADMITTED,
                FrontierV3ResourceSiteProjectionAdmission.classifyConfirmedHarvestRegrowth(terminal, ready, 7, 0, true, true),
                "COLD reaching READY before player return does not invalidate the exact terminal predecessor");
        assertEquals(FrontierV3ResourceSiteExecutor.ConfirmedHarvestRegrowthAdmission.PHYSICAL_RECEIPT_MISMATCH,
                FrontierV3ResourceSiteProjectionAdmission.classifyConfirmedHarvestRegrowth(terminal, ready, 7, 0, false, true),
                "maturity never grants authority to overwrite a damaged predecessor");
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
        FrontierV3ResourceSiteLedger.Claim pending = new FrontierV3ResourceSiteLedger.Claim(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-projection-1-wheat-field"),
                FrontierV3ResourceSiteLedger.Status.PENDING, 0, 0);
        assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(pending, false, true),
                "the reserved initial writer owns its crop cells before its claim becomes active");
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
