package io.farfrontier.palemirror.internal.frontier.v3;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ARC-001A recurrence fence: durable lifecycle authority is supplied at every production
 * construction/recovery boundary, never derived from an intent kind or a compatibility default.
 */
class PhysicalIntentExplicitIdentityArchitectureTest {
    @Test
    void productionPhysicalIntentSeamHasNoOwnerlessOrInferredAuthority() throws IOException {
        Path root = repositoryRoot();
        List<Path> files;
        try (Stream<Path> paths = Stream.concat(
                Files.walk(root.resolve("pale-mirror-frontier/src/main/java")),
                Files.walk(root.resolve("pale-mirror-neoforge/src/main/java")))) {
            files = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        String allProduction = files.stream().map(this::read).reduce("", String::concat);
        assertFalse(allProduction.contains("fixtureDefault"), "production must not infer a lifecycle owner from PhysicalIntentKind");
        assertFalse(allProduction.contains("fixtureOnly"), "typed role bindings must not retain a fixture or ownerless construction bridge");
        assertFalse(allProduction.contains("projectOrAssault"), "one nominal role must not be context-polymorphic");
        assertFalse(allProduction.contains("PhysicalIntentRoleBinding.exactConsumption("), "exact consumption producers must state their nominal owner role");
        assertFalse(allProduction.contains("PhysicalIntentRoleBinding.sceneStrike("), "scene-strike producers must state route or assault identity");
        assertFalse(allProduction.contains("PhysicalIntentRoleBinding.equipmentIssue("), "equipment producers must state engineering or assault identity");
        assertFalse(allProduction.contains("PhysicalIntentRoleBinding.equipmentReturn("), "equipment producers must state engineering or assault identity");

        for (Path file : files) {
            String source = read(file);
            int occurrence = source.indexOf("new PhysicalIntent(");
            while (occurrence >= 0) {
                int end = Math.min(source.length(), occurrence + 2_000);
                String construction = source.substring(occurrence, end);
                assertTrue(construction.contains("PhysicalIntentLifecycleOwner") || construction.contains("lifecycleOwner"),
                        () -> "ownerless physical-intent construction in " + file);
                occurrence = source.indexOf("new PhysicalIntent(", occurrence + 1);
            }
        }

        String payloadCodec = read(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/persistence/PhysicalIntentPayloadCodec.java"));
        String snapshotCodec = read(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/persistence/PhysicalIntentStateCodec.java"));
        assertTrue(payloadCodec.contains("PhysicalIntentLifecycleOwner.fromWire") && snapshotCodec.contains("PhysicalIntentLifecycleOwner.fromWire"),
                "WAL and snapshot recovery must decode the explicit stable owner identity, not derive one from intent kind");
        assertTrue(payloadCodec.contains("PhysicalIntentRoleSchema.fromWire") && snapshotCodec.contains("PhysicalIntentRoleSchema.fromWire"),
                "WAL and snapshot recovery must retain the exact closed role schema");

        String sceneExecutor = read(root.resolve("pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3SceneExecutor.java"));
        assertTrue(sceneExecutor.contains("PhysicalIntentLifecycleOwner strikeOwner, PhysicalIntentLifecycleOwner explosionOwner")
                        && sceneExecutor.contains("executeExplosion(level, runtime, state, lease, explosionOwner)")
                        && sceneExecutor.contains("executeStrike(level, runtime, state, lease, strikeOwner)"),
                "scene dispatch must receive family-supplied owners rather than derive authority from its intent kind or scene state");
    }

    @Test
    void productionCannotRecoverSemanticRolesFromTheCompatibilityProjection() throws IOException {
        Path root = repositoryRoot();
        List<Path> files;
        try (Stream<Path> paths = Stream.concat(
                Files.walk(root.resolve("pale-mirror-frontier/src/main/java")),
                Files.walk(root.resolve("pale-mirror-neoforge/src/main/java")))) {
            files = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        for (Path file : files) {
            String source = read(file);
            String name = file.getFileName().toString();
            if (name.equals("PhysicalIntent.java") || name.equals("PhysicalIntentRoleBinding.java")) continue;
            if (name.equals("FrontierV3DiagnosticJson.java")) {
                assertTrue(source.contains("Presentation-only deterministic compatibility projection"),
                        "the sole derived role projection must remain explicitly presentation-only");
                continue;
            }
            assertFalse(source.contains("subjectIds("), () -> "production role extraction must use named bindings in " + file);
        }
    }

    @Test
    void productionCannotReadRetiredContextPolymorphicRoleNames() throws IOException {
        Path root = repositoryRoot();
        try (Stream<Path> paths = Stream.concat(Files.walk(root.resolve("pale-mirror-frontier/src/main/java")),
                Files.walk(root.resolve("pale-mirror-neoforge/src/main/java")))) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("PhysicalIntentSubjectRole.java")) continue;
                String source = read(file);
                assertFalse(source.contains("PhysicalIntentSubjectRole.PROJECT"), () -> "contextual PROJECT role in " + file);
                assertFalse(source.contains("PhysicalIntentSubjectRole.OPERATION"), () -> "contextual OPERATION role in " + file);
                assertFalse(source.contains("PhysicalIntentSubjectRole.DEFENDER"), () -> "contextual DEFENDER role in " + file);
                assertFalse(source.contains("PhysicalIntentSubjectRole.JOB"), () -> "contextual JOB role in " + file);
                assertFalse(source.contains("PhysicalIntentSubjectRole.OWNER"), () -> "contextual OWNER role in " + file);
            }
        }
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("pale-mirror-frontier")) && Files.isDirectory(current.resolve("pale-mirror-neoforge"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate Pale Mirror source root for explicit-identity architecture guard");
    }

    private String read(Path path) {
        try { return Files.readString(path); }
        catch (IOException error) { throw new IllegalStateException("cannot read " + path, error); }
    }
}
