package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** One resident has selected and reserved a single depot-owned bread unit. */
public record ResidentMealStarted(ResidentMeal meal) implements FrontierPayload {
    public ResidentMealStarted { Objects.requireNonNull(meal, "started meal"); }
    @Override public String type() { return "frontier.resident_meal_started"; }
}
