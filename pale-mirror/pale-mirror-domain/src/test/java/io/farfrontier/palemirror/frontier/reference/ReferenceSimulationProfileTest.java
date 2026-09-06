package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReferenceSimulationProfileTest {
    @Test
    void grayboxUsesPythonHalfUpAndNoCohortMultiplier() {
        ReferenceSimulationProfile profile = ReferenceSimulationProfile.GRAYBOX_1_40;

        assertEquals(1.0d, profile.peopleFromSource(20.0d));
        assertEquals(1.0d, profile.peopleFromSource(21.0d));
        assertEquals(2.0d, profile.peopleFromSource(60.0d));
        assertEquals(2, profile.individualPeopleFromSource(60.0d));
        assertEquals(0.5d, profile.humanAmountFromSource(20.0d));
        assertEquals(2.0d, profile.collapsePopulationFromSource(40.0d));
        assertEquals(2, profile.individualCollapsePopulationFromSource(0.0d));
    }

    @Test
    void sourceProfileDoesNotApplyGrayboxConversion() {
        ReferenceSimulationProfile profile = ReferenceSimulationProfile.SOURCE_V2;

        assertEquals(120.5d, profile.peopleFromSource(120.5d));
        assertEquals(120.0d, profile.humanAmountFromSource(120.0d));
        assertThrows(IllegalStateException.class, () -> profile.individualPeopleFromSource(120.0d));
        assertEquals(40.0d, profile.collapsePopulationFromSource(40.0d));
        assertThrows(IllegalStateException.class, () -> profile.individualCollapsePopulationFromSource(40.0d));
    }

    @Test
    void rejectsValuesThatCannotBecomeCanonicalPeople() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceSimulationProfile.GRAYBOX_1_40.peopleFromSource(-0.1d));
        assertThrows(IllegalArgumentException.class, () -> ReferenceSimulationProfile.GRAYBOX_1_40.humanAmountFromSource(Double.NaN));
    }
}
