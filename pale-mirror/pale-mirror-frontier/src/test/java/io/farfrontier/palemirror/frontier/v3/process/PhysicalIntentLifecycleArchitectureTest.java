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
        Path process = repositoryRoot().resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/process");
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
        assertTrue(composition.contains("retirementPolicy().plan") && composition.contains("retirementPolicy().reduce"),
                "terminal and late input must execute the owner-supplied retirement boundary");
        assertTrue(Files.readString(process.resolve("PhysicalIntentLifecycleCapability.java")).contains("retirementPolicy()"),
                "every lifecycle capability must declare an executable retirement policy");
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
