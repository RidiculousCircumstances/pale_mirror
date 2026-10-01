package io.farfrontier.palemirror.frontier.v3.model;

/** Durable before-effect fence for one non-replayable HOT meal step. */
public record ResidentMealPhysicalStep(ResidentMeal.Phase phase, java.util.Map<Integer, Integer> sourceCounts,
                                       int consumptionQuantity,
                                       long sourceEpoch, long destinationEpoch, long ambientRevision) {
    public ResidentMealPhysicalStep(ResidentMeal.Phase phase, int sourceSlot, int sourceCount,
                                    long sourceEpoch, long destinationEpoch, long ambientRevision) {
        this(phase, phase == ResidentMeal.Phase.TAKE ? java.util.Map.of(sourceSlot, sourceCount) : java.util.Map.of(),
                phase == ResidentMeal.Phase.CONSUME ? sourceCount : 0, sourceEpoch, destinationEpoch, ambientRevision);
        if (phase == ResidentMeal.Phase.CONSUME && sourceSlot != -1)
            throw new IllegalArgumentException("consumption fence is not a container source");
    }
    public ResidentMealPhysicalStep {
        sourceCounts = java.util.Map.copyOf(sourceCounts);
        if (phase != ResidentMeal.Phase.TAKE && phase != ResidentMeal.Phase.CONSUME
                || sourceCounts.size() > 27 || sourceCounts.entrySet().stream().anyMatch(entry ->
                    entry.getKey() < 0 || entry.getKey() >= 27 || entry.getValue() < 1 || entry.getValue() > 64)
                || sourceEpoch < 1 || destinationEpoch < 0 || ambientRevision < 1
                || phase == ResidentMeal.Phase.TAKE && (sourceCounts.isEmpty() || destinationEpoch < 1 || consumptionQuantity != 0)
                || phase == ResidentMeal.Phase.CONSUME && (!sourceCounts.isEmpty() || consumptionQuantity < 1 || consumptionQuantity > 64 || destinationEpoch != 0))
            throw new IllegalArgumentException("invalid retained HOT meal physical step");
    }

    public static java.util.Map<Integer, Integer> sourceCounts(java.util.List<MaterialSourceSelection.Slice> slices) {
        var counts = new java.util.TreeMap<Integer, Integer>();
        for (var slice : slices) {
            if (!(slice.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                    || counts.put(slot.slot().slot(), slice.before()) != null)
                throw new IllegalArgumentException("meal take requires distinct current container slots");
        }
        return java.util.Map.copyOf(counts);
    }
}
