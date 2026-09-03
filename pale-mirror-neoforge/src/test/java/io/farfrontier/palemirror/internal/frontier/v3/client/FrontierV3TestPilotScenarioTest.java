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
    void acceptsTheReadOnlyGlobalPerformanceDiagnosticWithoutAnObjectIdentity() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_diagnostic","view":"performance","id":"",
                "expect":{"status":"ok","stageCount":1},"timeoutMs":180000}]}""");
        assertEquals(1, parsed.actionCount());
    }

    @Test
    void acceptsOneNamedHiveDiagnosticWithoutGrantingMutationAuthority() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_diagnostic","view":"hive","id":"hive:frontier",
                "expect":{"addedOrgans":1},"timeoutMs":180000}]}""");
        assertEquals(1, parsed.actionCount());
    }

    @Test
    void acceptsOneReadOnlyExactHiveNutrientTransferDiagnostic() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_diagnostic","view":"hive_transfer",
                "id":"transfer:hive-nutrient-task-development-hive-nutrient-transfer",
                "expect":{"phase":"IN_TRANSIT","cursor":0},"timeoutMs":180000}]}""");
        assertEquals(1, parsed.actionCount());
    }

    @Test
    void acceptsOneReadOnlyExactHiveMobilizationDiagnostic() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"inspect","view":"hive_mobilization",
                "id":"mobilization:development-hive-mobilization"}]}""");
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
    void acceptsOneReadOnlyExactMedicalTreatmentDiagnostic() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"wait_until_diagnostic","view":"medical","id":"medical:1-1",
                "expect":{"medicalStatus":"COMPLETED"},"timeoutMs":180000}]}""");
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
    void permitsOnlyNamedImmutableDiagnosticAnchorsForPresentationAndContainerOpening() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[
                {"type":"wait_until_block","position":{"diagnostic":{"view":"site","id":"site:1-wheat-field","field":"firstCrop"}},"block":"minecraft:wheat","timeoutMs":10000},
                {"type":"look","at":{"diagnostic":{"view":"site","id":"site:1-wheat-field","field":"firstCrop"}}},
                {"type":"assert_visible_block","position":{"diagnostic":{"view":"site","id":"site:1-wheat-field","field":"firstCrop"}},"timeoutMs":10000},
                {"type":"open_container","position":{"diagnostic":{"view":"container","id":"container:1-depot","field":"position"}},"timeoutMs":10000},
                {"type":"look","at":{"diagnostic":{"view":"scene","id":"job:production-example","field":"productionCurrent"}}}]}""");
        assertEquals(5, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"break","position":{"diagnostic":{"view":"site","id":"site:1-wheat-field","field":"firstCrop"}}}]}"""));
    }

    @Test
    void permitsOnlyBoundedLocalNamedEntityCameraTargets() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"look_nearest_entity","entityType":"minecraft:zombie",
                "nameContains":"HIVE","maxDistance":128,"timeoutMs":30000}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"look_nearest_entity","entityType":"minecraft:zombie",
                "nameContains":"HIVE","maxDistance":129,"timeoutMs":30000}]}"""));
    }

    @Test
    void acceptsAReadOnlyOperationRelativeCameraAndLocalEntityPresentationProof() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[
                {"type":"visit_operation","operationId":"operation:supply-1-2","dimension":"pale_mirror:frontier_graybox",
                "offset":{"x":10,"y":1,"z":-10},"settleMs":1000,"timeoutMs":30000},
                {"type":"look_operation","operationId":"operation:supply-1-2","anchor":"travelCargo","timeoutMs":30000},
                {"type":"assert_visible_entity","entityType":"minecraft:text_display","nameContains":"CARAVAN",
                "maxDistance":64,"maxAngleDeg":50,"timeoutMs":30000}]}""");
        assertEquals(3, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"visit_operation","operationId":"operation:supply-1-2",
                "dimension":"pale_mirror:frontier_graybox","offset":{"x":33,"y":1,"z":0},"settleMs":1000,"timeoutMs":30000}]}"""));
    }

    @Test
    void permitsOneBoundedOrdinaryBlockPlacementWithoutWorldCommandAuthority() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"place","position":{"x":1,"y":65,"z":2},
                "item":"minecraft:gray_concrete","timeoutMs":10000}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"place","position":{"x":1,"y":65,"z":2},
                "item":"gray_concrete","timeoutMs":10000}]}"""));
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
                {"schema":1,"actions":[{"type":"open_container","position":{"diagnostic":{"view":"container","id":"container:1-depot","field":"slots"}},"timeoutMs":10000}]}"""));
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
                "nameContains":"ARMED DEFENDER","maxDistance":8,"maxAttacks":4,"timeoutMs":30000}]}""");
        assertEquals(1, parsed.actionCount());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"attack_nearest_entity","entityType":"minecraft:villager",
                "maxDistance":8,"maxAttacks":41,"timeoutMs":30000}]}"""));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TestPilotScenario.parse("""
                {"schema":1,"actions":[{"type":"attack_nearest_entity","entityType":"minecraft:villager",
                "nameContains":"","maxDistance":8,"maxAttacks":4,"timeoutMs":30000}]}"""));
    }

    @Test
    void permitsTheReadOnlyTraversalFoundryFixtureGate() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"setup":[{"type":"assert_fixture","timeoutMs":30000,"checks":[
                {"view":"traversal_foundry","id":"compiled","expect":{"status":"ok","passed":true}}]}],"actions":[]}""");
        assertEquals(1, parsed.setupCount());
    }

    @Test
    void permitsTheReadOnlyHiveFoundryFixtureGate() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"setup":[{"type":"assert_fixture","timeoutMs":30000,"checks":[
                {"view":"hive_foundry","id":"compiled@organ:west-heart","expect":{"status":"ok","passed":true}}]}],"actions":[]}""");
        assertEquals(1, parsed.setupCount());
    }

    @Test
    void permitsTheReadOnlyDeclaredRouteTopologyFixtureGate() {
        FrontierV3TestPilotScenario.Parsed parsed = FrontierV3TestPilotScenario.parse("""
                {"schema":1,"setup":[{"type":"assert_fixture","timeoutMs":30000,"checks":[
                {"view":"route_topology","id":"settlement:1","expect":{"status":"ok","gradedEdges":4}}]}],"actions":[]}""");
        assertEquals(1, parsed.setupCount());
    }
}
