package io.farfrontier.palemirror.frontier.v3.process;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
