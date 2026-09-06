package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringToolCustodyTest {
    @Test void recognizesOnlyTheDeclaredFirstGrayboxTool() {
        assertTrue(EngineeringToolCustody.isTool("minecraft:iron_pickaxe"));
        assertFalse(EngineeringToolCustody.isTool("minecraft:iron_sword"));
    }
}
