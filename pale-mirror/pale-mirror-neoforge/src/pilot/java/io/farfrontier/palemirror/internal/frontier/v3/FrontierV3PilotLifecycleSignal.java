package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pilot-source-only acknowledgement bridge for the external F0.VA supervisor.
 *
 * It never reads or mutates canonical state.  The Node supervisor validates
 * the copied immutable identity and decides the barrier-journal transition.
 */
final class FrontierV3PilotLifecycleSignal {
    static final String CONTROL_DIRECTORY_PROPERTY = "pale_mirror.frontier_v3.pilot.lifecycle_control_directory";
    static final String RUN_ID_PROPERTY = "pale_mirror.frontier_v3.pilot.run_id";
    /** Enables only the disposable F0.2B recovery evidence carrier. */
    static final String RECOVERY_CHECKPOINT_PROPERTY = "pale_mirror.frontier_v3.pilot.recovery_checkpoint";

    private FrontierV3PilotLifecycleSignal() { }

    static void serverRunReady(MinecraftServer server) {
        publish("server_run_ready", System.getProperty(RUN_ID_PROPERTY, ""), recoveryCheckpoint(server, "recovery_boot"));
    }

    static void durableServerSave(MinecraftServer server) {
        // This method is called only from the MinecraftServer.stopServer tail:
        // the normal graceful save has completed, but the pilot has not
        // restarted or reintroduced a client.  The snapshot is therefore the
        // actual durable boundary, not an earlier action observation.
        publish("durable_server_save", System.getProperty(RUN_ID_PROPERTY, ""), recoveryCheckpoint(server, "durable_stop"));
    }

    static void normalDemandLossRelease() {
        publish("normal_demand_loss_release");
    }

    static void normalDemandLossRelease(String suffix) {
        publish("normal_demand_loss_release", suffix);
    }

    /**
     * The supervisor creates this token only after it has authenticated the visible client's
     * own normal-disconnect receipt.  This server-side read is test-pilot correlation only; it
     * cannot issue a command or alter the canonical world.
     */
    static Optional<String> normalDemandLossAuthorized(Set<String> published) {
        Objects.requireNonNull(published, "published");
        String configured = System.getProperty(CONTROL_DIRECTORY_PROPERTY, "");
        String serverRunId = System.getProperty(RUN_ID_PROPERTY, "");
        if (configured.isBlank() || !serverRunId.matches("[0-9a-f-]{36}")) return Optional.empty();
        try {
            Path directory = Path.of(configured);
            JsonObject identity = JsonParser.parseString(Files.readString(directory.resolve("identity.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            if (identity.get("schema") == null || identity.get("schema").getAsInt() != 1 || identity.get("runId") == null) return Optional.empty();
            String runId = identity.get("runId").getAsString();
            Pattern request = Pattern.compile("demand-loss-" + Pattern.quote(serverRunId) + "-([0-9]{4})\\.token");
            try (var entries = Files.list(directory)) {
                for (Path candidate : entries.sorted().toList()) {
                    Matcher match = request.matcher(candidate.getFileName().toString());
                    if (!match.matches()) continue;
                    String suffix = serverRunId + "-" + match.group(1);
                    if (!published.contains(suffix) && Files.isRegularFile(candidate)
                            && (runId + ":" + match.group(1) + "\n").equals(Files.readString(candidate, StandardCharsets.UTF_8))) {
                        return Optional.of(suffix);
                    }
                }
            }
            // Retain the isolated-runner protocol while the persistent matrix uses the
            // append-only per-boundary requests above.
            Path token = directory.resolve("demand-loss-" + serverRunId + ".token");
            return !published.contains(serverRunId) && Files.isRegularFile(token) && (runId + ":" + serverRunId + "\n")
                    .equals(Files.readString(token, StandardCharsets.UTF_8)) ? Optional.of(serverRunId) : Optional.empty();
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("pilot normal-demand-loss receipt is invalid", failure);
        }
    }

    private static void publish(String kind) {
        publish(kind, System.getProperty(RUN_ID_PROPERTY, ""), null);
    }

    private static void publish(String kind, String suffix) {
        publish(kind, suffix, null);
    }

    private static void publish(String kind, String suffix, JsonObject recoveryCheckpoint) {
        String configured = System.getProperty(CONTROL_DIRECTORY_PROPERTY, "");
        if (configured.isBlank()) return;
        try {
            Path directory = Path.of(configured);
            String runId = System.getProperty(RUN_ID_PROPERTY, "");
            if (!runId.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("pilot lifecycle run id is invalid");
            if (!suffix.matches("[A-Za-z0-9_.:-]{1,127}")) throw new IllegalArgumentException("pilot lifecycle signal suffix is invalid");
            JsonElement identity = JsonParser.parseString(Files.readString(directory.resolve("identity.json"), StandardCharsets.UTF_8));
            if (!identity.isJsonObject() || identity.getAsJsonObject().get("schema") == null
                    || identity.getAsJsonObject().get("schema").getAsInt() != 1) {
                throw new IllegalArgumentException("pilot lifecycle identity is invalid");
            }
            JsonObject detail = new JsonObject(); detail.addProperty("serverRunId", runId); detail.addProperty("serverPid", ProcessHandle.current().pid());
            if (recoveryCheckpoint != null) {
                detail.addProperty("recoveryCheckpointPhase", recoveryCheckpoint.get("phase").getAsString());
                detail.addProperty("recoveryCheckpoint", recoveryCheckpoint.toString());
            }
            JsonObject signal = new JsonObject(); signal.addProperty("schema", 1); signal.addProperty("signal", kind);
            signal.addProperty("suffix", suffix); signal.add("identity", identity); signal.add("detail", detail);
            Path signals = directory.resolve("signals");
            if (!Files.isDirectory(signals)) throw new IOException("pilot lifecycle signal directory is unavailable");
            FrontierV3LifecycleFilePublisher.publish(directory.resolve("staging"),
                    signals.resolve(kind + "-" + suffix + ".json"), signal + "\n");
            PaleMirrorMod.LOGGER.info("PMV3_PILOT lifecycle signal={} runId={} suffix={}", kind, runId, suffix);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("pilot server could not publish its exact lifecycle readiness", failure);
        }
    }

    /**
     * Captures the two RC-6 projections through the production read API.  The
     * pilot merely serializes the immutable read into its already identity-bound
     * lifecycle signal; it has no scheduling, persistence, ticket, custody, or
     * shutdown authority.
     */
    private static JsonObject recoveryCheckpoint(MinecraftServer server, String phase) {
        if (!Boolean.getBoolean(RECOVERY_CHECKPOINT_PROPERTY)) return null;
        try {
            JsonObject checkpoint = new JsonObject();
            checkpoint.addProperty("schema", 1); checkpoint.addProperty("phase", phase);
            checkpoint.addProperty("serverRunId", System.getProperty(RUN_ID_PROPERTY, ""));
            checkpoint.addProperty("serverPid", ProcessHandle.current().pid());
            checkpoint.add("reference", diagnosticPayload(FrontierV3ServerLifecycle.diagnostic(server, "reference_container", "f02b")));
            checkpoint.add("depot", diagnosticPayload(FrontierV3ServerLifecycle.diagnostic(server, "container", "container:1-depot")));
            return checkpoint;
        } catch (RuntimeException failure) {
            throw new IllegalStateException("pilot recovery checkpoint could not read the exact F0.2B boundary", failure);
        }
    }

    /** Parses the exact production diagnostic envelope used by both checkpoint reads. */
    static JsonObject diagnosticPayload(String diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (!diagnostic.startsWith(FrontierV3DiagnosticJson.PREFIX)) {
            throw new IllegalArgumentException("pilot recovery checkpoint diagnostic prefix is invalid");
        }
        JsonElement payload = JsonParser.parseString(diagnostic.substring(FrontierV3DiagnosticJson.PREFIX.length()));
        if (!payload.isJsonObject()) {
            throw new IllegalArgumentException("pilot recovery checkpoint diagnostic payload is not an object");
        }
        return payload.getAsJsonObject();
    }
}
