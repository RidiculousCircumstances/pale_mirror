package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveSettlementAssaultProcessTest {
    @Test void startsFromOneFreshScoutSightingAndRetainsExactAttackersAndDefendersAcrossSnapshot() {
        Fixture fixture = fixture(true);
        List<ProposedEvent> events = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));

        assertTrue(events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance).map(ScheduleEffect.Created.class::cast)
                .anyMatch(created -> created.action().kind().equals(DefenderEquipmentReturnProcess.REVIEW_ACTION)),
                "an assault schedules the independent post-resolution equipment-return review");
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, events.getFirst().payload()));
        SettlementAssaultStarted started = assertInstanceOf(SettlementAssaultStarted.class, events.get(1).payload());
        assertEquals(fixture.sighting(), started.assault().sighting());
        assertTrue(started.assault().attackerIds().stream().allMatch(id -> id.value().startsWith("bioform:")));
        assertTrue(started.assault().defenderIds().stream().allMatch(id -> id.value().startsWith("resident:")));
        assertEquals(started.assault().defenderIds().getFirst(), started.assault().defenderUnit().leaderId());
        assertEquals(HumanAssignmentKind.IDLE, HumanAssignmentProjection.compile(fixture.state()).assignment(started.assault().defenderUnit().leaderId()).kind());
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), started);

        SettlementAssault retained = state.strategicPlans().settlementAssaults().get(started.assault().id());
        assertEquals(started.assault(), retained);
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        assertTrue(retained.defenderIds().stream().allMatch(id -> {
            HumanAssignment assignment = assignments.assignment(id);
            return assignment.kind() == HumanAssignmentKind.SETTLEMENT_DEFENCE && assignment.ownerId().equals(java.util.Optional.of(retained.id()));
        }));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        ScheduledAction scheduled = scheduled(events, "frontier.settlement_assault.progress");
        assertEquals(retained.id(), scheduled.subject());
    }

    @Test void admissionDoesNotStealAnExactResidentFromAnExistingCivilianClaimAndRetainsOneStableLeader() {
        Fixture fixture = fixture(true);
        ResidentProfile resident = fixture.state().humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(fixture.sighting().settlementId()))
                .filter(value -> value.profession() == ResidentProfession.BAKER).findFirst().orElseThrow();
        SubjectId productionOwner = resident.settlementId();
        StrategicObjective productionObjective = new StrategicObjective(new SubjectId("objective:assault-occupied-production"),
                productionOwner, StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 2,
                StrategicObjectiveStatus.ACTIVE);
        StrategicTask productionTask = new StrategicTask(new SubjectId("task:assault-occupied-production"),
                productionObjective.id(), productionOwner, StrategicTaskKind.PRODUCE_BREAD, Optional.empty(),
                List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT),
                List.of(), StrategicTaskStatus.ACTIVE);
        FrontierWorldState withProductionTask = fixture.state().withStrategicPlans(fixture.state().strategicPlans()
                .addObjective(productionObjective).addTask(productionTask));
        CustodyAccount input = withProductionTask.inventory().fungibleResources().accounts().values().stream().filter(account -> account.custody()
                .equals(new ResourceCustody.Container(FrontierWorldState.depotId(resident.settlementId())))).findFirst().orElseThrow();
        ResourceLot wheat = input.lotQuantities().keySet().stream().map(withProductionTask.inventory().fungibleResources().lots()::get)
                .filter(lot -> "minecraft:wheat".equals(lot.itemKind())).findFirst().orElseThrow();
        ProductionJob job = new ProductionJob(new SubjectId("job:assault-occupied"), productionTask.id(), resident.settlementId(),
                FrontierWorldStateSupport.settlement(withProductionTask.bootstrap(), resident.settlementId()).structures().stream()
                        .filter(structure -> structure.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow().id(), resident.id(),
                wheat.id(), new ProductionInputHold.FungibleCold(wheat.id(), input.id(), new SubjectId("claim:assault-occupied")),
                new SubjectId("lot:occupied-output"), "minecraft:bread", 64);
        FrontierWorldState occupied = withProductionTask.startFungibleProductionJob(job);
        List<ProposedEvent> events = HiveSettlementAssaultProcess.planStart(occupied,
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        SettlementAssaultStarted started = assertInstanceOf(SettlementAssaultStarted.class, events.get(1).payload());
        assertTrue(!started.assault().defenderIds().contains(resident.id()));

        List<ProposedEvent> unoccupiedStart = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, unoccupiedStart.getFirst().payload()));
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(),
                assertInstanceOf(SettlementAssaultStarted.class, unoccupiedStart.get(1).payload()));
        SettlementAssault assault = state.strategicPlans().settlementAssaults().values().stream().findFirst().orElseThrow();
        SubjectId leader = assault.defenderUnit().leaderId();
        assertEquals(leader, assault.defenderIds().getFirst());
        assertEquals(assault.defenderIds(), assault.defenderUnit().memberIds());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
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
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(state, HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, fixture.hive(), (StrategicTaskTransition) start.getFirst().payload());
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), (SettlementAssaultStarted) start.get(1).payload());
        ScheduledAction next = scheduled(start, "frontier.settlement_assault.progress");
        for (int step = 0; step < 4_096; step++) {
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
        // The first deterministic attacker need not be the bomber. Advance the same ordinary
        // COLD cadence until its exact turn; no fixture inserts the aftermath itself.
        DeferredAftermathPrepared aftermath = null;
        ScheduledAction bomberTurn = replacement;
        for (int turn = 0; turn < 8 && aftermath == null; turn++) {
            List<ProposedEvent> candidate = HiveSettlementAssaultProcess.planCombat(state, bomberTurn);
            aftermath = candidate.stream().map(ProposedEvent::payload).filter(DeferredAftermathPrepared.class::isInstance)
                    .map(DeferredAftermathPrepared.class::cast).findFirst().orElse(null);
            SettlementAssaultStrike candidateStrike = candidate.stream().map(ProposedEvent::payload).filter(SettlementAssaultStrike.class::isInstance)
                    .map(SettlementAssaultStrike.class::cast).findFirst().orElseThrow();
            state = HiveSettlementAssaultProcess.reduceStrike(state, fixture.hive(), candidateStrike);
            bomberTurn = candidate.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                    .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).findFirst().orElseThrow();
        }
        assertTrue(aftermath != null, "the real COLD bomber turn must commit one deferred aftermath without a visit");
        assertEquals(DeferredAftermathKnowledge.KNOWN_CLEAR, aftermath.aftermath().knowledge());
        assertEquals(DeferredAftermathCellStatus.PENDING, aftermath.aftermath().nextPending().status());
        assertEquals(aftermath, FrontierWorldRuntimeDefinition.payloadCodecs().decode(aftermath.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(aftermath)));
        java.util.Map<SubjectId, BlockPosition> originalPositions = state.coldSettlementAssaultSceneCandidates().getFirst().memberPositions();
        SubjectId casualty = null;
        for (int turn = 0; turn < 512 && casualty == null; turn++) {
            List<ProposedEvent> candidate = HiveSettlementAssaultProcess.planCombat(state, bomberTurn);
            SettlementAssaultStrike ordinary = candidate.stream().map(ProposedEvent::payload)
                    .filter(SettlementAssaultStrike.class::isInstance).map(SettlementAssaultStrike.class::cast).findFirst().orElseThrow();
            state = HiveSettlementAssaultProcess.reduceStrike(state, fixture.hive(), ordinary);
            if (state.actorLocations().get(ordinary.targetId()).condition().status() == ActorLifeStatus.DEAD) casualty = ordinary.targetId();
            bomberTurn = scheduled(candidate, "frontier.settlement_assault.combat");
        }
        assertTrue(casualty != null, "an ordinary lethal strike must reach survivor continuation, not just the first bomber turn");
        assertEquals(assault.attackerIds(), state.strategicPlans().settlementAssaults().get(assault.id()).attackerIds());
        assertEquals(assault.defenderIds(), state.strategicPlans().settlementAssaults().get(assault.id()).defenderIds());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        List<ProposedEvent> survivorTurn = HiveSettlementAssaultProcess.planCombat(state, bomberTurn);
        assertEquals(survivorTurn, HiveSettlementAssaultProcess.planCombat(recovered, bomberTurn));
        SettlementAssaultStrike survivorStrike = survivorTurn.stream().map(ProposedEvent::payload)
                .filter(SettlementAssaultStrike.class::isInstance).map(SettlementAssaultStrike.class::cast).findFirst().orElseThrow();
        assertFalse(survivorStrike.attackerId().equals(casualty));
        assertFalse(survivorStrike.targetId().equals(casualty));
        state = HiveSettlementAssaultProcess.reduceStrike(state, fixture.hive(), survivorStrike);
        java.util.Map<SubjectId, BlockPosition> survivors = state.coldSettlementAssaultSceneCandidates().getFirst().memberPositions();
        assertFalse(survivors.containsKey(casualty), "fresh physical admission must not create a body for a retained casualty");
        assertEquals(originalPositions.size() - 1, survivors.size());
        SubjectId dead = casualty;
        FrontierWorldState afterCasualty = state;
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ActorBodyAuthority.demand(afterCasualty, dead));
        SceneLease staleRoster = battleLease(state, assault, originalPositions, "lease:assault-dead-roster");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> afterCasualty.prepareSceneLease(staleRoster));
        SceneLease survivorLease = battleLease(state, assault, survivors, "lease:assault-survivors");
        state = state.prepareSceneLease(survivorLease);
        FrontierWorldState withoutPhysicalPresence = state;
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> withoutPhysicalPresence.transitionSceneLease(survivorLease.id(), SceneLeaseStatus.HOT));
        state = FrontierTestActorBodies.present(state, survivorLease).transitionSceneLease(survivorLease.id(), SceneLeaseStatus.HOT);
        assertEquals(SettlementAssaultStatus.HOT, state.strategicPlans().settlementAssaults().get(assault.id()).status());
        assertFalse(ActorExecutionCoordinator.coldAvailable(state, survivors.keySet()));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void defenderEquipmentReviewCannotAdvanceTheAssaultApproach() {
        Fixture fixture = fixture(true);
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(fixture.state(), HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, start.getFirst().payload()));
        SettlementAssault assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), new SettlementAssaultStarted(assault, io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.admission(state, assault)));

        assertTrue(HiveSettlementAssaultProcess.planProgress(state, DefenderEquipmentProcess.review(assault, 201L)).isEmpty());
    }

    @Test void assaultFloorsRequireOneDeclaredGrayboxProviderAndRejectTheRetainedUnclaimedColumn() {
        Fixture fixture = fixture(true);
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        SettlementAssault assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
        java.util.List<BlockPosition> supports = new java.util.ArrayList<>(assault.attackers().stream()
                .map(attacker -> attacker.route().getLast()).toList());
        supports.addAll(assault.defenderIds().stream().map(id -> fixture.state().actorLocations().get(id).supportingSurface().support()).toList());
        assertEquals(assault.attackerIds().size() + assault.defenderIds().size(), supports.size());
        assertTrue(supports.stream().allMatch(position -> FrontierSettlementAssaultBattlefield.serviceableFloor(fixture.state(), position)),
                "every admitted assault support must have one declared provider surface and planned headroom");

        BlockPosition retainedR11AttackerFloor = new BlockPosition(-362, 64, -346);
        assertFalse(FrontierSettlementAssaultBattlefield.serviceableFloor(fixture.state(), retainedR11AttackerFloor),
                "the retained R11 attacker floor is clear terrain, not a declared graybox provider surface");

        BlockPosition unavailable = assault.attackers().getFirst().route().getLast();
        GrayboxCell provider = FrontierGrayboxPlan.compile(fixture.state()).cells().get(unavailable);
        java.util.Map<BlockPosition, PhysicalDelta> losses = new java.util.LinkedHashMap<>(fixture.state().physicalDeltas());
        losses.put(unavailable, new PhysicalDelta(unavailable, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(provider.semanticTarget()), Optional.of(provider.semanticPart()), "test:assault-provider-unavailable"));
        FrontierWorldState unavailableState = fixture.state().withChanges(FrontierWorldStateUpdate.begin().physicalDeltas(losses));
        assertFalse(FrontierSettlementAssaultBattlefield.serviceableFloor(unavailableState, unavailable),
                "a missing declared provider surface must fail admission instead of becoming an opportunistic floor");
        assertTrue(unavailableState.coldSettlementAssaultSceneCandidates().isEmpty(),
                "one unavailable registered support must keep the complete assault out of scene admission");
    }

    @Test void attackerFloorsUseOnlyTheBoundedResidentApronInsteadOfIngressOrRouteSurfaces() {
        Fixture fixture = fixture(true);
        Settlement settlement = FrontierWorldStateSupport.settlement(fixture.state().bootstrap(), fixture.sighting().settlementId());
        SettlementResidentIngressPlan.Plan ingress = SettlementResidentIngressPlan.compile(fixture.state().bootstrap().bounds(),
                fixture.state().bootstrap().terrain(), settlement, fixture.state().bootstrap().ruleset().facilityCapacity().intactHousingBeds());
        java.util.Set<BlockPosition> perimeter = ingress.perimeterSurfaces().stream().map(SurfaceAnchor::support)
                .collect(java.util.stream.Collectors.toSet());
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        SettlementAssault assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();

        assertTrue(assault.attackers().stream().map(attacker -> attacker.route().getLast()).allMatch(perimeter::contains),
                "every attacker destination must be one of the bounded resident-apron perimeter floors, never a Hall connector, ingress ramp, or generic route");
        BlockPosition hallConnector = settlement.anchor().offset(-7, 0, 0);
        BlockPosition ingressRamp = ingress.ownedSurfaces().stream().map(SurfaceAnchor::support)
                .filter(position -> Math.abs(position.x() - settlement.anchor().x()) > 30 || Math.abs(position.z() - settlement.anchor().z()) > 30)
                .findFirst().orElseThrow();
        BlockPosition genericRoute = FrontierGrayboxPlan.compile(fixture.state()).cells().values().stream()
                .filter(cell -> cell.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE).map(GrayboxCell::position).findFirst().orElseThrow();
        assertFalse(perimeter.contains(hallConnector), "the known Hall connector is declared provider geometry but not an assault perimeter");
        assertFalse(perimeter.contains(ingressRamp), "the ingress ramp is declared provider geometry but not an assault perimeter");
        assertFalse(perimeter.contains(genericRoute), "an arbitrary generic route surface is not an assault perimeter");
    }

    @Test void battleWaitsForDistinctCompiledFloorsAndConflictsInsteadOfMovingASeparatedDefender() {
        Fixture fixture = fixture(true); FrontierWorldState state = fixture.state();
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(state, HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, fixture.hive(), (StrategicTaskTransition) start.getFirst().payload());
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), (SettlementAssaultStarted) start.get(1).payload());
        SettlementAssault started = state.strategicPlans().settlementAssaults().values().stream().findFirst().orElseThrow();
        FrontierWorldState startedState = state;
        java.util.Set<BlockPosition> defenderFloors = started.defenderIds().stream().map(id -> startedState.actorLocations().get(id).supportingSurface().support())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(started.attackers().size(), started.attackers().stream().map(attacker -> attacker.route().getLast()).distinct().count());
        assertTrue(started.attackers().stream().map(attacker -> attacker.route().getLast()).noneMatch(defenderFloors::contains));
        assertTrue(started.attackers().stream().noneMatch(attacker -> attacker.route().getLast().equals(started.settlementAnchor())));

        ScheduledAction next = scheduled(start, "frontier.settlement_assault.progress");
        for (int step = 0; step < 4_096; step++) {
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
        state = state.withActorBody(defender, BodyPosition.above(new SurfaceAnchor(started.settlementAnchor().offset(43, 0, 0))));
        List<ProposedEvent> conflict = HiveSettlementAssaultProcess.planProgress(state, next);
        assertEquals(List.of(TerminalDiagnosticProducer.assaultConflict(started.id(), io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.current(state, started))),
                conflict.stream().map(ProposedEvent::payload).toList());
        var transition = (SettlementAssaultTransition) conflict.getFirst().payload();
        assertEquals(DiagnosticReason.SETTLEMENT_ASSAULT_CONFLICT, transition.diagnostic().orElseThrow().reason());
        assertEquals(started.id(), transition.diagnostic().orElseThrow().subject().id());
        var retained = HiveSettlementAssaultProcess.reduceTransition(state, fixture.hive(), transition);
        assertEquals(state.actorLocations().get(defender), retained.actorLocations().get(defender),
                "local conflict must not teleport the separated defender back into battle");
    }

    @Test void battlefieldEnvelopeComesFromTheResidentApronRatherThanTheFormerThirtyTwoBlockCircle() {
        Fixture fixture = fixture(true);
        Settlement settlement = FrontierWorldStateSupport.settlement(fixture.state().bootstrap(), fixture.sighting().settlementId());
        SubjectId perimeterResident = fixture.state().humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(settlement.id()))
                .filter(value -> {
                    BlockPosition position = fixture.state().actorLocations().get(value.id()).supportingSurface().support();
                    long x = (long) position.x() - settlement.anchor().x(), z = (long) position.z() - settlement.anchor().z();
                    return x * x + z * z > 32L * 32L;
                }).map(ResidentProfile::id).findFirst().orElseThrow();

        assertTrue(FrontierSettlementAssaultBattlefield.attackerFloors(fixture.state(), fixture.sighting(), List.of(perimeterResident), 1).isPresent(),
                "one exact resident on the immutable apron remains a valid defender, not an off-map body");
        FrontierWorldState outside = fixture.state().withActorBody(perimeterResident,
                BodyPosition.above(new SurfaceAnchor(settlement.anchor().offset(43, 0, 0))));
        assertTrue(FrontierSettlementAssaultBattlefield.attackerFloors(outside, fixture.sighting(), List.of(perimeterResident), 1).isEmpty(),
                "the battlefield remains bounded by the declared resident plan instead of admitting arbitrary actor coordinates");
    }

    @Test void typedHotSceneRetainsAnExactCargoFreeAssaultAcrossSnapshotAndRelease() {
        Fixture fixture = fixture(true); FrontierWorldState state = fixture.state();
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(state, HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, fixture.hive(), (StrategicTaskTransition) start.getFirst().payload());
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), (SettlementAssaultStarted) start.get(1).payload());
        ScheduledAction next = scheduled(start, "frontier.settlement_assault.progress");
        for (int step = 0; step < 4_096; step++) {
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
        assertEquals(java.util.Set.copyOf(positions.keySet()), FrontierSceneAdmission.reservedActors(state),
                "the shared reservation index must retain every exact COLD assault member and no substitute body");
        FrontierWorldState positioned = state;
        List<SceneMember> members = positions.keySet().stream().map(actor -> new SceneMember(actor,
                SceneLease.deterministicEntityId(positioned.bootstrap().worldId(), actor))).toList();
        SceneLease lease = SceneLease.forCause(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:assault-hot"), state.bootstrap().worldId(),
                new SettlementAssaultSceneCause(assault.id(), assault.settlementId()), assault.settlementAnchor(), new io.farfrontier.palemirror.frontier.v3.api.SimInstant(400L),
                7L, SceneLeaseStatus.PREPARED, members, java.util.Set.of(), java.util.Optional.empty());
        SettlementAssaultSceneLeasePrepared payload = new SettlementAssaultSceneLeasePrepared(lease);
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
        FrontierWorldState unknown = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        unknown = FrontierSceneLeaseStateSupport.recoveryUnresolved(unknown, new SceneLeaseRecoveryUnresolved(lease.id(), java.util.Set.of(members.getFirst().actorId())));
        assertEquals(SettlementAssaultStatus.UNKNOWN_AFTER_RESTART, unknown.strategicPlans().settlementAssaults().get(assault.id()).status());
        assertEquals(unknown, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknown)));
        state = FrontierTestActorBodies.present(state.prepareSceneLease(lease), lease)
                .transitionSceneLease(lease.id(), SceneLeaseStatus.HOT).transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        FrontierWorldState draining = state;
        List<SceneMemberPosition> captured = members.stream().map(member -> {
            ActorLocation actor = draining.actorLocations().get(member.actorId()); return new SceneMemberPosition(member.actorId(), actor.body(), actor.condition().health());
        }).toList();
        state = state.releaseSceneLease(lease.id(), captured);
        assertEquals(SettlementAssaultStatus.COLD_COMBAT, state.strategicPlans().settlementAssaults().get(assault.id()).status());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void coldProgressUsesTheBoundedDurableAssaultOwnerSetBeforeTheSelectorCompilesItsProvider() {
        Fixture fixture = fixture(true);
        FrontierWorldState state = fixture.state();
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(state,
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, fixture.hive(), (StrategicTaskTransition) start.getFirst().payload());
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), (SettlementAssaultStarted) start.get(1).payload());
        ScheduledAction next = scheduled(start, "frontier.settlement_assault.progress");
        ScheduledAction terminal = null;

        for (int step = 0; step < 4_096; step++) {
            List<ProposedEvent> events = HiveSettlementAssaultProcess.planProgress(state, next);
            for (ProposedEvent event : events) {
                if (event.payload() instanceof SettlementAssaultAttackerAdvanced advanced) state = HiveSettlementAssaultProcess.reduceAdvanced(state, fixture.hive(), advanced);
                if (event.payload() instanceof SettlementAssaultTransition transition) state = HiveSettlementAssaultProcess.reduceTransition(state, fixture.hive(), transition);
            }
            terminal = events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                    .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).findFirst().orElseThrow();
            if (state.strategicPlans().settlementAssaults().get(next.subject()).status() == SettlementAssaultStatus.COLD_COMBAT) break;
            next = terminal;
        }

        SettlementAssault assault = state.strategicPlans().settlementAssaults().values().stream().findFirst().orElseThrow();
        assertEquals(SettlementAssaultStatus.COLD_COMBAT, assault.status());
        assertEquals(1, state.strategicPlans().settlementAssaults().size(), "the COLD driver reads its one bounded durable owner");
        assertEquals("frontier.settlement_assault.combat", terminal.kind(), "COLD progress preserves the ordinary registered combat owner");
    }

    @Test void hotAndColdAssaultViewsDeriveOneExactStrikeCauseWithoutModeSpecificAliases() {
        SubjectId assault = new SubjectId("assault:shared-cause");
        SubjectId attacker = new SubjectId("bioform:shared-bomber");
        SettlementAssaultSceneCause hot = new SettlementAssaultSceneCause(assault, new SubjectId("settlement:northwatch"));

        SubjectId coldCause = SettlementAssaultCauseIdentity.strike(assault, attacker, 4L);
        assertEquals(coldCause, SettlementAssaultCauseIdentity.strike(hot, attacker, 4L),
                "HOT presentation and COLD scheduling must correlate the same semantic strike");
        assertTrue(!coldCause.equals(SettlementAssaultCauseIdentity.strike(hot, new SubjectId("bioform:other-bomber"), 4L)));
        assertTrue(!coldCause.equals(SettlementAssaultCauseIdentity.strike(hot, attacker, 5L)));
    }

    private static Fixture fixture(boolean territory) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:assault-process-" + territory), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
        state = FrontierTestPositions.deployBioform(state, scout.id(), BodyPosition.above(new SurfaceAnchor(settlement.anchor())));
        for (Bioform bioform : state.bootstrap().hive().bioforms()) {
            if (bioform.isExplosiveAssaulter() || bioform.isDefender() || bioform.isOverseer()) {
                // External mobilisation uses the authored tray floor, not the dormant feet-cell datum.
                state = FrontierTestPositions.deployBioform(state, bioform.id(),
                        state.actorLocations().get(bioform.id()).body().offset(0, -1, 0));
            }
        }
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        StrategicPlanState plans = state.strategicPlans().withHiveSettlementKnowledge(new HiveSettlementKnowledge(java.util.Map.of(settlement.id(), sighting)))
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

    private static ScheduledAction scheduled(List<ProposedEvent> events, String kind) {
        return events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).filter(action -> action.kind().equals(kind))
                .findFirst().orElseThrow(() -> new AssertionError("missing scheduled action: " + kind));
    }

    private static SceneLease battleLease(FrontierWorldState state, SettlementAssault assault,
            java.util.Map<SubjectId, BlockPosition> positions, String id) {
        List<SceneMember> members = positions.keySet().stream().sorted().map(actor -> new SceneMember(actor,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor))).toList();
        return SceneLease.forCause(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(id), state.bootstrap().worldId(),
                new SettlementAssaultSceneCause(assault.id(), assault.settlementId()), assault.settlementAnchor(), new SimInstant(400L),
                7L, SceneLeaseStatus.PREPARED, members, java.util.Set.of(), Optional.empty());
    }

    private record Fixture(FrontierWorldState state, SubjectId hive, StrategicTask task, HiveSettlementKnowledge.Sighting sighting) { }
}
