package io.farfrontier.palemirror.internal.frontier.v3.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3TestPilotScenarioTest {
    @Test
    void parsesSetupSeparatelyFromEvidenceActions() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"setup":[{"type":"command","command":"/tp PMTestPilot 0 64 0"}],
                "actions":[{"type":"wait_until_block","position":{"x":1,"y":64,"z":1},"block":"minecraft:wheat","timeoutMs":1000}]}""");
        assertEquals(1, parsed.setupCount());
        assertEquals(1, parsed.actionCount());
    }

    @Test
    void rejectsUnknownActionBeforeTheVisibleClientConnects() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"rewrite_canon"}]}"""));
    }

    @Test
    void acceptsOnlyBooleanLocalHudPresentationAction() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"hud","visible":false}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"hud","visible":"false"}]}"""));
    }
}
