package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.serialization.JsonOps;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3GeneratedStructuresTest {
    private static final BlockPos POSITION = new BlockPos(1, 65, 2);
    private static final GrayboxCell CELL = new GrayboxCell(new BlockPosition(1, 65, 2),
            new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE, new SubjectId("structure:test")),
            GrayboxMaterial.HALL, GrayboxSemanticPart.WALL);
    @Test void chunkReceiptRoundTripsCompleteSemanticIdentity() {
        var value = new FrontierV3GeneratedStructures("test:initial", "configuration", 42, Map.of(POSITION, CELL));
        var encoded = FrontierV3GeneratedStructures.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, FrontierV3GeneratedStructures.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        encoded.getAsJsonObject().getAsJsonArray("cells").get(0).getAsJsonObject().addProperty("target", 999);
        assertThrows(RuntimeException.class, () -> FrontierV3GeneratedStructures.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
    @Test void lossAndRepairOverlayGeneratedProvenanceWithoutChangingOrRegeneratingIt() {
        var initial = FrontierV3GeneratedStructures.claim(CELL);
        var ledger = FrontierV3GrayboxLedger.inMemory(position -> position.equals(POSITION) ? initial : null);
        assertEquals(initial, ledger.claim(POSITION));
        ledger.defer(POSITION);
        assertTrue(ledger.claim(POSITION).deferred());
        assertEquals(1, ledger.claim(POSITION).revision());
        ledger.repaired(POSITION, initial.owner(), initial.targetTag(), initial.material(), initial.semanticPart());
        assertFalse(ledger.claim(POSITION).deferred());
        assertEquals(2, ledger.claim(POSITION).revision());
        assertFalse(initial.deferred());
        assertThrows(IllegalStateException.class, () -> ledger.retire(POSITION, initial.owner(), initial.targetTag(), initial.material(), initial.semanticPart()));
        assertThrows(IllegalStateException.class, () -> ledger.applied(POSITION, "structure:other", initial.targetTag(), initial.material(), initial.semanticPart()));
    }
    @Test void readOnlyIngressSharesTheWriterCursorAndLeavesChangedCellsToTheirOwner() {
        var second = new GrayboxCell(CELL.position().offset(1, 0, 0), CELL.semanticTarget(), CELL.material(), CELL.semanticPart());
        var work = new FirstVisibilityWork(List.of(CELL, second));
        work.observeCurrent(CELL::equals, new FrontierV3FirstVisibilityBudget(4, 1, 1, () -> 0));
        assertEquals(1, work.remaining());
        assertFalse(work.blocked());
        assertTrue(work.preparePending(4, window -> window.equals(List.of(second))));
        assertEquals(second, work.next());
        work.observe(FrontierV3GrayboxExecutor.ProjectionResult.CURRENT);
        assertTrue(work.complete());
    }
}
