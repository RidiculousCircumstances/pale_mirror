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
        if (!"ok".equals(string(site, "status"))
                || !"ok".equals(string(intent, "status")) || !"CONFIRMED".equals(string(intent, "intentStatus"))
                || !"RESOURCE_SITE_HARVEST".equals(string(intent, "intentKind")) || string(intent, "receiptId").isBlank()
                || !intent.has("subjects") || !intent.get("subjects").isJsonArray()
                || !site.has("terminalHarvest") || !site.get("terminalHarvest").isJsonArray()) return false;
        boolean terminal = java.util.stream.StreamSupport.stream(site.getAsJsonArray("terminalHarvest").spliterator(), false)
                .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .anyMatch(value -> itemId.equals(string(value, "outputItem"))
                        && "CONFIRMED".equals(string(value, "intentStatus"))
                        && value.has("physicalReceiptResolved") && value.get("physicalReceiptResolved").getAsBoolean()
                        && value.has("physicalReceiptConfirmed") && value.get("physicalReceiptConfirmed").getAsBoolean());
        if (!terminal) return false;
        return java.util.stream.StreamSupport.stream(intent.getAsJsonArray("subjects").spliterator(), false)
                .anyMatch(value -> value.isJsonPrimitive() && itemId.equals(value.getAsString()));
    }

    static boolean containerContains(JsonObject container, JsonObject action) {
        if (!"ok".equals(string(container, "status"))) return false;
        String item = action.get("item").getAsString(); int count = action.get("count").getAsInt();
        Integer slot = action.has("slot") ? action.get("slot").getAsInt() : null;
        if (containsStack(container, "occupied", item, count, slot)) return true;
        // Field output is a fungible lot bound to a real chest slot, not an
        // ExactItemStack. Require current observed physical custody as well as
        // the binding; a canonical-only or stale slot is not a client oracle.
        if (!container.has("replica") || !container.get("replica").isJsonObject()
                || !"OBSERVED_CURRENT".equals(string(container.getAsJsonObject("replica"), "state"))
                || !container.has("physicalSocket") || !container.get("physicalSocket").isJsonObject()) return false;
        JsonObject socket = container.getAsJsonObject("physicalSocket");
        return "OWNED".equals(string(socket, "chest")) && "CURRENT".equals(string(socket, "slots"))
                && containsStack(container, "fungibleOccupied", item, count, slot);
    }

    private static boolean containsStack(JsonObject container, String member, String item, int count, Integer slot) {
        if (!container.has(member) || !container.get(member).isJsonArray()) return false;
        for (JsonElement element : container.getAsJsonArray(member)) {
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
