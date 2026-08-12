package io.farfrontier.palemirror.internal.materialization;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public final class ParcelLedger {
    private final Map<String, ParcelRecord> parcels;
    public ParcelLedger() { this(new LinkedHashMap<>()); }
    public ParcelLedger(Map<String, ParcelRecord> parcels) { this.parcels = new LinkedHashMap<>(parcels); }
    public Collection<ParcelRecord> parcels() { return java.util.List.copyOf(parcels.values()); }
    public Optional<ParcelRecord> find(String id) { return Optional.ofNullable(parcels.get(id)); }
    public Optional<ParcelRecord> managedAt(String dimensionId, BlockPos position) {
        return parcels.values().stream().filter(parcel -> parcel.dimensionId().equals(dimensionId)
                && parcel.kind().pmManaged() && parcel.contains(position)).findFirst();
    }
    public Optional<ParcelRecord> reservedAt(String dimensionId, BlockPos position) {
        return parcels.values().stream().filter(parcel -> parcel.dimensionId().equals(dimensionId)
                && parcel.kind() == io.farfrontier.palemirror.api.ParcelKind.RESERVED && parcel.contains(position)).findFirst();
    }
    public void register(ParcelRecord parcel) {
        if (parcels.putIfAbsent(parcel.id(), parcel) != null) throw new IllegalStateException("Duplicate parcel " + parcel.id());
    }
    public void clear() { parcels.clear(); }
}
