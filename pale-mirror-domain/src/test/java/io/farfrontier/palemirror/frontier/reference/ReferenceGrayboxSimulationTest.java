package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenceGrayboxSimulationTest {
    @Test
    void persistsOnlyTheCompleteSourceStateAndContinuesFromIt() {
        ReferenceGrayboxSimulation source = ReferenceGrayboxSimulation.create(42L);
        byte[] document = source.save();
        ReferenceGrayboxSimulation restored = ReferenceGrayboxSimulation.restore(document);

        assertArrayEquals(document, restored.save());
        assertEquals(source.snapshot(), restored.snapshot());
        source.tick();
        restored.tick();
        assertEquals(source.snapshot(), restored.snapshot());
    }

    @Test
    void restoresASeparatedHarvesterAfterItsOriginOrganWasDestroyed() {
        ReferenceGrayboxSimulation source = ReferenceGrayboxSimulation.create(9_031_746_258_841_137_206L);
        for (int day = 0; day < 239; day++) source.tick();

        byte[] document = source.save();
        ReferenceGrayboxSimulation restored = ReferenceGrayboxSimulation.restore(document);

        assertArrayEquals(document, restored.save(),
                "a live swarm's historical origin must not become a dangling-reference persistence failure");
        assertEquals(source.snapshot(), restored.snapshot());
    }

    @Test
    void exposesTypedPhysicalFactsWithoutLettingNeoForgeMutateOwners() {
        ReferenceGrayboxSimulation simulation = ReferenceGrayboxSimulation.create(42L);
        simulation.tick();
        simulation.tick();
        ReferenceGrayboxSnapshot snapshot = simulation.snapshot();
        String residentId = snapshot.residents().getFirst().id();

        ReferenceGrayboxObservationOutcome outcome = simulation.observe(ReferenceGrayboxResidentObservation.killed(
                "test:resident-killed", snapshot.stateRevision(), residentId));

        assertTrue(outcome.applied());
        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_UNKNOWN,
                simulation.observe(ReferenceGrayboxResidentObservation.killed(
                        "test:resident-repeat", outcome.stateRevision(), residentId)).status());
        assertEquals(simulation.snapshot(), ReferenceGrayboxSimulation.restore(simulation.save()).snapshot(),
                "a physical death must not leave a stale company employee reference in a persisted source state");
    }

    @Test
    void rejectsAStateDocumentThatCannotBeHydrated() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxSimulation.restore(new byte[] {1, 2, 3}));
    }
}
