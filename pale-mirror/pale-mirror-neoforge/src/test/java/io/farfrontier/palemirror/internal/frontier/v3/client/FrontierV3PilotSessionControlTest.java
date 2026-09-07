package io.farfrontier.palemirror.internal.frontier.v3.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PilotSessionControlTest {
    private static final String CONTROL = "pale_mirror.frontier_v3.test_pilot.session_control_directory";
    private static final String MODE = "pale_mirror.frontier_v3.test_pilot.session_mode";

    @Test
    void finalMatrixCloseRequiresTheExactSupervisorToken(@TempDir Path root) throws Exception {
        Path control = Files.createDirectory(root.resolve("control"));
        Files.createDirectory(control.resolve("segments"));
        Files.createDirectory(control.resolve("close"));
        String runId = UUID.randomUUID().toString();
        Files.writeString(control.resolve("run-id"), runId + "\n", StandardCharsets.UTF_8);
        Path scenario = root.resolve("scenario.json");
        Files.writeString(scenario, "{\"id\":\"final_scenario\"}\n", StandardCharsets.UTF_8);
        Files.writeString(control.resolve("segments").resolve("0000.json"), """
                {"schema":1,"kind":"frontier-v3-pilot-matrix-segment","runId":"%s","epoch":0,
                "segment":"final_segment","scenarioId":"final_scenario","final":true,"scenarioSha256":"%s"}
                """.formatted(runId, sha256(Files.readAllBytes(scenario))), StandardCharsets.UTF_8);

        String previousControl = System.getProperty(CONTROL);
        String previousMode = System.getProperty(MODE);
        try {
            System.setProperty(CONTROL, control.toString());
            System.setProperty(MODE, "matrix");
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(scenario);
            assertTrue(FrontierV3PilotSessionControl.shouldAwaitFinalClose());
            FrontierV3PilotSessionControl.markAwaitingFinalClose();
            assertFalse(FrontierV3PilotSessionControl.requestFinalClose());

            Path token = control.resolve("close").resolve("0000.token");
            Files.writeString(token, "foreign\n", StandardCharsets.UTF_8);
            assertThrows(IllegalArgumentException.class, FrontierV3PilotSessionControl::requestFinalClose);
            Files.writeString(token, runId + ":0\n", StandardCharsets.UTF_8);
            assertTrue(FrontierV3PilotSessionControl.requestFinalClose());
            assertTrue(FrontierV3PilotSessionControl.finalCloseRequested());
        } finally {
            FrontierV3PilotSessionControl.reset();
            restore(CONTROL, previousControl);
            restore(MODE, previousMode);
        }
    }

    @Test
    void acknowledgesOneSemanticNormalDisconnectDespiteDuplicateMinecraftLogoutCallbacks(@TempDir Path root) throws Exception {
        Path control = Files.createDirectory(root.resolve("control"));
        Files.createDirectory(control.resolve("segments"));
        Files.createDirectory(control.resolve("close"));
        String runId = UUID.randomUUID().toString();
        Files.writeString(control.resolve("run-id"), runId + "\n", StandardCharsets.UTF_8);
        Path scenario = root.resolve("scenario.json");
        Files.writeString(scenario, "{\"id\":\"final_scenario\"}\n", StandardCharsets.UTF_8);
        Files.writeString(control.resolve("segments").resolve("0000.json"), """
                {"schema":1,"kind":"frontier-v3-pilot-matrix-segment","runId":"%s","epoch":0,
                "segment":"final_segment","scenarioId":"final_scenario","final":true,"scenarioSha256":"%s"}
                """.formatted(runId, sha256(Files.readAllBytes(scenario))), StandardCharsets.UTF_8);
        Path lifecycle = Files.createDirectory(root.resolve("lifecycle"));
        Files.createDirectory(lifecycle.resolve("staging"));
        Path signals = Files.createDirectory(lifecycle.resolve("signals"));
        Files.writeString(lifecycle.resolve("identity.json"), "{\"schema\":1}\n", StandardCharsets.UTF_8);

        String previousControl = System.getProperty(CONTROL);
        String previousMode = System.getProperty(MODE);
        String lifecycleProperty = "pale_mirror.frontier_v3.test_pilot.lifecycle_control_directory";
        String previousLifecycle = System.getProperty(lifecycleProperty);
        try {
            System.setProperty(CONTROL, control.toString());
            System.setProperty(MODE, "matrix");
            System.setProperty(lifecycleProperty, lifecycle.toString());
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(scenario);
            FrontierV3PilotSessionControl.markAwaitingFinalClose();
            Files.writeString(control.resolve("close").resolve("0000.token"), runId + ":0\n", StandardCharsets.UTF_8);
            assertTrue(FrontierV3PilotSessionControl.requestFinalClose());

            assertTrue(FrontierV3PilotSessionControl.publishNormalDisconnectAcknowledgement());
            assertFalse(FrontierV3PilotSessionControl.publishNormalDisconnectAcknowledgement());
            Path acknowledgement = signals.resolve("client_normally_disconnected-final_segment.json");
            assertTrue(Files.isRegularFile(acknowledgement));
            try (var entries = Files.list(signals)) {
                assertEquals(1L, entries.count());
            }
        } finally {
            FrontierV3PilotSessionControl.reset();
            restore(CONTROL, previousControl);
            restore(MODE, previousMode);
            restore(lifecycleProperty, previousLifecycle);
        }
    }

    @Test
    void matrixDiagnosticStampBindsCurrentRunSessionEpochSegmentAndActionStep(@TempDir Path root) throws Exception {
        Path control = Files.createDirectory(root.resolve("control"));
        Files.createDirectory(control.resolve("segments"));
        String runId = UUID.randomUUID().toString(); String sessionId = UUID.randomUUID().toString();
        Files.writeString(control.resolve("run-id"), runId + "\n", StandardCharsets.UTF_8);
        Path scenario = root.resolve("scenario.json"); Files.writeString(scenario, "{\"id\":\"scenario\"}\n", StandardCharsets.UTF_8);
        Files.writeString(control.resolve("segments").resolve("0000.json"), """
                {"schema":1,"kind":"frontier-v3-pilot-matrix-segment","runId":"%s","epoch":0,
                "segment":"segment_0","scenarioId":"scenario","final":false,"scenarioSha256":"%s"}
                """.formatted(runId, sha256(Files.readAllBytes(scenario))), StandardCharsets.UTF_8);
        Path lifecycle = Files.createDirectory(root.resolve("lifecycle"));
        Files.writeString(lifecycle.resolve("identity.json"), """
                {"schema":1,"runId":"%s","sessionId":"%s"}
                """.formatted(runId, sessionId), StandardCharsets.UTF_8);
        String priorControl = System.getProperty(CONTROL); String priorMode = System.getProperty(MODE);
        String lifecycleProperty = "pale_mirror.frontier_v3.test_pilot.lifecycle_control_directory";
        String priorLifecycle = System.getProperty(lifecycleProperty);
        try {
            System.setProperty(CONTROL, control.toString()); System.setProperty(MODE, "matrix"); System.setProperty(lifecycleProperty, lifecycle.toString());
            FrontierV3PilotSessionControl.reset(); FrontierV3PilotSessionControl.onLogin(scenario);
            var value = new com.google.gson.JsonObject(); FrontierV3PilotSessionControl.stampDiagnostic(value, 3);
            assertEquals(runId, value.get("pilotRunId").getAsString()); assertEquals(sessionId, value.get("pilotSessionId").getAsString());
            assertEquals(0, value.get("pilotEpoch").getAsInt()); assertEquals("segment_0", value.get("pilotSegment").getAsString());
            assertEquals(3, value.get("pilotActionStep").getAsInt());
        } finally {
            FrontierV3PilotSessionControl.reset(); restore(CONTROL, priorControl); restore(MODE, priorMode); restore(lifecycleProperty, priorLifecycle);
        }
    }

    @Test
    void ordinaryRestartReconnectRetainsExactlyOnePredecessorThroughConnectScreenCleanup(@TempDir Path root) throws Exception {
        Path control = Files.createDirectory(root.resolve("control"));
        String runId = UUID.randomUUID().toString();
        Files.writeString(control.resolve("run-id"), runId + "\n", StandardCharsets.UTF_8);
        Path scenario = root.resolve("scenario.json");
        Files.writeString(scenario, "{\"id\":\"restart_scenario\"}\n", StandardCharsets.UTF_8);
        String previousControl = System.getProperty(CONTROL);
        String previousMode = System.getProperty(MODE);
        try {
            System.setProperty(CONTROL, control.toString());
            System.clearProperty(MODE);
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(scenario);
            Object predecessor = new Object();
            FrontierV3PilotSessionControl.bindActiveConnection(predecessor);
            FrontierV3PilotSessionControl.markAwaitingResume();
            Files.writeString(control.resolve("resume"), runId + "\n", StandardCharsets.UTF_8);

            FrontierV3PilotSessionControl.requestReconnect("127.0.0.1:25575");
            assertTrue(FrontierV3PilotSessionControl.reconnectInFlight());
            FrontierV3PilotSessionControl.executeReconnectTransportDeparture(() ->
                    assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(predecessor),
                            "the ordinary restart cleanup must not reset the session before its replacement login"));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null),
                    "the null callback exception is limited to ConnectScreen's synchronous cleanup");
        } finally {
            FrontierV3PilotSessionControl.reset();
            restore(CONTROL, previousControl);
            restore(MODE, previousMode);
        }
    }

    private static void restore(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder rendered = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            rendered.append(String.format("%02x", value));
        }
        return rendered.toString();
    }
}
