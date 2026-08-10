package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Keeps regional physical placement records out of the canonical SavedData codec. */
final class CampaignRegionPresentationCodec {
    private CampaignRegionPresentationCodec() { }

    static void write(CompoundTag tag, Map<String, CampaignRegionRecord> regions) {
        ListTag values = new ListTag();
        regions.values().forEach(region -> {
            CompoundTag value = new CompoundTag();
            value.putString("id", region.id());
            value.putString("dimension", region.dimensionId());
            value.putString("place", region.placeId().value());
            value.putLong("settlementAnchor", region.settlementAnchor().asLong());
            value.putLong("primaryMineColumn", region.primaryMineColumn().asLong());
            value.putLong("alternateMineColumn", region.alternateMineColumn().asLong());
            if (region.primaryMineAnchor() != null) value.putLong("primaryMineAnchor", region.primaryMineAnchor().asLong());
            if (region.alternateMineAnchor() != null) value.putLong("alternateMineAnchor", region.alternateMineAnchor().asLong());
            value.putString("status", region.status().name());
            value.putString("diagnostic", region.diagnostic());
            value.putInt("nextOperation", region.nextOperationIndex());
            value.putLong("originTrainSeenAtStep", region.originTrainSeenAtStep());
            value.putLong("destinationTrainSeenAtStep", region.destinationTrainSeenAtStep());
            value.putInt("originTrainCapacity", region.originTrainCapacity());
            value.putInt("destinationTrainCapacity", region.destinationTrainCapacity());
            value.putString("originVehicleId", region.originVehicleId());
            value.putString("destinationVehicleId", region.destinationVehicleId());
            value.put("primaryMineBaseline", writeBaseline(region.primaryMineBaseline()));
            value.put("alternateMineBaseline", writeBaseline(region.alternateMineBaseline()));
            values.add(value);
        });
        tag.put("campaignRegions", values);
    }

    static Map<String, CampaignRegionRecord> read(CompoundTag tag) {
        Map<String, CampaignRegionRecord> result = new LinkedHashMap<>();
        for (Tag element : tag.getList("campaignRegions", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            CampaignRegionRecord region = new CampaignRegionRecord(value.getString("id"), value.getString("dimension"),
                    new io.farfrontier.palemirror.domain.WorldObjectId(value.getString("place")),
                    BlockPos.of(value.getLong("settlementAnchor")), BlockPos.of(value.getLong("primaryMineColumn")),
                    BlockPos.of(value.getLong("alternateMineColumn")),
                    value.contains("primaryMineAnchor", Tag.TAG_LONG) ? BlockPos.of(value.getLong("primaryMineAnchor")) : null,
                    value.contains("alternateMineAnchor", Tag.TAG_LONG) ? BlockPos.of(value.getLong("alternateMineAnchor")) : null,
                    CampaignRegionPresentationStatus.valueOf(value.getString("status")), value.getString("diagnostic"),
                    value.getInt("nextOperation"), value.contains("originTrainSeenAtStep", Tag.TAG_LONG)
                    ? value.getLong("originTrainSeenAtStep") : -1, value.contains("destinationTrainSeenAtStep", Tag.TAG_LONG)
                    ? value.getLong("destinationTrainSeenAtStep") : -1, value.getInt("originTrainCapacity"),
                    value.getInt("destinationTrainCapacity"), value.getString("originVehicleId"),
                    value.getString("destinationVehicleId"));
            region.restoreMineBaselines(readBaseline(value.getList("primaryMineBaseline", Tag.TAG_COMPOUND)),
                    readBaseline(value.getList("alternateMineBaseline", Tag.TAG_COMPOUND)));
            result.put(region.id(), region);
        }
        return result;
    }

    private static ListTag writeBaseline(Map<Long, String> baseline) {
        ListTag values = new ListTag(); baseline.forEach((position, state) -> {
            CompoundTag value = new CompoundTag(); value.putLong("position", position); value.putString("state", state); values.add(value);
        }); return values;
    }

    private static Map<Long, String> readBaseline(ListTag values) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (Tag entry : values) { CompoundTag value = (CompoundTag) entry; result.put(value.getLong("position"), value.getString("state")); }
        return result;
    }
}
