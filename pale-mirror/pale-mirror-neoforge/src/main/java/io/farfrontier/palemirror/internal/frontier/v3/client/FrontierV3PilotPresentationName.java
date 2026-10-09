package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

/** Read-only test camera selector: names come from declared diagnostics, never a profession. */
final class FrontierV3PilotPresentationName {
    private FrontierV3PilotPresentationName() { }
    static String resolve(Minecraft minecraft, JsonObject action) {
        JsonElement selector = action.get("nameContains");
        if (selector == null) return null;
        if (selector.isJsonPrimitive()) return selector.getAsString();
        JsonObject reference = selector.getAsJsonObject().getAsJsonObject("diagnostic");
        String view = reference.get("view").getAsString(), id = reference.get("id").getAsString();
        var observed = FrontierV3TestPilotClient.diagnostics.get(new FrontierV3TestPilotClient.DiagnosticIdentity(view, id));
        JsonObject diagnostic = observed == null ? null : observed.value();
        JsonElement value = diagnostic;
        for (String part : reference.get("field").getAsString().split("\\.")) {
            value = value != null && value.isJsonObject() ? value.getAsJsonObject().get(part) : null;
        }
        if (value != null && value.isJsonPrimitive() && !value.getAsString().isBlank()) return value.getAsString();
        if (minecraft.player.tickCount % 20 == 0) minecraft.player.connection.sendCommand("pale_mirror v3 inspect " + view + " " + id);
        return null;
    }
}
