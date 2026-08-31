package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveSettlementAssaultProcessTest {
    @Test void startsFromOneFreshScoutSightingAndRetainsExactAttackersAndDefendersAcrossSnapshot() {
        Fixture fixture = fixture(true);
        List<ProposedEvent> events = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));

        assertEquals(3, events.size());
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, events.getFirst().payload()));
        SettlementAssaultStarted started = assertInstanceOf(SettlementAssaultStarted.class, events.get(1).payload());
        assertEquals(fixture.sighting(), started.assault().sighting());
        assertTrue(started.assault().attackerIds().stream().allMatch(id -> id.value().startsWith("bioform:")));
        assertTrue(started.assault().defenderIds().stream().allMatch(id -> id.value().startsWith("resident:")));
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), started);

        SettlementAssault retained = state.strategicPlans().settlementAssaults().get(started.assault().id());
        assertEquals(started.assault(), retained);
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        ScheduledAction scheduled = assertInstanceOf(ScheduleEffect.Created.class, events.get(2).payload()).action();
        assertEquals(retained.id(), scheduled.subject());
    }

    @Test void missingFreshLocalTerritoryBlocksTheSameSightedAssaultInsteadOfRetargeting() {
        Fixture fixture = fixture(false);
        List<ProposedEvent> events = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));

        assertEquals(List.of(new StrategicTaskTransition(fixture.task().id(), StrategicTaskStatus.BLOCKED)),
                events.stream().map(ProposedEvent::payload).toList());
        assertTrue(fixture.state().strategicPlans().settlementAssaults().isEmpty());
    }

    @Test void scoutOpportunityIsBoundToTheExactSightingRatherThanAnotherSettlement() {
        Fixture fixture = fixture(true);
        ScheduledAction opportunity = StrategicObjectiveProcess.assaultOpportunity(fixture.hive(), fixture.sighting(), 200L);
        List<ProposedEvent> events = StrategicObjectiveProcess.planAssaultOpportunity(fixture.state(), opportunity);

        StrategicTask task = events.stream().map(ProposedEvent::payload).filter(StrategicTaskPlanned.class::isInstance)
                .map(StrategicTaskPlanned.class::cast).map(StrategicTaskPlanned::task).findFirst().orElseThrow();
        ScheduleEffect.Created start = events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).findFirst().orElseThrow();
        assertEquals(StrategicTaskKind.ASSAULT_SETTLEMENT, task.kind());
        assertEquals(task.id(), start.action().subject());
        assertEquals("frontier.settlement_assault.start", start.action().kind());
    }

    @Test void coldApproachAndStrikeRemainExactAndRejectAReplayedStrike() {
        Fixture fixture = fixture(true);
        FrontierWorldState state = fixture.state();
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream()
                .filter(value -> value.role() == BioformRole.GUARD || value.role() == BioformRole.BOMBER).toList()) {
            state = state.withActorLocation(bioform.id(), fixture.sighting().settlementAnchor().offset(-1, 0, 0));
        }
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(state, HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, fixture.hive(), (StrategicTaskTransition) start.getFirst().payload());
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), (SettlementAssaultStarted) start.get(1).payload());
        ScheduledAction next = ((ScheduleEffect.Created) start.get(2).payload()).action();
        for (int step = 0; step < 8; step++) {
            List<ProposedEvent> events = HiveSettlementAssaultProcess.planProgress(state, next);
            for (ProposedEvent event : events) {
                if (event.payload() instanceof SettlementAssaultAttackerAdvanced advanced) state = HiveSettlementAssaultProcess.reduceAdvanced(state, fixture.hive(), advanced);
                if (event.payload() instanceof SettlementAssaultTransition transition) state = HiveSettlementAssaultProcess.reduceTransition(state, fixture.hive(), transition);
            }
            ScheduledAction scheduled = events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                    .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).findFirst().orElseThrow();
            next = scheduled;
            if (state.strategicPlans().settlementAssaults().values().stream().anyMatch(value -> value.status() == SettlementAssaultStatus.COLD_COMBAT)) break;
        }
        SettlementAssault assault = state.strategicPlans().settlementAssaults().values().stream().findFirst().orElseThrow();
        assertEquals(SettlementAssaultStatus.COLD_COMBAT, assault.status());
        List<ProposedEvent> combat = HiveSettlementAssaultProcess.planCombat(state, next);
        SettlementAssaultStrike strike = (SettlementAssaultStrike) combat.getFirst().payload();
        ScheduledAction replacement = combat.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).findFirst().orElseThrow();
        assertTrue(!next.id().equals(replacement.id()), "a recurring COLD combat action must get a fresh identity before the current due action is consumed");
        assertEquals(strike, FrontierWorldRuntimeDefinition.payloadCodecs().decode(strike.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(strike)));
        state = HiveSettlementAssaultProcess.reduceStrike(state, fixture.hive(), strike);
        FrontierWorldState afterStrike = state;
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> HiveSettlementAssaultProcess.reduceStrike(afterStrike, fixture.hive(), strike));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void battleWaitsForDistinctCompiledFloorsAndConflictsInsteadOfMovingASeparatedDefender() {
        Fixture fixture = fixture(true); FrontierWorldState state = fixture.state();
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(state, HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, fixture.hive(), (StrategicTaskTransition) start.getFirst().payload());
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), (SettlementAssaultStarted) start.get(1).payload());
        SettlementAssault started = state.strategicPlans().settlementAssaults().values().stream().findFirst().orElseThrow();
        FrontierWorldState startedState = state;
        java.util.Set<BlockPosition> defenderFloors = started.defenderIds().stream().map(id -> startedState.actorLocations().get(id).position())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(started.attackers().size(), started.attackers().stream().map(attacker -> attacker.route().getLast()).distinct().count());
        assertTrue(started.attackers().stream().map(attacker -> attacker.route().getLast()).noneMatch(defenderFloors::contains));
        assertTrue(started.attackers().stream().noneMatch(attacker -> attacker.route().getLast().equals(started.settlementAnchor())));

        ScheduledAction next = ((ScheduleEffect.Created) start.get(2).payload()).action();
        for (int step = 0; step < 256; step++) {
            List<ProposedEvent> events = HiveSettlementAssaultProcess.planProgress(state, next);
            for (ProposedEvent event : events) {
                if (event.payload() instanceof SettlementAssaultAttackerAdvanced advanced) state = HiveSettlementAssaultProcess.reduceAdvanced(state, fixture.hive(), advanced);
                if (event.payload() instanceof SettlementAssaultTransition transition) state = HiveSettlementAssaultProcess.reduceTransition(state, fixture.hive(), transition);
            }
            next = events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                    .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).findFirst().orElseThrow();
            if (state.strategicPlans().settlementAssaults().get(started.id()).status() == SettlementAssaultStatus.WAITING_FOR_BATTLE) break;
        }
        assertEquals(SettlementAssaultStatus.WAITING_FOR_BATTLE, state.strategicPlans().settlementAssaults().get(started.id()).status());
        SubjectId defender = started.defenderIds().getFirst();
        state = state.withActorLocation(defender, started.settlementAnchor().offset(33, 0, 0));
        List<ProposedEvent> conflict = HiveSettlementAssaultProcess.planProgress(state, next);
        assertEquals(List.of(new SettlementAssaultTransition(started.id(), SettlementAssaultStatus.CONFLICT)),
                conflict.stream().map(ProposedEvent::payload).toList());
    }

    @Test void typedHotSceneRetainsAnExactCargoFreeAssaultAcrossSnapshotAndRelease() {
        Fixture fixture = fixture(true); FrontierWorldState state = fixture.state();
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(state, HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, fixture.hive(), (StrategicTaskTransition) start.getFirst().payload());
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), (SettlementAssaultStarted) start.get(1).payload());
        ScheduledAction next = ((ScheduleEffect.Created) start.get(2).payload()).action();
        for (int step = 0; step < 256; step++) {
            List<ProposedEvent> events = HiveSettlementAssaultProcess.planProgress(state, next);
            for (ProposedEvent event : events) {
                if (event.payload() instanceof SettlementAssaultAttackerAdvanced advanced) state = HiveSettlementAssaultProcess.reduceAdvanced(state, fixture.hive(), advanced);
                if (event.payload() instanceof SettlementAssaultTransition transition) state = HiveSettlementAssaultProcess.reduceTransition(state, fixture.hive(), transition);
            }
            next = events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                    .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).findFirst().orElseThrow();
            if (state.strategicPlans().settlementAssaults().values().stream().anyMatch(value -> value.status() == SettlementAssaultStatus.COLD_COMBAT)) break;
        }
        SettlementAssault assault = state.strategicPlans().settlementAssaults().values().stream().findFirst().orElseThrow();
        java.util.Map<SubjectId, BlockPosition> positions = state.coldSettlementAssaultSceneCandidates().getFirst().memberPositions();
        FrontierWorldState positioned = state;
        List<SceneMember> members = positions.keySet().stream().map(actor -> new SceneMember(actor,
                SceneLease.deterministicEntityId(positioned.bootstrap().worldId(), actor))).toList();
        SceneLease lease = SceneLease.forCause(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:assault-hot"), state.bootstrap().worldId(),
                new SettlementAssaultSceneCause(assault.id(), assault.settlementId()), assault.settlementAnchor(), new io.farfrontier.palemirror.frontier.v3.api.SimInstant(400L),
                7L, SceneLeaseStatus.PREPARED, members, positions, java.util.Set.of(), java.util.Optional.empty());
        SettlementAssaultSceneLeasePrepared payload = new SettlementAssaultSceneLeasePrepared(lease);
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
        FrontierWorldState unknown = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        unknown = FrontierSceneLeaseStateSupport.recoveryUnresolved(unknown, new SceneLeaseRecoveryUnresolved(lease.id(), java.util.Set.of(members.getFirst().actorId()), false));
        assertEquals(SettlementAssaultStatus.UNKNOWN_AFTER_RESTART, unknown.strategicPlans().settlementAssaults().get(assault.id()).status());
        assertEquals(unknown, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknown)));
        state = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT).transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        FrontierWorldState draining = state;
        List<SceneMemberPosition> captured = members.stream().map(member -> {
            ActorLocation actor = draining.actorLocations().get(member.actorId()); return new SceneMemberPosition(member.actorId(), actor.position(), actor.condition().health());
        }).toList();
        state = state.releaseSceneLease(lease.id(), captured);
        assertEquals(SettlementAssaultStatus.COLD_COMBAT, state.strategicPlans().settlementAssaults().get(assault.id()).status());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    private static Fixture fixture(boolean territory) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:assault-process-" + territory), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        state = state.withActorLocation(scout.id(), settlement.anchor());
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        StrategicPlanState plans = StrategicPlanState.empty().withHiveSettlementKnowledge(new HiveSettlementKnowledge(java.util.Map.of(settlement.id(), sighting)))
                .withHiveDoctrine(new HiveDoctrineState(HiveDoctrine.INTERDICT, 100L));
        if (territory) {
            InfectionCell cell = InfectionCell.at(settlement.anchor());
            FixedRatio intensity = new FixedRatio(FixedScalar.ONE);
            state = state.withInfection(cell, intensity);
            plans = plans.withHiveTerritoryKnowledge(new HiveTerritoryKnowledge(java.util.Map.of(cell,
                    new HiveTerritoryKnowledge.Belief(cell, intensity, scout.id(), settlement.anchor(), 100L))));
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:assault-test-" + territory), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:assault-test-" + territory), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        return new Fixture(state.withStrategicPlans(plans.addObjective(objective).addTask(task)), hive, task, sighting);
    }

    private record Fixture(FrontierWorldState state, SubjectId hive, StrategicTask task, HiveSettlementKnowledge.Sighting sighting) { }
}
