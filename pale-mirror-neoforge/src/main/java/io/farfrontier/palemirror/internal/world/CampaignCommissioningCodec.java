package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class CampaignCommissioningCodec {
    private CampaignCommissioningCodec() { }

    static void write(CompoundTag root, Map<String, CampaignCommissioningRecord> records) {
        ListTag values = new ListTag();
        records.values().forEach(record -> {
            CompoundTag value = new CompoundTag();
            value.putString("region", record.regionId()); value.putString("dimension", record.dimensionId());
            value.putString("connection", record.connectionId()); value.putString("service", record.serviceId());
            value.putLong("start", record.railStart().asLong()); value.putLong("target", record.railTarget().asLong());
            value.putLong("assembly", record.assemblyTrack().asLong()); value.putString("axis", record.trackAxis().name());
            value.putString("direction", record.assemblyDirection().name()); value.putInt("maximumLength", record.maximumLength());
            value.putString("origin", record.originStation()); value.putString("destination", record.destinationStation());
            value.putString("status", record.status().name()); value.putString("planHash", record.planHash());
            value.putString("nativeRail", record.nativeRailReference()); value.putString("nativeTrain", record.nativeTrainReference());
            value.putString("schedule", record.scheduleFingerprint()); value.putString("diagnostic", record.diagnostic());
            value.putInt("arrivals", record.baselineArrivals()); value.putLong("infectionEligible", record.infectionEligibleAtStep());
            ListTag cells = new ListTag();
            record.railCells().values().forEach(cell -> {
                CompoundTag encoded = new CompoundTag(); encoded.putLong("position", cell.position().asLong());
                encoded.putString("baseline", cell.baselineState()); encoded.putString("lastApproved", cell.lastApprovedState());
                encoded.putBoolean("conflicted", cell.conflicted()); cells.add(encoded);
            });
            value.put("cells", cells); values.add(value);
        });
        root.put("campaignCommissioning", values);
    }

    static Map<String, CampaignCommissioningRecord> read(CompoundTag root) {
        Map<String, CampaignCommissioningRecord> result = new LinkedHashMap<>();
        for (Tag entry : root.getList("campaignCommissioning", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) entry;
            Map<Long, RailwayMutableCell> cells = new LinkedHashMap<>();
            for (Tag cellEntry : value.getList("cells", Tag.TAG_COMPOUND)) {
                CompoundTag cell = (CompoundTag) cellEntry; BlockPos position = BlockPos.of(cell.getLong("position"));
                cells.put(position.asLong(), new RailwayMutableCell(position, cell.getString("baseline"),
                        cell.getString("lastApproved"), cell.getBoolean("conflicted")));
            }
            CampaignCommissioningRecord record = new CampaignCommissioningRecord(value.getString("region"),
                    value.getString("dimension"), value.getString("connection"), value.getString("service"),
                    BlockPos.of(value.getLong("start")), BlockPos.of(value.getLong("target")), BlockPos.of(value.getLong("assembly")),
                    Direction.Axis.valueOf(value.getString("axis")), Direction.valueOf(value.getString("direction")),
                    value.getInt("maximumLength"), value.getString("origin"), value.getString("destination"),
                    CampaignCommissioningStatus.valueOf(value.getString("status")), value.getString("planHash"),
                    value.getString("nativeRail"), value.getString("nativeTrain"), value.getString("schedule"),
                    value.getString("diagnostic"), value.getInt("arrivals"), value.getLong("infectionEligible"), cells);
            result.put(record.regionId(), record);
        }
        return result;
    }
}
