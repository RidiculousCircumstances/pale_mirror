package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionInputRepresentationTest {
    @Test
    void fungibleCustodyTransferPreservesLotRelationshipsAndTerminalReceiptIdentity() {
        SubjectId input = new SubjectId("lot:test-wheat");
        SubjectId account = new SubjectId("custody:test-depot");
        SubjectId claim = new SubjectId("claim:test-production");
        for (ProductionInputHold hold : List.of(new ProductionInputHold.FungibleCold(input, account, claim),
                new ProductionInputHold.FungibleBound(input, account, claim, 1L),
                new ProductionInputHold.FungibleBound(input, account, claim, 2L))) {
            assertEquals(FrontierDomainRelationships.EntityKind.RESOURCE_LOT, hold.resourceEntityKind());
            assertReceipt(hold, TerminalProductionReceipt.ResourceRepresentation.RESOURCE_LOT);
        }
    }

    @Test
    void exactCustodyTransferPreservesItemRelationshipsAndTerminalReceiptIdentity() {
        SubjectId input = new SubjectId("item:test-wheat");
        ExactItemStack stack = new ExactItemStack(input, new SubjectId("settlement:test"), "minecraft:wheat", 64,
                new InventoryCustody.ContainerSlot(new SubjectId("container:test-depot"), 0));
        for (ProductionInputHold hold : List.of(new ProductionInputHold.Cold(stack), new ProductionInputHold.Materialized(input))) {
            assertEquals(FrontierDomainRelationships.EntityKind.EXACT_ITEM, hold.resourceEntityKind());
            assertReceipt(hold, TerminalProductionReceipt.ResourceRepresentation.EXACT_ITEM);
        }
    }

    private static void assertReceipt(ProductionInputHold hold, TerminalProductionReceipt.ResourceRepresentation expected) {
        ProductionJob job = new ProductionJob(new SubjectId("job:test"), new SubjectId("task:test"), new SubjectId("settlement:test"),
                new SubjectId("facility:test"), new SubjectId("resident:test"), hold.itemId(), hold,
                new SubjectId("output:test"), "minecraft:bread", 64);
        TerminalProductionReceipt receipt = TerminalProductionReceipt.of(job);
        assertEquals(expected, receipt.inputRepresentation());
        assertEquals(expected, receipt.outputRepresentation());
        assertEquals(hold.itemId(), receipt.inputId());
        assertEquals(job.outputItemId(), receipt.outputId());
        assertEquals(job.workerId(), receipt.workerId());
    }
}
