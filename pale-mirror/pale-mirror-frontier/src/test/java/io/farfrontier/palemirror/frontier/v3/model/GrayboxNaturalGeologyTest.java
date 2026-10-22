package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrayboxNaturalGeologyTest {
    @Test void initialGeologyContainsNaturalSourcesButNotTheBuiltMine() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:graybox"), 125,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r22"));
        var geology = GrayboxNaturalGeology.compile(bootstrap);
        assertFalse(geology.isEmpty());
        assertTrue(geology.values().stream().anyMatch(block -> block.kind().equals("minecraft:coal_ore")));
        for (var deposit : GrayboxQuarryPlan.compile(bootstrap).sites().deposits().values()) {
            var layout = deposit.site().layout();
            for (var cell : layout.cells()) assertEquals(cell.definition().before(), geology.get(cell.source()));
            layout.terrainSupports().forEach((position, block) -> assertEquals(block, geology.get(position)));
            layout.fixedBlocks().keySet().forEach(position -> assertFalse(geology.containsKey(position),
                    "infrastructure must remain with its subsequent projection owner: " + position));
        }
        assertEquals(geology, GrayboxNaturalGeology.compile(bootstrap));
    }
}
