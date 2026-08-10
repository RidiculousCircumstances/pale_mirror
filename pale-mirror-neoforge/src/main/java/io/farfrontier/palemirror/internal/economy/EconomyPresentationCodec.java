package io.farfrontier.palemirror.internal.economy;

import java.util.LinkedHashMap;
import java.util.Map;

import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.world.InfectionBiomeStage;
import io.farfrontier.palemirror.internal.world.MutableCell;
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
            tag.putString("state", depot.state().name());
            tag.putString("diagnostic", depot.diagnostic());
            ListTag cells = new ListTag();
            depot.cells().forEach(cell -> {
                CompoundTag value = new CompoundTag();
                value.putLong("pos", cell.position().asLong());
                value.putString("baseline", cell.baselineBlock());
                value.putString("lastApplied", cell.lastAppliedBlock());
                value.putBoolean("conflicted", cell.conflicted());
                cells.add(value);
            });
            tag.put("cells", cells);
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
                    ResourceTransferState.valueOf(tag.getString("state")), tag.getString("diagnostic"));
            values.put(value.id(), value);
        }
        return new ResourceTransferLedger(values);
    }

    public static Map<WorldObjectId, SettlementDepotRecord> readDepots(CompoundTag root) {
        Map<WorldObjectId, SettlementDepotRecord> values = new LinkedHashMap<>();
        for (Tag element : root.getList("settlementDepots", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) element;
            java.util.List<MutableCell> cells = new java.util.ArrayList<>();
            for (Tag cellElement : tag.getList("cells", Tag.TAG_COMPOUND)) {
                CompoundTag cell = (CompoundTag) cellElement;
                cells.add(new MutableCell(BlockPos.of(cell.getLong("pos")), cell.getString("baseline"),
                        cell.getString("lastApplied"), cell.getBoolean("conflicted"), InfectionBiomeStage.NODE));
            }
            SettlementDepotRecord depot = new SettlementDepotRecord(new WorldObjectId(tag.getString("site")),
                    new WorldObjectId(tag.getString("community")), tag.getString("dimension"),
                    BlockPos.of(tag.getLong("anchor")), cells, SettlementDepotState.valueOf(tag.getString("state")),
                    tag.getString("diagnostic"));
            values.put(depot.communityId(), depot);
        }
        return values;
    }
}
