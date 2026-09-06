package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3LifecycleFilePublisherTest {
    @Test
    void publishesOnlyOneCompleteImmutableAcknowledgement() throws Exception {
        Path root = Files.createTempDirectory("frontier-v3-lifecycle-publisher-");
        Path staging = Files.createDirectory(root.resolve("staging"));
        Path signals = Files.createDirectory(root.resolve("signals"));
        Path target = signals.resolve("client_connected_fixture_ready-single.json");

        FrontierV3LifecycleFilePublisher.publish(staging, target, "{\"schema\":1}\n");

        assertEquals("{\"schema\":1}\n", Files.readString(target));
        try (var pending = Files.list(staging)) {
            assertEquals(0L, pending.count());
        }
        assertThrows(IOException.class, () -> FrontierV3LifecycleFilePublisher.publish(staging, target, "{\"schema\":2}\n"));
        assertEquals("{\"schema\":1}\n", Files.readString(target));
    }
}
