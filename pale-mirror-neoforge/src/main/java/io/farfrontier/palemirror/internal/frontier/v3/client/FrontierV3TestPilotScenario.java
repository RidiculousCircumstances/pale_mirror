package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;

/** Strict, side-effect-free schema boundary shared by the visible client pilot and unit tests. */
final class FrontierV3TestPilotScenario {
    private static final Set<String> ACTION_TYPES = Set.of("wait", "wait_until_block", "wait_until_diagnostic", "wait_until_harvest_result", "fast_forward", "command", "inspect", "look", "walk", "break", "hud");
    private static final Set<String> DIAGNOSTIC_VIEWS = Set.of("summary", "site", "actor", "item", "operation", "intent", "trace");

    record Parsed(JsonArray setup, JsonArray actions) {
        int setupCount() { return setup.size(); }
        int actionCount() { return actions.size(); }
    }

    private FrontierV3TestPilotScenario() { }

    static Parsed parse(String source) {
        JsonElement parsed = JsonParser.parseString(source);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("test-pilot scenario must be an object");
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("schema") || root.get("schema").getAsInt() != 1) {
            throw new IllegalArgumentException("expected test-pilot scenario schema 1");
        }
        JsonArray setup = array(root, "setup", true);
        JsonArray actions = array(root, "actions", false);
        validate(setup, "setup"); validate(actions, "actions");
        return new Parsed(setup, actions);
    }

    private static JsonArray array(JsonObject root, String name, boolean optional) {
        if (!root.has(name)) {
            if (optional) return new JsonArray();
            throw new IllegalArgumentException("scenario lacks " + name + " array");
        }
        if (!root.get(name).isJsonArray()) throw new IllegalArgumentException(name + " must be an array");
        return root.getAsJsonArray(name);
    }

    private static void validate(JsonArray actions, String section) {
        for (int index = 0; index < actions.size(); index++) {
            JsonElement element = actions.get(index);
            if (!element.isJsonObject() || !element.getAsJsonObject().has("type")) {
                throw new IllegalArgumentException(section + " action " + index + " lacks type");
            }
            String type = element.getAsJsonObject().get("type").getAsString();
            if (!ACTION_TYPES.contains(type)) throw new IllegalArgumentException("unsupported test-pilot action: " + type);
            JsonObject action = element.getAsJsonObject();
            if ((type.equals("command") && !action.has("command")) ||
                    (type.equals("inspect") && !validDiagnosticIdentity(action)) ||
                    (type.equals("wait_until_diagnostic") && (!validDiagnosticIdentity(action) || !action.has("expect")
                            || !action.get("expect").isJsonObject() || !timeout(action, 300_000L))) ||
                    (type.equals("wait_until_harvest_result") && !validHarvestResult(action)) ||
                    (type.equals("fast_forward") && !wholeTicks(action, 24_000L)) ||
                    (type.equals("hud") && (!action.has("visible") || !action.get("visible").isJsonPrimitive()
                            || !action.get("visible").getAsJsonPrimitive().isBoolean())) ||
                    ((type.equals("look") || type.equals("walk") || type.equals("break") || type.equals("wait_until_block")) && !action.has("position") && !action.has("at"))) {
                throw new IllegalArgumentException(section + " action " + index + " lacks required position/command");
            }
        }
    }

    private static boolean validDiagnosticIdentity(JsonObject action) {
        if (!action.has("view") || !action.has("id") || !action.get("view").isJsonPrimitive() || !action.get("id").isJsonPrimitive()) return false;
        String view = action.get("view").getAsString(); String id = action.get("id").getAsString();
        return DIAGNOSTIC_VIEWS.contains(view) && (view.equals("summary") || !id.isBlank());
    }

    private static boolean timeout(JsonObject action, long maximum) {
        if (!action.has("timeoutMs") || !action.get("timeoutMs").isJsonPrimitive() || !action.get("timeoutMs").getAsJsonPrimitive().isNumber()) return false;
        long value = action.get("timeoutMs").getAsLong();
        return value >= 0L && value <= maximum;
    }

    /** A harvest result is deliberately stronger than a transient READY phase. */
    private static boolean validHarvestResult(JsonObject action) {
        return requiredId(action, "siteId", "site:") && requiredId(action, "intentId", "intent:")
                && requiredId(action, "itemId", "item:") && timeout(action, 300_000L);
    }

    private static boolean requiredId(JsonObject action, String field, String prefix) {
        return action.has(field) && action.get(field).isJsonPrimitive()
                && action.get(field).getAsString().startsWith(prefix) && action.get(field).getAsString().length() > prefix.length();
    }

    private static boolean wholeTicks(JsonObject action, long maximum) {
        if (!action.has("ticks") || !action.get("ticks").isJsonPrimitive() || !action.get("ticks").getAsJsonPrimitive().isNumber()) return false;
        Number value = action.get("ticks").getAsNumber(); long whole = value.longValue();
        return whole >= 1L && whole <= maximum && value.doubleValue() == (double) whole;
    }
}
