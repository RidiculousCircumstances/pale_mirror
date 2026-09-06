package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/** Immutable wire checks for the noncanonical expected-loss control boundary. */
final class FrontierV3PilotExpectedCrash {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final int MAX_CONTROL_RECORD_BYTES = 65_536;
    private static final Set<String> CRASH_BOUNDARIES = Set.of(
            "lease_recorded_before_physical_materialization",
            "physical_effect_visible_before_typed_observation",
            "typed_observation_durable_before_next_process_checkpoint",
            "hot_checkpoint_durable_before_drain_release",
            "release_durable_before_cold_resumption");

    private FrontierV3PilotExpectedCrash() { }

    static void validateArm(Path control, Path lifecycle, JsonObject descriptor, int epoch) throws IOException {
        JsonObject arm = object(read(control.resolve("expected-crash").resolve(String.format("%04d.json", epoch))), "expected crash arm");
        require(number(arm, "schema") == 1 && text(arm, "kind").equals("frontier-v3-persistent-expected-crash-arm") && number(arm, "epoch") == epoch,
                "expected crash arm is foreign or stale");
        JsonObject armDescriptor = object(arm.get("descriptor"), "expected crash descriptor");
        JsonObject declaration = object(descriptor.get("expectedCrash"), "expected crash declaration");
        require(text(armDescriptor, "laneId").equals(text(declaration, "laneId")) && text(armDescriptor, "boundary").equals(text(declaration, "boundary"))
                && text(armDescriptor, "owner").equals(text(declaration, "owner")) && text(armDescriptor, "payloadType").equals(text(declaration, "payloadType"))
                && CRASH_BOUNDARIES.contains(text(declaration, "boundary")) && sameRevision(armDescriptor, declaration)
                && sameAuthorityDeclaration(armDescriptor, declaration), "expected crash declaration is foreign or stale");
        require(text(armDescriptor, "completion").equals("expected_crash") && text(armDescriptor, "segment").equals(text(descriptor, "segment"))
                && text(armDescriptor, "scenarioId").equals(text(descriptor, "scenarioId")) && text(armDescriptor, "scenarioSha256").equals(text(descriptor, "scenarioSha256"))
                && text(armDescriptor, "worldKey").equals(text(descriptor, "worldKey")) && token(text(armDescriptor, "segment"))
                && token(text(armDescriptor, "scenarioId")) && sha256(text(armDescriptor, "scenarioSha256")) && token(text(armDescriptor, "worldKey"))
                && token(text(arm, "serverRunId")), "expected crash descriptor is foreign or stale");
        require(positive(arm, "clientPid") && positive(arm, "serverPid") && port(arm, "port") && nonnegative(arm, "resolvedRevision")
                && (!arm.has("authorityEpoch") || nonnegative(arm, "authorityEpoch")), "expected crash arm numeric fields are malformed");
        require(number(arm, "clientPid") == ProcessHandle.current().pid() && number(arm, "resolvedRevision") == number(armDescriptor, "resolvedRevision")
                && (!arm.has("authorityEpoch") ? !armDescriptor.has("expectedAuthorityEpoch") : number(arm, "authorityEpoch") == number(armDescriptor, "expectedAuthorityEpoch")), "expected crash arm observation is foreign or stale");
        JsonObject identity = object(read(lifecycle.resolve("identity.json")), "lifecycle identity");
        JsonObject armIdentity = object(arm.get("identity"), "expected crash identity");
        require(validIdentity(armIdentity) && validIdentity(identity) && validIdentity(descriptor)
                && sameIdentity(armIdentity, identity) && sameIdentity(identity, descriptor), "expected crash identity is foreign or stale");
        JsonObject ready = object(read(control.resolve("server-ready").resolve(String.format("%04d.json", epoch))), "server readiness");
        requireReady(ready, descriptor, epoch);
        require(text(ready, "serverRunId").equals(text(arm, "serverRunId")) && number(ready, "serverPid") == number(arm, "serverPid")
                && number(ready, "port") == number(arm, "port"), "expected crash server readiness is foreign or stale");
        verifyHash(arm, "expected crash arm content hash");
    }

    static void validateRelease(Path control, Path lifecycle, JsonObject predecessor, JsonObject successor, int predecessorEpoch) throws IOException {
        validateArm(control, lifecycle, predecessor, predecessorEpoch);
        JsonObject arm = object(read(control.resolve("expected-crash").resolve(String.format("%04d.json", predecessorEpoch))), "retained expected crash arm");
        JsonObject release = object(read(control.resolve("recovery").resolve(String.format("%04d.json", predecessorEpoch + 1))), "expected crash release");
        JsonObject lifecycleIdentity = object(read(lifecycle.resolve("identity.json")), "lifecycle identity");
        require(number(release, "schema") == 1 && text(release, "kind").equals("frontier-v3-persistent-expected-crash-release")
                && number(release, "predecessorEpoch") == predecessorEpoch && text(release, "armSha256").equals(text(arm, "contentSha256"))
                && validIdentity(object(release.get("identity"), "release identity"))
                && sameIdentity(object(release.get("identity"), "release identity"), lifecycleIdentity), "expected crash release is foreign or stale");
        JsonObject proof = object(release.get("proof"), "expected crash proof");
        JsonObject fired = object(proof.get("fired"), "fired proof");
        JsonObject exit = object(proof.get("ownedExit"), "exit proof");
        JsonObject loss = object(proof.get("clientLoss"), "client loss proof");
        JsonObject closed = object(proof.get("portClosed"), "port closure proof");
        JsonObject armDescriptor = object(arm.get("descriptor"), "expected crash descriptor");
        JsonObject armIdentity = object(arm.get("identity"), "expected crash identity");
        require(text(fired, "runId").equals(text(arm, "serverRunId")) && number(fired, "serverPid") == number(arm, "serverPid")
                && text(fired, "boundary").equals(text(armDescriptor, "boundary")) && text(fired, "owner").equals(text(armDescriptor, "owner"))
                && text(fired, "payloadType").equals(text(armDescriptor, "payloadType")) && number(fired, "revision") == number(arm, "resolvedRevision")
                && (!arm.has("authorityEpoch") || number(fired, "authorityEpoch") == number(arm, "authorityEpoch")), "expected crash fired proof is incomplete or foreign");
        require(number(exit, "serverPid") == number(arm, "serverPid") && text(exit, "serverRunId").equals(text(arm, "serverRunId")) && truth(exit, "exited"),
                "expected crash exit proof is incomplete or foreign");
        require(number(loss, "clientPid") == number(arm, "clientPid") && number(loss, "epoch") == predecessorEpoch
                && text(loss, "segment").equals(text(armDescriptor, "segment")) && sameIdentity(loss, armIdentity), "expected crash client-loss proof is incomplete or foreign");
        require(number(closed, "serverPid") == number(arm, "serverPid") && text(closed, "serverRunId").equals(text(arm, "serverRunId"))
                && number(closed, "port") == number(arm, "port") && truth(closed, "closed"), "expected crash port-closure proof is incomplete or foreign");
        JsonObject next = object(release.get("successor"), "expected crash successor");
        require(number(next, "epoch") == predecessorEpoch + 1 && truth(next, "ready") && text(next, "segment").equals(text(successor, "segment"))
                && text(next, "scenarioSha256").equals(text(successor, "scenarioSha256")) && text(next, "worldKey").equals(text(successor, "worldKey"))
                && text(next, "worldKey").equals(text(predecessor, "worldKey")) && !text(next, "serverRunId").equals(text(arm, "serverRunId")), "expected crash release successor is foreign or stale");
        require(validIdentity(successor) && sameIdentity(successor, lifecycleIdentity), "expected crash successor identity is foreign or stale");
        JsonObject ready = object(read(control.resolve("server-ready").resolve(String.format("%04d.json", predecessorEpoch + 1))), "replacement readiness");
        requireReady(ready, successor, predecessorEpoch + 1);
        require(text(next, "serverRunId").equals(text(ready, "serverRunId")) && number(next, "serverPid") == number(ready, "serverPid"),
                "expected crash replacement readiness is foreign or stale");
        verifyHash(release, "expected crash release content hash");
    }

    private static void requireReady(JsonObject ready, JsonObject descriptor, int epoch) {
        require(number(ready, "schema") == 1 && text(ready, "kind").equals("frontier-v3-persistent-server-ready")
                && number(ready, "epoch") == epoch && text(ready, "runId").equals(text(descriptor, "runId"))
                && text(ready, "segment").equals(text(descriptor, "segment")) && text(ready, "scenarioSha256").equals(text(descriptor, "scenarioSha256"))
                && text(ready, "worldKey").equals(text(descriptor, "worldKey")) && token(text(ready, "serverRunId"))
                && truth(ready, "ready") && positive(ready, "serverPid") && port(ready, "port"),
                "expected crash server readiness is foreign or stale");
    }

    private static void verifyHash(JsonObject value, String label) {
        String actualHash = text(value, "contentSha256");
        value.remove("contentSha256");
        require(actualHash.equals(sha256(value.toString().getBytes(StandardCharsets.UTF_8))), label + " is foreign or stale");
    }
    private static JsonElement read(Path path) throws IOException {
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(path)) {
            bytes = stream.readNBytes(MAX_CONTROL_RECORD_BYTES + 1);
        }
        if (bytes.length > MAX_CONTROL_RECORD_BYTES) throw new IllegalArgumentException("expected crash control record is oversized");
        try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)); }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("expected crash control record is malformed", malformed); }
    }
    private static JsonObject object(JsonElement value, String label) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(label + " is malformed"); return value.getAsJsonObject(); }
    private static String text(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.get(key).getAsJsonPrimitive().isString()) throw new IllegalArgumentException("expected crash wire is malformed");
        return value.get(key).getAsString();
    }
    private static long number(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.get(key).getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("expected crash wire is malformed");
        try { return value.get(key).getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException | NumberFormatException malformed) { throw new IllegalArgumentException("expected crash wire is malformed", malformed); }
    }
    private static boolean truth(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonPrimitive() && value.get(key).getAsJsonPrimitive().isBoolean() && value.get(key).getAsBoolean(); }
    private static boolean sameIdentity(JsonObject left, JsonObject right) {
        return text(left, "runId").equals(text(right, "runId")) && text(left, "workerId").equals(text(right, "workerId"))
                && text(left, "buildIdentitySha256").equals(text(right, "buildIdentitySha256")) && text(left, "nonce").equals(text(right, "nonce"))
                && text(left, "sessionId").equals(text(right, "sessionId"));
    }
    private static boolean validIdentity(JsonObject value) {
        try {
            return sha256(text(value, "buildIdentitySha256")) && token(text(value, "workerId")) && uuid(text(value, "runId"))
                    && uuid(text(value, "nonce")) && uuid(text(value, "sessionId"));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }
    private static boolean sameRevision(JsonObject left, JsonObject right) {
        JsonElement leftValue = left.get("expectedRevision");
        JsonElement rightValue = right.get("expectedRevision");
        if (leftValue == null || rightValue == null || !leftValue.isJsonPrimitive() || !rightValue.isJsonPrimitive()) return false;
        if (leftValue.getAsJsonPrimitive().isString() || rightValue.getAsJsonPrimitive().isString()) {
            return leftValue.getAsJsonPrimitive().isString() && rightValue.getAsJsonPrimitive().isString()
                    && leftValue.getAsString().equals("observed_at_boundary") && leftValue.getAsString().equals(rightValue.getAsString());
        }
        try {
            return nonnegative(left, "expectedRevision") && number(left, "expectedRevision") == number(right, "expectedRevision")
                    && number(left, "resolvedRevision") == number(right, "expectedRevision");
        }
        catch (IllegalArgumentException malformed) { return false; }
    }
    private static boolean sameAuthorityDeclaration(JsonObject armDescriptor, JsonObject declaration) {
        if (!declaration.has("expectedAuthorityEpoch")) return !armDescriptor.has("expectedAuthorityEpoch");
        try {
            return armDescriptor.has("expectedAuthorityEpoch") && nonnegative(declaration, "expectedAuthorityEpoch")
                    && nonnegative(armDescriptor, "expectedAuthorityEpoch")
                    && number(armDescriptor, "expectedAuthorityEpoch") == number(declaration, "expectedAuthorityEpoch");
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }
    private static boolean positive(JsonObject value, String key) { long number = number(value, key); return number > 1L && number <= MAX_SAFE_INTEGER; }
    private static boolean nonnegative(JsonObject value, String key) { long number = number(value, key); return number >= 0L && number <= MAX_SAFE_INTEGER; }
    private static boolean port(JsonObject value, String key) { long number = number(value, key); return number >= 1024L && number <= 65535L; }
    private static boolean token(String value) { return value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"); }
    private static boolean sha256(String value) { return value.matches("[a-f0-9]{64}"); }
    private static boolean uuid(String value) { return value.matches("[0-9a-f-]{36}"); }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
    private static String sha256(byte[] bytes) {
        try { StringBuilder out = new StringBuilder(); for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x", value)); return out.toString(); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("JRE has no SHA-256", impossible); }
    }
}
