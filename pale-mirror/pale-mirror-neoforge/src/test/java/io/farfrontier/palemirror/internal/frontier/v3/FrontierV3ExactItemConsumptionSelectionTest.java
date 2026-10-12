package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Selection only: no claim of physical writes, chest persistence or native recovery. */
class FrontierV3ExactItemConsumptionSelectionTest {
    @Test void physicalTargetUsesTheRetainedWarehouseAndCurrentExactSlotAfterRecovery() {
        var state = state();
        var home = state.bootstrap().settlements().getFirst();
        var depot = FrontierWorldState.depotId(home.id());
        var container = new SubjectId("container:medical-consumption-warehouse");
        var position = home.anchor().offset(7, 0, 7);
        var station = new SurfaceAnchor(position.offset(-1, -1, 0));
        var endpoint = new ShipmentEndpoint.Storage(home.id(), container, station, java.util.Optional.empty(), station);
        var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 5, ResourceStorageRole.WAREHOUSE);
        state = new ResourceStorageDeclared(new ContainerRecord(container, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE,
                java.util.Optional.empty(), java.util.Optional.of(policy)), position).apply(state, home.id());
        var supply = new SubjectId("item:medical-warehouse-remedy");
        state = state.withInventory(state.inventory().withSurfaceStatus(container, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(container, ContainerSurfaceStatus.ACTIVE).withSurfaceStatus(depot, ContainerSurfaceStatus.CONFLICT)
                .store(new ExactItemStack(supply, home.id(), MedicalEvacuationStateSupport.FIRST_TREATMENT_SUPPLY,
                        3, new InventoryCustody.ContainerSlot(container, 19))));
        var population = state.humanPopulation();
        var patient = home.residents().stream().map(Resident::id)
                .filter(id -> population.resident(id).capability(HumanCapability.MEDICINE) < 50).findFirst().orElseThrow();
        state = state.withHumanPopulation(population.transitionHealth(patient, ResidentHealthStatus.EXPOSED, 100)
                .transitionHealth(patient, ResidentHealthStatus.INFECTED, 200));
        var events = io.farfrontier.palemirror.frontier.v3.process.MedicalTreatmentProcess.planStart(state, home.id(), 1);
        var started = events.stream().map(ProposedEvent::payload).filter(MedicalTreatmentStarted.class::isInstance)
                .map(MedicalTreatmentStarted.class::cast).findFirst().orElseThrow();
        var intent = events.stream().map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow().intent();
        state = io.farfrontier.palemirror.frontier.v3.process.MedicalTreatmentProcess.reduceStarted(state, home.id(), started);
        state = io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleFixture.prepare(state, home.id(), intent);
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var target = FrontierV3ExactItemConsumptionExecutor.target(codec.decode(codec.encode(state)), intent);
        assertNotNull(target);
        assertEquals(container, target.containerId());
        assertEquals(new net.minecraft.core.BlockPos(position.x(), position.y(), position.z()), target.chestPosition());
        assertEquals(19, target.slot());
        assertEquals(1, target.count());
        assertEquals(3, target.item().count(), "a one-unit dose does not consume the whole exact stack");
        var conflicted = state.withInventory(state.inventory().withSurfaceStatus(container, ContainerSurfaceStatus.CONFLICT));
        assertNull(FrontierV3ExactItemConsumptionExecutor.target(conflicted, intent));
    }

    @Test void unresolvedHeadCannotStarveIndependentPreparedConsumption() {
        PhysicalIntent first = growth("a");
        first = first.withRecoveryUnknown(PhysicalIntentRecoveryDiagnosticProducer.HIVE_GROWTH.stamp(first));
        PhysicalIntent second = growth("b");
        var state = state();
        var inventory = List.of(second, first);
        var turns = new FrontierV3FairTurn<PhysicalIntentId>();
        assertEquals(first, FrontierV3ExactItemConsumptionExecutor.nextIntent(state, inventory, turns).orElseThrow());
        assertEquals(second, FrontierV3ExactItemConsumptionExecutor.nextIntent(state, inventory, turns).orElseThrow());
        assertEquals(first, FrontierV3ExactItemConsumptionExecutor.nextIntent(state, inventory, turns).orElseThrow());
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, first.status(),
                "service selection grants no restart or physical-write authority");
    }

    @Test void waitingPreparedHeadAndIneligibleMedicalEntryDoNotMonopolizeTurns() {
        PhysicalIntent first = growth("a");
        PhysicalIntent second = growth("b");
        PhysicalIntent medical = new PhysicalIntent(new PhysicalIntentId("intent:0-medical"),
                PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, PhysicalIntentStatus.PREPARED,
                new SubjectId("medical:test"), PhysicalIntentRoleBinding.medicalTreatmentConsumption(
                new SubjectId("medical:test"), new SubjectId("item:medical")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED, PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT);
        var state = state();
        var inventory = List.of(second, medical, first);
        var turns = new FrontierV3FairTurn<PhysicalIntentId>();
        // No completion/removal between turns: an unloaded or custody-waiting head remains.
        for (PhysicalIntent expected : List.of(first, second, first, second)) {
            assertEquals(expected, FrontierV3ExactItemConsumptionExecutor.nextIntent(state, inventory, turns).orElseThrow());
        }
        assertTrue(FrontierV3ExactItemConsumptionExecutor.nextIntent(state, List.of(medical), turns).isEmpty());
    }

    private static FrontierWorldState state() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:consumption-selection"), 42L));
    }

    private static PhysicalIntent growth(String suffix) {
        var cause = new SubjectId("job:hive-growth-" + suffix);
        return new PhysicalIntent(new PhysicalIntentId("intent:" + suffix), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION,
                PhysicalIntentStatus.PREPARED, cause,
                PhysicalIntentRoleBinding.hiveGrowthConsumption(cause, new SubjectId("item:" + suffix)),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED, PhysicalIntentLifecycleOwner.HIVE_GROWTH);
    }
}
