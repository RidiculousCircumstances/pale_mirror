package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Whole-slot conflict boundary with sparse per-cell precondition evidence. */
public final class SemanticSlotRecord {
    private final SemanticSlotKey key;
    private final String parcelId;
    private ParcelKind parcelKind;
    private final Map<Long, SemanticCellRecord> cells;
    private boolean conflicted;
    private String diagnostic;
    private String resetPermit;

    public SemanticSlotRecord(SemanticSlotKey key, String parcelId, ParcelKind parcelKind,
                              Collection<SemanticCellRecord> cells,
                              boolean conflicted, String diagnostic, String resetPermit) {
        this.key = key;
        if (parcelId == null || parcelId.isBlank()) throw new IllegalArgumentException("Semantic slot parcel is required");
        this.parcelId = parcelId;
        this.parcelKind = parcelKind;
        this.cells = new LinkedHashMap<>();
        cells.forEach(cell -> this.cells.put(cell.position().asLong(), cell));
        this.conflicted = conflicted;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        this.resetPermit = resetPermit == null ? "" : resetPermit;
    }
    public SemanticSlotKey key() { return key; }
    public String parcelId() { return parcelId; }
    public ParcelKind parcelKind() { return parcelKind; }
    public List<SemanticCellRecord> cells() { return List.copyOf(cells.values()); }
    public SemanticCellRecord cell(BlockPos position) { return cells.get(position.asLong()); }
    public boolean conflicted() { return conflicted; }
    public String diagnostic() { return diagnostic; }
    public String resetPermit() { return resetPermit; }
    public boolean mutable() { return parcelKind.pmManaged() && (!conflicted || !resetPermit.isBlank()); }
    public void conflict(String reason) { conflicted = true; diagnostic = reason; resetPermit = ""; }
    public void authorizeReset(String permit) {
        if (permit == null || permit.isBlank()) throw new IllegalArgumentException("Reset permit is required");
        resetPermit = permit;
    }
    public void commissionCommunity() {
        if (parcelKind != ParcelKind.RESERVED && parcelKind != ParcelKind.PLAYER_LEASE) {
            throw new IllegalStateException("Only a reserved semantic slot can be commissioned");
        }
        parcelKind = ParcelKind.COMMUNITY;
    }
    public void completeReset() { conflicted = false; diagnostic = ""; resetPermit = ""; }
}
