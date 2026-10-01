package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Prevents another scene/ambient caller from restoring the retired direct locomotion path. */
class SingleHotNavigationArchitectureTest {
    @Test
    void productionCallersCannotUseTheLegacyTargetActuator() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("pale-mirror-neoforge"))) root = root.getParent();
        assertNotNull(root);
        Path source = root.resolve("pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3");
        Pattern legacy = Pattern.compile("FrontierV3ControlledMobMotion\\.(?:moveToward|moveWithinWorldBounds|moveWithinEnvelope|moveWithinSemanticEnvelope|pursueRetainedCheckpoint|pursueRetainedSemanticCheckpoint|followContinuously)\\s*\\(");
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().contains("GameTests")) continue;
                String content = Files.readString(file);
                assertFalse(legacy.matcher(content).find(), () -> "parallel locomotion owner in " + file);
                if (!file.getFileName().toString().equals("FrontierV3ControlledMobMotion.java")
                        && !file.getFileName().toString().equals("FrontierV3MinecraftGoalNavigation.java")) {
                    assertFalse(content.contains("getNavigation().stop()"), () -> "partial path cancellation bypasses the owner in " + file);
                }
            }
        }
    }
}
