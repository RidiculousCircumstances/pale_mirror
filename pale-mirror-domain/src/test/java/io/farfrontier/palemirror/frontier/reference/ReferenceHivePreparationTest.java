package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenceHivePreparationTest {
    @Test
    void scheduledPreparationRefreshesSignalStatusThenPaysExactAdaptationCost() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        model.organs().get(1).samples(20.0d);
        ReferenceHiveOrgan synapse = model.createOrgan(3, 2, 100.0d, 6.0d, 1, ReferenceOrganKind.SYNAPSE);
        model.recordDamage("combat", 5.0d);
        model.recordDamage("scorch", 8.0d);

        model.prepareHiveOrders(10);

        assertEquals(1.0d, model.genome().get("thermotolerance"));
        assertEquals(7.294365542527572d, model.organs().get(1).samples());
        assertEquals(6.0d, synapse.samples());
        assertFalse(model.organs().get(1).feral());
        assertFalse(synapse.feral());
        ReferenceHiveHistoryEvent event = model.projectHistory().getLast();
        assertEquals(10, event.day());
        assertEquals("adaptation:thermotolerance", event.kind());
        assertEquals(1, event.sourceOrganId());
    }

    @Test
    void preparationDoesNotSpendOrAdaptWhenTheSourceThresholdIsNotMet() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        model.organs().get(1).samples(11.99d);
        model.recordDamage("combat", 8.0d);

        model.prepareHiveOrders(10);

        assertTrue(model.genome().isEmpty());
        assertEquals(11.99d, model.organs().get(1).samples());
        assertTrue(model.projectHistory().isEmpty());
    }
}
