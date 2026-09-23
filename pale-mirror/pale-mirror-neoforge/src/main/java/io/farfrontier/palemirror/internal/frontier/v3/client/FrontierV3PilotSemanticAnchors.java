package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

/** Interprets immutable diagnostics as typed operation anchors and visit body cells. */
final class FrontierV3PilotSemanticAnchors {
    private FrontierV3PilotSemanticAnchors() { }

    static String visitAnchor(JsonObject action) {
        String anchor = action.has("anchor") ? action.get("anchor").getAsString() : "travelCurrent";
        if (!anchor.equals("travelCurrent") && !anchor.equals("travelCargo")) {
            throw new IllegalArgumentException("unsupported operation visit anchor: " + anchor);
        }
        return anchor;
    }

    static BlockPos operationAnchor(FrontierV3TestPilotClient.ObservedDiagnostic diagnostic, String operation, String anchor) {
        JsonObject value = diagnostic.value().getAsJsonObject(anchor);
        if (value == null || !value.has("x") || !value.has("y") || !value.has("z")) {
            throw new IllegalStateException("operation diagnostic lacks " + anchor + " for " + operation);
        }
        return new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt());
    }

    static FrontierV3PilotVisitTarget visitTarget(JsonObject declaration, BlockPos resolved) {
        if (declaration.has("x") && declaration.has("y") && declaration.has("z")) {
            return FrontierV3PilotVisitTarget.fromClientFeet(resolved);
        }
        JsonObject reference = declaration.getAsJsonObject("diagnostic");
        String view = reference.get("view").getAsString();
        String field = reference.get("field").getAsString();
        // Crop slots name exact standing body cells. Support is derived solely by the observer.
        return view.equals("site") && (field.equals("firstCrop") || field.equals("lastCrop"))
                ? FrontierV3PilotVisitTarget.fromSemanticBody(resolved)
                : FrontierV3PilotVisitTarget.fromClientFeet(resolved);
    }
}
