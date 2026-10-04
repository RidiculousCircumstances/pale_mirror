package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.persistence.StrategicPlanStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.HiveSettlementAssaultProcess;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleFixture;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
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
                SceneLeaseStatus.PREPARED, members, Set.of(), Optional.empty());
        state = FrontierTestActorBodies.present(state.prepareSceneLease(lease), lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);

        List<SubjectId> attackers = assault.combatantAttackerIds().stream().sorted().toList();
        List<SubjectId> targets = assault.defenderIds().stream().sorted().toList();
        SubjectId attacker = attackers.getFirst(), target = targets.getFirst();
        SubjectId cause = SettlementAssaultCauseIdentity.strike(assault.id(), attacker, assault.nextStrikeEpoch());
        PhysicalIntent intent = strike(state, lease, cause, attacker, target);
        SubjectId hive = assault.hiveId();
        FrontierWorldState hot = state;
        var wrongFamily = new PhysicalIntent(intent.id(), intent.kind(), intent.status(), cause,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.routeSceneStrike(attacker, target, lease.id(), lease.revision()),
                intent.origin(), 0, intent.postcondition(), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.ROUTE_ENGAGEMENT);
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.prepare(hot, hive, wrongFamily),
                "an exact scene identity cannot authorize a foreign lifecycle family");
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.prepare(hot, hive, strike(hot, lease,
                SettlementAssaultCauseIdentity.strike(assault.id(), attackers.getLast(), assault.nextStrikeEpoch()), attackers.getLast(), target)),
                "a lease member who is not the COLD-selected attacker must not manufacture a HOT cause");
        if (targets.size() > 1) {
            SubjectId wrongTarget = targets.getLast();
            assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.prepare(hot, hive, strike(hot, lease, cause, attacker, wrongTarget)),
                    "the shared cause must not make the target interchangeable inside the HOT lease");
        }

        state = PhysicalIntentLifecycleFixture.prepare(state, hive, intent);
        var releasedBeforeStart = state.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING)
                .releaseSceneLease(leaseId, members.stream().map(member -> new SceneMemberPosition(member.actorId(),
                        hot.actorLocations().get(member.actorId()).body(), hot.actorLocations().get(member.actorId()).condition().health())).toList());
        assertFalse(releasedBeforeStart.physicalIntents().containsKey(intent.id()));
        var retiredStrike = FencedRecoveryPhysicalIntentSupport.bindingId(intent);
        assertFalse(releasedBeforeStart.fencedRecovery().current().containsKey(retiredStrike));
        assertTrue(releasedBeforeStart.fencedRecovery().tombstones().containsKey(retiredStrike));
        assertEquals(0, releasedBeforeStart.strategicPlans().settlementAssaults().get(assault.id()).nextStrikeEpoch(),
                "cancelling an unstarted strike must not manufacture a hit");
        assertEquals(releasedBeforeStart, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(releasedBeforeStart)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(releasedBeforeStart, hive, intent,
                PhysicalIntentStatus.RUNNING, Optional.empty()));
        state = PhysicalIntentLifecycleFixture.transition(state, hive, intent, PhysicalIntentStatus.RUNNING, Optional.empty());
        var startedStrike = state;
        for (var unresolved : List.of(startedStrike, PhysicalIntentLifecycleFixture.transition(startedStrike, hive, intent,
                PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()))) {
            var held = unresolved.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
            var captured = members.stream().map(member -> new SceneMemberPosition(member.actorId(),
                    held.actorLocations().get(member.actorId()).body(), held.actorLocations().get(member.actorId()).condition().health())).toList();
            assertThrows(IllegalArgumentException.class, () -> held.releaseSceneLease(leaseId, captured),
                    "possible physical effects must be settled before scene release");
            assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.FrontierSceneContinuationPlanner.releaseEvents(
                    held, held.sceneLeases().get(leaseId), 1000L, new SceneLeaseReleased(leaseId, captured)),
                    "planning must reject before an impossible release reaches the journal");
        }
        SceneStrikeObservation observation = new SceneStrikeObservation(new PhysicalObservationId("observation:hot-receipt-selection"), intent.id(), attacker, target,
                FixedScalar.whole(20), FixedScalar.whole(18));
        var afterLethalDeath = ModeledActorBodyFacts.died(startedStrike, target,
                startedStrike.actorLocations().get(target).body(), "actual-strike-death", 11L);
        var lethalReceipt = new SceneStrikeObservation(new PhysicalObservationId("observation:hot-lethal-receipt"),
                intent.id(), attacker, target, FixedScalar.whole(20), FixedScalar.ZERO);
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(startedStrike, hive, intent,
                PhysicalIntentStatus.CONFIRMED, Optional.of(lethalReceipt)),
                "a receipt cannot invent death without the ordinary exact death transition");
        var lethalConfirmed = PhysicalIntentLifecycleFixture.transition(afterLethalDeath, hive, intent,
                PhysicalIntentStatus.CONFIRMED, Optional.of(lethalReceipt));
        assertEquals(ActorLifeStatus.DEAD, lethalConfirmed.actorLocations().get(target).condition().status());
        assertEquals(PhysicalIntentStatus.CONFIRMED, lethalConfirmed.physicalIntents().get(intent.id()).status(),
                "death must not reselect a different target for an already admitted exact strike");
        var lethalUnknown = PhysicalIntentLifecycleFixture.transition(afterLethalDeath, hive, intent,
                PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        var recoveredLethal = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(lethalUnknown));
        var recoveredConfirmed = PhysicalIntentLifecycleFixture.transition(recoveredLethal, hive, intent,
                PhysicalIntentStatus.CONFIRMED, Optional.of(lethalReceipt));
        assertEquals(lethalConfirmed.actorLocations(), recoveredConfirmed.actorLocations());
        assertEquals(lethalConfirmed.physicalObservations(), recoveredConfirmed.physicalObservations());
        var unknownScene = recoveredConfirmed.transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        unknownScene = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknownScene));
        assertEquals(SceneLeaseStatus.DRAINING,
                FrontierSceneBehaviors.recoveredStatus(unknownScene, unknownScene.sceneLeases().get(leaseId)));
        var recoveredDrain = unknownScene.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        var survivors = members.stream().filter(member -> !member.actorId().equals(target))
                .map(member -> new SceneMemberPosition(member.actorId(),
                        recoveredDrain.actorLocations().get(member.actorId()).body(),
                        recoveredDrain.actorLocations().get(member.actorId()).condition().health())).toList();
        var releasedAfterDeath = recoveredDrain.releaseSceneLease(leaseId, survivors);
        assertEquals(SceneLeaseStatus.CLOSED, releasedAfterDeath.sceneLeases().get(leaseId).status());
        assertEquals(ActorLifeStatus.DEAD, releasedAfterDeath.actorLocations().get(target).condition().status());
        assertEquals(recoveredConfirmed.physicalObservations(), releasedAfterDeath.physicalObservations());
        var unknownStrike = PhysicalIntentLifecycleFixture.transition(startedStrike, hive, intent,
                PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        var firstUnknown = unknownStrike;
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(firstUnknown, hive, intent,
                PhysicalIntentStatus.CONFLICTED, Optional.empty()), "missing evidence cannot bypass bounded inspection");
        for (int attempt = 1; attempt < FencedRecoveryBinding.MAX_RECOVERY_ATTEMPTS; attempt++) {
            unknownStrike = PhysicalIntentLifecycleFixture.transition(unknownStrike, hive, intent,
                    PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        }
        unknownStrike = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknownStrike));
        var abandoned = PhysicalIntentLifecycleFixture.transition(unknownStrike, hive, intent,
                PhysicalIntentStatus.CONFLICTED, Optional.empty());
        assertEquals(startedStrike.actorLocations(), abandoned.actorLocations());
        assertEquals(startedStrike.physicalObservations(), abandoned.physicalObservations());
        assertEquals(FencedRecoveryDisposition.ABANDON, abandoned.fencedRecovery().tombstones().get(retiredStrike).disposition());
        var drained = abandoned.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        var safelyClosed = drained.releaseSceneLease(leaseId, members.stream().map(member -> new SceneMemberPosition(member.actorId(),
                drained.actorLocations().get(member.actorId()).body(), drained.actorLocations().get(member.actorId()).condition().health())).toList());
        assertEquals(SceneLeaseStatus.CLOSED, safelyClosed.sceneLeases().get(leaseId).status());
        assertEquals(0, safelyClosed.strategicPlans().settlementAssaults().get(assault.id()).nextStrikeEpoch(),
                "explicit abandonment cannot fabricate a confirmed combat round");
        FrontierWorldState preparedStrike = state;
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(preparedStrike, hive, intent, PhysicalIntentStatus.CONFIRMED,
                Optional.of(new SceneStrikeObservation(new PhysicalObservationId("observation:hot-receipt-stale-health"), intent.id(), attacker, target,
                        FixedScalar.whole(19), FixedScalar.whole(18)))),
                "a stale physical wound cannot overwrite the current exact target health");
        List<SubjectId> currentCommitments = state.strategicPlans().objectives().values().stream()
                .filter(objective -> objective.ownerId().equals(hive) && objective.status() == StrategicObjectiveStatus.ACTIVE)
                .map(StrategicObjective::id).toList();
        FrontierWorldState reconsidered = state.withStrategicPlans(state.strategicPlans().reconsider(hive, currentCommitments, List.of()));
        assertTrue(reconsidered.coldSettlementAssaultSceneCandidates().isEmpty(),
                "a superseded expedition tactical plan must not admit a second HOT scene");
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalIntentLifecycleFixture.transition(reconsidered, hive, intent, PhysicalIntentStatus.CONFIRMED, Optional.of(observation)),
                "a superseded expedition authority must not commit its stale child-front receipt");
        state = PhysicalIntentLifecycleFixture.transition(state, hive, intent, PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        assertEquals(1, state.strategicPlans().settlementAssaults().get(assault.id()).nextStrikeEpoch(),
                "one exact confirmed HOT receipt advances the retained COLD epoch once");
        assertEquals(FixedScalar.whole(18), state.actorLocations().get(target).condition().health(),
                "a non-lethal HOT receipt retains the exact wound instead of resetting it at the next COLD boundary");
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
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.prepare(confirmed, hive, strike(confirmed, lease, cause, attacker, target)),
                "the confirmed prior epoch cannot be prepared again while its HOT lease remains authoritative");

        FrontierWorldState draining = state.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        // A scope release cannot manufacture an unsupported displacement or silently snap
        // it back to the old formation. Common physical observation is independently required.
        var unrecorded = members.stream()
                .map(member -> new SceneMemberPosition(member.actorId(), draining.actorLocations().get(member.actorId()).body().offset(0, 10, 0),
                        draining.actorLocations().get(member.actorId()).condition().health())).toList();
        assertThrows(IllegalArgumentException.class, () -> draining.releaseSceneLease(leaseId, unrecorded));
        state = draining.releaseSceneLease(leaseId, members.stream()
                .map(member -> new SceneMemberPosition(member.actorId(), draining.actorLocations().get(member.actorId()).body(),
                        draining.actorLocations().get(member.actorId()).condition().health())).toList());
        assertEquals(draining.actorLocations(), state.actorLocations(), "scene release never substitutes a historical formation pose");
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
        assertFalse(FrontierSceneLeaseStateSupport.mayCompact(restored, restored.sceneLeases().get(leaseId)),
                "retained physical history pins its exact closed scene until owner receipt compaction");
        assertTrue(FrontierSceneLeaseStateSupport.mayCompact(hot, lease.withStatus(SceneLeaseStatus.CLOSED)),
                "an otherwise unreferenced closed scene remains eligible for bounded retention");
        members.forEach(member -> assertEquals(lease.memberBody(hot.actorLocations(), member.actorId()), restored.actorLocations().get(member.actorId()).body(),
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
                SceneLeaseStatus.PREPARED, nextMembers, Set.of(), Optional.empty());
        FrontierWorldState nextHot = FrontierTestActorBodies.present(restored.prepareSceneLease(nextLease), nextLease).transitionSceneLease(nextLeaseId, SceneLeaseStatus.HOT);
        List<SubjectId> nextAttackers = assault.defenderIds().stream().sorted().toList();
        List<SubjectId> nextTargets = assault.combatantAttackerIds().stream().sorted().toList();
        SubjectId nextAttacker = nextAttackers.get(Math.floorMod(1, nextAttackers.size()));
        SubjectId nextTarget = nextTargets.get(Math.floorMod(1, nextTargets.size()));
        SubjectId nextCause = SettlementAssaultCauseIdentity.strike(assault.id(), nextAttacker, 1);
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.prepare(nextHot, hive, strike(nextHot, lease, cause, attacker, target)),
                "release must not make the prior confirmed epoch replayable");
        PhysicalIntent nextIntent = strike(nextHot, nextLease, nextCause, nextAttacker, nextTarget);
        assertEquals(PhysicalIntentStatus.PREPARED, PhysicalIntentLifecycleFixture.prepare(nextHot, hive, nextIntent).physicalIntents().get(nextIntent.id()).status(),
                "the released COLD assault admits only its next exact epoch");
    }

    @Test void hotOverseerLossRetreatsTheSameExpeditionBeforeItsColdTerminalDecision() {
        FrontierDevelopmentScenarios.SettlementAssaultFixture fixture = FrontierDevelopmentScenarios.settlementAssaultFixture(
                new WorldId("frontier:overseer-retreat"), 92L);
        FrontierWorldState state = fixture.state();
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(fixture.assaultId());
        SettlementAssaultSceneCandidate candidate = state.coldSettlementAssaultSceneCandidates().stream()
                .filter(value -> value.assaultId().equals(assault.id())).findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:overseer-retreat");
        WorldId worldId = state.bootstrap().worldId();
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(id -> new SceneMember(id, SceneLease.deterministicEntityId(worldId, id))).toList();
        SceneLease lease = SceneLease.forCause(leaseId, worldId,
                new SettlementAssaultSceneCause(assault.id(), assault.settlementId()), candidate.handoffPosition(), fixture.instant(), 4L,
                SceneLeaseStatus.PREPARED, members, Set.of(), Optional.empty());
        state = FrontierTestActorBodies.present(state.prepareSceneLease(lease), lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        long contactPlanEpoch = state.strategicPlans().settlementAssaults().get(assault.id()).tacticalPlan().planEpoch();

        state = ModeledActorBodyFacts.died(state, assault.overseerId(), lease.memberBody(state.actorLocations(), assault.overseerId()), "test-overseer-loss", 11L);

        SettlementAssault retreating = state.strategicPlans().settlementAssaults().get(assault.id());
        assertEquals(assault.expeditionId(), retreating.expeditionId());
        assertEquals(TacticalPlanPhase.RETREAT, retreating.tacticalPlan().phase());
        assertEquals(contactPlanEpoch + 1L, retreating.tacticalPlan().planEpoch());
        assertEquals(SceneLeaseStatus.DRAINING, state.sceneLeases().get(leaseId).status(), "exact death quiesces the same scope without removing its surviving bodies");
        PhysicalIntent staleContact = new PhysicalIntent(new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:overseer-retreat"),
                PhysicalIntentKind.SCENE_STRIKE, PhysicalIntentStatus.RUNNING,
                SettlementAssaultCauseIdentity.strike(assault.id(), assault.combatantAttackerIds().getFirst(), 0),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.assaultSceneStrike(assault.combatantAttackerIds().getFirst(), assault.defenderIds().getFirst(), lease.id(), lease.revision()),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
        FrontierWorldState afterLoss = state;
        assertThrows(IllegalArgumentException.class, () -> afterLoss.strategicPlans().afterConfirmedHotStrike(staleContact),
                "the old contact directive cannot commit after the same expedition orders retreat");

        state = state.releaseSceneLease(leaseId, members.stream().filter(member -> !member.actorId().equals(assault.overseerId()))
                .map(member -> new SceneMemberPosition(member.actorId(), lease.memberBody(afterLoss.actorLocations(), member.actorId()), FixedScalar.whole(20))).toList());
        SettlementAssault coldRetreat = state.strategicPlans().settlementAssaults().get(assault.id());
        assertEquals(SettlementAssaultStatus.COLD_COMBAT, coldRetreat.status());
        assertEquals(TacticalPlanPhase.RETREAT, coldRetreat.tacticalPlan().phase());
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> terminal = HiveSettlementAssaultProcess.planCombat(state,
                HiveSettlementAssaultProcess.combat(coldRetreat, 20L));
        assertEquals(HiveSettlementAssaultProcess.resolution(state, assault, SettlementAssaultOutcome.ABORTED), terminal.getFirst().payload());
    }

    @Test void oneSharedNonFlatFormationCursorSurvivesSnapshotAndRejectsForgedHotArrival() {
        FrontierDevelopmentScenarios.SettlementAssaultFixture fixture = FrontierDevelopmentScenarios.startedSettlementAssaultFixture(
                new WorldId("frontier:graybox"), 41L);
        FrontierWorldState state = fixture.state();
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(fixture.assaultId());
        // The real admission compiler now retains surveyed tray-to-ground grades and legal
        // obstacle detours. Do not inject an unsupported one-cell rise as physical evidence.
        assertFalse(assault.march().complete());
        assertTrue(assault.march().memberTopologies().values().stream().anyMatch(path -> java.util.stream.IntStream
                        .range(1, path.linearCorridorSurfaces().size()).anyMatch(index -> path.linearCorridorSurfaces().get(index - 1).y()
                                != path.linearCorridorSurfaces().get(index).y())),
                "the retained approach carries non-flat GROUND_BIOFORM topology rather than an endpoint shortcut");

        state = HiveSettlementAssaultProcess.reduceAdvanced(state, state.bootstrap().hive().id(),
                new SettlementAssaultAttackerAdvanced(assault.id(), assault.overseerId(), ExpeditionMarchStep.capture(assault.march()), io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.current(state, assault)));
        assault = state.strategicPlans().settlementAssaults().get(assault.id());
        assertEquals(1, assault.march().cursor());
        FrontierWorldState cold = state;
        assertEquals(assault.formationBodies(), assault.attackerIds().stream().collect(java.util.stream.Collectors.toMap(
                id -> id, id -> cold.actorLocations().get(id).body())));

        FrontierWorldState restored = new FrontierWorldStateCodec(state.bootstrap()).decode(new FrontierWorldStateCodec(state.bootstrap()).encode(state));
        assault = restored.strategicPlans().settlementAssaults().get(assault.id());
        assertEquals(1, assault.march().cursor());
        assertEquals(assault.formationBodies(), assault.attackerIds().stream().collect(java.util.stream.Collectors.toMap(
                id -> id, id -> restored.actorLocations().get(id).body())));

        SettlementAssaultSceneCandidate candidate = FrontierSettlementAssaultSceneSupport.marchCandidate(restored, assault).orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:expedition-formation-snapshot");
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted().map(id -> new SceneMember(id,
                SceneLease.deterministicEntityId(restored.bootstrap().worldId(), id))).toList();
        SceneLease lease = SceneLease.forCause(leaseId, restored.bootstrap().worldId(), new SettlementAssaultSceneCause(assault.id(), assault.settlementId()),
                candidate.handoffPosition(), fixture.instant(), 1L, SceneLeaseStatus.PREPARED, members, Set.of(), Optional.empty());
        FrontierWorldState hot = FrontierTestActorBodies.present(restored.prepareSceneLease(lease), lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        SettlementAssault hotAssault = hot.strategicPlans().settlementAssaults().get(assault.id());
        Map<SubjectId, BodyPosition> forged = new java.util.LinkedHashMap<>(hotAssault.nextFormationBodies());
        forged.put(hotAssault.overseerId(), hotAssault.formationBodies().get(hotAssault.overseerId()));
        assertThrows(IllegalArgumentException.class, () -> HiveSettlementAssaultProcess.reduceFormationObserved(hot, hotAssault.hiveId(),
                formationObserved(hot, hotAssault, hot.sceneLeases().get(leaseId), forged)),
                "one missing retained arrival cannot advance a complete formation");
        var arrival = formationObserved(hot, hotAssault, hot.sceneLeases().get(leaseId), hotAssault.nextFormationBodies());
        assertThrows(IllegalArgumentException.class, () -> HiveSettlementAssaultProcess.reduceFormationObserved(hot, hotAssault.hiveId(), arrival),
                "semantic arrival cannot manufacture the physical pose");
        FrontierWorldState inspected = hot;
        for (var body : arrival.bodies().entrySet()) inspected = ModeledActorBodyFacts.inspected(inspected, body.getKey(), body.getValue());
        FrontierWorldState advanced = HiveSettlementAssaultProcess.reduceFormationObserved(inspected, hotAssault.hiveId(), arrival);
        assertEquals(inspected.actorLocations(), advanced.actorLocations(), "only independent common inspection writes HOT pose");
        assertEquals(hotAssault.march().cursor() + 1, advanced.strategicPlans().settlementAssaults().get(hotAssault.id()).march().cursor());
        assertEquals(hotAssault.nextFormationBodies(), hotAssault.attackerIds().stream().collect(java.util.stream.Collectors.toMap(
                id -> id, id -> advanced.actorLocations().get(id).body())),
                "the semantic HOT formation agrees with independently inspected canonical actor positions");
    }

    @Test void retainsTypedMarchObstructionAndControllerLossOnTheSameHotExpedition() {
        for (ExpeditionMarchIssueKind kind : List.of(ExpeditionMarchIssueKind.BLOCKED_EDGE,
                ExpeditionMarchIssueKind.OCCUPIED_NEXT_BODY, ExpeditionMarchIssueKind.MISSING_OWNED_BODY)) {
            HotMarch hot = hotMarch("typed-march-" + kind.name().toLowerCase(), 94L);
            SettlementAssault assault = hot.assault();
            ExpeditionMarchIssue issue = new ExpeditionMarchIssue(kind, assault.overseerId(),
                    assault.march().memberTopologies().get(assault.overseerId()).edgeAfterCursor(assault.march().cursor()).id(), assault.march().cursor());
            SettlementAssaultMarchIssueObserved observed = new SettlementAssaultMarchIssueObserved(assault.id(), hot.lease().id(), hot.lease().revision(),
                    ExpeditionMarchStep.capture(assault.march()), actuations(hot.state(), assault), issue,
                    SettlementAssaultExecutionAuthority.current(hot.state(), assault));
            var payloads = FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(observed, payloads.decode(observed.type(), payloads.encode(observed)),
                    "WAL retains the captured body, scope and spatial declarations");
            var staleBodies = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationGroup(
                    observed.actuations().members().stream().map(member ->
                            new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(
                                    new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(
                                            member.body().actorId(), member.body().physicalEpoch() + 1), member.execution())).toList());
            for (var stale : List.of(
                    new SettlementAssaultMarchIssueObserved(assault.id(), hot.lease().id(), hot.lease().revision() + 1,
                            observed.predecessor(), observed.actuations(), issue, observed.executions()),
                    new SettlementAssaultMarchIssueObserved(assault.id(), hot.lease().id(), hot.lease().revision(),
                            new ExpeditionMarchStep(observed.predecessor().cursor(), observed.predecessor().spatialRevision() + 1,
                                    observed.predecessor().members()), observed.actuations(), issue, observed.executions()),
                    new SettlementAssaultMarchIssueObserved(assault.id(), hot.lease().id(), hot.lease().revision(),
                            observed.predecessor(), staleBodies, issue, observed.executions()))) {
                CommandId staleId = new CommandId("command:stale-march-" + kind.name().toLowerCase());
                assertFalse(FrontierWorldProcessCatalog.planCommand("hive", hot.state(), new FrontierCommand(1, staleId,
                        hot.state().bootstrap().worldId(), new Revision(1L), new SimInstant(19L),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(staleId), stale)) instanceof CommandPlan.Accepted,
                        "an obsolete body, scope or spatial receipt must fail before WAL admission");
                assertThrows(IllegalArgumentException.class, () -> HiveSettlementAssaultProcess.reduceMarchIssueObserved(
                        hot.state(), assault.hiveId(), stale));
            }
            CommandId commandId = new CommandId("command:typed-march-" + kind.name().toLowerCase());
            assertTrue(FrontierWorldProcessCatalog.planCommand("hive", hot.state(), new FrontierCommand(1, commandId,
                    hot.state().bootstrap().worldId(), new Revision(1L), new SimInstant(19L),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), observed)) instanceof CommandPlan.Accepted,
                    "the loaded exact expedition issue must pass the production command owner before reduction");
            FrontierWorldState conflicted = HiveSettlementAssaultProcess.reduceMarchIssueObserved(hot.state(), assault.hiveId(),
                    observed);
            SettlementAssault retained = conflicted.strategicPlans().settlementAssaults().get(assault.id());
            assertEquals(SettlementAssaultStatus.CONFLICT, retained.status());
            assertEquals(TacticalPlanPhase.TRAVEL, retained.tacticalPlan().phase());
            assertEquals(issue, retained.march().issue().orElseThrow());
            assertEquals(SceneLeaseStatus.CONFLICT, conflicted.sceneLeases().get(hot.lease().id()).status());
            FrontierWorldState restored = new FrontierWorldStateCodec(conflicted.bootstrap()).decode(new FrontierWorldStateCodec(conflicted.bootstrap()).encode(conflicted));
            assertEquals(issue, restored.strategicPlans().settlementAssaults().get(assault.id()).march().issue().orElseThrow(),
                    "the terminal typed issue survives the canonical snapshot envelope");
        }

        HotMarch controllerHot = hotMarch("typed-controller-loss", 95L);
        SettlementAssault controllerAssault = controllerHot.assault();
        FrontierWorldState afterDeath = ModeledActorBodyFacts.died(controllerHot.state(), controllerAssault.overseerId(),
                controllerHot.lease().memberBody(controllerHot.state().actorLocations(), controllerAssault.overseerId()), "test-march-controller-loss", 19L);
        SettlementAssault controllerRetreat = afterDeath.strategicPlans().settlementAssaults().get(controllerAssault.id());
        assertEquals(ExpeditionMarchIssueKind.CONTROLLER_LOST, controllerRetreat.march().issue().orElseThrow().kind());
        assertEquals(TacticalPlanPhase.RETREAT, controllerRetreat.tacticalPlan().phase());
        assertEquals(controllerAssault.expeditionId(), controllerRetreat.expeditionId());
    }

    @Test void savedPartialFormationRetainsActualBodiesAndOriginalCheckpointThroughColdRecovery() {
        var hot = hotMarch("expedition-actual-rejoin", 94L);
        var state = hot.state(); var original = hot.assault();
        var originalGroup = SettlementAssaultExecutionAuthority.current(state, original);
        var stale = new SettlementAssaultAttackerAdvanced(original.id(), original.overseerId(),
                ExpeditionMarchStep.capture(original.march()), originalGroup);
        var arrivedBody = original.nextFormationBodies().get(original.overseerId());
        state = ModeledActorBodyFacts.inspected(state, original.overseerId(), arrivedBody);
        var inventory = state.inventory(); var executions = state.actorExecutions();
        state = ModeledActorBodyFacts.unloaded(state, original.overseerId());
        var retained = state.strategicPlans().settlementAssaults().get(original.id());
        assertEquals(original.march().cursor(), retained.march().cursor(), "departure is not formation arrival");
        assertEquals(original.march().memberTopologies(), retained.march().memberTopologies());
        assertEquals(original.tacticalPlan(), retained.tacticalPlan());
        assertEquals(originalGroup, SettlementAssaultExecutionAuthority.current(state, retained));
        assertEquals(executions, state.actorExecutions()); assertEquals(inventory, state.inventory());
        assertEquals(arrivedBody, state.actorLocations().get(original.overseerId()).body());
        assertEquals(retained.march().memberIds(), retained.march().rejoins().keySet());
        var held = state;
        var receipt = new SettlementAssaultAttackerAdvanced(retained.id(), retained.overseerId(),
                ExpeditionMarchStep.capture(retained.march()), originalGroup);
        assertThrows(IllegalArgumentException.class, () -> HiveSettlementAssaultProcess.reduceAdvanced(held, retained.hiveId(), receipt),
                "remaining physically held peers prohibit COLD movement");
        for (var actor : original.attackerIds()) if (!actor.equals(original.overseerId())) state = ModeledActorBodyFacts.unloaded(state, actor);
        state = state.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING);
        var saved = state;
        state = state.releaseSceneLease(hot.lease().id(), original.attackerIds().stream().map(actor ->
                new SceneMemberPosition(actor, saved.actorLocations().get(actor).body(), saved.actorLocations().get(actor).condition().health())).toList());
        var codec = new FrontierWorldStateCodec(state.bootstrap());
        state = codec.decode(codec.encode(state));
        assertEquals(retained.march(), state.strategicPlans().settlementAssaults().get(original.id()).march());
        var recovered = state;
        assertThrows(IllegalArgumentException.class, () -> HiveSettlementAssaultProcess.reduceAdvanced(recovered, original.hiveId(), stale));
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receipt, payloads.decode(receipt.type(), payloads.encode(receipt)));
        var before = state.strategicPlans().settlementAssaults().get(original.id());
        var next = before.advanceFormation();
        var beforeSelection = before;
        var movingPeer = before.attackerIds().stream().filter(actor -> !beforeSelection.formationBodies().get(actor).equals(next.formationBodies().get(actor)))
                .findFirst().orElseThrow();
        var blocked = state.recordPhysicalDelta(new PhysicalDelta(next.formationBodies().get(movingPeer).supportingSurface().support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "player:expedition-approach-block"));
        assertThrows(IllegalArgumentException.class, () -> HiveSettlementAssaultProcess.reduceAdvanced(blocked, original.hiveId(), receipt));
        int remaining = before.march().rejoins().values().stream().mapToInt(path -> path.path().size() - path.cursor()).sum();
        for (int step = 0; step < remaining && before.march().cursor() == original.march().cursor(); step++) {
            var event = new SettlementAssaultAttackerAdvanced(before.id(), before.overseerId(), ExpeditionMarchStep.capture(before.march()), originalGroup);
            var previous = state;
            state = HiveSettlementAssaultProcess.reduceAdvanced(state, before.hiveId(), event);
            var progressed = state;
            assertThrows(IllegalArgumentException.class, () -> HiveSettlementAssaultProcess.reduceAdvanced(progressed, original.hiveId(), event));
            assertEquals(arrivedBody, state.actorLocations().get(original.overseerId()).body(), "early leader waits at its original next checkpoint");
            assertEquals(previous.inventory(), state.inventory()); assertEquals(previous.actorExecutions(), state.actorExecutions());
            before = state.strategicPlans().settlementAssaults().get(before.id());
        }
        assertEquals(original.march().cursor() + 1, before.march().cursor());
        assertTrue(before.march().rejoins().isEmpty());
        assertEquals(original.march().memberTopologies(), before.march().memberTopologies());
    }

    private static io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationGroup actuations(
            FrontierWorldState state, SettlementAssault assault) {
        var executions = SettlementAssaultExecutionAuthority.current(state, assault);
        return new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationGroup(
                assault.march().memberIds().stream().sorted().map(actor ->
                        new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(
                                ActorBodyAuthority.current(state, actor), executions.requireMember(actor))).toList());
    }
    private static SettlementAssaultFormationObserved formationObserved(FrontierWorldState state,
            SettlementAssault assault, SceneLease lease, Map<SubjectId, BodyPosition> bodies) {
        return new SettlementAssaultFormationObserved(assault.id(), lease.id(), lease.revision(),
                ExpeditionMarchStep.capture(assault.march()), actuations(state, assault), bodies,
                SettlementAssaultExecutionAuthority.current(state, assault));
    }

    private static HotMarch hotMarch(String suffix, long seed) {
        FrontierDevelopmentScenarios.SettlementAssaultFixture fixture = FrontierDevelopmentScenarios.startedSettlementAssaultFixture(
                new WorldId("frontier:" + suffix), seed);
        FrontierWorldState state = fixture.state();
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(fixture.assaultId());
        SettlementAssaultSceneCandidate candidate = FrontierSettlementAssaultSceneSupport.marchCandidate(state, assault).orElseThrow();
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(id -> new SceneMember(id, SceneLease.deterministicEntityId(state.bootstrap().worldId(), id))).toList();
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:" + suffix), state.bootstrap().worldId(),
                new SettlementAssaultSceneCause(assault.id(), assault.settlementId()), candidate.handoffPosition(), fixture.instant(), 3L,
                SceneLeaseStatus.PREPARED, members, Set.of(), Optional.empty());
        FrontierWorldState hot = FrontierTestActorBodies.present(state.prepareSceneLease(lease), lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        return new HotMarch(hot, hot.strategicPlans().settlementAssaults().get(assault.id()), lease);
    }

    private record HotMarch(FrontierWorldState state, SettlementAssault assault, SceneLease lease) { }

    private static PhysicalIntent strike(FrontierWorldState state, SceneLease lease, SubjectId cause, SubjectId attacker, SubjectId target) {
        return new PhysicalIntent(SettlementAssaultStrikeReceiptBinding.intentId(state, lease, cause), PhysicalIntentKind.SCENE_STRIKE,
                PhysicalIntentStatus.PREPARED, cause, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.assaultSceneStrike(attacker, target, lease.id(), lease.revision()),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
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
