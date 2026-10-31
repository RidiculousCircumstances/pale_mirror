package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierAdvisoryStatus;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import com.google.gson.*;

/** Bounded owning-thread evidence. No polling completion, queue changes or canonical reads. */
final class FrontierV3AdvisoryDiagnosticJson {
    static String render(FrontierAdvisoryStatus status) {
        var json = new JsonObject();
        json.addProperty("inFlight", status.inFlight()); json.addProperty("ready", status.ready());
        json.addProperty("capacityWaiters", status.capacityWaiters()); json.addProperty("hostAvailable", status.hostAvailable());
        json.addProperty("reservationReadiness", status.reservationReadiness().name());
        json.add("reservation", status.reservation().map(FrontierV3AdvisoryDiagnosticJson::action).orElse(JsonNull.INSTANCE));
        json.add("capacityHead", status.capacityHead().map(FrontierV3AdvisoryDiagnosticJson::action).orElse(JsonNull.INSTANCE));
        // Current capacity admission began here; business dueAt also includes earlier route/calculation stages.
        json.add("capacityHeadSinceTick", status.capacityHeadSince().<JsonElement>map(at -> new JsonPrimitive(at.ticks())).orElse(JsonNull.INSTANCE));
        return json.toString();
    }
    private static JsonElement action(ScheduledAction action) {
        var json = new JsonObject(); json.addProperty("id", action.id().value());
        json.addProperty("kind", action.kind()); json.addProperty("subject", action.subject().value());
        json.addProperty("dueAt", action.dueAt().ticks()); return json;
    }
    private FrontierV3AdvisoryDiagnosticJson() { }
}
