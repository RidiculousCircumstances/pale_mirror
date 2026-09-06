package io.farfrontier.palemirror.internal.economy;

import java.util.LinkedHashMap;
import java.util.Map;

import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class EconomyPresentationCodec {
    private EconomyPresentationCodec() { }

    public static void write(CompoundTag root, ResourceTransferLedger ledger,
                             Map<WorldObjectId, SettlementDepotRecord> depots) {
        ListTag transfers = new ListTag();
        ledger.transfers().forEach(value -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", value.id());
            tag.putString("direction", value.direction().name());
            tag.putUUID("player", value.playerId());
            tag.putString("community", value.communityId().value());
            tag.putString("site", value.siteId().value());
            tag.putString("resource", value.resource().name());
            tag.putInt("amount", value.amount());
            tag.putInt("slot", value.inventorySlot());
            tag.putString("mapping", value.mappingHash());
            tag.putLong("createdStep", value.createdStep());
            tag.putString("purpose", value.purpose().name());
            tag.putString("developmentIntent", value.developmentIntentId());
            tag.putString("state", value.state().name());
            tag.putString("diagnostic", value.diagnostic());
            transfers.add(tag);
        });
        root.put("resourceTransfers", transfers);
        ListTag serializedDepots = new ListTag();
        depots.values().forEach(depot -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("site", depot.siteId().value());
            tag.putString("community", depot.communityId().value());
            tag.putString("dimension", depot.dimensionId());
            tag.putLong("anchor", depot.anchor().asLong());
            tag.putString("slotObject", depot.semanticSlot().objectId());
            tag.putString("slotModule", depot.semanticSlot().moduleId());
            tag.putString("slotId", depot.semanticSlot().slotId());
            serializedDepots.add(tag);
        });
        root.put("settlementDepots", serializedDepots);
    }

    public static ResourceTransferLedger readLedger(CompoundTag root) {
        Map<String, ResourceTransfer> values = new LinkedHashMap<>();
        for (Tag element : root.getList("resourceTransfers", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) element;
            ResourceTransfer value = new ResourceTransfer(tag.getString("id"),
                    ResourceTransferDirection.valueOf(tag.getString("direction")), tag.getUUID("player"),
                    new WorldObjectId(tag.getString("community")), new WorldObjectId(tag.getString("site")),
                    ResourceKind.valueOf(tag.getString("resource")), tag.getInt("amount"), tag.getInt("slot"),
                    tag.getString("mapping"), tag.getLong("createdStep"),
                    tag.contains("purpose", Tag.TAG_STRING) ? ResourceTransferPurpose.valueOf(tag.getString("purpose"))
                            : ResourceTransferPurpose.SETTLEMENT_STOCK,
                    tag.getString("developmentIntent"),
                    ResourceTransferState.valueOf(tag.getString("state")), tag.getString("diagnostic"));
            values.put(value.id(), value);
        }
        return new ResourceTransferLedger(values);
    }

    public static Map<WorldObjectId, SettlementDepotRecord> readDepots(CompoundTag root) {
        Map<WorldObjectId, SettlementDepotRecord> values = new LinkedHashMap<>();
        for (Tag element : root.getList("settlementDepots", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) element;
            SettlementDepotRecord depot = new SettlementDepotRecord(new WorldObjectId(tag.getString("site")),
                    new WorldObjectId(tag.getString("community")), tag.getString("dimension"),
                    BlockPos.of(tag.getLong("anchor")), new SemanticSlotKey(tag.getString("slotObject"),
                    tag.getString("slotModule"), tag.getString("slotId")));
            values.put(depot.communityId(), depot);
        }
        return values;
    }
}
