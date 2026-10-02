package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.Minecraft;
import java.util.ArrayDeque;
import java.util.Map;
import static io.farfrontier.palemirror.internal.frontier.v3.client.FrontierV3TestPilotClient.*;

/** Read-only client oracle for one field-work identity and its causal progress. */
final class FrontierV3HarvestSemanticOracle {
    private final String siteId;
    private final String requestedJobId;
    private final long durationTicks;
    private final long sampleEveryTicks;
    private final long maxCanonicalStallTicks;
    private final ArrayDeque<JsonObject> tail = new ArrayDeque<>();
    private long lastRequestTick = Long.MIN_VALUE;
    private long lastSiteReceipt = -1L;
    private long lastCanonicalInstant = -1L;
    private long firstCanonicalInstant = -1L;
    private long lastSemanticProgressInstant = -1L;
    private long lastPhysicalProgressInstant = -1L;
    private String jobId = "", workerId = "", intentId = "";
    private String semanticSignature = "", physicalSignature = "";
    private boolean bound, sawCanonicalProgress, sawPhysicalProgress, sawActive, passed;

    FrontierV3HarvestSemanticOracle(JsonObject action) {
        this.siteId = action.get("siteId").getAsString();
        this.requestedJobId = action.has("jobId") ? action.get("jobId").getAsString() : "";
        this.durationTicks = action.get("durationTicks").getAsLong(); this.sampleEveryTicks = action.get("sampleEveryTicks").getAsLong();
        this.maxCanonicalStallTicks = action.get("maxCanonicalStallTicks").getAsLong();
    }

    Result sample(Minecraft minecraft, Map<DiagnosticIdentity, ObservedDiagnostic> diagnostics) {
        long tick = minecraft.level.getGameTime();
        if (lastRequestTick == Long.MIN_VALUE || tick - lastRequestTick >= sampleEveryTicks) request(minecraft, diagnostics);
        ObservedDiagnostic site = diagnostics.get(new DiagnosticIdentity("site", siteId));
        if (site != null && site.receiptSequence() > lastSiteReceipt) {
            lastSiteReceipt = site.receiptSequence(); inspect(site.value(), diagnostics);
        }
        return passed ? Result.PASS : Result.WAIT;
    }

    private void request(Minecraft minecraft, Map<DiagnosticIdentity, ObservedDiagnostic> diagnostics) {
        lastRequestTick = minecraft.level.getGameTime();
        minecraft.player.connection.sendCommand("pale_mirror v3 inspect site " + siteId);
        if (!jobId.isBlank()) minecraft.player.connection.sendCommand("pale_mirror v3 inspect process " + jobId);
        if (!workerId.isBlank()) minecraft.player.connection.sendCommand("pale_mirror v3 inspect actor " + workerId);
        if (!intentId.isBlank()) minecraft.player.connection.sendCommand("pale_mirror v3 inspect intent " + intentId);
    }

    private void inspect(JsonObject site, Map<DiagnosticIdentity, ObservedDiagnostic> diagnostics) {
        if (!"ok".equals(string(site, "status"))) fail("site_diagnostic_unavailable");
        long instant = number(site, "instant");
        if (instant <= lastCanonicalInstant) return;
        lastCanonicalInstant = instant;
        String phase = string(site, "phase");
        var activeJobs = new java.util.ArrayList<String>();
        site.getAsJsonArray("activeWork").forEach(value -> activeJobs.add(value.getAsString()));
        if (!site.get("conflictDisposition").isJsonNull()) fail("truthful_local_blocker=" + site.get("conflictDisposition"));
        if ("HARVESTING".equals(phase) && activeJobs.isEmpty()) fail("false_active_without_current_work");
        if (sawActive && !activeJobs.contains(jobId)) {
            JsonObject terminal = null;
            for (var receipt : site.getAsJsonArray("terminalHarvest")) {
                var candidate = receipt.getAsJsonObject();
                if (jobId.equals(string(candidate, "job"))) terminal = candidate;
            }
            if (!sawCanonicalProgress || !sawPhysicalProgress || terminal == null) fail("terminal_without_observed_progress");
            if (!terminal.get("physicalReceiptResolved").getAsBoolean()) return;
            publish("PASS:TERMINAL");
            semanticSignature = "TERMINAL";
            passed = true;
            return;
        }
        if (!bound && !activeJobs.isEmpty()) {
            if (!requestedJobId.isBlank() && !activeJobs.contains(requestedJobId)) return;
            jobId = requestedJobId.isBlank() ? activeJobs.getFirst() : requestedJobId;
            bound = true; sawActive = true;
        }
        ObservedDiagnostic process = jobId.isBlank() ? null : diagnostics.get(new DiagnosticIdentity("process", jobId));
        if (process == null) return;
        if (!"ok".equals(string(process.value(), "status"))) fail("process_diagnostic_unavailable");
        if (number(process.value(), "instant") < instant) return;
        JsonObject value = process.value();
        String processSite = path(value, "claims", "site"); String worker = path(value, "identity", "worker"); String intent = path(value, "claims", "intent");
        if (!siteId.equals(processSite) || !jobId.equals(path(value, "identity", "job")) || worker.isBlank() || intent.isBlank()) fail("incoherent_site_process_claim");
        workerId = worker; intentId = intent;
        ObservedDiagnostic actor = diagnostics.get(new DiagnosticIdentity("actor", workerId));
        ObservedDiagnostic intentDiagnostic = diagnostics.get(new DiagnosticIdentity("intent", intentId));
        if (actor == null || intentDiagnostic == null) return;
        JsonObject actorValue = actor.value(); JsonObject intentValue = intentDiagnostic.value();
        if (!"ok".equals(string(actorValue, "status"))) fail("actor_diagnostic_unavailable");
        if (!"ok".equals(string(intentValue, "status"))) fail("intent_diagnostic_unavailable");
        if (!jobId.equals(string(actorValue, "assignmentOwner")) || !"FIELD_HARVEST".equals(string(actorValue, "assignment"))) fail("worker_assignment_diverges");
        if (!intentId.equals(string(intentValue, "id")) || !"RESOURCE_SITE_HARVEST".equals(string(intentValue, "intentKind"))) fail("intent_diverges");
        JsonObject admission = object(actorValue, "physicalAdmission");
        if (admission == null || "UUID_CONFLICT".equals(string(admission, "status")) || "CARRIER".equals(string(admission, "status"))) fail("worker_physical_admission_invalid");
        String lease = path(value, "claims", "lease", "status");
        String completed = path(value, "conservation", "completedCropSlots"); String cursor = path(value, "cursor", "index");
        String physical = path(admission, "observedExact", "x") + "," + path(admission, "observedExact", "y") + "," + path(admission, "observedExact", "z");
        String nextSemanticSignature = completed + "/" + cursor + "/" + lease + "/" + string(intentValue, "intentStatus");
        JsonObject snapshot = new JsonObject(); snapshot.addProperty("instant", instant); snapshot.addProperty("phase", phase); snapshot.addProperty("job", jobId);
        snapshot.addProperty("worker", workerId); snapshot.addProperty("intent", intentId); snapshot.addProperty("lease", lease);
        snapshot.addProperty("completedCropSlots", completed); snapshot.addProperty("cursor", cursor); snapshot.addProperty("physical", physical);
        // The carrier's causal tail must distinguish an admitted retained edge that the
        // actuator cannot physically accept from one that was never submitted.  These
        // are read-only body facts published by the server diagnostic; they cannot choose
        // a route, relax an arrival condition, or turn a failed motion into progress.
        snapshot.addProperty("motionStatus", string(admission, "motionStatus"));
        snapshot.addProperty("motionTarget", path(admission, "motionTarget", "x") + ","
                + path(admission, "motionTarget", "y") + "," + path(admission, "motionTarget", "z"));
        snapshot.addProperty("motionAcceptedMoves", string(admission, "motionAcceptedMoves"));
        tail.addLast(snapshot); while (tail.size() > 12) tail.removeFirst();
        // A dynamic oracle cannot start its canonical observation window until the site
        // has yielded its exact job/worker/intent relation and the matching diagnostics
        // arrived.  At accelerated tick rates that first diagnostic round-trip can span
        // the whole window; charging that transport/bootstrap interval would turn a
        // healthy, already-progressing HOT scene into a false rejection.
        if (firstCanonicalInstant < 0L) firstCanonicalInstant = instant;
        if (semanticSignature.isBlank()) {
            lastSemanticProgressInstant = instant;
        } else if (!nextSemanticSignature.equals(semanticSignature)) {
            sawCanonicalProgress |= !completed.equals(pathTail("completedCropSlots")) || !cursor.equals(pathTail("cursor"));
            lastSemanticProgressInstant = instant;
        }
        if (physicalSignature.isBlank() || !physical.equals(physicalSignature)) {
            sawPhysicalProgress |= !physicalSignature.isBlank();
            lastPhysicalProgressInstant = instant;
        }
        if (lastSemanticProgressInstant >= 0L && instant - lastSemanticProgressInstant > maxCanonicalStallTicks) {
            String kind = lastPhysicalProgressInstant >= 0L && instant - lastPhysicalProgressInstant <= maxCanonicalStallTicks
                    ? "canonical_physical_divergence=" : "unexplained_canonical_stall=";
            fail(kind + (instant - lastSemanticProgressInstant));
        }
        semanticSignature = nextSemanticSignature;
        physicalSignature = physical;
        if (instant - firstCanonicalInstant >= durationTicks) {
            if (!bound || !sawActive || !sawCanonicalProgress || !sawPhysicalProgress) fail("incomplete_online_observation");
            publish("PASS:IN_FLIGHT");
            passed = true;
        }
    }

    private String pathTail(String field) { JsonObject prior = tail.size() < 2 ? null : tail.stream().skip(tail.size() - 2L).findFirst().orElse(null); return prior == null ? "" : string(prior, field); }
    private void fail(String reason) { publish("REJECTED:" + reason); throw new IllegalStateException("harvest semantic oracle site=" + siteId + " job=" + jobId + " worker=" + workerId + " intent=" + intentId + " " + reason); }
    void publish(String outcome) { JsonObject value = new JsonObject(); value.addProperty("schema", 1); value.addProperty("kind", "harvest_oracle"); value.addProperty("id", siteId); value.addProperty("status", outcome);
            value.addProperty("job", jobId); value.addProperty("worker", workerId); value.addProperty("intent", intentId); value.addProperty("lastCanonicalInstant", lastCanonicalInstant); value.add("causalTail",
            tailJson()); PaleMirrorMod.LOGGER.info("PMV3_PILOT_DIAGNOSTIC {}", value); }
    private JsonArray tailJson() { JsonArray result = new JsonArray(); tail.forEach(result::add); return result; }
    private static String string(JsonObject value, String name) { JsonElement element = value.get(name); return element != null && element.isJsonPrimitive() ? element.getAsString() : ""; }
    private static long number(JsonObject value, String name) { JsonElement element = value.get(name); return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsLong() : -1L; }
    private static JsonObject object(JsonObject value, String name) { JsonElement element = value.get(name); return element != null && element.isJsonObject() ? element.getAsJsonObject() : null; }
    private static String path(JsonObject value, String... parts) { JsonObject current = value; for (int index = 0; index < parts.length - 1; index++) { current = object(current, parts[index]); if (current == null)
            return ""; } return string(current, parts[parts.length - 1]); }
    enum Result { WAIT, PASS }
}
