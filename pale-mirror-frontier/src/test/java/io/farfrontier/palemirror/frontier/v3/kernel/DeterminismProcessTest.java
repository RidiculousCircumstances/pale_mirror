package io.farfrontier.palemirror.frontier.v3.kernel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterminismProcessTest {
    @Test
    void independentJvmRunsProduceByteIdenticalTranscript() throws IOException, InterruptedException {
        assertEquals(runFixture(), runFixture());
    }

    private static String runFixture() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java", "-cp", System.getProperty("java.class.path"),
                DeterminismFixture.class.getName()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
