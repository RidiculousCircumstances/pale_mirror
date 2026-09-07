package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Small pure matcher shared by the development-only pilot's read-only diagnostics. */
final class FrontierV3PilotDiagnosticMatcher {
    private FrontierV3PilotDiagnosticMatcher() { }

    static boolean matches(JsonObject actual, JsonObject expected) {
        for (var entry : expected.entrySet()) {
            JsonElement value = actual.get(entry.getKey());
            if (value == null) return false;
            if (entry.getValue().isJsonObject()) {
                if (!value.isJsonObject() || !matches(value.getAsJsonObject(), entry.getValue().getAsJsonObject())) return false;
            } else if (!value.equals(entry.getValue())) return false;
        }
        return true;
    }

    /** Read-only evidence that a named numeric diagnostic field advanced after an action began. */
    static boolean increasedAtPath(JsonObject baseline, JsonObject actual, String path) {
        JsonElement before = atPath(baseline, path);
        JsonElement after = atPath(actual, path);
        if (before == null || after == null || !before.isJsonPrimitive() || !after.isJsonPrimitive()
                || !before.getAsJsonPrimitive().isNumber() || !after.getAsJsonPrimitive().isNumber()) return false;
        try {
            return after.getAsBigDecimal().compareTo(before.getAsBigDecimal()) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static JsonElement atPath(JsonObject value, String path) {
        JsonElement current = value;
        for (String segment : path.split("\\.", -1)) {
            if (!current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(segment);
            if (current == null) return null;
        }
        return current;
    }

    static boolean harvestComplete(JsonObject site, JsonObject intent, String itemId) {
        if (!"ok".equals(string(site, "status")) || !"GROWING".equals(string(site, "phase")) || site.get("growthEpoch").getAsLong() < 2L
                || !"ok".equals(string(intent, "status")) || !"CONFIRMED".equals(string(intent, "intentStatus"))
                || !"RESOURCE_SITE_HARVEST".equals(string(intent, "intentKind")) || string(intent, "receiptId").isBlank()
                || !intent.has("subjects") || !intent.get("subjects").isJsonArray()) return false;
        return java.util.stream.StreamSupport.stream(intent.getAsJsonArray("subjects").spliterator(), false)
                .anyMatch(value -> value.isJsonPrimitive() && itemId.equals(value.getAsString()));
    }

    static boolean containerContains(JsonObject container, JsonObject action) {
        if (!"ok".equals(string(container, "status")) || !container.has("occupied") || !container.get("occupied").isJsonArray()) return false;
        String item = action.get("item").getAsString(); int count = action.get("count").getAsInt();
        Integer slot = action.has("slot") ? action.get("slot").getAsInt() : null;
        for (JsonElement element : container.getAsJsonArray("occupied")) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            if (item.equals(string(value, "itemKind")) && value.has("count") && value.get("count").getAsInt() == count
                    && (slot == null || value.has("slot") && value.get("slot").getAsInt() == slot)) return true;
        }
        return false;
    }

    private static String string(JsonObject object, String member) {
        JsonElement value = object.get(member); return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
