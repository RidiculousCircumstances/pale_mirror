package io.farfrontier.palemirror.internal.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class MaterializationJobRegistryTest {
    @Test
    void rejectsTwoCurrentJobsForSameTargetChannel() {
        MaterializationJobRegistry registry = new MaterializationJobRegistry();
        registry.put(job("one", 1, JobState.PLANNED));

        assertThrows(IllegalStateException.class, () -> registry.put(job("two", 2, JobState.RUNNING)));
    }

    @Test
    void compactsSupersededTerminalJobsIntoReceipts() {
        MaterializationJobRegistry registry = new MaterializationJobRegistry();
        registry.put(job("one", 1, JobState.COMPLETED));
        registry.put(job("two", 2, JobState.COMPLETED));

        registry.compactTerminalJobs();

        assertEquals(1, registry.jobs().size());
        assertEquals(1, registry.receipts().size());
    }

    private static MaterializationJob job(String id, long revision, JobState state) {
        return new MaterializationJob("pm:job:" + id, "pale_mirror:target", "channel",
                MaterializationJobClass.CAPABILITY, revision, "policy", "v35", state,
                List.of(), 0, 0, "");
    }
}
