package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** One resident has selected and reserved a single depot-owned bread unit. */
public record ResidentMealStarted(ResidentMeal meal,
        java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt> clearingAccess) implements FrontierPayload {
    public ResidentMealStarted {
        Objects.requireNonNull(meal, "started meal");
        Objects.requireNonNull(clearingAccess, "meal admission clearance");
        if (meal.portable() == clearingAccess.isPresent())
            throw new IllegalArgumentException("meal start needs the clearance proof of its declared source");
    }
    @Override public String type() { return "frontier.resident_meal_started"; }
}
