package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.process.HiveMobilizationProcess;
import io.farfrontier.palemirror.frontier.v3.process.HiveSettlementAssaultProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveMobilizationProcessTest {
    @Test void registeredPhysicalReleaseCommandAdmitsTheExactWakingGroup() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        HiveMobilizationStarted started = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload());
        state = HiveMobilizationProcess.reduceStarted(state, fixture.hive(), started);
        CommandId commandId = new CommandId("command:hive-mobilization-release");

        CommandPlan plan = FrontierWorldRuntimeDefinition.planCommand(state, new FrontierCommand(1, commandId,
                state.bootstrap().worldId(), Revision.ZERO, new SimInstant(200L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(commandId), new HiveMobilizationReleaseStarted(started.mobilization().id())));

        assertInstanceOf(CommandPlan.Accepted.class, plan);
    }

    @Test void assaultTaskWakesOnlyExactDormantMembersThenAdmitsTheWholeObservedGroupWithoutASyntheticBody() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        assertEquals(2, planned.size());
        StrategicTaskTransition active = assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload());
        HiveMobilizationStarted started = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload());

        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(), active);
        state = HiveMobilizationProcess.reduceStarted(state, fixture.hive(), started);
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(started.mobilization().id());
        assertEquals(HiveMobilizationStatus.WAKING, mobilization.status());
        FrontierWorldState waking = state;
        assertTrue(mobilization.memberIds().stream().allMatch(member -> waking.hiveColony().bioformLifecycles().get(member).phase() == BioformLifecyclePhase.WAKING));
        SubjectId first = mobilization.memberIds().getFirst();
        HiveCocoonSlot firstSlot = waking.hiveColony().bioformLifecycles().get(first).homeSlot().orElseThrow();
        HiveOrgan firstHibernaculum = waking.bootstrap().hive().organs().stream()
                .filter(organ -> organ.id().equals(firstSlot.hibernaculumId())).findFirst().orElseThrow();
        GrayboxCell retainedCocoon = FrontierGrayboxPlan.compileStructuralBaseline(waking).cells()
                .get(HiveCocoonPlan.cocoonCell(firstHibernaculum, firstSlot));
        assertEquals(first, retainedCocoon.ownerId(), "task selection keeps the same physical cocoon until its release receipt");
        assertEquals(GrayboxSemanticPart.COCOON, retainedCocoon.semanticPart());
        assertFalse(FrontierGrayboxPlan.structuralInput(fixture.state()).equals(FrontierGrayboxPlan.structuralInput(waking)),
                "a waking cocoon changes the retained structural projection input so a naturally loaded projector cannot keep a stale plan");
        assertThrows(IllegalArgumentException.class, () -> AmbientActorProcess.nextLease(waking, first,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(201L)), "unconfirmed waking must not create an ambient body");

        state = HiveMobilizationProcess.reduceReleaseStarted(state, fixture.hive(), new HiveMobilizationReleaseStarted(mobilization.id()));
        HiveMobilization releasing = state.hiveColony().mobilizations().get(mobilization.id());
        assertEquals(Optional.of(first), releasing.releasingMemberId());
        state = HiveMobilizationProcess.reduceCocoonReleased(state, fixture.hive(), new HiveMobilizationCocoonReleased(mobilization.id(), first));

        HiveMobilization after = state.hiveColony().mobilizations().get(mobilization.id());
        assertEquals(HiveMobilizationStatus.WAKING, after.status(), "the next exact cocoon remains a separate durable effect");
        assertEquals(List.of(first), after.releasedMemberIds());
        assertEquals(BioformLifecyclePhase.ASSEMBLING, state.hiveColony().bioformLifecycles().get(first).phase());
        FrontierWorldState partial = state;
        assertThrows(IllegalArgumentException.class, () -> AmbientActorProcess.nextLease(partial, first,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(202L)), "one confirmed member must wait for the exact group");
        assertFalse(HivePhysiologySupport.permitsAmbientLease(state, after.memberIds().get(1)),
                "the next waking cocoon remains physically closed until its own confirmation");

        for (int index = 1; index < after.memberIds().size(); index++) {
            state = HiveMobilizationProcess.reduceReleaseStarted(state, fixture.hive(), new HiveMobilizationReleaseStarted(after.id()));
            SubjectId member = after.memberIds().get(index);
            state = HiveMobilizationProcess.reduceCocoonReleased(state, fixture.hive(), new HiveMobilizationCocoonReleased(after.id(), member));
        }
        HiveMobilization assembled = state.hiveColony().mobilizations().get(after.id());
        assertEquals(HiveMobilizationStatus.ASSEMBLING, assembled.status());
        FrontierWorldState assembledState = state;
        assertTrue(assembled.memberIds().stream().allMatch(member -> HivePhysiologySupport.permitsAmbientLease(assembledState, member)));
        assertTrue(assembled.memberIds().stream().noneMatch(member -> HivePhysiologySupport.availableForIndependentOperation(assembledState, member)),
                "the assembled group remains owned by its original task until its later departure boundary");
        assertTrue(AmbientActorProcess.nextLease(state, first, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(203L)).actorId().equals(first));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void playerBrokenCocoonInterruptsOnlyItsInFlightMobilizationAndReleasesTheExactOccupant() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        HiveMobilizationStarted started = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload());
        state = HiveMobilizationProcess.reduceStarted(state, fixture.hive(), started);
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(started.mobilization().id());
        SubjectId broken = mobilization.memberIds().get(1);
        HiveCocoonSlot slot = state.hiveColony().bioformLifecycles().get(broken).homeSlot().orElseThrow();
        HiveOrgan hibernaculum = state.bootstrap().hive().organs().stream()
                .filter(organ -> organ.id().equals(slot.hibernaculumId())).findFirst().orElseThrow();
        BlockPosition position = HiveCocoonPlan.cocoonCell(hibernaculum, slot);

        state = state.recordPhysicalDelta(new PhysicalDelta(position, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(broken), Optional.of(GrayboxSemanticPart.COCOON), "player:test"));

        HiveMobilization conflicted = state.hiveColony().mobilizations().get(mobilization.id());
        assertEquals(HiveMobilizationStatus.CONFLICT, conflicted.status());
        assertEquals(Optional.of(HiveMobilizationConflictReason.COCOON_CHANGED), conflicted.conflictReason());
        assertEquals(BioformLifecyclePhase.WAKING, state.hiveColony().bioformLifecycles().get(broken).phase());
        assertTrue(HivePhysiologySupport.permitsAmbientLease(state, broken));
        FrontierWorldState conflictedState = state;
        assertThrows(IllegalArgumentException.class, () -> AmbientActorProcess.nextLease(conflictedState, mobilization.memberIds().getFirst(),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(202L)));
    }

    @Test void changedOrRestartUnconfirmedCocoonConflictsWithoutMovingOrLeasingItsExactOccupant() {
        Fixture fixture = fixture();
        List<ProposedEvent> planned = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        HiveMobilizationStarted started = assertInstanceOf(HiveMobilizationStarted.class, planned.get(1).payload());
        state = HiveMobilizationProcess.reduceStarted(state, fixture.hive(), started);
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(started.mobilization().id());
        SubjectId first = mobilization.memberIds().getFirst();
        BodyPosition original = state.actorLocations().get(first).body();
        state = HiveMobilizationProcess.reduceReleaseStarted(state, fixture.hive(), new HiveMobilizationReleaseStarted(mobilization.id()));
        state = HiveMobilizationProcess.reduceConflicted(state, fixture.hive(),
                new HiveMobilizationConflicted(mobilization.id(), HiveMobilizationConflictReason.UNKNOWN_AFTER_RESTART));

        HiveMobilization conflicted = state.hiveColony().mobilizations().get(mobilization.id());
        assertEquals(HiveMobilizationStatus.CONFLICT, conflicted.status());
        assertEquals(Optional.of(HiveMobilizationConflictReason.UNKNOWN_AFTER_RESTART), conflicted.conflictReason());
        assertEquals(original, state.actorLocations().get(first).body());
        FrontierWorldState conflictedState = state;
        assertThrows(IllegalArgumentException.class, () -> AmbientActorProcess.nextLease(conflictedState, first,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(202L)));
    }

    private static Fixture fixture() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-mobilization"), 8128L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        FrontierWorldState initial = state;
        Bioform scout = initial.bootstrap().hive().bioforms().stream().filter(Bioform::isScout)
                .filter(bioform -> initial.hiveColony().bioformLifecycles().get(bioform.id()).phase() == BioformLifecyclePhase.ACTIVE).findFirst().orElseThrow();
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        InfectionCell cell = InfectionCell.at(settlement.anchor());
        state = state.withInfection(cell, new FixedRatio(FixedScalar.ONE));
        StrategicPlanState plans = StrategicPlanState.empty()
                .withHiveSettlementKnowledge(new HiveSettlementKnowledge(java.util.Map.of(settlement.id(), sighting)))
                .withHiveTerritoryKnowledge(new HiveTerritoryKnowledge(java.util.Map.of(cell,
                        new HiveTerritoryKnowledge.Belief(cell, new FixedRatio(FixedScalar.ONE), scout.id(), settlement.anchor(), 100L))))
                .withHiveDoctrine(new HiveDoctrineState(HiveDoctrine.INTERDICT, 100L));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-mobilization"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-mobilization"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        return new Fixture(state.withStrategicPlans(plans.addObjective(objective).addTask(task)), hive, task, sighting);
    }

    private record Fixture(FrontierWorldState state, SubjectId hive, StrategicTask task, HiveSettlementKnowledge.Sighting sighting) { }
}
