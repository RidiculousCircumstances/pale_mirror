package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InfectionSourceIdTest {
    @Test
    void sourceIsCanonicalFacilityDataAndDoesNotChangeDuringLifecycle() {
        FacilityState facility = new FacilityState(new WorldObjectId("pale_mirror:spore_site"),
                new InfectionSourceId("pale_mirror:test_source"), 80, 10, 10);

        facility.infect(12);
        facility.beginRecovery(1);
        facility.advanceRecovery();

        assertEquals(new InfectionSourceId("pale_mirror:test_source"), facility.infectionSource());
    }

    @Test
    void rejectsNonSemanticSourceId() {
        assertThrows(IllegalArgumentException.class, () -> new InfectionSourceId("Spore"));
    }
}
