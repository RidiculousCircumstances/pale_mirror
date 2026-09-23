package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoFootprintCoverageTest {
    private static final UUID ENTITY = new UUID(0, 1);

    @Test void allColumnsRequiredAndLaterUnknownOrPresentWriteInvalidatesEarlierAbsence() {
        var coverage = new FrontierV3CargoFootprintCoverage();
        coverage.observe(10, Set.of()); coverage.saved();
        assertFalse(coverage.absent(ENTITY, Set.of(10L, 20L)));
        coverage.observe(20, Set.of());
        assertTrue(coverage.absent(ENTITY, Set.of(10L, 20L)), "candidate only; caller still owes batch sync");
        coverage.invalidate(10);
        assertFalse(coverage.absent(ENTITY, Set.of(10L, 20L)));
        coverage.observe(10, Set.of(ENTITY)); coverage.saved();
        assertFalse(coverage.absent(ENTITY, Set.of(10L, 20L)));
        coverage.observe(10, Set.of()); coverage.saved();
        assertTrue(coverage.absent(ENTITY, Set.of(10L, 20L)));
    }

    @Test void oldColumnWriteFailurePreventsCrossColumnCandidateFromBecomingSavedEvidence() {
        var coverage = new FrontierV3CargoFootprintCoverage(); var batch = new FrontierV3EntitySaveBatch();
        var failed = new CompletableFuture<Void>();
        batch.record(10, failed); coverage.observe(10, Set.of());
        batch.record(20, CompletableFuture.completedFuture(null)); coverage.observe(20, Set.of());
        assertTrue(coverage.absent(ENTITY, Set.of(10L, 20L)));
        var ticket = batch.completePass(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        failed.completeExceptionally(new IllegalStateException("old column failed"));
        assertTrue(ticket.saved().isCompletedExceptionally()); assertFalse(batch.accept(ticket));
        coverage.invalidate(10);
        assertFalse(coverage.absent(ENTITY, Set.of(10L, 20L)));
    }

    @Test void inventoryIsBoundedAndDoesNotDropOlderEntriesToProduceFalseAbsence() {
        var coverage = new FrontierV3CargoFootprintCoverage();
        for (int index = 0; index < FrontierV3EntitySaveBatch.MAX_CHUNKS; index++) coverage.observe(index, Set.of());
        coverage.saved();
        assertTrue(coverage.absent(ENTITY, Set.of(0L)));
        coverage.observe(FrontierV3EntitySaveBatch.MAX_CHUNKS, Set.of());
        assertFalse(coverage.absent(ENTITY, Set.of(0L)), "capacity failure never grants cleanup");
        assertFalse(coverage.absent(ENTITY, Set.of()));
    }
}
