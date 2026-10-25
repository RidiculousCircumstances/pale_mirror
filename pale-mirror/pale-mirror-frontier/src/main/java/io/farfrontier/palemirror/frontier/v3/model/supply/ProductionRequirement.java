package io.farfrontier.palemirror.frontier.v3.model.supply;

/** Owner-declared minimum input to restore productive service, not stock,
 * a reservation, a promised output or an assertion of timely delivery. */
public record ProductionRequirement(long quantity, long withinTicks) {
    public ProductionRequirement {
        if (quantity < 1 || withinTicks < 0) throw new IllegalArgumentException("invalid production recovery requirement");
    }
    public ProductionRequirement plus(ProductionRequirement other) {
        return new ProductionRequirement(Math.addExact(quantity, other.quantity), Math.min(withinTicks, other.withinTicks));
    }
}
