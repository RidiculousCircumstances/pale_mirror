package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;

/** Strict, side-effect-free schema boundary shared by the visible client pilot and unit tests. */
final class FrontierV3TestPilotScenario {
    private static final Set<String> ACTION_TYPES = Set.of("wait", "wait_until_block", "command", "inspect", "look", "walk", "break", "hud");

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
                    (type.equals("inspect") && (!action.has("view") || !action.has("id"))) ||
                    (type.equals("hud") && (!action.has("visible") || !action.get("visible").isJsonPrimitive()
                            || !action.get("visible").getAsJsonPrimitive().isBoolean())) ||
                    ((type.equals("look") || type.equals("walk") || type.equals("break") || type.equals("wait_until_block")) && !action.has("position") && !action.has("at"))) {
                throw new IllegalArgumentException(section + " action " + index + " lacks required position/command");
            }
        }
    }
}
