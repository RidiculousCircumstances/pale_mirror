package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.core.BlockPos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ARC-001C recurrence fence for the producer-stamped physical semantic target path. */
class PhysicalDeltaSemanticIdentityArchitectureTest {
    @Test
    void repairAndObservationBoundariesDoNotClassifyStructuralAuthorityFromIdentifiers() {
        Path root = root();
        List<Path> boundaries = List.of(
                root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/model/FrontierWorldPhysicalDeltaSupport.java"),
                root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/process/StructuralRepairProcess.java"),
                root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/model/StructuralRepairStateSupport.java"),
                root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/process/FrontierWorldPhysicalObservationProcess.java"),
                root.resolve("pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3GrayboxExecutor.java"),
                root.resolve("pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3HiveMobilizationExecutor.java"),
                root.resolve("pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3PhysicalObservationExecutor.java"));
        for (Path boundary : boundaries) {
            String source = read(boundary);
            assertFalse(source.contains("startsWith(\"structure:"), () -> "identifier prefix dispatch in " + boundary);
            assertTrue(source.contains("semanticTarget") || source.contains("targetTag"), () -> "explicit target absent from " + boundary);
        }
    }

    @Test
    void durableCodecsAndBridgeCarryTheTypedTargetInsteadOfReconstructingIt() {
        Path root = root();
        String delta = read(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/persistence/PhysicalDeltaPayloadCodecs.java"));
        String snapshot = read(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/persistence/FrontierWorldStateCodec.java"));
        String aftermath = read(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/persistence/DeferredAftermathStateCodec.java"));
        String bridge = read(root.resolve("pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3PhysicalObservationLedger.java"));
        assertTrue(delta.contains("writeTarget") && delta.contains("readTarget"));
        assertTrue(snapshot.contains("VERSION = 163") && snapshot.contains("writePhysicalDeltaTarget"));
        assertTrue(aftermath.contains("cell.semanticTarget()"));
        assertTrue(bridge.contains("claim.targetTag()") && bridge.contains("value.putInt(\"targetTag\""));
    }

    @Test
    void persistedBridgeRejectsAnUnknownSemanticTagInsteadOfSelectingATypeFromProvenance() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FrontierV3PhysicalObservationLedger.Semantic("structure:test", 127, "FOUNDATION"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FrontierV3GrayboxLedger.Claim("structure:test", 127, "HALL", "FOUNDATION", false));
    }

    @Test
    void terminalRetirementRejectsAnOtherwiseMatchingClaimWithTheWrongSemanticTarget() {
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.inMemory();
        BlockPos position = new BlockPos(1, 64, 1);
        ledger.applied(position, "structure:test", 1, "HALL", "FOUNDATION");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> ledger.retire(position, "structure:test", 2, "HALL", "FOUNDATION"));
    }

    @Test
    void productionConstructorsCannotRepresentABareSemanticOwner() {
        List<Class<?>> targets = List.of(
                io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta.class,
                io.farfrontier.palemirror.frontier.v3.model.GrayboxCell.class,
                io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCell.class);
        for (Class<?> target : targets) {
            assertFalse(java.util.Arrays.stream(target.getConstructors())
                    .anyMatch(constructor -> java.util.Arrays.asList(constructor.getParameterTypes())
                            .contains(io.farfrontier.palemirror.frontier.v3.api.SubjectId.class)),
                    () -> "bare-owner production constructor in " + target.getName());
        }
        assertFalse(read(root().resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/model/PhysicalDelta.java"))
                .contains("Optional<?>"));
    }

    private static Path root() {
        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent()) {
            if (Files.isDirectory(current.resolve("pale-mirror-frontier"))) return current;
        }
        throw new IllegalStateException("cannot locate Pale Mirror source root");
    }

    private static String read(Path path) {
        try { return Files.readString(path); }
        catch (IOException error) { throw new IllegalStateException("cannot read " + path, error); }
    }
}
