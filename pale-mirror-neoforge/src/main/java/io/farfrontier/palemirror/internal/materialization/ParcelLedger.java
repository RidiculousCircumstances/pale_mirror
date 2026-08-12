package io.farfrontier.palemirror.internal.materialization;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.farfrontier.palemirror.api.ParcelKind;
import net.minecraft.core.BlockPos;

public final class ParcelLedger {
    private final Map<String, ParcelRecord> parcels;
    private final List<ParcelRecord> influenceParcels;
    public ParcelLedger() { this(new LinkedHashMap<>()); }
    public ParcelLedger(Map<String, ParcelRecord> parcels) {
        this.parcels = new LinkedHashMap<>(parcels);
        this.influenceParcels = new ArrayList<>(parcels.values().stream()
                .filter(parcel -> parcel.kind() == ParcelKind.INFLUENCE).toList());
    }
    public Collection<ParcelRecord> parcels() { return java.util.List.copyOf(parcels.values()); }
    public Optional<ParcelRecord> find(String id) { return Optional.ofNullable(parcels.get(id)); }
    public Optional<ParcelRecord> managedAt(String dimensionId, BlockPos position) {
        return parcels.values().stream().filter(parcel -> parcel.dimensionId().equals(dimensionId)
                && parcel.kind().pmManaged() && parcel.contains(position)).findFirst();
    }
    public Optional<ParcelRecord> reservedAt(String dimensionId, BlockPos position) {
        return parcels.values().stream().filter(parcel -> parcel.dimensionId().equals(dimensionId)
                && parcel.kind() == ParcelKind.RESERVED && parcel.contains(position)).findFirst();
    }
    /** Settlement territory is a horizontal policy boundary, not block-mutation ownership. */
    public Optional<ParcelRecord> influenceAtColumn(String dimensionId, BlockPos position) {
        return influenceParcels.stream().filter(parcel -> parcel.dimensionId().equals(dimensionId)
                && position.getX() >= parcel.min().getX() && position.getX() <= parcel.max().getX()
                && position.getZ() >= parcel.min().getZ() && position.getZ() <= parcel.max().getZ()).findFirst();
    }
    public void register(ParcelRecord parcel) {
        if (parcels.putIfAbsent(parcel.id(), parcel) != null) throw new IllegalStateException("Duplicate parcel " + parcel.id());
        if (parcel.kind() == ParcelKind.INFLUENCE) influenceParcels.add(parcel);
    }
    public void clear() {
        parcels.clear();
        influenceParcels.clear();
    }
}
