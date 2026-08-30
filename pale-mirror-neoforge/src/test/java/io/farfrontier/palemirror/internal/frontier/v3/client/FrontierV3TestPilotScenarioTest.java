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
    void visualFramesAreCleanByDefaultAndCannotBeDuplicated() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait","ms":0}],"frames":[{"after":1,"name":"clean-frame"}]}""");
        assertEquals(1, parsed.actionCount());
        assertEquals(1, parsed.frames().size());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait","ms":0}],"frames":[
                {"after":1,"name":"one"},{"after":1,"name":"two"}]}"""));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"hud","visible":false}]}"""));
    }

    @Test
    void acceptsOnlyBoundedReadOnlyDiagnosticWaitPredicates() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_diagnostic","view":"site","id":"site:1-wheat-field",
                "expect":{"growthStage":7},"timeoutMs":180000}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_diagnostic","view":"site","id":"site:1-wheat-field",
                "expect":[],"timeoutMs":180000}]}"""));
    }

    @Test
    void acceptsOneNamedHiveDiagnosticWithoutGrantingMutationAuthority() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_diagnostic","view":"hive","id":"hive:frontier",
                "expect":{"addedOrgans":1},"timeoutMs":180000}]}""");
        assertEquals(1, parsed.actionCount());
    }

    @Test
    void acceptsOneReadOnlyExactResidentTransitDiagnostic() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_diagnostic","view":"transit","id":"resident:1-1",
                "expect":{"journeyStatus":"EN_ROUTE"},"timeoutMs":180000}]}""");
        assertEquals(1, parsed.actionCount());
    }

    @Test
    void acceptsOnlyBoundedWholeTickFastForwardActions() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"fast_forward","ticks":24000}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"fast_forward","ticks":24001}]}"""));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"fast_forward","ticks":1.5}]}"""));
    }

    @Test
    void requiresAllCanonicalIdentitiesForADomainHarvestResult() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_harvest_result","siteId":"site:1-wheat-field",
                "intentId":"intent:site-harvest-1-wheat-field-1","itemId":"item:site-harvest-1-wheat-field-1-wheat","timeoutMs":180000}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_harvest_result","siteId":"site:1-wheat-field",
                "intentId":"intent:site-harvest-1-wheat-field-1","timeoutMs":180000}]}"""));
    }

    @Test
    void acceptsBoundedOrdinaryPlayerChunkVisitsAndSemanticCameraEvidence() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"setup":[{"type":"visit","dimension":"pale_mirror:frontier_graybox",
                "position":{"x":1,"y":65,"z":2},"settleMs":1000}],"actions":[
                {"type":"assert_visible_block","position":{"x":1,"y":64,"z":2},"timeoutMs":10000},
                {"type":"assert_visible_board","text":"WHEAT FIELD","position":{"x":4,"y":67,"z":5},
                "radius":3,"maxDistance":64,"maxAngleDeg":50,"timeoutMs":30000}]}""");
        assertEquals(1, parsed.setupCount());
        assertEquals(2, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"setup":[{"type":"visit","dimension":"bad_dimension",
                "position":{"x":1,"y":65,"z":2},"settleMs":1000}],"actions":[]}"""));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"assert_visible_board","text":"WHEAT FIELD",
                "position":{"x":4,"y":67,"z":5},"maxDistance":129,"timeoutMs":30000}]}"""));
    }

    @Test
    void acceptsSemanticCameraTargetsInEitherSupportedForm() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[
                {"type":"look","position":{"x":1,"y":64,"z":2}},
                {"type":"look","at":{"x":3,"y":65,"z":4}}]}""");
        assertEquals(2, parsed.actionCount());
    }

    @Test
    void permitsOnlyBoundedOrdinaryV3BoardInteractionWithAVisibleCardReceipt() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"interact_board","text":"WHEAT FIELD","title":"Northwatch",
                "position":{"x":4,"y":67,"z":5},"radius":3,"maxDistance":64,"maxAngleDeg":50,"timeoutMs":30000}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"interact_board","text":"WHEAT FIELD",
                "position":{"x":4,"y":67,"z":5},"timeoutMs":30000}]}"""));
    }

    @Test
    void acceptsOnlyBoundedOrdinaryContainerActionsAndReadOnlyIngressWaits() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[
                {"type":"open_container","position":{"x":1,"y":64,"z":2},"timeoutMs":10000},
                {"type":"quick_move_from_inventory","item":"minecraft:glowstone_dust","count":1,"timeoutMs":10000},
                {"type":"quick_move_from_container","item":"minecraft:wheat","count":64,"timeoutMs":10000},
                {"type":"wait_until_container_item","containerId":"container:4-depot","item":"minecraft:glowstone_dust","count":1,"slot":0,"timeoutMs":30000}]}""");
        assertEquals(4, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"quick_move_from_inventory","item":"minecraft:glowstone_dust","count":65,"timeoutMs":10000}]}"""));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"quick_move_from_container","item":"minecraft:wheat","count":0,"timeoutMs":10000}]}"""));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_container_item","containerId":"depot:4","item":"minecraft:glowstone_dust","count":1,"timeoutMs":30000}]}"""));
    }

    @Test
    void permitsATypeBoundedOrdinaryEntityInteractionWithoutEntityIdentityAuthority() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"interact_nearest_entity","entityType":"minecraft:chest_minecart",
                "maxDistance":16,"timeoutMs":30000}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"interact_nearest_entity","entityType":"minecraft:chest_minecart",
                "maxDistance":65,"timeoutMs":30000}]}"""));
    }

    @Test
    void permitsBoundedOrdinaryAttacksWithoutEntityIdentityAuthority() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"attack_nearest_entity","entityType":"minecraft:villager",
                "maxDistance":8,"maxAttacks":4,"timeoutMs":30000}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"attack_nearest_entity","entityType":"minecraft:villager",
                "maxDistance":8,"maxAttacks":41,"timeoutMs":30000}]}"""));
    }
}
