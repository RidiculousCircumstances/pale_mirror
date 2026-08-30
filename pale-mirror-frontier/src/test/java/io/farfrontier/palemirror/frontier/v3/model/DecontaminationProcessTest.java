package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecontaminationProcessTest {
    @Test
    void activeTaskConsumesOneExactReagentOnlyAfterObservedCellReduction() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:decontamination"), 101L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        SubjectId depot = FrontierWorldState.depotId(settlement.id()), item = new SubjectId("item:decontamination-reagent");
        InfectionCell cell = InfectionCell.at(infirmary(settlement).anchor());
        FrontierWorldState state = stateWithTask(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 2, new InventoryCustody.ContainerSlot(depot, 1))));
        state = activateAndPrepare(state, settlement);
        PhysicalIntent intent = onlyIntent(state);
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        long prior = state.infection().get(cell).value().raw();
        DecontaminationObservation observation = new DecontaminationObservation(new PhysicalObservationId("observation:decontamination"), intent.id(), item,
                cell, prior, prior - DecontaminationPolicy.REDUCTION_RAW);
        StrategicTask task = onlyTask(state);
        CommandPlan plan = FrontierWorldRuntimeDefinition.planCommand(state, command(state, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation)));
        CommandPlan.Accepted accepted = assertInstanceOf(CommandPlan.Accepted.class, plan);
        assertEquals(new StrategicTaskTransition(task.id(), StrategicTaskStatus.COMPLETED), accepted.events().getLast().payload());
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, settlement.id(), (StrategicTaskTransition) accepted.events().getLast().payload());
        assertEquals(prior - DecontaminationPolicy.REDUCTION_RAW, state.infection().get(cell).value().raw());
        assertEquals(1, state.inventory().items().get(item).count());
        assertEquals(StrategicTaskStatus.COMPLETED, onlyTask(state).status());
        assertEquals(StrategicObjectiveStatus.COMPLETED, onlyObjective(state).status());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void unavailableExactReagentBlocksTheTaskInsteadOfStartingAnUnbackedIntent() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:decontamination-blocked"), 102L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        FrontierWorldState state = stateWithTask(bootstrap, settlement, InfectionCell.at(infirmary(settlement).anchor()));

        List<ProposedEvent> planned = DecontaminationProcess.plan(state, DecontaminationProcess.scan(1, 1_000L));

        StrategicTaskTransition blocked = planned.stream().map(ProposedEvent::payload).filter(StrategicTaskTransition.class::isInstance)
                .map(StrategicTaskTransition.class::cast).findFirst().orElseThrow();
        assertEquals(StrategicTaskStatus.BLOCKED, blocked.status());
        state = StrategicObjectiveProcess.reduceTaskTransition(state, settlement.id(), blocked);
        assertEquals(StrategicTaskStatus.BLOCKED, onlyTask(state).status());
        assertEquals(StrategicObjectiveStatus.BLOCKED, onlyObjective(state).status());
        assertTrue(planned.stream().noneMatch(event -> event.payload() instanceof PhysicalIntentPrepared));
    }

    @Test
    void unknownPhysicalOutcomeBlocksItsActiveTaskAndSurvivesRecovery() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:decontamination-unknown"), 104L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        SubjectId depot = FrontierWorldState.depotId(settlement.id()), item = new SubjectId("item:unknown-decontamination-reagent");
        InfectionCell cell = InfectionCell.at(infirmary(settlement).anchor());
        FrontierWorldState state = stateWithTask(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        state = activateAndPrepare(state, settlement);
        PhysicalIntent intent = onlyIntent(state);
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());

        CommandPlan plan = FrontierWorldRuntimeDefinition.planCommand(state, command(state, intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
        CommandPlan.Accepted accepted = assertInstanceOf(CommandPlan.Accepted.class, plan);
        StrategicTaskTransition blocked = assertInstanceOf(StrategicTaskTransition.class, accepted.events().getLast().payload());
        assertEquals(StrategicTaskStatus.BLOCKED, blocked.status());
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        state = StrategicObjectiveProcess.reduceTaskTransition(state, settlement.id(), blocked);
        assertEquals(StrategicObjectiveStatus.BLOCKED, onlyObjective(state).status());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void finalObservedTreatmentRemovesOnlyTheTargetCellAndRejectsAStaleReceipt() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:decontamination-final"), 103L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        SubjectId depot = FrontierWorldState.depotId(settlement.id()), item = new SubjectId("item:final-decontamination-reagent");
        InfectionCell cell = InfectionCell.at(infirmary(settlement).anchor());
        FrontierWorldState state = stateWithTask(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        state = state.withInfection(cell, new FixedRatio(new FixedScalar(DecontaminationPolicy.REDUCTION_RAW)));
        state = activateAndPrepare(state, settlement);
        PhysicalIntent intent = onlyIntent(state);
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        DecontaminationObservation stale = new DecontaminationObservation(new PhysicalObservationId("observation:stale-decontamination"), intent.id(), item,
                cell, DecontaminationPolicy.REDUCTION_RAW + 1L, 1L);
        FrontierWorldState running = state;
        assertThrows(IllegalArgumentException.class, () -> running.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(stale)));
        DecontaminationObservation cleared = new DecontaminationObservation(new PhysicalObservationId("observation:final-decontamination"), intent.id(), item,
                cell, DecontaminationPolicy.REDUCTION_RAW, 0L);
        FrontierWorldState complete = running.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(cleared));
        assertTrue(!complete.infection().containsKey(cell) && !complete.inventory().items().containsKey(item));
    }

    @Test
    void historicalDecontaminationReceiptDoesNotForbidLaterAutonomousReinfection() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:decontamination-reinfection"), 109L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        SubjectId depot = FrontierWorldState.depotId(settlement.id()), item = new SubjectId("item:reinfection-reagent");
        InfectionCell cell = InfectionCell.at(infirmary(settlement).anchor());
        FrontierWorldState state = stateWithTask(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        state = state.withInfection(cell, new FixedRatio(new FixedScalar(DecontaminationPolicy.REDUCTION_RAW)));
        state = activateAndPrepare(state, settlement);
        PhysicalIntent intent = onlyIntent(state);
        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        DecontaminationObservation receipt = new DecontaminationObservation(new PhysicalObservationId("observation:reinfection"), intent.id(), item,
                cell, DecontaminationPolicy.REDUCTION_RAW, 0L);

        FrontierWorldState cleared = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        FrontierWorldState reinfected = cleared.withInfection(cell, new FixedRatio(new FixedScalar(125_000L)));

        assertEquals(125_000L, reinfected.infection().get(cell).value().raw());
        assertEquals(reinfected, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reinfected)));
    }

    private static FrontierWorldState activateAndPrepare(FrontierWorldState state, Settlement settlement) {
        List<ProposedEvent> events = DecontaminationProcess.plan(state, DecontaminationProcess.scan(1, 1_000L));
        StrategicTaskTransition activation = events.stream().map(ProposedEvent::payload).filter(StrategicTaskTransition.class::isInstance)
                .map(StrategicTaskTransition.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = events.stream().map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(state, settlement.id(), activation);
        return DecontaminationProcess.reducePrepared(active, settlement.id(), prepared.intent());
    }

    private static FrontierWorldState stateWithTask(FrontierBootstrap bootstrap, Settlement settlement, InfectionCell cell) {
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap).withInfection(cell, new FixedRatio(new FixedScalar(750_000L)));
        String suffix = settlement.id().value().replace(':', '-');
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:" + suffix), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(cell), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:" + suffix), objective.id(), settlement.id(),
                StrategicTaskKind.DECONTAMINATE_INFECTION_CELL, Optional.of(cell), List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY,
                StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.PENDING);
        return initial.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }

    private static FrontierCommand command(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId intentId,
                                           PhysicalIntentStatus status, Optional<DecontaminationObservation> observation) {
        CommandId id = new CommandId("command:decontamination-confirm");
        return new FrontierCommand(1, id, state.bootstrap().worldId(), new Revision(1L), io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO,
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalIntentTransition(intentId, status, observation.map(value -> (PhysicalEffectObservation) value)));
    }

    private static PhysicalIntent onlyIntent(FrontierWorldState state) { return state.physicalIntents().values().stream().findFirst().orElseThrow(); }
    private static StrategicTask onlyTask(FrontierWorldState state) { return state.strategicPlans().tasks().values().stream().findFirst().orElseThrow(); }
    private static StrategicObjective onlyObjective(FrontierWorldState state) { return state.strategicPlans().objectives().values().stream().findFirst().orElseThrow(); }
    private static Settlement settlement(FrontierBootstrap bootstrap, String id) { return bootstrap.settlements().stream().filter(value -> value.id().value().equals(id)).findFirst().orElseThrow(); }
    private static SettlementStructure infirmary(Settlement settlement) { return settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY).findFirst().orElseThrow(); }
}
