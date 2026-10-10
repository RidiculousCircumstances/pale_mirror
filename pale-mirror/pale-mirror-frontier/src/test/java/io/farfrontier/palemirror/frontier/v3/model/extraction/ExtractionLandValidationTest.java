package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionLandValidationTest {
    @Test void aValidatedLayoutCannotAuthorizeChangedBoundsOrAnotherLayoutsForeignGeometry() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:land-validation"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r3"));
        var layout = FrontierWorldState.initial(bootstrap).extractionSites().deposits().values()
                .iterator().next().site().layout();
        assertDoesNotThrow(() -> ExtractionLandValidation.require(layout, bootstrap.bounds()));
        assertDoesNotThrow(() -> ExtractionLandValidation.require(layout, bootstrap.bounds()));
        assertThrows(IllegalArgumentException.class, () -> ExtractionLandValidation.require(layout,
                new WorldBounds(10000, 10000, 1, 1)));
        assertDoesNotThrow(() -> ExtractionLandValidation.require(layout, bootstrap.bounds()));
        var blocks = new HashMap<>(layout.fixedBlocks());
        blocks.put(new BlockPosition(10000, 60, 10000), new BlockExtraction.Block("minecraft:stone", java.util.Map.of()));
        var foreign = new ExtractionLayout(layout.cells(), blocks, layout.accessSurfaces(),
                layout.entrance(), layout.container(), layout.storagePort());
        assertThrows(IllegalArgumentException.class, () -> ExtractionLandValidation.require(foreign, bootstrap.bounds()));
        assertDoesNotThrow(() -> ExtractionLandValidation.require(layout, bootstrap.bounds()));
    }
}
