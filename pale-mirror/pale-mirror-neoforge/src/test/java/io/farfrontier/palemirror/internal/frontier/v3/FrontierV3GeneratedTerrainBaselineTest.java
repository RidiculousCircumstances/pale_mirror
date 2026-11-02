package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** A declared COLD soil sample cannot be used over an unrelated native generated surface. */
class FrontierV3GeneratedTerrainBaselineTest {
    @Test void actualSurfaceMapMustMatchTheDeclaredAgricultureBeforeBootstrap(){
        var declared=FrontierRulesets.installed("frontier-v3-simulation-terrain-r31").fields().agriculture();
        assertTrue(declared.isPresent());
        assertThrows(IllegalStateException.class,()->FrontierV3GeneratedTerrainBaseline.requireAgriculture(declared,Optional.empty()));
        var different=Optional.of(GrayboxAgriculture.rules());assertNotEquals(declared,different);
        assertThrows(IllegalStateException.class,()->FrontierV3GeneratedTerrainBaseline.requireAgriculture(declared,different));
        assertDoesNotThrow(()->FrontierV3GeneratedTerrainBaseline.requireAgriculture(declared,declared));
        assertDoesNotThrow(()->FrontierV3GeneratedTerrainBaseline.requireAgriculture(Optional.empty(),Optional.empty()));
    }
}
