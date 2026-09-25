package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StrategicScheduleRetirementTest {
    @Test
    void compactionCancelsExactPulseInSameWalAndReplaysWithoutDanglingReference() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:strategic-retirement"), 41L);
        var initial = fullHistory(base.initialState());
        var oldest = initial.strategicPlans().tasks().get(new SubjectId("task:retention-1"));
        var retained = initial.strategicPlans().tasks().get(new SubjectId("task:retention-128"));
        var pulse = HiveInfectionProcess.task(oldest, 7, 10_000L);
        var otherPulse = HiveInfectionProcess.task(retained, 2, 10_000L);
        var review = StrategicObjectiveProcess.review(initial.bootstrap().hive().id(), 129, 1L);
        var wal = new ArrayList<TransactionRecord>();
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), initial, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(review, pulse, otherPulse), (transaction, durability) -> wal.add(transaction), base.stateValidator());
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        engine.advanceTo(new SimInstant(1L), new WorkBudget(1, 100));
        assertEquals("ACTIVE", engine.status().kind().name(), engine.status().toString());
        assertEquals(1, wal.size());
        assertFalse(engine.canonicalState().state().strategicPlans().tasks().containsKey(oldest.id()));
        assertFalse(engine.checkpoint().schedules().contains(pulse));
        assertTrue(engine.checkpoint().schedules().contains(otherPulse), "unrelated retained task must keep its pulse");
        assertEquals(List.of(new ScheduleEffect.Cancelled(pulse.id())), wal.getFirst().events().stream()
                .map(FrontierEvent::payload).filter(ScheduleEffect.Cancelled.class::isInstance).toList());
        var recovered = FrontierEngines.recoverCanonicalStateAccess(configuration,
                new RecoveryImage(base.worldId(), Optional.empty(), wal));
        assertArrayEquals(engine.checkpoint().canonicalState(), recovered.checkpoint().canonicalState());
        assertEquals(engine.checkpoint().schedules(), recovered.checkpoint().schedules());

        var selected = wal.getFirst().events().stream()
                .filter(event -> event.payload() instanceof StrategicObjectiveSelected).findFirst().orElseThrow();
        var fixturePlanner = FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(base.worldId(), 41L).scheduledPlanner();
        assertEquals(List.of(pulse), fixturePlanner.retiredBy(initial, engine.canonicalState().state(), selected,
                () -> List.of(pulse, otherPulse)), "native fixture wrappers must preserve the production retirement policy");

        var failedWrite = FrontierEngines.createCanonicalStateAccess(configuration.withTransactionCommitter((transaction, durability) -> {
            throw new IllegalStateException("injected WAL failure");
        }));
        failedWrite.advanceTo(new SimInstant(1L), new WorkBudget(1, 100));
        assertEquals("QUARANTINED", failedWrite.status().kind().name());
        assertTrue(failedWrite.canonicalState().state().strategicPlans().tasks().containsKey(oldest.id()));
        assertTrue(failedWrite.checkpoint().schedules().contains(pulse), "retirement and cancellation must publish together after WAL only");

        // The same domain fixture with the pre-fix planner policy reproduces the live quarantine.
        var oldPolicy = new FrontierEngineConfiguration<>(base.worldId(), initial, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner()::plan, base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(review, pulse, otherPulse), TransactionCommitter.noOp(), base.stateValidator());
        var broken = FrontierEngines.create(oldPolicy);
        broken.advanceTo(new SimInstant(1L), new WorkBudget(1, 100));
        assertEquals("QUARANTINED", broken.status().kind().name());
        assertTrue(broken.checkpoint().schedules().contains(pulse), "failed transaction must not publish partial cancellation");
    }

    @Test
    void unchangedOwnerDoesNotScanQueueAndMissingUnrelatedSubjectsAreNotRepaired() {
        var state = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:retirement-negative"), 41L).initialState();
        assertTrue(StrategicScheduleRetirement.retiredBy(state, state, () -> { throw new AssertionError("queue scanned"); }).isEmpty());
        var absent = new ScheduledAction(new ScheduleId("schedule:unrelated-missing"), new SimInstant(1L), 0,
                new SubjectId("task:missing"), "frontier.hive.infection.task", 1);
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateRecoveryInitial(state, List.of(absent)));
    }

    private static FrontierWorldState fullHistory(FrontierWorldState state) {
        var plans = state.strategicPlans();
        var owner = state.bootstrap().hive().id();
        var authority = plans.requireDecisionAuthority(owner);
        var target = Optional.of(InfectionCell.at(state.bootstrap().hive().seedNests().getFirst().anchor()));
        for (int i = 1; i <= StrategicPlanState.MAX_OBJECTIVES; i++) {
            var objective = new StrategicObjective(new SubjectId("objective:retention-" + i), owner,
                    StrategicObjectiveKind.HIVE_EXPAND_INFECTION, target, Optional.empty(), i,
                    StrategicObjectiveStatus.ACTIVE, authority.ownerId(), authority.reconsiderationEpoch());
            var task = new StrategicTask(new SubjectId("task:retention-" + i), objective.id(), owner,
                    StrategicTaskKind.SPREAD_INFECTION_CELL, target, Optional.empty(), Optional.empty(),
                    List.of(StrategicTaskRequirement.OPERATIONAL_GANGLION), List.of(), StrategicTaskStatus.PENDING,
                    Optional.empty(), authority.ownerId(), authority.reconsiderationEpoch());
            plans = plans.addObjective(objective).addTask(task).transitionTask(task.id(), StrategicTaskStatus.BLOCKED);
        }
        return state.withStrategicPlans(plans);
    }
}
