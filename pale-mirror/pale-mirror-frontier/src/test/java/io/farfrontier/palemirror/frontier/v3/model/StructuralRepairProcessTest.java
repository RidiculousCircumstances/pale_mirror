package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

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
import io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleFixture;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuralRepairProcessTest {
    @Test
    void untypedOrMismatchedLossEvidenceCannotGainRepairAuthority() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:typed-repair"), 91L));
        GrayboxCell structureCell = FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(value -> value.semanticTarget().kind() == PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(PhysicalDelta.class.getRecordComponents())
                .noneMatch(component -> component.getName().equals("semanticTargetEvidence") || component.getGenericType().getTypeName().contains("?")),
                "the production physical-delta API must expose only its typed semantic target");
        PhysicalDeltaSemanticTarget mismatched = new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, structureCell.ownerId());
        assertThrows(IllegalArgumentException.class, () -> state.recordPhysicalDelta(new PhysicalDelta(structureCell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(mismatched), Optional.of(structureCell.semanticPart()), "test:mismatched-target")));
    }

    @Test
    void priorSnapshotFormatCannotHydrateWithoutTheDurableSemanticTarget() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:semantic-target-recovery"), 91L));
        byte[] current = new FrontierWorldStateCodec().encode(state);
        current[4] = (byte) 162;
        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldStateCodec().decode(current));
    }

    @Test
    void exactConcreteRepairConsumesOnePhysicalUnitAndRestoresOnlyItsRecordedCell() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:structural-repair"), 91L));
        SettlementStructure structure = state.bootstrap().settlements().getFirst().structures().getFirst();
        GrayboxCell cell = FrontierGrayboxPlan.compile(state).cells().values().stream().filter(value -> value.ownerId().equals(structure.id())).findFirst().orElseThrow();
        PhysicalDelta loss = new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(cell.semanticTarget()), Optional.of(cell.semanticPart()), "explosion:test");
        state = state.recordPhysicalDelta(loss);
        SubjectId depot = FrontierWorldState.depotId(state.bootstrap().settlements().getFirst().id()); SubjectId materialId = new SubjectId("item:repair-white-concrete");
        state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(materialId, state.bootstrap().settlements().getFirst().id(), cell.material().repairItemKind(), 2, new InventoryCustody.ContainerSlot(depot, 4))));
        assertTrue(StructuralRepairProcess.plan(state, StructuralRepairProcess.scan(1, 800L)).stream().anyMatch(event -> event.payload() instanceof PhysicalIntentPrepared));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:repair-runtime-definition"), PhysicalIntentKind.STRUCTURAL_REPAIR,
                PhysicalIntentStatus.PREPARED, structure.id(), List.of(structure.id(), materialId), new FixedPosition(FixedScalar.whole(cell.position().x()), FixedScalar.whole(cell.position().y()), FixedScalar.whole(cell.position().z())),
                0, PhysicalPostcondition.STRUCTURAL_REPAIR_OBSERVED, cell.semanticTarget(), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE);
        SubjectId repairOwner = StructuralRepairProcess.repairOwner(state, cell.semanticTarget());
        state = PhysicalIntentLifecycleFixture.prepare(state, repairOwner, intent);
        state = PhysicalIntentLifecycleFixture.transition(state, repairOwner, intent, PhysicalIntentStatus.RUNNING, Optional.empty());
        StructuralRepairObservation observation = new StructuralRepairObservation(new PhysicalObservationId("observation:repair-runtime-definition"), intent.id(), materialId, cell.position());
        FrontierWorldState repaired = PhysicalIntentLifecycleFixture.transition(state, repairOwner, intent, PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        assertEquals(1, repaired.inventory().items().get(materialId).count()); assertTrue(!repaired.physicalDeltas().containsKey(cell.position()));
        assertTrue(!repaired.structureDamage().containsKey(structure.id())); assertEquals(StructureCondition.INTACT, repaired.structureConditions().get(structure.id()));
        assertEquals(repaired, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(repaired)));
        PhysicalIntentTransition transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation));
        assertEquals(observation, ((PhysicalIntentTransition) FrontierWorldRuntimeDefinition.payloadCodecs().decode(transition.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(transition))).observation().orElseThrow());
        ExactItemStack diamond = new ExactItemStack(materialId, state.bootstrap().settlements().getFirst().id(), "minecraft:diamond", 2,
                new InventoryCustody.ContainerSlot(depot, 4));
        FrontierWorldState wrongMaterial = state.withInventory(state.inventory().withoutItem(materialId).store(diamond));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(
                PhysicalIntentLifecycleFixture.prepare(wrongMaterial, repairOwner, intent), repairOwner, intent,
                PhysicalIntentStatus.RUNNING, Optional.empty()));
    }

    @Test
    void exactHiveStoreMaterialCanRestoreAnOrganWithoutSettlementConditionSideEffects() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-organ-repair"), 91L));
        HiveOrgan organ = state.bootstrap().hive().organs().getFirst();
        GrayboxCell cell = FrontierGrayboxPlan.compile(state).cells().values().stream().filter(value -> value.ownerId().equals(organ.id())).findFirst().orElseThrow();
        state = state.recordPhysicalDelta(new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(cell.semanticTarget()), Optional.of(cell.semanticPart()), "explosion:test"));
        SubjectId store = new SubjectId("container:hive-east-store"), materialId = new SubjectId("item:hive-repair-red-concrete");
        state = state.withInventory(state.inventory().withSurfaceStatus(store, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(store, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(materialId, state.bootstrap().hive().id(), cell.material().repairItemKind(), 1, new InventoryCustody.ContainerSlot(store, 4))));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:hive-organ-repair"), PhysicalIntentKind.STRUCTURAL_REPAIR, PhysicalIntentStatus.PREPARED,
                organ.id(), List.of(organ.id(), materialId), new FixedPosition(FixedScalar.whole(cell.position().x()), FixedScalar.whole(cell.position().y()), FixedScalar.whole(cell.position().z())),
                0, PhysicalPostcondition.STRUCTURAL_REPAIR_OBSERVED, cell.semanticTarget(), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE);
        SubjectId repairOwner = StructuralRepairProcess.repairOwner(state, cell.semanticTarget());
        state = PhysicalIntentLifecycleFixture.prepare(state, repairOwner, intent);
        state = PhysicalIntentLifecycleFixture.transition(state, repairOwner, intent, PhysicalIntentStatus.RUNNING, Optional.empty());
        FrontierWorldState repaired = PhysicalIntentLifecycleFixture.transition(state, repairOwner, intent, PhysicalIntentStatus.CONFIRMED,
                Optional.of(new StructuralRepairObservation(new PhysicalObservationId("observation:hive-organ-repair"), intent.id(), materialId, cell.position())));
        assertTrue(!repaired.physicalDeltas().containsKey(cell.position()) && repaired.isHiveOrganOperational(organ.id()));
        assertTrue(!repaired.inventory().items().containsKey(materialId), "the final real concrete item is consumed exactly once");
    }

    @Test
    void routeLossIsNeverAdmittedToGenericStructuralRepair() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:route-repair"), 91L));
        BlockPosition routeCell = FrontierRouteNetwork.supplyWaypoints(state.bootstrap(), state.bootstrap().settlements().getFirst().id()).get(2);
        state = state.recordPhysicalDelta(new PhysicalDelta(routeCell, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, FrontierRouteNetwork.OWNER)), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "explosion:test"));
        SubjectId materialId = new SubjectId("item:route-repair-gray-concrete");
        FrontierWorldState noStock = state.withInventory(state.inventory().withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.ACTIVE));
        assertTrue(StructuralRepairProcess.plan(noStock, StructuralRepairProcess.scan(1, 800L)).stream().noneMatch(event -> event.payload() instanceof PhysicalIntentPrepared));
        state = noStock.withInventory(noStock.inventory().store(new ExactItemStack(materialId, FrontierRouteNetwork.OWNER, "minecraft:gray_concrete", 1,
                new InventoryCustody.ContainerSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER, 0))));
        assertTrue(StructuralRepairProcess.plan(state, StructuralRepairProcess.scan(1, 800L)).stream()
                .noneMatch(event -> event.payload() instanceof PhysicalIntentPrepared));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:route-repair"), PhysicalIntentKind.STRUCTURAL_REPAIR, PhysicalIntentStatus.PREPARED,
                FrontierRouteNetwork.OWNER, List.of(FrontierRouteNetwork.OWNER, materialId), new FixedPosition(FixedScalar.whole(routeCell.x()), FixedScalar.whole(routeCell.y()), FixedScalar.whole(routeCell.z())),
                0, PhysicalPostcondition.STRUCTURAL_REPAIR_OBSERVED, new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, FrontierRouteNetwork.OWNER), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE);
        FrontierWorldState routeState = state;
        assertThrows(IllegalArgumentException.class, () -> StructuralRepairProcess.reducePrepared(routeState, FrontierRouteNetwork.OWNER, intent));
        FrontierWorldState invalidRouteRepairState = state;
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.prepare(invalidRouteRepairState, FrontierRouteNetwork.OWNER, intent));
    }
}
