package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoRemovalRetryTest {
    @TempDir Path directory;

    private static FrontierV3CargoRetirementFootprint birth() {
        var world = new WorldId("frontier:removal-retry"); var lease = new SceneLeaseId("lease:retry");
        var cargo = new SubjectId("cargo:retry");
        return new FrontierV3CargoRetirementFootprint(world, lease, cargo, CargoCarrierIdentity.id(world, lease, cargo),
                2, 1, Set.of(11L), OptionalLong.empty());
    }

    @Test void failedWriteRetainsObservedRemovalAndRetryPersistsBeforeSaveMayProceed() throws IOException {
        var birth = birth(); var archive = new FrontierV3CargoFootprintArchive(directory, birth.world());
        archive.retain(birth);
        var retry = new FrontierV3CargoRemovalRetry();
        var removal = FrontierV3CargoRemovalRetry.Removal.from(birth, 22L);
        retry.retain(removal);
        assertThrows(IOException.class, () -> retry.flush(value -> { throw new IOException("injected disk failure"); }));
        assertEquals(birth, archive.read(birth.entity(), 1).orElseThrow());
        retry.retain(removal); // Duplicate observation must not duplicate work.
        var calls = new ArrayList<FrontierV3CargoRemovalRetry.Removal>();
        retry.flush(value -> { value.persist(archive); calls.add(value); });
        assertEquals(List.of(removal), calls);
        assertEquals(birth.removedAt(22L), new FrontierV3CargoFootprintArchive(directory, birth.world()).read(birth.entity(), 1).orElseThrow());
        retry.flush(value -> fail("already persisted"));
    }

    @Test void missingBirthCannotBeManufacturedByRetry() throws IOException {
        var birth = birth(); var archive = new FrontierV3CargoFootprintArchive(directory, birth.world());
        var retry = new FrontierV3CargoRemovalRetry(); retry.retain(FrontierV3CargoRemovalRetry.Removal.from(birth, 22L));
        assertThrows(IOException.class, () -> retry.flush(value -> value.persist(archive)));
        assertTrue(archive.inventory().isEmpty());
        archive.retain(birth); // Restore exact fixture evidence, not inferred production repair.
        retry.flush(value -> value.persist(archive));
        assertEquals(birth.removedAt(22L), archive.read(birth.entity(), 1).orElseThrow());
    }

    @Test void budgetDefersSaveUntilEveryObservationIsPersistedWithoutDroppingColumns() throws IOException {
        var retry = new FrontierV3CargoRemovalRetry(); var seen = new ArrayList<Long>();
        for (int i = 0; i <= FrontierV3CargoRemovalRetry.MAX_WRITES_PER_PASS; i++)
            retry.retain(FrontierV3CargoRemovalRetry.Removal.from(birth(), i));
        assertThrows(IOException.class, () -> retry.flush(value -> seen.add(value.chunk())));
        assertEquals(FrontierV3CargoRemovalRetry.MAX_WRITES_PER_PASS, seen.size());
        retry.flush(value -> seen.add(value.chunk()));
        assertEquals(FrontierV3CargoRemovalRetry.MAX_WRITES_PER_PASS + 1, new HashSet<>(seen).size());
    }

    @Test void capacityOrMalformedEvidenceCannotSilentlyResumeEntitySaves() throws IOException {
        var retry = new FrontierV3CargoRemovalRetry(); var birth = birth();
        for (int i = 0; i < FrontierV3CargoRemovalRetry.MAX_PENDING; i++)
            retry.retain(FrontierV3CargoRemovalRetry.Removal.from(birth, i));
        assertThrows(IOException.class, () -> retry.retain(FrontierV3CargoRemovalRetry.Removal.from(birth, Long.MAX_VALUE)));
        assertThrows(IOException.class, () -> retry.flush(value -> fail("lost event cannot be waived")));
        var malformed = new FrontierV3CargoRemovalRetry(); malformed.fence();
        assertThrows(IOException.class, () -> malformed.flush(value -> fail("not resumable")));
    }
}
