package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class ResidentJourneyLeaseCodec {
    private ResidentJourneyLeaseCodec() { }
    static void write(CompoundTag root, ResidentJourneyLeaseLedger ledger) {
        ListTag values = new ListTag();
        ledger.leases().forEach(lease -> {
            CompoundTag tag = new CompoundTag(); tag.putString("resident", lease.residentId());
            tag.putString("journey", lease.journeyId()); tag.putString("phase", lease.phase().name());
            tag.putInt("checkpoint", lease.checkpointIndex());
            tag.putLong("revision", lease.revision()); values.add(tag);
        });
        root.put("residentJourneyLeases", values);
    }
    static ResidentJourneyLeaseLedger read(CompoundTag root) {
        Map<String, ResidentJourneyLease> leases = new LinkedHashMap<>();
        for (Tag raw : root.getList("residentJourneyLeases", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            ResidentJourneyLease lease = new ResidentJourneyLease(tag.getString("resident"), tag.getString("journey"),
                    ResidentJourneyLeasePhase.valueOf(tag.getString("phase")), tag.getInt("checkpoint"), tag.getLong("revision"));
            if (leases.putIfAbsent(lease.residentId(), lease) != null) throw new IllegalStateException("Duplicate resident lease");
        }
        return new ResidentJourneyLeaseLedger(leases);
    }
}
