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

}
