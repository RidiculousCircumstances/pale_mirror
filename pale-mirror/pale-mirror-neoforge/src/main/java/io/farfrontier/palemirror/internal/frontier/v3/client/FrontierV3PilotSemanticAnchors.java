package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

/** Interprets immutable diagnostics as typed visit body cells. */
final class FrontierV3PilotSemanticAnchors {
    private FrontierV3PilotSemanticAnchors() { }



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
