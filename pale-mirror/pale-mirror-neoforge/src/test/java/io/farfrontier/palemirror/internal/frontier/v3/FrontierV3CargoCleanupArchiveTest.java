package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoCleanupArchiveTest {
    @TempDir Path temporary;
    private static final WorldId WORLD = new WorldId("frontier:cleanup-archive");

    private static FrontierV3CargoDeparture receipt(long epoch) {
        return new FrontierV3CargoDeparture(new SceneLeaseId("lease:archived"), new SubjectId("cargo:archived"),
                new UUID(0, 5), 8, epoch, new BodyPosition(3, 64, 4),
                IntStream.range(0, 27).mapToObj(index -> { var tag = new CompoundTag(); tag.putInt("slot", index); return tag; }).toList());
    }

    @Test void onlyCurrentDrainingOwnerMayRefreshPreparationBeforeRelease() throws IOException {
        var config = io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(WORLD, 41L);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(config);
        var initial = config.initialState();
        var lease = FrontierV3GameTestSceneLeases.exact(initial, engine.checkpoint(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:prepared-cleanup"));
        var hot = initial.prepareSceneLease(lease);
        for (var member : lease.members()) hot = io.farfrontier.palemirror.frontier.v3.model.ModeledActorBodyFacts.present(hot, member.actorId());
        hot = hot.transitionSceneLease(lease.id(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.HOT);
        var state = hot.transitionSceneLease(lease.id(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.DRAINING);
        var draining = state.sceneLeases().get(lease.id());
        var cargo = io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.logistics(lease).cargoId();
        var first = new FrontierV3CargoDeparture(lease.id(), cargo, FrontierV3CargoCarrierExecutor.id(lease), lease.revision(),
                FrontierV3CargoCarrierAuthority.currentEpoch(state.fencedRecovery(), draining).orElseThrow(),
                new BodyPosition(3, 64, 4), receipt(1).inventory());
        var changed = new FrontierV3CargoDeparture(first.leaseId(), first.cargoId(), first.entityId(), first.sceneRevision(),
                first.authorityEpoch(), new BodyPosition(4, 64, 4), first.inventory());
        var archive = new FrontierV3CargoCleanupArchive(temporary.resolve("prepared"), WORLD);
        var active = hot;
        assertThrows(IOException.class, () -> archive.prepareRelease(active, active.sceneLeases().get(lease.id()), first));
        archive.prepareRelease(state, draining, first);
        archive.prepareRelease(state, draining, changed);
        assertEquals(changed, archive.observation(first.entityId()).orElseThrow());
        assertThrows(IOException.class, () -> archive.retain(first));
        assertThrows(IOException.class, () -> archive.prepareRelease(state, lease, first));
        archive.markSaved(changed);
        assertThrows(IOException.class, () -> archive.prepareRelease(state, draining, changed));
        assertEquals(changed, archive.observation(first.entityId()).orElseThrow());
    }

    @Test void exactWitnessSurvivesReopenAndContradictoryRetryCannotOverwriteIt() throws IOException {
        var directory = temporary.resolve("archive");
        var archive = new FrontierV3CargoCleanupArchive(directory, WORLD);
        var receipt = receipt(1);
        archive.retain(receipt); archive.retain(receipt);
        var reopened = new FrontierV3CargoCleanupArchive(directory, WORLD);
        assertEquals(receipt, reopened.observation(receipt.entityId()).orElseThrow());
        assertThrows(IOException.class, () -> reopened.retain(receipt(2)));
        assertEquals(receipt, reopened.observation(receipt.entityId()).orElseThrow());
        assertFalse(Files.exists(directory.resolve(receipt.entityId() + ".pending")));
        assertThrows(IOException.class, () -> new FrontierV3CargoCleanupArchive(directory, new WorldId("frontier:foreign"))
                .observation(receipt.entityId()));
        assertThrows(IOException.class, () -> reopened.forgetAcknowledged(receipt(2)));
        assertTrue(reopened.observation(receipt.entityId()).isPresent());
        reopened.forgetAcknowledged(receipt);
        assertTrue(reopened.observation(receipt.entityId()).isEmpty());
    }

    @Test void partialUnpublishedWriteIsNotEvidenceAndCanBeRetried() throws IOException {
        var directory = temporary.resolve("archive"); Files.createDirectory(directory);
        var receipt = receipt(1);
        Files.write(directory.resolve(receipt.entityId() + ".pending"), new byte[]{1, 2});
        var archive = new FrontierV3CargoCleanupArchive(directory, WORLD);
        assertTrue(archive.observation(receipt.entityId()).isEmpty());
        archive.retain(receipt);
        assertEquals(receipt, archive.observation(receipt.entityId()).orElseThrow());
    }

    @Test void saveConfirmationSurvivesRestartAndInterruptedMetadataCompaction() throws IOException {
        var directory = temporary.resolve("archive");
        var archive = new FrontierV3CargoCleanupArchive(directory, WORLD);
        var receipt = receipt(1);
        archive.retain(receipt);
        assertTrue(archive.savedWitnesses().isEmpty());
        archive.markSaved(receipt);
        archive.markSaved(receipt);
        assertThrows(IOException.class, () -> archive.markSaved(receipt(2)));
        var reopened = new FrontierV3CargoCleanupArchive(directory, WORLD);
        assertEquals(java.util.List.of(receipt), reopened.savedWitnesses());
        // Crash boundary: original witness unlink persisted, marker unlink did not.
        Files.delete(directory.resolve(receipt.entityId() + ".nbt"));
        assertEquals(java.util.List.of(receipt), reopened.savedWitnesses());
        assertThrows(IOException.class, () -> reopened.forgetAcknowledged(receipt(2)));
        reopened.forgetAcknowledged(receipt);
        assertTrue(reopened.savedWitnesses().isEmpty());
        reopened.forgetAcknowledged(receipt);
    }

    @Test void missingOrForeignWitnessCannotBecomeSaveConfirmation() throws IOException {
        var directory = temporary.resolve("archive");
        var archive = new FrontierV3CargoCleanupArchive(directory, WORLD);
        assertThrows(IOException.class, () -> archive.markSaved(receipt(1)));
        archive.retain(receipt(1));
        archive.markSaved(receipt(1));
        var foreign = new FrontierV3CargoCleanupArchive(directory, new WorldId("frontier:other"));
        assertThrows(IOException.class, foreign::savedWitnesses);
        assertThrows(IOException.class, () -> foreign.forgetAcknowledged(receipt(1)));
        assertEquals(java.util.List.of(receipt(1)), archive.savedWitnesses());
    }

    @Test void corruptPublishedWitnessFailsClosedInsteadOfBeingReplaced() throws IOException {
        var directory = temporary.resolve("archive"); Files.createDirectory(directory);
        var receipt = receipt(1);
        Files.write(directory.resolve(receipt.entityId() + ".nbt"), new byte[]{1, 2});
        var archive = new FrontierV3CargoCleanupArchive(directory, WORLD);
        assertThrows(IOException.class, () -> archive.observation(receipt.entityId()));
        assertThrows(IOException.class, () -> archive.retain(receipt));
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(directory.resolve(receipt.entityId() + ".nbt")));
    }
}
