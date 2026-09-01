package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveInfectionProcessTest {
    @Test
    void liveHeartReseedsThroughItsDurableExpansionTaskAfterExactDecontamination() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection"), 105L));
        for (InfectionCell cell : List.copyOf(state.infection().keySet())) state = state.withInfection(cell, new FixedRatio(FixedScalar.ZERO));
        InfectionCell target = HiveInfectionProcess.expansionTarget(state, 0L).orElseThrow();
        state = withTask(state, target);

        List<ProposedEvent> planned = HiveInfectionProcess.plan(state, HiveInfectionProcess.task(onlyTask(state), 1, 100L));

        assertEquals(new StrategicTaskTransition(onlyTask(state).id(), StrategicTaskStatus.ACTIVE), planned.getFirst().payload());
        InfectionChanged changed = assertInstanceOf(InfectionChanged.class, planned.get(1).payload());
        assertEquals(target, changed.cell());
        assertTrue(changed.intensity().value().raw() > 0L);
        ScheduleEffect.Created successor = assertInstanceOf(ScheduleEffect.Created.class, planned.get(2).payload());
        assertEquals(100L + state.bootstrap().ruleset().cadence().hiveInfectionPulseInterval(), successor.action().dueAt().ticks(),
                "COLD infection must wait for its next visible semantic boundary");
    }

    @Test
    void destroyedHeartsBlockTheOwnedTaskInsteadOfContinuingOwnerlessMetabolism() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection-destroyed"), 106L));
        InfectionCell target = HiveInfectionProcess.expansionTarget(state, 0L).orElseThrow();
        for (HiveOrgan heart : state.bootstrap().hive().organs().stream().filter(organ -> organ.kind() == HiveOrganKind.HEART).toList()) {
            int threshold = (FrontierGrayboxPlan.intactOrganCellCount(heart) + 2) / 3;
            List<GrayboxCell> cells = FrontierGrayboxPlan.compile(state).cells().values().stream().filter(cell -> cell.ownerId().equals(heart.id()))
                    .sorted(java.util.Comparator.comparingInt((GrayboxCell cell) -> cell.position().x())
                            .thenComparingInt(cell -> cell.position().y()).thenComparingInt(cell -> cell.position().z())).toList();
            for (int index = 0; index < threshold; index++) {
                GrayboxCell cell = cells.get(index);
                state = state.recordPhysicalDelta(new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                        Optional.of(heart.id()), Optional.of(cell.semanticPart()), "test:heart-loss"));
            }
        }
        state = withTask(state, target);

        List<ProposedEvent> planned = HiveInfectionProcess.plan(state, HiveInfectionProcess.task(onlyTask(state), 1, 100L));

        assertEquals(List.of(new ProposedEvent(state.bootstrap().hive().id(), new StrategicTaskTransition(onlyTask(state).id(), StrategicTaskStatus.BLOCKED))), planned);
    }

    @Test
    void compactedPreemptedTaskLeavesItsStalePulseAsOneSafeNoOp() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection-stale"), 107L));
        SubjectId staleTask = new SubjectId("task:hive-preempted-infection");
        var action = new io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction(
                new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:hive-infection-task-hive-preempted-infection-1"),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(100L), 0, staleTask, "frontier.hive.infection.task", 1);

        assertEquals(List.of(new ProposedEvent(staleTask, new ScheduleEffect.Cancelled(action.id()))), HiveInfectionProcess.plan(state, action));
    }

    @Test
    void expansionTargetUsesOnlyFreshLocalTerritoryBeliefs() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection-order"), 108L));
        for (InfectionCell cell : List.copyOf(state.infection().keySet())) state = state.withInfection(cell, new FixedRatio(FixedScalar.ZERO));
        state = state.withInfection(new InfectionCell(-12, -8), new FixedRatio(new FixedScalar(750_000L)));
        state = state.withInfection(new InfectionCell(-11, -8), new FixedRatio(new FixedScalar(125_000L)));
        state = state.withInfection(new InfectionCell(-10, -8), new FixedRatio(new FixedScalar(500_000L)));
        state = state.withInfection(new InfectionCell(30, 14), new FixedRatio(new FixedScalar(250_000L)));

        Map<InfectionCell, FixedRatio> local = Map.of(new InfectionCell(-12, -8), new FixedRatio(new FixedScalar(750_000L)),
                new InfectionCell(-11, -8), new FixedRatio(new FixedScalar(125_000L)), new InfectionCell(-10, -8), new FixedRatio(new FixedScalar(500_000L)));
        state = withTerritoryKnowledge(state, local, new BlockPosition(-44, 64, -32), 0L);

        assertEquals(sortedReferenceTarget(state.bootstrap().bounds(), local), HiveInfectionProcess.expansionTarget(state, 0L),
                "a remote canonical cell must not participate until a hive sensor has observed it");
    }

    @Test
    void freshScoutSettlementSightingTurnsLocalExpansionIntoTerritorialPressure() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection-pressure"), 110L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        List<Bioform> scouts = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).toList();
        Bioform observer = scouts.getFirst(), territorySensor = scouts.get(1);
        state = state.withActorBody(observer.id(), FrontierTestPositions.bodyAboveSupport(settlement.anchor()));
        HiveSettlementObserved observation = new HiveSettlementObserved(new HiveSettlementKnowledge.Sighting(settlement.id(), observer.id(), settlement.anchor(), 0L));
        state = HiveSettlementPerceptionProcess.reduce(state, state.bootstrap().hive().id(), observation)
                .withActorBody(observer.id(), FrontierTestPositions.bodyAboveSupport(state.bootstrap().hive().seedNests().getFirst().anchor()));
        Map<InfectionCell, FixedRatio> local = Map.of(new InfectionCell(-12, -8), new FixedRatio(new FixedScalar(750_000L)),
                new InfectionCell(-11, -8), new FixedRatio(new FixedScalar(125_000L)), new InfectionCell(-10, -8), new FixedRatio(new FixedScalar(500_000L)));
        Map<InfectionCell, HiveTerritoryKnowledge.Belief> beliefs = new LinkedHashMap<>();
        BlockPosition sensorPosition = new BlockPosition(-44, 64, -32);
        local.forEach((cell, intensity) -> beliefs.put(cell, new HiveTerritoryKnowledge.Belief(cell, intensity, territorySensor.id(), sensorPosition, 0L)));
        state = state.withActorBody(territorySensor.id(), FrontierTestPositions.bodyAboveSupport(sensorPosition)).withStrategicPlans(state.strategicPlans()
                .withHiveTerritoryKnowledge(new HiveTerritoryKnowledge(beliefs)));

        assertEquals(new InfectionCell(-13, -8), HiveInfectionProcess.expansionTarget(state, 0L).orElseThrow(),
                "the next sensed frontier cell must reduce distance to the scout-observed settlement, not use global geometry");
    }

    @Test
    void persistentFrontierMatchesTheCompleteReferenceAcrossGrowthAndRetreat() {
        WorldBounds bounds = new WorldBounds(-64, -64, 128, 128);
        Map<InfectionCell, FixedRatio> infection = new LinkedHashMap<>();
        FrontierInfectionFrontier frontier = FrontierInfectionFrontier.compile(bounds, infection);
        Random random = new Random(884422L);

        for (int step = 0; step < 512; step++) {
            InfectionCell changed = new InfectionCell(-15 + random.nextInt(31), -15 + random.nextInt(31));
            Map<InfectionCell, FixedRatio> next = new LinkedHashMap<>(infection);
            if (random.nextInt(5) == 0) next.remove(changed);
            else next.put(changed, new FixedRatio(new FixedScalar((1L + random.nextInt(8)) * 125_000L)));
            frontier = frontier.changed(infection, next, changed);
            infection = next;
            assertEquals(sortedReferenceTarget(bounds, infection), frontier.best(infection), "step " + step);
        }
    }

    @Test
    void sparsePersistentMapRetainsExactMembershipAcrossBoundedDeltaCompaction() {
        Map<InfectionCell, FixedRatio> reference = new LinkedHashMap<>();
        PersistentInfectionMap persistent = PersistentInfectionMap.from(reference);
        Random random = new Random(912_441L);

        for (int step = 0; step < 256; step++) {
            InfectionCell changed = new InfectionCell(-16 + random.nextInt(33), -16 + random.nextInt(33));
            if (random.nextInt(4) == 0) {
                reference.remove(changed);
                persistent = persistent.changed(changed, null);
            } else {
                FixedRatio value = new FixedRatio(new FixedScalar((1L + random.nextInt(8)) * 125_000L));
                reference.put(changed, value);
                persistent = persistent.changed(changed, value);
            }
            assertEquals(reference, persistent, "delta step " + step);
            assertEquals(reference.entrySet(), persistent.entrySet(), "enumerated delta view at step " + step);
            for (InfectionCell candidate : List.of(changed, new InfectionCell(changed.x() + 1, changed.z()),
                    new InfectionCell(changed.x() - 1, changed.z()))) {
                assertEquals(reference.containsKey(candidate), persistent.containsKey(candidate), "membership at step " + step);
                assertEquals(reference.get(candidate), persistent.get(candidate), "value at step " + step);
            }
        }
    }

    @Test
    void oneInfectionPulseAndItsTaskTransitionShareTheDependencyAwarePreWalAudit() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection-combined-audit"), 109L));
        InfectionCell target = HiveInfectionProcess.expansionTarget(initial, 0L).orElseThrow();
        FrontierWorldState previous = withTask(initial, target);
        StrategicTask task = onlyTask(previous);
        FrontierWorldState next = previous.withInfection(target, new FixedRatio(new FixedScalar(250_000L)))
                .withStrategicPlans(previous.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.ACTIVE));

        assertDoesNotThrow(() -> next.validateTransitionFrom(previous));
    }

    private static FrontierWorldState withTask(FrontierWorldState state, InfectionCell target) {
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-infection"), hive, StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                Optional.of(target), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-infection"), objective.id(), hive, StrategicTaskKind.SPREAD_INFECTION_CELL,
                Optional.of(target), List.of(StrategicTaskRequirement.OPERATIONAL_HEART), List.of(), StrategicTaskStatus.PENDING);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }

    private static StrategicTask onlyTask(FrontierWorldState state) { return state.strategicPlans().tasks().values().stream().findFirst().orElseThrow(); }

    private static FrontierWorldState withTerritoryKnowledge(FrontierWorldState state, Map<InfectionCell, FixedRatio> cells, BlockPosition scoutPosition, long observedAt) {
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        Map<InfectionCell, HiveTerritoryKnowledge.Belief> beliefs = new LinkedHashMap<>();
        cells.forEach((cell, intensity) -> beliefs.put(cell, new HiveTerritoryKnowledge.Belief(cell, intensity, scout.id(), scoutPosition, observedAt)));
        return state.withActorBody(scout.id(), FrontierTestPositions.bodyAboveSupport(scoutPosition)).withStrategicPlans(state.strategicPlans()
                .withHiveTerritoryKnowledge(new HiveTerritoryKnowledge(beliefs)));
    }

    private static Optional<InfectionCell> sortedReferenceTarget(FrontierWorldState state) {
        return sortedReferenceTarget(state.bootstrap().bounds(), state.infection());
    }

    private static Optional<InfectionCell> sortedReferenceTarget(WorldBounds bounds, Map<InfectionCell, FixedRatio> infection) {
        LinkedHashSet<InfectionCell> candidates = new LinkedHashSet<>();
        infection.keySet().stream().sorted(Comparator.comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z)).forEach(source ->
                List.of(new InfectionCell(source.x() + 1, source.z()), new InfectionCell(source.x(), source.z() + 1),
                        new InfectionCell(source.x() - 1, source.z()), new InfectionCell(source.x(), source.z() - 1)).stream()
                        .filter(cell -> bounds.contains(cell.originAtY(64))).forEach(candidates::add));
        return candidates.stream().sorted(Comparator.comparingLong((InfectionCell cell) -> infection
                .getOrDefault(cell, new FixedRatio(FixedScalar.ZERO)).value().raw()).thenComparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z)).findFirst();
    }
}
