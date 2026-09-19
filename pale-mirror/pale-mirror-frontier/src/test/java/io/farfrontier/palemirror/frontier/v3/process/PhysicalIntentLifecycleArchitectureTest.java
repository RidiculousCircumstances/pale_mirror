package io.farfrontier.palemirror.frontier.v3.process;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** ARC-001B recurrence fence for the family-agnostic ingress boundary. */
class PhysicalIntentLifecycleArchitectureTest {
    @Test
    void commonIngressDoesNotDispatchByPhysicalKindOrConcreteFamily() throws IOException {
        Path root = repositoryRoot().resolve("pale-mirror-frontier/src");
        Path process = root.resolve("main/java/io/farfrontier/palemirror/frontier/v3/process");
        String ingress = Files.readString(process.resolve("FrontierPhysicalProcessModule.java"));
        String composition = Files.readString(process.resolve("PhysicalIntentLifecycleCapabilities.java"));

        assertFalse(ingress.contains("PhysicalIntentKind"), "common physical ingress must resolve the explicit owner, never a kind");
        assertFalse(ingress.contains("SceneStrikeStateSupport") || ingress.contains("RouteConstructionStateSupport")
                        || ingress.contains("ResourceSiteHarvestProcess.reducePrepared"),
                "common physical ingress must not call physical-family state support or reducer implementations");
        assertFalse(Files.exists(process.resolve("FrontierPhysicalIntentCommandProcess.java")),
                "the former common kind-dispatch command process must not return");
        assertFalse(composition.contains("switch (intent.kind") || composition.contains("switch (owner"),
                "composition validates declared compatibility but must not choose behavior by kind or owner switch");
        assertTrue(composition.contains("intent.lifecycleOwner()") && composition.contains("missing physical lifecycle capability"),
                "composition must resolve only the durable explicit owner and fail closed");
        assertTrue(composition.contains("declaration().admits(intent)") && composition.contains("requirePreparationCapacity"),
                "owner/kind/schema admission and visible bounded backpressure must come from the closed capability declaration");
        String declaration = Files.readString(process.resolve("PhysicalIntentLifecycleDeclaration.java"));
        assertTrue(declaration.contains("canonicalMaterial")
                        && Files.readString(process.resolve("FrontierWorldProcessCatalog.java")).contains("physicalLifecycleFingerprint"),
                "recovery must fence the exact installed lifecycle declaration rather than infer a compatible capability");
        assertFalse(declaration.contains("PhysicalIntentRoleSchema.values") || declaration.contains("expectedSchemas")
                        || composition.contains("expectedSchemas") || composition.contains("::supports")
                        || Files.readString(repositoryRoot().resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/api/PhysicalIntentLifecycleOwner.java")).contains(" supports("),
                "composition may validate submitted tuples and global completeness, never discover schemas or compatible kinds");
        assertTrue(Files.readString(process.resolve("FunctionalPhysicalIntentLifecycleCapability.java"))
                        .contains("FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleDeclaration declaration")
                        && !Files.readString(process.resolve("AbstractPhysicalIntentLifecycleCapability.java"))
                        .contains("PhysicalIntentLifecycleDeclaration.declared"),
                "family construction must require one complete declaration instead of an owner-and-kind bridge");
        try (Stream<Path> paths = Files.walk(process)) {
            assertFalse(paths.filter(path -> path.toString().endsWith(".java"))
                            .map(PhysicalIntentLifecycleArchitectureTest::readUnchecked)
                            .anyMatch(source -> source.contains("new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleOwner")
                                    || source.contains("new FunctionalPhysicalIntentLifecycleCapability(\n                PhysicalIntentLifecycleOwner")),
                    "every executable family must pass a complete declaration at its first lifecycle-capability boundary");
        }
        assertTrue(composition.contains("retirementPolicy().plan") && composition.contains("retirementPolicy().reduce"),
                "terminal and late input must execute the owner-supplied retirement boundary");
        assertTrue(Files.readString(process.resolve("PhysicalIntentLifecycleCapability.java")).contains("retirementPolicy()"),
                "every lifecycle capability must declare an executable retirement policy");
        String retention = Files.readString(process.resolve("PhysicalIntentResolvedRetentionPolicy.java"));
        assertTrue(retention.contains("FencedRecoveryPhysicalIntentSupport.bindingId(intent)")
                        && retention.contains("PhysicalIntentStatus.CONFIRMED"),
                "resolved-history disposition must preserve every live recovery authority and only select settled receipts");
        assertTrue(composition.contains("compactResolvedForPreparation")
                        && composition.contains("owner retention share is saturated")
                        && composition.contains("must partition the aggregate unresolved admission bound"),
                "same owner declarations must drive executable compaction and fair bounded admission, not diagnostic-only pressure");
        String aggregate = Files.readString(root.resolve("main/java/io/farfrontier/palemirror/frontier/v3/model/FrontierWorldState.java"));
        int compactionStart = aggregate.indexOf("compactResolvedPhysicalIntents");
        int compactionEnd = aggregate.indexOf("FrontierWorldState transitionPhysicalIntent", compactionStart);
        String compaction = aggregate.substring(compactionStart, compactionEnd);
        assertTrue(compaction.contains("nextObservations.remove(observationId)") && compaction.contains("PhysicalIntentStatus.CONFIRMED"),
                "terminal compaction must atomically retire only the exact confirmed intent/receipt pair");
        assertFalse(compaction.contains("PhysicalIntentKind") || compaction.contains("lifecycleOwner()"),
                "aggregate retention may validate owner-selected identities but must never discover a family to compact");
        try (Stream<Path> paths = Files.walk(root.resolve("main/java"))) {
            assertFalse(paths.filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> !path.getFileName().toString().equals("FrontierWorldState.java"))
                            .filter(path -> !path.getFileName().toString().equals("PhysicalIntentLifecycleCapabilities.java"))
                            .map(PhysicalIntentLifecycleArchitectureTest::readUnchecked)
                            .anyMatch(source -> source.contains(".compactResolvedPhysicalIntents(")),
                    "only the closed lifecycle composition may invoke aggregate terminal-history storage");
        }
        String fence = Files.readString(repositoryRoot().resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/model/FencedRecoveryPhysicalIntentSupport.java"));
        assertFalse(fence.contains("intent.kind()") || fence.contains("switch (intent"),
                "common recovery fencing must consume a family-supplied typed asset, never infer it from an intent kind");
        assertTrue(Files.readString(process.resolve("PhysicalIntentLifecycleCapability.java")).contains("recoveryAsset(PhysicalIntent intent)"),
                "every executable owner capability must declare its typed recovery fence asset");
    }

    @Test
    void aggregateStorageCannotRegainTerminalFamilyDispatchOrBecomeAProductionBypass() throws IOException {
        Path root = repositoryRoot().resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3");
        String aggregate = Files.readString(root.resolve("model/FrontierWorldState.java"));
        int start = aggregate.indexOf("FrontierWorldState transitionPhysicalIntent(");
        int end = aggregate.indexOf("public FrontierWorldState prepareSceneLease", start);
        String seam = aggregate.substring(start, end);
        assertTrue(seam.contains("PhysicalIntentTransitionStorage.reduce"),
                "aggregate transition seam must be family-neutral typed storage");
        assertFalse(seam.contains("PhysicalIntentKind") || seam.contains("instanceof") || seam.contains("StateSupport."),
                "aggregate terminal storage must not select a kind, observation subtype, or family state support");
        try (Stream<Path> paths = Files.walk(root.resolve("process"))) {
            assertFalse(paths.filter(path -> path.toString().endsWith(".java"))
                            .map(PhysicalIntentLifecycleArchitectureTest::readUnchecked)
                            .anyMatch(source -> source.contains(".transitionPhysicalIntent(")),
                    "production family modules must not bypass composed lifecycle storage through aggregate transition");
        }
    }

    private static String readUnchecked(Path path) {
        try { return Files.readString(path); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("pale-mirror-frontier"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate Pale Mirror source root");
    }
}
