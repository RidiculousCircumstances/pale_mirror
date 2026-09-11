package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.persistence.StrategicPlanStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementAssaultTest {
    @Test void retainsExactSidesAndCannotReuseRouteEngagementSemantics() throws Exception {
        SubjectId hive = new SubjectId("hive:frontier");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:assault"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:assault"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD, StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.ACTIVE);
        SettlementAssault assault = assault(task, List.of(new SubjectId("bioform:west-0")), List.of(new SubjectId("resident:1-1")));
        StrategicPlanState plans = new StrategicPlanState(Map.of(objective.id(), objective), Map.of(task.id(), task), Map.of(), Map.of(),
                SettlementInfectionKnowledge.empty(), HiveOperationKnowledge.empty(), HiveTerritoryKnowledge.empty(), HiveSettlementKnowledge.empty(),
                HiveDoctrineState.initial(), Map.of(assault.id(), assault));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StrategicPlanStateCodec.write(new DataOutputStream(bytes), plans);
        StrategicPlanState restored = StrategicPlanStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(assault, restored.settlementAssaults().get(assault.id()));
        assertEquals(assault.sighting().settlementAnchor(), restored.settlementAssaults().get(assault.id()).settlementAnchor());
        assertThrows(IllegalArgumentException.class, () -> new StrategicPlanState(Map.of(objective.id(), objective), Map.of(), Map.of(), Map.of(),
                SettlementInfectionKnowledge.empty(), HiveOperationKnowledge.empty(), HiveTerritoryKnowledge.empty(), HiveSettlementKnowledge.empty(),
                HiveDoctrineState.initial(), Map.of(assault.id(), assault)));
    }

    @Test void refusesDuplicateDefendersAndAResolvedStateWithoutAnOutcome() {
        StrategicTask task = new StrategicTask(new SubjectId("task:assault"), new SubjectId("objective:assault"), new SubjectId("hive:frontier"),
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD, StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> assault(task, List.of(new SubjectId("bioform:west-0")),
                List.of(new SubjectId("resident:1-1"), new SubjectId("resident:1-1"))));
        assertThrows(IllegalArgumentException.class, () -> new SettlementAssault(new SubjectId("assault:northwatch"), task.id(), task.ownerId(), sighting(),
                new SubjectId("bioform:west-0"),
                List.of(new SettlementAssaultAttacker(new SubjectId("bioform:west-0"), List.of(new BlockPosition(0, 64, 0), sighting().settlementAnchor()), 0)),
                List.of(new SubjectId("resident:1-1")), SettlementAssaultStatus.RESOLVED, 0, Optional.empty()));
    }

    @Test void hotReceiptUsesTheColdSelectedBodiesAndSurvivesReleaseAndCodecRecovery() {
        FrontierDevelopmentScenarios.SettlementAssaultFixture fixture = FrontierDevelopmentScenarios.settlementAssaultFixture(
                new WorldId("frontier:hot-receipt-selection"), 91L);
        FrontierWorldState state = fixture.state();
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(fixture.assaultId());
        SettlementAssaultSceneCandidate candidate = state.coldSettlementAssaultSceneCandidates().stream()
                .filter(value -> value.assaultId().equals(assault.id())).findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:hot-receipt-selection");
        WorldId worldId = state.bootstrap().worldId();
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(id -> new SceneMember(id, SceneLease.deterministicEntityId(worldId, id))).toList();
        SceneLease lease = SceneLease.forCause(leaseId, state.bootstrap().worldId(),
                new SettlementAssaultSceneCause(assault.id(), assault.settlementId()), candidate.handoffPosition(), fixture.instant(), 7L,
                SceneLeaseStatus.PREPARED, members, SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), Set.of(), Optional.empty());
        state = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);

        List<SubjectId> attackers = assault.combatantAttackerIds().stream().sorted().toList();
        List<SubjectId> targets = assault.defenderIds().stream().sorted().toList();
        SubjectId attacker = attackers.getFirst(), target = targets.getFirst();
        SubjectId cause = SettlementAssaultCauseIdentity.strike(assault.id(), attacker, assault.nextStrikeEpoch());
        PhysicalIntent intent = strike(state, lease, cause, attacker, target);
        FrontierWorldState hot = state;
        assertThrows(IllegalArgumentException.class, () -> hot.preparePhysicalIntent(strike(hot, lease,
                SettlementAssaultCauseIdentity.strike(assault.id(), attackers.getLast(), assault.nextStrikeEpoch()), attackers.getLast(), target)),
                "a lease member who is not the COLD-selected attacker must not manufacture a HOT cause");
        if (targets.size() > 1) {
            SubjectId wrongTarget = targets.getLast();
            assertThrows(IllegalArgumentException.class, () -> hot.preparePhysicalIntent(strike(hot, lease, cause, attacker, wrongTarget)),
                    "the shared cause must not make the target interchangeable inside the HOT lease");
        }

        state = state.preparePhysicalIntent(intent).transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        SceneStrikeObservation observation = new SceneStrikeObservation(new PhysicalObservationId("observation:hot-receipt-selection"), intent.id(), attacker, target,
                FixedScalar.whole(20), FixedScalar.whole(18));
        SubjectId hive = state.bootstrap().hive().id();
        List<SubjectId> currentCommitments = state.strategicPlans().objectives().values().stream()
                .filter(objective -> objective.ownerId().equals(hive) && objective.status() == StrategicObjectiveStatus.ACTIVE)
                .map(StrategicObjective::id).toList();
        FrontierWorldState reconsidered = state.withStrategicPlans(state.strategicPlans().reconsider(hive, currentCommitments, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> reconsidered.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation)),
                "a superseded expedition authority must not commit its stale child-front receipt");
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        assertEquals(1, state.strategicPlans().settlementAssaults().get(assault.id()).nextStrikeEpoch(),
                "one exact confirmed HOT receipt advances the retained COLD epoch once");
        String frontPrefix = "front:" + assault.id().value().substring("assault:".length()).replace(':', '-');
        OperationFrontEffectKey effect = new OperationFrontEffectKey(cause, new SubjectId(frontPrefix + "-attack"),
                new SubjectId(frontPrefix + "-defence"), 0L);
        assertEquals(assault.expeditionId(), assault.attackFront().expeditionId());
        assertEquals(assault.expeditionId(), assault.defenceFront().expeditionId());
        assertTrue(java.util.Collections.disjoint(assault.attackFront().actorIds(), assault.defenceFront().actorIds()),
                "the two child fronts must retain disjoint canonical actor allocations");
        assertFalse(state.strategicPlans().frontEffects().accepts(effect),
                "the attack and defence allocations share one durable cross-front receipt");
        FrontierWorldState confirmed = state;
        assertThrows(IllegalArgumentException.class, () -> confirmed.preparePhysicalIntent(strike(confirmed, lease, cause, attacker, target)),
                "the confirmed prior epoch cannot be prepared again while its HOT lease remains authoritative");

        FrontierWorldState draining = state.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        // A loaded HOT body can be between the retained provider floors when release observes
        // it.  That transient position must not become a COLD admission authority.
        state = draining.releaseSceneLease(leaseId, members.stream()
                .map(member -> new SceneMemberPosition(member.actorId(), draining.actorLocations().get(member.actorId()).body().offset(0, 10, 0),
                        draining.actorLocations().get(member.actorId()).condition().health())).toList());
        FrontierWorldState restored = new FrontierWorldStateCodec(state.bootstrap()).decode(new FrontierWorldStateCodec(state.bootstrap()).encode(state));
        assertEquals(SettlementAssaultStatus.COLD_COMBAT, restored.strategicPlans().settlementAssaults().get(assault.id()).status());
        assertEquals(1, restored.strategicPlans().settlementAssaults().get(assault.id()).nextStrikeEpoch());
        assertEquals(state.strategicPlans().settlementAssaults().get(assault.id()).tacticalPlan(),
                restored.strategicPlans().settlementAssaults().get(assault.id()).tacticalPlan(),
                "the same expedition tactical authority must survive the HOT/COLD recovery boundary");
        assertFalse(restored.strategicPlans().frontEffects().accepts(effect),
                "recovery must retain the completed typed cross-front receipt");
        assertEquals(PhysicalIntentStatus.CONFIRMED, restored.physicalIntents().get(intent.id()).status());
        assertEquals(observation, restored.physicalObservations().get(observation.id()));
        members.forEach(member -> assertEquals(lease.memberPosition(member.actorId()), restored.actorLocations().get(member.actorId()).body(),
                "release must restore the exact provider-approved assault floor rather than a transient HOT observation"));

        // The closed receipt owns its completed HOT epoch, not the outcome of a later COLD
        // admission.  A later ordinary battlefield validation may conflict without making the
        // already durable release internally inconsistent or quarantining its runtime.
        assertDoesNotThrow(() -> restored.withStrategicPlans(restored.strategicPlans()
                .transitionSettlementAssault(assault.id(), SettlementAssaultStatus.CONFLICT)));

        SettlementAssaultSceneCandidate nextCandidate = restored.coldSettlementAssaultSceneCandidates().stream()
                .filter(value -> value.assaultId().equals(assault.id())).findFirst().orElseThrow();
        List<SceneMember> nextMembers = nextCandidate.memberPositions().keySet().stream().sorted()
                .map(id -> new SceneMember(id, SceneLease.deterministicEntityId(worldId, id))).toList();
        SceneLeaseId nextLeaseId = new SceneLeaseId("lease:hot-receipt-next");
        SceneLease nextLease = SceneLease.forCause(nextLeaseId, worldId,
                new SettlementAssaultSceneCause(assault.id(), assault.settlementId()), nextCandidate.handoffPosition(), fixture.instant(), 8L,
                SceneLeaseStatus.PREPARED, nextMembers, SceneLease.bodiesAboveSupportCells(nextCandidate.memberPositions()), Set.of(), Optional.empty());
        FrontierWorldState nextHot = restored.prepareSceneLease(nextLease).transitionSceneLease(nextLeaseId, SceneLeaseStatus.HOT);
        List<SubjectId> nextAttackers = assault.defenderIds().stream().sorted().toList();
        List<SubjectId> nextTargets = assault.combatantAttackerIds().stream().sorted().toList();
        SubjectId nextAttacker = nextAttackers.get(Math.floorMod(1, nextAttackers.size()));
        SubjectId nextTarget = nextTargets.get(Math.floorMod(1, nextTargets.size()));
        SubjectId nextCause = SettlementAssaultCauseIdentity.strike(assault.id(), nextAttacker, 1);
        assertThrows(IllegalArgumentException.class, () -> nextHot.preparePhysicalIntent(strike(nextHot, lease, cause, attacker, target)),
                "release must not make the prior confirmed epoch replayable");
        PhysicalIntent nextIntent = strike(nextHot, nextLease, nextCause, nextAttacker, nextTarget);
        assertEquals(PhysicalIntentStatus.PREPARED, nextHot.preparePhysicalIntent(nextIntent).physicalIntents().get(nextIntent.id()).status(),
                "the released COLD assault admits only its next exact epoch");
    }

    private static PhysicalIntent strike(FrontierWorldState state, SceneLease lease, SubjectId cause, SubjectId attacker, SubjectId target) {
        return new PhysicalIntent(SettlementAssaultStrikeReceiptBinding.intentId(state, lease, cause), PhysicalIntentKind.SCENE_STRIKE,
                PhysicalIntentStatus.PREPARED, cause, List.of(attacker, target),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED);
    }

    private static SettlementAssault assault(StrategicTask task, List<SubjectId> attackers, List<SubjectId> defenders) {
        HiveSettlementKnowledge.Sighting sighting = sighting();
        return new SettlementAssault(new SubjectId("assault:northwatch"), task.id(), task.ownerId(), sighting,
                attackers.getFirst(),
                attackers.stream().map(id -> new SettlementAssaultAttacker(id,
                        List.of(new BlockPosition(0, 64, 0), sighting.settlementAnchor()), 0)).toList(), defenders,
                SettlementAssaultStatus.APPROACHING, 0, Optional.empty());
    }

    private static HiveSettlementKnowledge.Sighting sighting() {
        return new HiveSettlementKnowledge.Sighting(new SubjectId("settlement:northwatch"), new SubjectId("bioform:west-0"),
                new BlockPosition(10, 64, 10), 100L);
    }
}
