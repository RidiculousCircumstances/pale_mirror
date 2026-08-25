package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Immutable, source-order observation retained by either territorial mind. */
public record ReferenceV2Belief(
        String sectorKey,
        int observedDay,
        double confidence,
        ReferenceObservationSource source,
        double infection,
        double organicMass,
        double hiveInfluence,
        double infrastructureValue,
        boolean chrysalis
) {
    public ReferenceV2Belief {
        sectorKey = Objects.requireNonNull(sectorKey, "sectorKey");
        source = Objects.requireNonNull(source, "source");
    }

    public ReferenceV2Belief decayed(int day, double decay) {
        return new ReferenceV2Belief(sectorKey, observedDay,
                Math.max(0.0d, confidence * Math.max(0.0d, 1.0d - decay * Math.max(0, day - observedDay))),
                source, infection, organicMass, hiveInfluence, infrastructureValue, chrysalis);
    }
}
