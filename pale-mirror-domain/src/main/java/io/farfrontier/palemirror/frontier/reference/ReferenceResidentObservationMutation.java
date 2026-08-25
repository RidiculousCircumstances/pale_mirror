package io.farfrontier.palemirror.frontier.reference;

/** Exact no-RNG resident mutation after the custody owner accepts a physical fact. */
final class ReferenceResidentObservationMutation {
    private ReferenceResidentObservationMutation() { }

    static boolean apply(
            ReferenceSettlement settlement,
            String residentId,
            ReferenceGrayboxResidentObservation.Kind kind
    ) {
        ReferenceResidentLedger residents = settlement.residents();
        if (residents == null) return false;
        boolean changed = kind == ReferenceGrayboxResidentObservation.Kind.KILLED
                ? residents.killExact(residentId)
                : residents.woundExact(residentId);
        if (changed) settlement.syncResidentProjection();
        return changed;
    }
}
