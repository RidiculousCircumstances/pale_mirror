package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.SeatPlace;
import java.util.Objects;
/** Exact standing meal owner replaces only its unused seat, retaining the acquired portion. */
public record ResidentMealSeatReselected(ActorActuationId actor, long revision, SeatPlace.Id destination) implements FrontierPayload {
    public ResidentMealSeatReselected {
        Objects.requireNonNull(actor); Objects.requireNonNull(destination);
        if (revision < 1 || actor.execution().activityKind() != ActorActivityKind.MEAL)
            throw new IllegalArgumentException("seat reselection requires a declared meal/body/reservation");
    }
    public String type() { return "frontier.resident_meal_seat_reselected"; }
}
