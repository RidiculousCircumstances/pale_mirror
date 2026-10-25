package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ForestryShutdownTest {
    @Test
    void forestryShutdownAfterQuarantineRetainsOwnershipWithoutNewCommands(@TempDir Path directory) {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:quarantined-forestry-stop"), 91L);
        var runtime = FrontierV3ServerRuntime.start(configuration,
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 20);
        var retained = runtime.decodedState().orElseThrow();
        runtime.quarantine(new IllegalArgumentException("test:primary-service-failure"));
        var status = runtime.status();
        assertDoesNotThrow(() -> FrontierV3ForestryExecutor.shutdown(null, runtime));
        assertSame(status, runtime.status());
        assertSame(retained, runtime.passiveOwnershipState().orElseThrow());
        assertTrue(runtime.decodedState().isEmpty());
        runtime.shutdown();
    }

}
