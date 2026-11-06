package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StructureIntegrityTest {
    private static final FrontierWorldState INITIAL = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:integrity"),139));
    private static NavigationObservation observed(NavigationBlockState block, NavigationShape shape) {
        return NavigationObservation.known(new NavigationCell(shape, true, Optional.of(block)));
    }
    @Test void actualLossReplacementAndRestorationRetainOneCellAcrossRecoveryWithoutDestroyingTheField() {
        var farm = INITIAL.bootstrap().settlements().getFirst().structures().stream().filter(s -> s.kind()==StructureKind.FARM).findFirst().orElseThrow();
        var cell = BuildingSpatialPlan.of(farm).blocks().values().stream().filter(c -> !c.material().emptySpace() && c.semanticPart()==GrayboxSemanticPart.FOUNDATION).findFirst().orElseThrow();
        var air = new NavigationBlockState("minecraft:air",Map.of());
        var loss = new StructureCellObserved(farm.id(),cell.position(),cell.semanticPart(),observed(air,NavigationShape.EMPTY),false,"player:verified-break");
        var damaged = StructureIntegrity.observe(INITIAL,loss);
        assertEquals(StructureCondition.DAMAGED,damaged.structureConditions().get(farm.id()));
        assertTrue(FacilityFunctionAvailability.mayUse(damaged,farm.id()));
        assertEquals(INITIAL.resourceSites(),damaged.resourceSites(),"farmhouse damage cannot destroy independently owned fields");
        assertFalse(PhysicalDeltaGeometry.unknown(damaged).contains(cell.position()),"confirmed AIR has known collision");
        var replacement = new NavigationBlockState("minecraft:granite",Map.of());
        var changed = StructureIntegrity.observe(damaged,new StructureCellObserved(farm.id(),cell.position(),cell.semanticPart(),observed(replacement,NavigationShape.FULL),false,"player:replacement"));
        assertTrue(changed.structureDamage().get(farm.id()).cells().containsKey(cell.position()));
        assertEquals(StructureIntegrity.Disposition.REPLACED, StructureIntegrity.candidates(changed,farm.id()).getFirst().disposition());
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(changed));
        assertEquals(changed.structureDamage(),recovered.structureDamage());
        assertEquals(replacement,recovered.navigationGeometry().cells().get(cell.position()).cell().orElseThrow().observedState().orElseThrow());
        var original = ((StructureMaterial.Authored)cell.material()).block();
        var restored = StructureIntegrity.observe(recovered,new StructureCellObserved(farm.id(),cell.position(),cell.semanticPart(),observed(original,NavigationShape.FULL),true,"player:restoration"));
        assertEquals(StructureCondition.INTACT,restored.structureConditions().get(farm.id()));
        assertFalse(restored.physicalDeltas().containsKey(cell.position()));
        assertTrue(StructureIntegrity.candidates(restored,farm.id()).isEmpty());
    }
    @Test void lossOnlyEvidenceCannotReuseAnOlderCollisionObservation() {
        var building = INITIAL.bootstrap().settlements().getFirst().structures().getFirst();
        var cell = BuildingSpatialPlan.of(building).blocks().values().stream()
                .filter(c -> !c.material().emptySpace()).findFirst().orElseThrow();
        var old = observed(((StructureMaterial.Authored) cell.material()).block(), NavigationShape.FULL);
        var known = INITIAL.withChanges(FrontierWorldStateUpdate.begin().navigationGeometry(
                INITIAL.navigationGeometry().observe(INITIAL.navigationGeometry().revision(), Map.of(cell.position(), old))));
        var loss = known.recordPhysicalDelta(new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(cell.semanticTarget()), Optional.of(cell.semanticPart()), "confirmed:loss-without-shape"));
        assertTrue(PhysicalDeltaGeometry.unknown(loss).contains(cell.position()), "old geometry is not proof of the post-loss block");
        var current = StructureIntegrity.observe(loss, new StructureCellObserved(building.id(), cell.position(), cell.semanticPart(),
                observed(new NavigationBlockState("minecraft:air", Map.of()), NavigationShape.EMPTY), false, "confirmed:current-shape"));
        assertFalse(PhysicalDeltaGeometry.unknown(current).contains(cell.position()), "current exact observation closes the local geometry wait");
    }
    @Test void forgedOwnerPartAndRestorationAreRejectedBeforeMutation() {
        var homes=INITIAL.bootstrap().settlements();var building=homes.getFirst().structures().getFirst();
        var cell=BuildingSpatialPlan.of(building).blocks().values().stream().filter(c -> !c.material().emptySpace()).findFirst().orElseThrow();
        var air=observed(new NavigationBlockState("minecraft:air",Map.of()),NavigationShape.EMPTY);
        assertThrows(IllegalArgumentException.class,()->StructureIntegrity.observe(INITIAL,new StructureCellObserved(homes.get(1).structures().getFirst().id(),cell.position(),cell.semanticPart(),air,false,"foreign")));
        assertThrows(IllegalArgumentException.class,()->StructureIntegrity.observe(INITIAL,new StructureCellObserved(building.id(),cell.position(),cell.semanticPart(),air,true,"forged-restoration")));
        assertTrue(INITIAL.structureDamage().isEmpty());
    }
    @Test void damageOfUnrelatedFabricCannotDisableAWorkplaceButMissingSupportDoes() {
        var spec = INITIAL.inventory().containers().values().stream().flatMap(c -> c.productionStation().stream()).findFirst().orElseThrow();
        var building = FrontierWorldStateSupport.structureById(INITIAL.bootstrap(),spec.facilityId());
        var decoration = BuildingSpatialPlan.of(building).blocks().values().stream()
                .filter(c -> c.semanticPart()==GrayboxSemanticPart.ROOF && !c.material().emptySpace()).findFirst().orElseThrow();
        var air=observed(new NavigationBlockState("minecraft:air",Map.of()),NavigationShape.EMPTY);
        var damaged=StructureIntegrity.observe(INITIAL,new StructureCellObserved(building.id(),decoration.position(),decoration.semanticPart(),air,false,"player:roof"));
        assertTrue(ProductionFacilityAvailability.mayUseStation(damaged,spec));
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.security.ProtectionObligations.read(INITIAL, building.settlementId()),
                io.farfrontier.palemirror.frontier.v3.model.security.ProtectionObligations.read(damaged, building.settlementId()),
                "a damaged roof does not retire the settlement's protected asset");
        var floor=FrontierGrayboxPlan.intactStructureCell(INITIAL.bootstrap().terrain(),building,spec.workerStation().support());
        var lost=StructureIntegrity.observe(damaged,new StructureCellObserved(building.id(),floor.position(),floor.semanticPart(),air,false,"player:work-support"));
        assertFalse(ProductionFacilityAvailability.mayUseStation(lost,spec));
        var replacement=StructureIntegrity.observe(lost,new StructureCellObserved(building.id(),floor.position(),floor.semanticPart(),
                observed(new NavigationBlockState("minecraft:granite",Map.of()),NavigationShape.FULL),false,"player:new-support"));
        assertTrue(ProductionFacilityAvailability.mayUseStation(replacement,spec),"usable replacement does not erase the material-damage record");
        assertEquals(2,replacement.structureDamage().get(building.id()).cells().size());
    }
    @Test void sitePlanRetainsSlopedPreparationAndRejectsExcessiveEarthwork() {
        var building=INITIAL.bootstrap().settlements().getFirst().structures().getFirst();
        var ground=INITIAL.bootstrap().terrain();var box=building.placement().volume(building.definition().fabric());
        var slope=ground.withSurveyedSupport(box.min().x(),box.min().z(),ground.baselineSupportY()+2);
        var plan=BuildingSitePlan.compile(slope,building);
        assertEquals(2,plan.removedBlocks());
        assertEquals(0,plan.placedBlocks());
        assertTrue(plan.preparation().values().stream().allMatch(c->c.material().emptySpace()));
        var excessive=ground.withSurveyedSupport(box.min().x(),box.min().z(),ground.baselineSupportY()+32);
        assertThrows(IllegalArgumentException.class,()->BuildingSitePlan.compile(excessive,building));
    }
}
