package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3LifecycleFilePublisher;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Development-only control plane for one visible pilot JVM.
 *
 * <p>This class has no canonical or Minecraft-world authority. It only validates the immutable
 * runner-owned session descriptors before the ordinary client reconnects. The runner remains
 * responsible for selecting an exact ready server and for publishing every lifecycle barrier.</p>
 */
final class FrontierV3PilotSessionControl {
    private static final String CONTROL_PROPERTY = "pale_mirror.frontier_v3.test_pilot.session_control_directory";
    private static final String MODE_PROPERTY = "pale_mirror.frontier_v3.test_pilot.session_mode";
    private static final String LIFECYCLE_CONTROL_PROPERTY = "pale_mirror.frontier_v3.test_pilot.lifecycle_control_directory";
    private static final String LIFECYCLE_SEGMENT_PROPERTY = "pale_mirror.frontier_v3.test_pilot.lifecycle_segment";
    private static final String LIFECYCLE_TERMINAL_PROPERTY = "pale_mirror.frontier_v3.test_pilot.lifecycle_terminal";
    private static final int SCHEMA = 1;

    private static boolean awaitingResume;
    private static boolean reconnectInFlight;
    private static boolean awaitingFinalClose;
    private static boolean finalCloseRequested;
    private static int epoch = -1;
    private static String segment = "";
    private static String scenarioId = "";
    private static String scenarioSha256 = "";
    private static boolean finalSegment;
    private static boolean preparedLifecycleSignal;
    private static boolean persistentLifecycleFailure;
    private static boolean expectedCrashSegment;
    private static boolean expectedLossArmed;
    private static boolean expectedLossAcknowledged;
    private static int expectedLossCompletedActionPrefix = -1;
    private static boolean awaitingExpectedLossProbe;
    private static String completion = "";
    private static Object activeConnection;
    private static Object reconnectPredecessorConnection;
    /**
     * A departed server can leave Minecraft without a LocalPlayer before ConnectScreen performs
     * its ordinary disconnect cleanup. A null LoggingOut callback is therefore never identity on
     * its own: it is tolerated only while this exact predecessor is retained by a validated
     * reconnect and the transport is beginning that one synchronous departure.
     */
    private static boolean reconnectTransportDeparturePrepared;
    private static boolean reconnectTransportDepartureStarted;
    /**
     * A Minecraft logout callback is not itself a lifecycle transition: NeoForge may deliver it
     * more than once while one network connection is closing.  Retain the one semantic
     * acknowledgement which this control plane has already claimed so only the first callback
     * can publish the immutable signal.  A different suffix is still a protocol violation.
     */
    private static String normalDisconnectAcknowledgement = "";

    private FrontierV3PilotSessionControl() { }

    static void onLogin(Path scenario) throws IOException {
        if (matrix() && epoch < 0) activate(0);
        if (matrix() && !scenarioSha256.equals(sha256(Files.readAllBytes(scenario)))) {
            throw new IllegalArgumentException("persistent matrix runtime scenario does not match its immutable descriptor");
        }
        if (matrix()) {
            JsonObject source = JsonParser.parseString(Files.readString(scenario, StandardCharsets.UTF_8)).getAsJsonObject();
            if (source.get("id") == null || !scenarioId.equals(source.get("id").getAsString())) {
                throw new IllegalArgumentException("persistent matrix runtime scenario identity does not match its immutable descriptor");
            }
        }
        // The actual client completes this only after it has parsed and installed the next
        // immutable scenario.  Keeping the flag through that work makes a post-login failure
        // visibly fatal instead of turning the persistent pilot into an idle client.
    }

    static boolean enabled() { return controlDirectory() != null || lifecycleDirectory() != null; }
    static boolean resumed() { return matrix() ? epoch > 0 : controlDirectory() != null && Files.isRegularFile(controlDirectory().resolve("resumed")); }
    static boolean awaitingResume() { return awaitingResume; }
    static boolean reconnectInFlight() { return reconnectInFlight; }
    static boolean awaitingFinalClose() { return awaitingFinalClose; }
    static boolean finalCloseRequested() { return finalCloseRequested; }
    static boolean expectedCrashSegment() { return expectedCrashSegment; }
    static boolean expectedLossArmed() { return expectedLossArmed; }
    static boolean mayContinueScenarioActions() { return !expectedLossArmed && !expectedLossAcknowledged && !persistentLifecycleFailure; }
    static boolean expectedLossClaimed() { return expectedLossAcknowledged; }
    /** Actions are exhausted, but the admitted real server probe has not yet published its arm. */
    static boolean awaitingExpectedLossProbe() { return awaitingExpectedLossProbe; }
    static void markAwaitingExpectedLossProbe() {
        if (!matrix() || !expectedCrashSegment || expectedLossArmed || expectedLossAcknowledged) {
            throw new IllegalStateException("persistent expected-loss probe is not pending");
        }
        awaitingExpectedLossProbe = true;
    }
    static boolean persistentLifecycleFailure() { return persistentLifecycleFailure; }

    static void completeReconnect() {
        reconnectPredecessorConnection = null;
        reconnectTransportDeparturePrepared = false;
        reconnectTransportDepartureStarted = false;
        if (!reconnectInFlight) return;
        reconnectInFlight = false;
    }

    static boolean expectedReconnectPredecessorDeparture(Object observedConnection) {
        if (!enabled() || !reconnectInFlight || reconnectPredecessorConnection == null) return false;
        if (observedConnection != null) return observedConnection == reconnectPredecessorConnection;
        return reconnectTransportDeparturePrepared;
    }

    /**
     * Called directly before ConnectScreen.startConnecting.  That Minecraft API synchronously
     * invokes its ordinary disconnect path, but after either an abrupt loss or an ordinary
     * handoff its LoggingOut event can have no LocalPlayer-derived Connection. This bounded
     * transport transition carries the already authenticated predecessor into that otherwise
     * unidentifiable cleanup.
     */
    static void prepareReconnectTransportDeparture() {
        if (!enabled() || !reconnectInFlight || reconnectPredecessorConnection == null
                || activeConnection != reconnectPredecessorConnection || reconnectTransportDepartureStarted) {
            throw new IllegalStateException("persistent pilot reconnect predecessor is unavailable");
        }
        reconnectTransportDepartureStarted = true;
        reconnectTransportDeparturePrepared = true;
    }

    /** Ends the synchronous ConnectScreen cleanup window before asynchronous replacement login. */
    static void finishReconnectTransportDeparture() {
        reconnectTransportDeparturePrepared = false;
    }

    /** Runs the one synchronous Minecraft cleanup call without extending its null-callback scope. */
    static void executeReconnectTransportDeparture(Runnable startConnecting) {
        prepareReconnectTransportDeparture();
        try {
            startConnecting.run();
        } finally {
            finishReconnectTransportDeparture();
        }
    }
    static boolean reconnectFailureRequiresFatal() { return enabled() && matrix() && reconnectInFlight; }
    static boolean reconnectRequestFailureRequiresFatal() { return enabled() && matrix() && (awaitingResume || reconnectInFlight); }
    static boolean unexpectedActiveLossRequiresFatal() {
        return enabled() && matrix() && activeConnection != null && !finalCloseRequested && !awaitingResume && !reconnectInFlight;
    }
    static boolean claimPersistentLifecycleFailure() {
        if (persistentLifecycleFailure) return false;
        persistentLifecycleFailure = true;
        return true;
    }

    /** Claims only the immutable arm for this active crash-capable descriptor. */
    static boolean armExpectedLoss() throws IOException { return armExpectedLoss(0); }
    static boolean armExpectedLoss(int completedActionPrefix) throws IOException {
        if (!matrix() || !expectedCrashSegment || finalSegment || expectedLossArmed) return false;
        if (completedActionPrefix < 0) throw new IllegalArgumentException("persistent expected-loss action prefix is invalid");
        Path arm = requireControlDirectory().resolve("expected-crash").resolve(String.format("%04d.json", epoch));
        if (!Files.isRegularFile(arm)) return false;
        JsonObject descriptor = activeDescriptor(epoch);
        FrontierV3PilotExpectedCrash.validateArm(requireControlDirectory(), requireLifecycleDirectory(), descriptor, epoch);
        expectedLossArmed = true;
        expectedLossCompletedActionPrefix = completedActionPrefix;
        awaitingExpectedLossProbe = false;
        JsonObject detail = new JsonObject(); detail.addProperty("completedActionSteps", expectedLossCompletedActionPrefix);
        publishLifecycleSignal("expected_loss_armed", segment, detail);
        return true;
    }

    /** LoggingOut observes loss; it is never the normal-disconnect acknowledgement. */
    static void bindActiveConnection(Object connection) {
        if (connection == null) throw new IllegalArgumentException("persistent pilot connection is unavailable");
        reconnectPredecessorConnection = null;
        reconnectTransportDeparturePrepared = false;
        reconnectTransportDepartureStarted = false;
        activeConnection = connection;
    }
    static boolean claimExpectedLoss() throws IOException { return claimExpectedLoss(activeConnection); }
    static boolean claimExpectedLoss(Object observedConnection) throws IOException {
        if (activeConnection == null || activeConnection != observedConnection) throw new IllegalStateException("expected loss callback is foreign to active connection");
        if (!expectedLossArmed) return false;
        if (expectedLossAcknowledged) return false;
        expectedLossAcknowledged = true;
        JsonObject detail = new JsonObject(); detail.addProperty("epoch", epoch); detail.addProperty("clientPid", ProcessHandle.current().pid()); detail.addProperty("completedActionSteps", expectedLossCompletedActionPrefix);
        publishLifecycleSignal("client_expected_loss", segment, detail);
        awaitingResume = true;
        return true;
    }
    static String lifecycleSegment() {
        if (matrix()) return requireSegment();
        // The standalone lifecycle protocol owns its explicit immutable segment name.  It is
        // not a legacy one-process restart session, so deriving before_restart here would
        // publish a different signal suffix than the supervisor authenticated at launch.
        if (lifecycleDirectory() != null && controlDirectory() == null) {
            String configured = System.getProperty(LIFECYCLE_SEGMENT_PROPERTY, "single");
            if (!token(configured)) throw new IllegalArgumentException("pilot lifecycle segment is invalid");
            return configured;
        }
        if (controlDirectory() != null) return resumed() ? "after_restart" : "before_restart";
        String configured = System.getProperty(LIFECYCLE_SEGMENT_PROPERTY, "single");
        if (!token(configured)) throw new IllegalArgumentException("pilot lifecycle segment is invalid");
        return configured;
    }
    static String runId() throws IOException { return readRunId(requireControlDirectory()); }

    /** Adds only local runner correlation; these fields are never canonical diagnostic meaning. */
    static void stampDiagnostic(JsonObject value, int actionStep) {
        if (actionStep < 1) return;
        // The historical ordinary-pilot correlation remains available even when the matrix
        // session stamp is not active. Matrix mode replaces this local step with the complete
        // run/session/epoch/segment stamp below.
        value.addProperty("pilotActionStep", actionStep);
        if (!matrix()) return;
        try {
            JsonObject identity = JsonParser.parseString(Files.readString(requireLifecycleDirectory().resolve("identity.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            if (identity.get("runId") == null || identity.get("sessionId") == null
                    || !readRunId(requireControlDirectory()).equals(identity.get("runId").getAsString())) {
                throw new IllegalArgumentException("persistent pilot diagnostic identity is invalid");
            }
            value.addProperty("pilotRunId", identity.get("runId").getAsString());
            value.addProperty("pilotSessionId", identity.get("sessionId").getAsString());
            value.addProperty("pilotEpoch", epoch);
            value.addProperty("pilotSegment", requireSegment());
            value.addProperty("pilotActionStep", actionStep);
        } catch (IOException failure) {
            throw new IllegalStateException("persistent pilot diagnostic identity is unreadable", failure);
        }
    }

    /** Announces the one actual Minecraft JVM once; the Node wrapper is not the client identity. */
    static void publishClientPrepared() throws IOException {
        if (!enabled() || preparedLifecycleSignal) return;
        JsonObject detail = new JsonObject(); detail.addProperty("clientPid", ProcessHandle.current().pid());
        // A restart owns two distinct ordinary client JVMs.  Their immutable prepared
        // acknowledgements must therefore be segmented just like their terminal and
        // disconnect evidence; one generic filename would reject the replacement before
        // it can authenticate its own connection.
        publishLifecycleSignal("prepared_client_ready", lifecycleSegment(), detail);
        preparedLifecycleSignal = true;
    }

    /** A matrix client pauses after every non-final independent world segment. */
    static boolean shouldAwaitNextSegment() {
        Path control = controlDirectory();
        return control != null && (matrix() ? !finalSegment : !Files.isRegularFile(control.resolve("resumed")));
    }

    static void markAwaitingResume() { awaitingResume = true; }

    /** The final matrix segment remains live until the supervisor authenticates its result. */
    static boolean shouldAwaitFinalClose() {
        return matrix() && finalSegment && !awaitingFinalClose && !finalCloseRequested;
    }

    /**
     * A one-shot isolated pilot has no restart-session descriptor, but its terminal result is
     * still not permission for the wrapper to kill the client.  The supervisor first freezes
     * the read-only terminal evidence, then writes this nonce-bound close token so Minecraft
     * itself performs the ordinary disconnect that the server must observe.
     */
    static boolean shouldAwaitLifecycleFinalClose() {
        return !matrix() && controlDirectory() == null && lifecycleDirectory() != null
                && "true".equals(System.getProperty(LIFECYCLE_TERMINAL_PROPERTY, "false"))
                && !awaitingFinalClose && !finalCloseRequested;
    }

    static boolean awaitingLifecycleFinalClose() {
        return !matrix() && controlDirectory() == null && lifecycleDirectory() != null && awaitingFinalClose && !finalCloseRequested;
    }

    static void markAwaitingLifecycleFinalClose() {
        if (!shouldAwaitLifecycleFinalClose()) {
            throw new IllegalStateException("isolated pilot final close is not available");
        }
        awaitingFinalClose = true;
    }

    static boolean requestLifecycleFinalClose() throws IOException {
        if (!awaitingLifecycleFinalClose()) return false;
        String suffix = lifecycleSegment();
        Path directory = requireLifecycleDirectory();
        Path token = directory.resolve("close-client-" + suffix + ".token");
        if (!Files.isRegularFile(token)) return false;
        JsonObject identity = JsonParser.parseString(Files.readString(directory.resolve("identity.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        if (identity.get("runId") == null || !identity.get("runId").getAsString().matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("pilot lifecycle identity is invalid");
        }
        String expected = identity.get("runId").getAsString() + ":" + suffix + "\n";
        if (!expected.equals(Files.readString(token, StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("isolated pilot final close nonce mismatch");
        }
        awaitingFinalClose = false;
        finalCloseRequested = true;
        return true;
    }

    static void markAwaitingFinalClose() {
        if (!shouldAwaitFinalClose()) {
            throw new IllegalStateException("persistent matrix final close is not available");
        }
        awaitingFinalClose = true;
    }

    /**
     * Claims the one immutable close token published only after Node has verified the final
     * diagnostic and result record.  A malformed or foreign token fails closed; the visible
     * client may never turn a local completion into its own shutdown authority.
     */
    static boolean requestFinalClose() throws IOException {
        if (!awaitingFinalClose) return false;
        Path control = requireControlDirectory();
        Path token = control.resolve("close").resolve(String.format("%04d.token", epoch));
        if (!Files.isRegularFile(token)) return false;
        String expected = readRunId(control) + ":" + epoch + "\n";
        String actual = Files.readString(token, StandardCharsets.UTF_8);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("persistent matrix final close nonce mismatch");
        }
        awaitingFinalClose = false;
        finalCloseRequested = true;
        return true;
    }

    /**
     * Claims exactly one runner-written reconnect marker. The marker is not a readiness proof:
     * the runner writes it only after recording the replacement server's typed ready barrier.
     */
    static void requestReconnect(String server) throws IOException {
        if (!awaitingResume) return;
        Path control = requireControlDirectory();
        int next = epoch + 1;
        Path resume = matrix() ? control.resolve("resume").resolve(String.format("%04d.token", next)) : control.resolve("resume");
        if (!Files.isRegularFile(resume)) return;
        String runId = readRunId(control);
        String value = Files.readString(resume, StandardCharsets.UTF_8).trim();
        if (matrix()) {
            if (!value.equals(runId + ":" + next)) throw new IllegalArgumentException("persistent matrix resume nonce mismatch");
            if (expectedCrashSegment) FrontierV3PilotExpectedCrash.validateRelease(control, requireLifecycleDirectory(), activeDescriptor(epoch), activeDescriptor(next), epoch);
            activate(next);
        } else if (!runId.equals(value)) {
            throw new IllegalArgumentException("persistent pilot resume nonce mismatch");
        }
        if (!server.matches("[A-Za-z0-9_.:-]{1,255}")) throw new IllegalArgumentException("persistent pilot server address is invalid");
        if (activeConnection == null) throw new IllegalStateException("persistent pilot reconnect predecessor is unavailable");
        awaitingResume = false;
        reconnectInFlight = true;
        reconnectPredecessorConnection = activeConnection;
        reconnectTransportDeparturePrepared = false;
        reconnectTransportDepartureStarted = false;
    }

    static void reset() {
        awaitingResume = false;
        reconnectInFlight = false;
        awaitingFinalClose = false;
        finalCloseRequested = false;
        epoch = -1;
        segment = "";
        scenarioId = "";
        scenarioSha256 = "";
        finalSegment = false;
        preparedLifecycleSignal = false;
        normalDisconnectAcknowledgement = "";
        persistentLifecycleFailure = false;
        expectedCrashSegment = false; expectedLossArmed = false; expectedLossAcknowledged = false; expectedLossCompletedActionPrefix = -1; awaitingExpectedLossProbe = false;
        completion = "";
        activeConnection = null;
        reconnectPredecessorConnection = null;
        reconnectTransportDeparturePrepared = false;
        reconnectTransportDepartureStarted = false;
    }

    /**
     * Event-bus exceptions alone can leave a NeoForge client with exit status zero. Publish one
     * machine-readable failure before stopping so a missing lifecycle acknowledgement is never
     * mistaken for a successful persistent-client matrix.
     */
    static void failPersistentLifecycle(String boundary, Exception failure) {
        if (!claimPersistentLifecycleFailure()) return;
        PaleMirrorMod.LOGGER.error("PMV3_PILOT_FATAL boundary={} reason={}", boundary, failure.toString(), failure);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(minecraft::stop);
    }

    /** A live matrix connection may not silently become an idle client after an unclassified loss. */
    static boolean failUnexpectedActiveLoss() {
        if (!unexpectedActiveLossRequiresFatal()) return false;
        failPersistentLifecycle("unexpected_active_connection_loss", new IllegalStateException("persistent pilot lost its active connection"));
        return true;
    }

    /**
     * Publishes the sole semantic normal-disconnect acknowledgement for the active segment.
     *
     * <p>The guard is deliberately before the filesystem publication. If publication fails, a
     * later duplicate callback may not turn that failure into an implicit retry. The runner will
     * receive the explicit fatal pilot marker and fail the bounded lifecycle wait.</p>
     *
     * @return {@code true} only for the callback which owned the acknowledgement; {@code false}
     *         for a duplicate Minecraft callback for that same already-acknowledged segment.
     */
    static boolean publishNormalDisconnectAcknowledgement() throws IOException {
        if (!enabled()) return false;
        if (!awaitingResume && !finalCloseRequested) {
            throw new IllegalStateException("normal disconnect acknowledgement has no active lifecycle boundary");
        }
        String suffix = lifecycleSegment();
        if (!normalDisconnectAcknowledgement.isEmpty()) {
            if (normalDisconnectAcknowledgement.equals(suffix)) return false;
            throw new IllegalStateException("normal disconnect acknowledgement belongs to a different segment");
        }
        normalDisconnectAcknowledgement = suffix;
        publishLifecycleSignal("client_normally_disconnected", suffix, new JsonObject());
        return true;
    }

    /** Writes one immutable development-only acknowledgement for the Node supervisor. */
    static void publishLifecycleSignal(String signal, String suffix, JsonObject detail) throws IOException {
        Path directory = lifecycleDirectory();
        if (directory == null) return;
        if (!signal.matches("[a-z][a-z_]{0,63}") || !token(suffix) || detail == null) {
            throw new IllegalArgumentException("pilot lifecycle signal is invalid");
        }
        JsonObject identity = JsonParser.parseString(Files.readString(directory.resolve("identity.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        if (identity.get("schema") == null || identity.get("schema").getAsInt() != SCHEMA) {
            throw new IllegalArgumentException("pilot lifecycle identity is invalid");
        }
        JsonObject value = new JsonObject(); value.addProperty("schema", SCHEMA); value.addProperty("signal", signal);
        value.addProperty("suffix", suffix); value.add("identity", identity); value.add("detail", detail);
        Path signals = directory.resolve("signals");
        if (!Files.isDirectory(signals)) throw new IOException("pilot lifecycle signal directory is unavailable");
        FrontierV3LifecycleFilePublisher.publish(directory.resolve("staging"),
                signals.resolve(signal + "-" + suffix + ".json"), value + "\n");
    }

    private static void activate(int requestedEpoch) throws IOException {
        if (requestedEpoch < 0 || requestedEpoch > 4095) throw new IllegalArgumentException("persistent matrix epoch is invalid");
        Path descriptor = requireControlDirectory().resolve("segments").resolve(String.format("%04d.json", requestedEpoch));
        JsonObject value = JsonParser.parseString(Files.readString(descriptor, StandardCharsets.UTF_8)).getAsJsonObject();
        if (value.get("schema") == null || value.get("schema").getAsInt() != SCHEMA
                || value.get("kind") == null || !"frontier-v3-pilot-matrix-segment".equals(value.get("kind").getAsString())
                || value.get("runId") == null || !readRunId(requireControlDirectory()).equals(value.get("runId").getAsString())
                || value.get("epoch") == null || value.get("epoch").getAsInt() != requestedEpoch
                || value.get("segment") == null || !token(value.get("segment").getAsString())
                || value.get("scenarioId") == null || !token(value.get("scenarioId").getAsString())
                || value.get("final") == null || !value.get("final").getAsJsonPrimitive().isBoolean()
                || value.get("scenarioSha256") == null || !sha256(value.get("scenarioSha256").getAsString())) {
            throw new IllegalArgumentException("persistent matrix segment descriptor is invalid");
        }
        epoch = requestedEpoch;
        segment = value.get("segment").getAsString();
        scenarioId = value.get("scenarioId").getAsString();
        finalSegment = value.get("final").getAsBoolean();
        completion = value.has("completion") ? value.get("completion").getAsString() : "terminal";
        if (!completion.equals("terminal") && !completion.equals("graceful_handoff") && !completion.equals("expected_crash") && !completion.equals("recovered_terminal")) {
            throw new IllegalArgumentException("persistent matrix completion mode is invalid");
        }
        expectedCrashSegment = completion.equals("expected_crash");
        scenarioSha256 = value.get("scenarioSha256").getAsString();
        awaitingFinalClose = false;
        finalCloseRequested = false;
        normalDisconnectAcknowledgement = "";
        expectedLossArmed = false; expectedLossAcknowledged = false; expectedLossCompletedActionPrefix = -1; awaitingExpectedLossProbe = false;
    }

    private static boolean matrix() { return "matrix".equals(System.getProperty(MODE_PROPERTY, "")); }
    private static Path controlDirectory() {
        String configured = System.getProperty(CONTROL_PROPERTY, "");
        return configured.isBlank() ? null : Path.of(configured);
    }
    private static Path lifecycleDirectory() {
        String configured = System.getProperty(LIFECYCLE_CONTROL_PROPERTY, "");
        return configured.isBlank() ? null : Path.of(configured);
    }
    private static Path requireControlDirectory() {
        Path control = controlDirectory();
        if (control == null || !Files.isDirectory(control)) throw new IllegalArgumentException("persistent pilot control directory is unavailable");
        return control;
    }
    private static Path requireLifecycleDirectory() {
        Path lifecycle = lifecycleDirectory();
        if (lifecycle == null || !Files.isDirectory(lifecycle)) throw new IllegalArgumentException("pilot lifecycle control directory is unavailable");
        return lifecycle;
    }
    private static JsonObject activeDescriptor(int requestedEpoch) throws IOException {
        Path descriptor = requireControlDirectory().resolve("segments").resolve(String.format("%04d.json", requestedEpoch));
        return JsonParser.parseString(Files.readString(descriptor, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static String readRunId(Path control) throws IOException {
        String value = Files.readString(control.resolve("run-id"), StandardCharsets.UTF_8).trim();
        if (!value.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("persistent pilot run id is invalid");
        return value;
    }
    private static String requireSegment() {
        if (!token(segment)) throw new IllegalStateException("persistent matrix segment is not active");
        return segment;
    }
    private static boolean token(String value) { return value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"); }
    private static boolean sha256(String value) { return value.matches("[a-f0-9]{64}"); }
    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder rendered = new StringBuilder(digest.length * 2);
            for (byte entry : digest) rendered.append(String.format("%02x", entry));
            return rendered.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JRE has no SHA-256", impossible);
        }
    }
}
