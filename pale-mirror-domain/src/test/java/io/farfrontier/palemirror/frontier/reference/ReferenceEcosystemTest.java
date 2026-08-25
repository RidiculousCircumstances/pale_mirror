package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceEcosystemTest {
    @Test
    void matchesSourceEcologyConsumptionHarvestRecoveryAndScorchMicroTrace() {
        ReferenceEcosystem ecosystem = ecosystem();
        assertEquals(84.93770730174153d, ecosystem.cell(0, 0).flora());
        assertEquals(25.60038891140793d, ecosystem.cell(1, 0).fauna());
        assertEquals(20.382273769302177d, ecosystem.cell(1, 1).flora());

        ReferenceEcosystem.Consumption consumed = ecosystem.consume(0, 0, 20.0d);
        assertEquals(20.0d, consumed.mass());
        assertEquals(0.2466233014718666d, consumed.geneticSignal());
        assertEquals(0.752217510356389d, ecosystem.humanOutputFactor("forest", 1, 0));
        ecosystem.humanExtract("forest", 1, 0, 15.0d);
        ecosystem.addDetritus(1, 1, 4.0d);
        ecosystem.restore(0, 0, 0.6d);
        ecosystem.scorch(1, 0, 0.4d);
        ecosystem.regenerate();

        assertCell(ecosystem.cell(0, 0), 81.3994305990937d, 8.262446064997617d, 0.04762959351351745d,
                69.70005108614868d, 0.55d, 0.01448d);
        assertCell(ecosystem.cell(1, 0), 73.74083576998439d, 16.979877450669854d, 15.159940899717258d,
                72.02187676422237d, 0.62d, 0.126368d);
        assertCell(ecosystem.cell(0, 1), 86.64585759190483d, 30.37119419947578d, 19.89025488528998d,
                83.94521203615909d, 0.88d, 0.0d);
        assertCell(ecosystem.cell(1, 1), 20.511593403315377d, 6.876157514057635d, 6.060961985428863d,
                23.88416763717718d, 0.28d, 0.0d);
        assertEquals(365.94617995744875d, ecosystem.totalOrganic());
        assertEquals(0.140848d, ecosystem.totalScar());
        assertEquals(73.74d, ecosystem.summaryAt(1, 0).get("flora"));
        assertEquals(0.126d, ecosystem.summaryAt(1, 0).get("scar"));
    }

    @Test
    void clampsCellReadsButRejectsEmptyOrRaggedWorlds() {
        ReferenceEcosystem ecosystem = ecosystem();
        assertEquals(ReferenceBiome.WETLAND, ecosystem.biomeAt(-3, 99));
        assertEquals(ecosystem.cell(0, 1), ecosystem.cell(-3, 99));
        assertThrows(IllegalArgumentException.class, () -> new ReferenceEcosystem(List.of(), new PythonRandom(1L)));
        assertThrows(IllegalArgumentException.class, () -> new ReferenceEcosystem(
                List.of(List.of(ReferenceBiome.PLAINS), List.of(ReferenceBiome.FOREST, ReferenceBiome.BARREN)), new PythonRandom(1L)));
    }

    private static ReferenceEcosystem ecosystem() {
        return new ReferenceEcosystem(List.of(List.of(ReferenceBiome.PLAINS, ReferenceBiome.FOREST),
                List.of(ReferenceBiome.WETLAND, ReferenceBiome.BARREN)), new PythonRandom(77L));
    }

    private static void assertCell(ReferenceEcosystemCell cell, double flora, double fauna, double detritus,
                                   double nutrients, double moisture, double scar) {
        assertEquals(flora, cell.flora());
        assertEquals(fauna, cell.fauna());
        assertEquals(detritus, cell.detritus());
        assertEquals(nutrients, cell.nutrients());
        assertEquals(moisture, cell.moisture());
        assertEquals(scar, cell.scar());
    }
}
