package io.farfrontier.palemirror.internal.materialization;

import java.util.Collection;
import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.SemanticSlotKey;

/** Creates a semantic slot only when one exact managed parcel owns every cell. */
public final class SemanticSlotRegistration {
    private SemanticSlotRegistration() { }

    public static SemanticSlotRecord register(SemanticSlotLedger slots, ParcelLedger parcels,
                                              SemanticSlotKey key, String parcelId, String dimensionId, ParcelKind kind,
                                              Collection<SemanticCellRecord> cells) {
        var captured = java.util.List.copyOf(cells);
        if (captured.isEmpty()) throw new IllegalArgumentException("Semantic slot must own at least one cell");
        ParcelRecord owner = parcels.find(parcelId).orElseThrow(() ->
                new IllegalStateException("Semantic slot " + key.value() + " references missing parcel " + parcelId));
        if (!owner.dimensionId().equals(dimensionId) || owner.kind() != kind
                || captured.stream().anyMatch(cell -> !owner.contains(cell.position()))) {
            throw new IllegalStateException("Semantic slot " + key.value()
                    + " does not fit its explicit parcel " + parcelId);
        }
        SemanticSlotRecord record = new SemanticSlotRecord(key, parcelId, kind, captured,
                false, "", "");
        slots.register(record);
        return record;
    }
}
