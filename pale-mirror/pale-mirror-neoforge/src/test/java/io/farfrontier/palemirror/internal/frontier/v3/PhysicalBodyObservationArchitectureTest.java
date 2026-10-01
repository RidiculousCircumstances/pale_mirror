package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Prevents the fractional-support defect from returning in another pedestrian producer. */
class PhysicalBodyObservationArchitectureTest {
    @Test
    void pedestrianBodyProducersCannotFloorEntityFeetOutsideTheObservationBoundary() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("pale-mirror-neoforge"))) root = root.getParent();
        assertNotNull(root, "module root");
        Path source = root.resolve("pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3");
        Set<String> nonPedestrian = Set.of("FrontierV3BodyObservation.java", "FrontierV3CargoCarrierExecutor.java",
                "FrontierV3CargoDepartureObserver.java");
        Pattern rawBody = Pattern.compile("new\\s+(?:[\\w.]+\\.)?BodyPosition\\s*\\([^;]*?getBlockY\\s*\\(", Pattern.DOTALL);
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (nonPedestrian.contains(file.getFileName().toString()) || file.getFileName().toString().contains("GameTests")) continue;
                assertFalse(rawBody.matcher(Files.readString(file)).find(), () -> "independent pedestrian floor-Y conversion in " + file);
            }
        }
    }
}
