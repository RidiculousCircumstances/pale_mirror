package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecontaminationProcessTest {
    @Test
    void nearbyActiveInfirmaryConsumesOneExactReagentOnlyAfterObservedCellReduction() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:decontamination"), 101L);
        SubjectId settlement = new SubjectId("settlement:9"), depot = FrontierWorldState.depotId(settlement), item = new SubjectId("item:decontamination-reagent");
        FrontierWorldState state = FrontierWorldState.initial(bootstrap).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, DecontaminationPolicy.REAGENT, 2, new InventoryCustody.ContainerSlot(depot, 1))));
        PhysicalIntentPrepared prepared = DecontaminationProcess.plan(state, DecontaminationProcess.scan(1, 1_000L)).stream().map(event -> event.payload())
                .filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        assertEquals(settlement, DecontaminationProcess.owner(state, prepared.intent().causeSubjectId()).id());
        state = DecontaminationProcess.reducePrepared(state, settlement, prepared.intent()).transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        InfectionCell cell = DecontaminationStateSupport.cell(prepared.intent()); long prior = state.infection().get(cell).value().raw();
        DecontaminationObservation observation = new DecontaminationObservation(new PhysicalObservationId("observation:decontamination"), prepared.intent().id(), item,
                cell, prior, prior - DecontaminationPolicy.REDUCTION_RAW);
        state = state.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        assertEquals(prior - DecontaminationPolicy.REDUCTION_RAW, state.infection().get(cell).value().raw());
        assertEquals(1, state.inventory().items().get(item).count());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void distantSettlementStockCannotPretendToTreatAnUnreachableInfectionCell() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:decontamination-distance"), 102L);
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1")), item = new SubjectId("item:distant-decontamination-reagent");
        FrontierWorldState state = FrontierWorldState.initial(bootstrap).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        assertTrue(DecontaminationProcess.plan(state, DecontaminationProcess.scan(1, 1_000L)).stream()
                .noneMatch(event -> event.payload() instanceof PhysicalIntentPrepared));
    }

    @Test
    void finalObservedTreatmentRemovesOnlyTheTargetCellAndRejectsAStaleReceipt() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:decontamination-final"), 103L);
        SubjectId settlement = new SubjectId("settlement:9"), depot = FrontierWorldState.depotId(settlement), item = new SubjectId("item:final-decontamination-reagent");
        FrontierWorldState state = FrontierWorldState.initial(bootstrap).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        PhysicalIntentPrepared prepared = DecontaminationProcess.plan(state, DecontaminationProcess.scan(1, 1_000L)).stream().map(event -> event.payload())
                .filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        InfectionCell cell = DecontaminationStateSupport.cell(prepared.intent());
        state = state.withInfection(cell, new FixedRatio(new FixedScalar(DecontaminationPolicy.REDUCTION_RAW)));
        state = DecontaminationProcess.reducePrepared(state, settlement, prepared.intent()).transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        FrontierWorldState running = state;
        DecontaminationObservation stale = new DecontaminationObservation(new PhysicalObservationId("observation:stale-decontamination"), prepared.intent().id(), item,
                cell, DecontaminationPolicy.REDUCTION_RAW + 1L, 1L);
        assertThrows(IllegalArgumentException.class, () -> running.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(stale)));
        DecontaminationObservation cleared = new DecontaminationObservation(new PhysicalObservationId("observation:final-decontamination"), prepared.intent().id(), item,
                cell, DecontaminationPolicy.REDUCTION_RAW, 0L);
        FrontierWorldState complete = running.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(cleared));
        assertTrue(!complete.infection().containsKey(cell) && !complete.inventory().items().containsKey(item));
    }
}
