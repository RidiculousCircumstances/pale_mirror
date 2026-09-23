package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.OptionalLong;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoFootprintArchiveTest {
    @TempDir Path temporary;
    private static FrontierV3CargoRetirementFootprint first() {
        var world = new WorldId("frontier:footprint-archive"); var lease = new SceneLeaseId("lease:footprint");
        var cargo = new SubjectId("cargo:footprint");
        return new FrontierV3CargoRetirementFootprint(world, lease, cargo, CargoCarrierIdentity.id(world, lease, cargo),
                1, 1, Set.of(10L), OptionalLong.empty());
    }
    @Test void reopenedArchiveRetainsEveryColumnAndRejectsRegression() throws IOException {
        var initial = first(); var directory = temporary.resolve("archive");
        var archive = new FrontierV3CargoFootprintArchive(directory, initial.world());
        archive.retain(initial); archive.retain(initial.include(20));
        var removed = initial.include(20).removedAt(30); archive.retain(removed); archive.retain(removed);
        var reopened = new FrontierV3CargoFootprintArchive(directory, initial.world());
        assertEquals(removed, reopened.read(initial.entity(), 1).orElseThrow());
        assertThrows(IOException.class, () -> reopened.retain(initial));
        assertEquals(removed, reopened.read(initial.entity(), 1).orElseThrow());
        assertThrows(IOException.class, () -> new FrontierV3CargoFootprintArchive(directory, new WorldId("frontier:foreign")).read(initial.entity(), 1));
        assertFalse(Files.exists(directory.resolve(initial.entity() + "-1.pending")));
    }
    @Test void partialStagingIsNotPublishedAndMalformedTargetIsNotOverwritten() throws IOException {
        var initial = first(); var directory = temporary.resolve("archive"); Files.createDirectories(directory);
        Files.write(directory.resolve(initial.entity() + "-1.pending"), new byte[]{1, 2});
        var archive = new FrontierV3CargoFootprintArchive(directory, initial.world());
        assertTrue(archive.read(initial.entity(), 1).isEmpty()); archive.retain(initial);
        Path target = directory.resolve(initial.entity() + "-1.nbt");
        Files.write(target, new byte[]{1}, StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> archive.read(initial.entity(), 1));
        assertThrows(IOException.class, () -> archive.retain(initial.include(20)));
    }

    @Test void successorAttemptDoesNotEraseOldColumnsAndCleanupIsCompareExact() throws IOException {
        var initial = first();
        var next = new FrontierV3CargoRetirementFootprint(initial.world(), initial.lease(), initial.cargo(), initial.entity(),
                initial.sceneRevision(), 2, Set.of(30L), OptionalLong.empty());
        var directory = temporary.resolve("archive");
        var archive = new FrontierV3CargoFootprintArchive(directory, initial.world());
        archive.retain(initial); archive.retain(next); archive.retain(initial.include(20));
        var reopened = new FrontierV3CargoFootprintArchive(directory, initial.world());
        assertEquals(java.util.List.of(initial.include(20), next), reopened.inventory());
        assertThrows(IOException.class, () -> reopened.forgetExact(initial));
        assertEquals(2, reopened.inventory().size());
        reopened.forgetExact(initial.include(20)); reopened.forgetExact(initial.include(20));
        assertEquals(java.util.List.of(next), reopened.inventory());
        assertEquals(next, reopened.read(next.entity(), 2).orElseThrow());
    }

    @Test void renamedAttemptCannotMasqueradeAsAnotherEpoch() throws IOException {
        var initial = first(); var directory = temporary.resolve("archive");
        var archive = new FrontierV3CargoFootprintArchive(directory, initial.world()); archive.retain(initial);
        Files.move(directory.resolve(initial.entity() + "-1.nbt"), directory.resolve(initial.entity() + "-2.nbt"));
        assertThrows(IOException.class, () -> archive.read(initial.entity(), 2));
        assertThrows(IOException.class, archive::inventory);
    }

    @Test void savedMarkerSurvivesInterruptedCompactionWithoutAuthorizingNewEvidence() throws IOException {
        var initial = first().removedAt(20); var directory = temporary.resolve("archive");
        var archive = new FrontierV3CargoFootprintArchive(directory, initial.world());
        archive.retain(initial); archive.markSaved(initial); archive.markSaved(initial);
        assertEquals(java.util.List.of(initial), archive.savedInventory());
        // File-backed fault injection between the two ordered metadata unlink barriers.
        Files.delete(directory.resolve(initial.entity() + "-1.nbt"));
        var reopened = new FrontierV3CargoFootprintArchive(directory, initial.world());
        assertTrue(reopened.inventory().isEmpty());
        assertEquals(java.util.List.of(initial), reopened.savedInventory());
        reopened.forgetExact(initial);
        assertTrue(reopened.savedInventory().isEmpty());
        assertThrows(IOException.class, () -> reopened.markSaved(initial));
    }

    @Test void newReturnedColumnInvalidatesOlderSaveProofWithoutErasingRemovalHistory() throws IOException {
        var first = first().removedAt(20); var archive = new FrontierV3CargoFootprintArchive(temporary.resolve("archive"), first.world());
        archive.retain(first); archive.markSaved(first);
        var returned = first.include(30); archive.retain(returned);
        assertEquals(returned, archive.read(first.entity(), 1).orElseThrow());
        assertTrue(archive.savedInventory().isEmpty(), "old save proof cannot cover the new column");
        assertEquals(first.removalChunk(), returned.removalChunk());
        assertThrows(IOException.class, () -> archive.markSaved(first));
        archive.markSaved(returned);
        assertEquals(java.util.List.of(returned), archive.savedInventory());
    }
}
