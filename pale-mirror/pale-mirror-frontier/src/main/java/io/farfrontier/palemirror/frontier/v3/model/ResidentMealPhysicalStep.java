package io.farfrontier.palemirror.frontier.v3.model;

/** Durable before-effect fence for one non-replayable HOT meal step. */
public record ResidentMealPhysicalStep(ResidentMeal.Phase phase, int sourceSlot, int sourceCount,
                                       long sourceEpoch, long destinationEpoch, long ambientRevision) {
    public ResidentMealPhysicalStep {
        if (phase != ResidentMeal.Phase.TAKE && phase != ResidentMeal.Phase.CONSUME
                || sourceSlot < -1 || sourceSlot >= 27 || sourceCount < 1 || sourceCount > 64
                || sourceEpoch < 1 || destinationEpoch < 0 || ambientRevision < 1
                || phase == ResidentMeal.Phase.TAKE && (sourceSlot < 0 || destinationEpoch < 1)
                || phase == ResidentMeal.Phase.CONSUME && (sourceSlot != -1 || sourceCount != 1))
            throw new IllegalArgumentException("invalid retained HOT meal physical step");
    }
}
