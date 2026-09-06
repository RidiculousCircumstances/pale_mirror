package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.internal.materialization.ParcelLedger;
import io.farfrontier.palemirror.internal.materialization.ParcelRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class ParcelCodec {
    private ParcelCodec() { }
    static void write(CompoundTag root, ParcelLedger ledger) {
        ListTag values = new ListTag();
        ledger.parcels().forEach(parcel -> {
            CompoundTag value = new CompoundTag();
            value.putString("id", parcel.id()); value.putString("region", parcel.regionId());
            value.putString("dimension", parcel.dimensionId()); value.putLong("min", parcel.min().asLong());
            value.putLong("max", parcel.max().asLong()); value.putString("binding", parcel.bindingId());
            value.putString("kind", parcel.kind().name()); value.putLong("revision", parcel.revision());
            if (parcel.leaseOwner() != null) value.putUUID("leaseOwner", parcel.leaseOwner());
            value.putString("commissioningPermit", parcel.commissioningPermit()); values.add(value);
        });
        root.put("parcels", values);
    }
    static ParcelLedger read(CompoundTag root) {
        Map<String, ParcelRecord> values = new LinkedHashMap<>();
        for (Tag raw : root.getList("parcels", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            ParcelRecord parcel = new ParcelRecord(value.getString("id"), value.getString("region"),
                    value.getString("dimension"), BlockPos.of(value.getLong("min")), BlockPos.of(value.getLong("max")),
                    value.getString("binding"), ParcelKind.valueOf(value.getString("kind")),
                    value.hasUUID("leaseOwner") ? value.getUUID("leaseOwner") : null, value.getLong("revision"),
                    value.getString("commissioningPermit"));
            if (values.putIfAbsent(parcel.id(), parcel) != null) throw new IllegalStateException("Duplicate parcel " + parcel.id());
        }
        return new ParcelLedger(values);
    }
}
