package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3AftermathOwnerSeamTest {
    @Test
    void ordinaryColdDueActionAndRegisteredHotScenePublishTheSameExactStrikeNamespace() {
        var coldConfiguration = FrontierV3FixtureCatalog.coldBomberAftermathConfiguration(new WorldId("frontier:aftermath-cold-seam"), 41L);
        var coldEngine = (io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection>)
                FrontierEngines.createCanonicalStateAccess(coldConfiguration);
        long dueAt = coldConfiguration.initialSchedules().getFirst().dueAt().ticks();
        coldEngine.advanceTo(new SimInstant(dueAt), new WorkBudget(64, 512));
        FrontierWorldState cold = coldEngine.canonicalState().state();
        var aftermath = cold.deferredAftermath().entries().values().stream().findFirst().orElseThrow();
        assertTrue(cold.physicalDeltas().containsKey(aftermath.cells().getFirst().position()),
                "the ordinary due action, not a ledger fixture, commits the canonical loss before projection");
        assertTrue(FrontierGrayboxPlan.compileStructuralBaseline(cold).cells().containsKey(aftermath.cells().getFirst().position()),
                "the bounded projector retains the actual structural candidate so it can observe the semantic-loss mask");
        assertTrue(!FrontierGrayboxPlan.compile(cold).cells().containsKey(aftermath.cells().getFirst().position()),
                "the ordinary COLD loss masks that exact projection candidate before deferred aftermath is admitted");
        var physicalOrder = FrontierV3PhysicalExecutors.registry().diagnostics().stream().map(FrontierV3PhysicalExecutorRegistry.Diagnostic::id).toList();
        assertTrue(physicalOrder.indexOf("graybox-projection") < physicalOrder.indexOf("deferred-aftermath"),
                "the actual physical registry observes the bounded graybox candidate before aftermath consumes it");

        FrontierWorldState coldBoundary = coldConfiguration.initialState();
        SettlementAssault assault = coldBoundary.strategicPlans().settlementAssaults().values().iterator().next();
        var candidate = coldBoundary.coldSettlementAssaultSceneCandidates().getFirst();
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(coldBoundary.bootstrap().worldId(), actor))).toList();
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:aftermath-owner-seam"), coldBoundary.bootstrap().worldId(),
                new SettlementAssaultSceneCause(assault.id(), assault.settlementId()), candidate.handoffPosition(), new SimInstant(400L), 1L,
                SceneLeaseStatus.PREPARED, members, SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), Set.of(), java.util.Optional.empty());
        FrontierWorldState hot = coldBoundary.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        SettlementAssault hotAssault = hot.strategicPlans().settlementAssaults().get(assault.id());
        long epoch = SettlementAssaultCauseIdentity.hotEpoch(hotAssault, hot.physicalIntents().values());
        SubjectId attacker = hotAssault.combatantAttackerIds().stream().sorted().skip(Math.floorMod(epoch, hotAssault.combatantAttackerIds().size())).findFirst().orElseThrow();

        SubjectId published = FrontierV3SceneBehaviorRegistry.strikeCause(hot, lease, attacker, epoch);
        assertEquals(aftermath.causeId(), published,
                "the registered HOT behavior, not a helper-only comparison, publishes the same ordinary COLD strike cause");
        assertNotEquals(published, FrontierV3SceneBehaviorRegistry.strikeCause(hot, lease, attacker, epoch + 1L));
        assertNotEquals(published, FrontierV3SceneBehaviorRegistry.strikeCause(hot, lease, new SubjectId("bioform:substituted"), epoch));

        List<SubjectId> targets = ((epoch & 1L) == 0L ? hotAssault.defenderIds() : hotAssault.combatantAttackerIds()).stream().sorted().toList();
        SubjectId target = targets.get(Math.floorMod(epoch, targets.size()));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:aftermath-owner-seam"), PhysicalIntentKind.SCENE_STRIKE,
                PhysicalIntentStatus.PREPARED, published, List.of(attacker, target),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED);
        FrontierWorldState confirmed = hot.preparePhysicalIntent(intent)
                .transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty())
                .transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(new SceneStrikeObservation(
                        new PhysicalObservationId("observation:aftermath-owner-seam"), intent.id(), attacker, target,
                        FixedScalar.ONE, FixedScalar.ZERO)));
        assertEquals(PhysicalIntentStatus.CONFIRMED, confirmed.physicalIntents().get(intent.id()).status(),
                "the exact shared HOT cause survives receipt and immutable-state validation");
    }
}
