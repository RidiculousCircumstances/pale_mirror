package io.farfrontier.palemirror.frontier.v3.model.agriculture;

import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoilPreparationCatalogTest {
    @Test void nextStepsAreDataAndAddingAnExistingToolTransformationNeedsNoNewSoilEnum() throws IOException {
        String data = definitions().replace("soils=UNKNOWN,FARMLAND,DIRT,OBSTRUCTED,GRASS,COARSE_DIRT",
                "soils=UNKNOWN,FARMLAND,DIRT,OBSTRUCTED,GRASS,COARSE_DIRT,PATH")
                .replace("steps=grass_till,dirt_till,coarse_till", "steps=grass_till,dirt_till,coarse_till,path_till")
                + "\nsoil.PATH.tag=7\nsoil.PATH.block=minecraft:dirt_path\nsoil.PATH.role=PREPARABLE\n"
                + "step.path_till.before=PATH\nstep.path_till.after=FARMLAND\nstep.path_till.operation=TILL\n"
                + "step.path_till.tool=pale_mirror:till\nstep.path_till.tier=0\nstep.path_till.wear=2\nstep.path_till.nativeAbility=till\nstep.path_till.effortPermille=1500\n";
        var catalog = read(data); var extra = catalog.byBlock("minecraft:dirt_path").orElseThrow();
        assertEquals(1, catalog.path(extra).size());
        assertEquals(catalog.require("FARMLAND"), catalog.path(extra).getFirst().after());
        assertEquals(2, catalog.step(extra).tool().wear());
        assertEquals(300, catalog.step(extra).workUnits(200));
        assertNotEquals(ResourceFieldCycle.Soil.catalog().canonicalText(), catalog.canonicalText());
    }
    @Test void malformedGraphsAndAmbiguousOrMissingDeclarationsFailClosed() throws IOException {
        String data = definitions();
        for (String bad : new String[]{data.replace("step.coarse_till.after=DIRT", "step.coarse_till.after=COARSE_DIRT"),
                data.replace("step.dirt_till.after=FARMLAND", "step.dirt_till.after=COARSE_DIRT"),
                data.replace("soil.COARSE_DIRT.tag=6", "soil.COARSE_DIRT.tag=3"),
                data.replace("step.coarse_till.before=COARSE_DIRT", "step.coarse_till.before=DIRT"),
                data.replace("step.coarse_till.operation=TILL\n", ""), data + "\nsoil.COARSE_DIRT.tag=6\n"})
            assertThrows(IllegalArgumentException.class, () -> read(bad));
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldCycle.Soil.fromWireTag(250));
    }
    private static SoilPreparationCatalog read(String value) throws IOException {
        return SoilPreparationCatalog.read(new ByteArrayInputStream(value.getBytes(StandardCharsets.ISO_8859_1)));
    }
    private static String definitions() throws IOException {
        try (var in = SoilPreparationCatalogTest.class.getResourceAsStream("/io/farfrontier/palemirror/frontier/v3/soil-preparation.properties")) {
            return new String(java.util.Objects.requireNonNull(in).readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
