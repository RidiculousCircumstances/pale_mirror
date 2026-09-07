package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.farfrontier.palemirror.PaleMirrorMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pilot-source-only acknowledgement bridge for the external F0.VA supervisor.
 *
 * It never reads or mutates canonical state.  The Node supervisor validates
 * the copied immutable identity and decides the barrier-journal transition.
 */
final class FrontierV3PilotLifecycleSignal {
    static final String CONTROL_DIRECTORY_PROPERTY = "pale_mirror.frontier_v3.pilot.lifecycle_control_directory";
    static final String RUN_ID_PROPERTY = "pale_mirror.frontier_v3.pilot.run_id";

    private FrontierV3PilotLifecycleSignal() { }

    static void serverRunReady() {
        publish("server_run_ready");
    }

    static void durableServerSave() {
        publish("durable_server_save");
    }

    static void normalDemandLossRelease() {
        publish("normal_demand_loss_release");
    }

    /**
     * The supervisor creates this token only after it has authenticated the visible client's
     * own normal-disconnect receipt.  This server-side read is test-pilot correlation only; it
     * cannot issue a command or alter the canonical world.
     */
    static boolean normalDemandLossAuthorized() {
        String configured = System.getProperty(CONTROL_DIRECTORY_PROPERTY, "");
        String serverRunId = System.getProperty(RUN_ID_PROPERTY, "");
        if (configured.isBlank() || !serverRunId.matches("[0-9a-f-]{36}")) return false;
        try {
            Path directory = Path.of(configured);
            JsonObject identity = JsonParser.parseString(Files.readString(directory.resolve("identity.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            if (identity.get("schema") == null || identity.get("schema").getAsInt() != 1 || identity.get("runId") == null) return false;
            Path token = directory.resolve("demand-loss-" + serverRunId + ".token");
            return Files.isRegularFile(token) && (identity.get("runId").getAsString() + ":" + serverRunId + "\n")
                    .equals(Files.readString(token, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("pilot normal-demand-loss receipt is invalid", failure);
        }
    }

    private static void publish(String kind) {
        String configured = System.getProperty(CONTROL_DIRECTORY_PROPERTY, "");
        if (configured.isBlank()) return;
        try {
            Path directory = Path.of(configured);
            String runId = System.getProperty(RUN_ID_PROPERTY, "");
            if (!runId.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("pilot lifecycle run id is invalid");
            JsonElement identity = JsonParser.parseString(Files.readString(directory.resolve("identity.json"), StandardCharsets.UTF_8));
            if (!identity.isJsonObject() || identity.getAsJsonObject().get("schema") == null
                    || identity.getAsJsonObject().get("schema").getAsInt() != 1) {
                throw new IllegalArgumentException("pilot lifecycle identity is invalid");
            }
            JsonObject detail = new JsonObject(); detail.addProperty("serverRunId", runId); detail.addProperty("serverPid", ProcessHandle.current().pid());
            JsonObject signal = new JsonObject(); signal.addProperty("schema", 1); signal.addProperty("signal", kind);
            signal.addProperty("suffix", runId); signal.add("identity", identity); signal.add("detail", detail);
            Path signals = directory.resolve("signals");
            if (!Files.isDirectory(signals)) throw new IOException("pilot lifecycle signal directory is unavailable");
            FrontierV3LifecycleFilePublisher.publish(directory.resolve("staging"),
                    signals.resolve(kind + "-" + runId + ".json"), signal + "\n");
            PaleMirrorMod.LOGGER.info("PMV3_PILOT lifecycle signal={} runId={}", kind, runId);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("pilot server could not publish its exact lifecycle readiness", failure);
        }
    }
}
