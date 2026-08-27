package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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
        assertTrue(state.structureConditions().values().stream().allMatch(condition -> condition == StructureCondition.INTACT));
        assertTrue(state.infection().isEmpty());
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
        assertTrue(changed.withInfection(cell, new FixedRatio(FixedScalar.ZERO)).infection().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> state.withActorLocation(resident, new BlockPosition(512, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> state.withStructureCondition(new SubjectId("structure:missing"), StructureCondition.DESTROYED));
    }

    @Test
    void codecRoundTripsCanonicalMutableStateAndRejectsInvalidState() {
        FrontierWorldState source = initial().withActorLocation(new SubjectId("bioform:west-0"), new BlockPosition(-400, 64, 400))
                .withStructureCondition(new SubjectId("structure:2-depot"), StructureCondition.DESTROYED)
                .withInfection(new InfectionCell(-100, 100), HALF);
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(source);
        assertEquals(source, codec.decode(encoded));
        encoded[4] = 2;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));

        Map<SubjectId, ActorLocation> missingActor = new LinkedHashMap<>(source.actorLocations());
        missingActor.remove(new SubjectId("resident:1-1"));
        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldState(source.bootstrap(), missingActor,
                source.structureConditions(), source.infection()));
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:state"), 1234L));
    }
}
