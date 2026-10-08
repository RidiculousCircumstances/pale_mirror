package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Requested distance per simulation/entity tick, not a Minecraft speed attribute or pose writer. */
public record TravelPace(double blocksPerTick) {
    public TravelPace {
        if (!Double.isFinite(blocksPerTick) || blocksPerTick <= 0)
            throw new IllegalArgumentException("travel pace must be finite and positive");
    }
}
