package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;

/** Strict, side-effect-free schema boundary shared by the visible client pilot and unit tests. */
final class FrontierV3TestPilotScenario {
    private static final Set<String> ACTION_TYPES = Set.of(
            "wait", "wait_until_block", "wait_until_diagnostic", "wait_until_harvest_result", "fast_forward", "command", "inspect", "look",
            "walk", "break", "hud", "assert_fixture", "visit", "assert_visible_block", "assert_visible_board");
    private static final Set<String> DIAGNOSTIC_VIEWS = Set.of("summary", "site", "settlement", "actor", "item", "operation", "intent", "trace");

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
                    (type.equals("visit") && !validVisit(action)) ||
                    (type.equals("inspect") && !validDiagnosticIdentity(action)) ||
                    (type.equals("wait_until_diagnostic") && (!validDiagnosticIdentity(action) || !action.has("expect")
                            || !action.get("expect").isJsonObject() || !timeout(action, 300_000L))) ||
                    (type.equals("wait_until_harvest_result") && !validHarvestResult(action)) ||
                    (type.equals("assert_fixture") && !validFixture(action)) ||
                    (type.equals("assert_visible_block") && (!position(action) || !timeout(action, 120_000L))) ||
                    (type.equals("assert_visible_board") && !validVisibleBoard(action)) ||
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

    private static boolean timeout(JsonObject action, long maximum, String field) {
        if (!action.has(field) || !action.get(field).isJsonPrimitive() || !action.get(field).getAsJsonPrimitive().isNumber()) return false;
        long value = action.get(field).getAsLong();
        return value >= 0L && value <= maximum;
    }

    /** A harvest result is deliberately stronger than a transient READY phase. */
    private static boolean validHarvestResult(JsonObject action) {
        return requiredId(action, "siteId", "site:") && requiredId(action, "intentId", "intent:")
                && requiredId(action, "itemId", "item:") && (!action.has("settlementId") || requiredId(action, "settlementId", "settlement:"))
                && timeout(action, 300_000L);
    }

    private static boolean validFixture(JsonObject action) {
        if (!timeout(action, 120_000L) || !action.has("checks") || !action.get("checks").isJsonArray()) return false;
        JsonArray checks = action.getAsJsonArray("checks");
        if (checks.isEmpty() || checks.size() > 16) return false;
        for (JsonElement check : checks) {
            if (!check.isJsonObject()) return false;
            JsonObject value = check.getAsJsonObject();
            if (!validDiagnosticIdentity(value) || !value.has("expect") || !value.get("expect").isJsonObject()) return false;
        }
        return true;
    }

    private static boolean validVisit(JsonObject action) {
        return action.has("dimension") && action.get("dimension").isJsonPrimitive()
                && action.get("dimension").getAsString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                && position(action) && timeout(action, 120_000L, "settleMs");
    }

    private static boolean validVisibleBoard(JsonObject action) {
        if (!position(action) || !timeout(action, 120_000L) || !action.has("text") || !action.get("text").isJsonPrimitive()
                || action.get("text").getAsString().isBlank()) return false;
        return boundedOptionalNumber(action, "radius", 0.0D, 16.0D)
                && boundedOptionalNumber(action, "maxDistance", 1.0D, 128.0D)
                && boundedOptionalNumber(action, "maxAngleDeg", 1.0D, 90.0D);
    }

    private static boolean boundedOptionalNumber(JsonObject action, String field, double minimum, double maximum) {
        if (!action.has(field)) return true;
        if (!action.get(field).isJsonPrimitive() || !action.get(field).getAsJsonPrimitive().isNumber()) return false;
        double value = action.get(field).getAsDouble(); return Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    private static boolean position(JsonObject action) {
        JsonObject position = action.has("position") ? action.getAsJsonObject("position") : action.getAsJsonObject("at");
        return position != null && whole(position, "x") && whole(position, "y") && whole(position, "z");
    }

    private static boolean whole(JsonObject value, String field) {
        if (!value.has(field) || !value.get(field).isJsonPrimitive() || !value.get(field).getAsJsonPrimitive().isNumber()) return false;
        Number number = value.get(field).getAsNumber(); return number.doubleValue() == (double) number.longValue();
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
