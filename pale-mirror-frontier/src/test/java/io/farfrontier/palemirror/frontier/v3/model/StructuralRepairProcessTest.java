package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuralRepairProcessTest {
    @Test
    void exactConcreteRepairConsumesOnePhysicalUnitAndRestoresOnlyItsRecordedCell() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:structural-repair"), 91L));
        SettlementStructure structure = state.bootstrap().settlements().getFirst().structures().getFirst();
        GrayboxCell cell = FrontierGrayboxPlan.compile(state).cells().values().stream().filter(value -> value.ownerId().equals(structure.id())).findFirst().orElseThrow();
        PhysicalDelta loss = new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(structure.id()), Optional.of(cell.semanticPart()), "explosion:test");
        state = state.recordPhysicalDelta(loss);
        SubjectId depot = FrontierWorldState.depotId(state.bootstrap().settlements().getFirst().id()); SubjectId materialId = new SubjectId("item:repair-white-concrete");
        state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(materialId, cell.material().repairItemKind(), 2, new InventoryCustody.ContainerSlot(depot, 4))));
        assertTrue(StructuralRepairProcess.plan(state, StructuralRepairProcess.scan(1, 800L)).stream().anyMatch(event -> event.payload() instanceof PhysicalIntentPrepared));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:repair-runtime-definition"), PhysicalIntentKind.STRUCTURAL_REPAIR,
                PhysicalIntentStatus.PREPARED, structure.id(), List.of(structure.id(), materialId), new FixedPosition(FixedScalar.whole(cell.position().x()), FixedScalar.whole(cell.position().y()), FixedScalar.whole(cell.position().z())),
                0, PhysicalPostcondition.STRUCTURAL_REPAIR_OBSERVED);
        state = state.preparePhysicalIntent(intent).transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        StructuralRepairObservation observation = new StructuralRepairObservation(new PhysicalObservationId("observation:repair-runtime-definition"), intent.id(), materialId, cell.position());
        FrontierWorldState repaired = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        assertEquals(1, repaired.inventory().items().get(materialId).count()); assertTrue(!repaired.physicalDeltas().containsKey(cell.position()));
        assertTrue(!repaired.structureDamage().containsKey(structure.id())); assertEquals(StructureCondition.INTACT, repaired.structureConditions().get(structure.id()));
        assertEquals(repaired, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(repaired)));
        PhysicalIntentTransition transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        assertEquals(observation, ((PhysicalIntentTransition) FrontierWorldRuntimeDefinition.payloadCodecs().decode(transition.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(transition))).observation().orElseThrow());
        FrontierWorldState wrongMaterial = state.withInventory(state.inventory().withoutItem(materialId).store(new ExactItemStack(materialId, "minecraft:diamond", 2, new InventoryCustody.ContainerSlot(depot, 4))));
        assertThrows(IllegalArgumentException.class, () -> wrongMaterial.preparePhysicalIntent(intent)
                .transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty())
                .transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation)));
    }
}
