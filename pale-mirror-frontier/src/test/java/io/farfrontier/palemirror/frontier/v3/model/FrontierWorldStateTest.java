package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierWorldStateTest {
    private static final FixedRatio HALF = new FixedRatio(new FixedScalar(500_000L));

    @Test
    void initialStateOwnsEveryExactActorAndFunctionalStructure() {
        FrontierWorldState state = initial();

        assertEquals(state.bootstrap().residentCount() + state.bootstrap().bioformCount(), state.actorLocations().size());
        assertEquals(12 * StructureKind.values().length, state.structureConditions().size());
        assertEquals(14, state.inventory().containers().size());
        assertEquals(1, state.inventory().items().size());
        assertTrue(state.productionJobs().isEmpty());
        assertTrue(state.operations().isEmpty());
        assertTrue(state.physicalIntents().isEmpty());
        assertTrue(state.structureConditions().values().stream().allMatch(condition -> condition == StructureCondition.INTACT));
        assertEquals(2, state.infection().size());
    }

    @Test
    void stateTransitionsRemainBoundedAndSparse() {
        FrontierWorldState state = initial();
        SubjectId resident = new SubjectId("resident:1-1");
        SubjectId structure = new SubjectId("structure:1-hall");
        InfectionCell cell = InfectionCell.at(new BlockPosition(-1, 64, -1));

        FrontierWorldState changed = state.withActorLocation(resident, new BlockPosition(-10, 64, -10))
                .withStructureCondition(structure, StructureCondition.DAMAGED)
                .withInfection(cell, HALF);
        assertEquals(new BlockPosition(-10, 64, -10), changed.actorLocations().get(resident).position());
        assertEquals(StructureCondition.DAMAGED, changed.structureConditions().get(structure));
        assertEquals(HALF, changed.infection().get(cell));
        assertEquals(new InfectionCell(-1, -1), cell);
        assertEquals(2, changed.withInfection(cell, new FixedRatio(FixedScalar.ZERO)).infection().size());
        assertThrows(IllegalArgumentException.class, () -> state.withActorLocation(resident, new BlockPosition(512, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> state.withStructureCondition(new SubjectId("structure:missing"), StructureCondition.DESTROYED));
    }

    @Test
    void codecRoundTripsCanonicalMutableStateAndRejectsInvalidState() {
        FrontierWorldState baseline = initial();
        SubjectId container = new SubjectId("container:1-depot");
        SubjectId item = new SubjectId("item:codec");
        ExactInventory inventory = new ExactInventory(baseline.inventory().containers(), Map.of(item,
                new ExactItemStack(item, "minecraft:iron_ingot", 64, new InventoryCustody.ContainerSlot(container, 0))), Map.of(), Map.of());
        ProductionJob activeJob = new ProductionJob(new SubjectId("job:production-1-1"), new SubjectId("settlement:1"),
                new SubjectId("structure:1-workshop"), new SubjectId("resident:1-3"), new SubjectId("item:bootstrap-1-wheat"),
                new SubjectId("item:production-1-1-bread"), "minecraft:bread", 64);
        FrontierWorldState source = baseline.withInventory(inventory).withActorLocation(new SubjectId("bioform:west-0"), new BlockPosition(-400, 64, 400))
                .withStructureCondition(new SubjectId("structure:2-depot"), StructureCondition.DESTROYED)
                .withInfection(new InfectionCell(-100, 100), HALF).withProductionJob(activeJob);
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(source);
        assertEquals(source, codec.decode(encoded));
        encoded[4] = 10;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));

        Map<SubjectId, ActorLocation> missingActor = new LinkedHashMap<>(source.actorLocations());
        missingActor.remove(new SubjectId("resident:1-1"));
        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldState(source.bootstrap(), missingActor,
                source.structureConditions(), source.infection(), source.inventory(), source.productionJobs(), source.contracts(), source.operations(), source.physicalIntents()));
    }

    @Test
    void physicalIntentCannotReferenceAForeignCauseSubject() {
        FrontierWorldState state = initial();
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:cargo-handoff-foreign"), PhysicalIntentKind.CARGO_HANDOFF,
                PhysicalIntentStatus.PREPARED, new SubjectId("operation:foreign"), List.of(new SubjectId("resident:1-1")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);

        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(),
                state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(), Map.of(intent.id(), intent)));
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:state"), 1234L));
    }
}
