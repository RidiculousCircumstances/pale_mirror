package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.Objects;
/** Request only: the meal owner commits an ordinary shared journey before using its entrance turn. */
public record ResidentMealEntranceRequested(ActorActuationId actor) implements FrontierPayload {
    public ResidentMealEntranceRequested {
        Objects.requireNonNull(actor);
        if (actor.execution().activityKind() != io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL)
            throw new IllegalArgumentException("meal entrance requires its exact meal execution");
    }
    public String type() { return "frontier.resident_meal_entrance_requested"; }
}
