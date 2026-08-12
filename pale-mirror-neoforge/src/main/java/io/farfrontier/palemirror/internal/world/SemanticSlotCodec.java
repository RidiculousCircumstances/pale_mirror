package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.internal.materialization.SemanticCellRecord;
import io.farfrontier.palemirror.internal.materialization.SemanticSlotLedger;
import io.farfrontier.palemirror.internal.materialization.SemanticSlotRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderLookup;

final class SemanticSlotCodec {
    private SemanticSlotCodec() { }

    static void write(CompoundTag root, SemanticSlotLedger ledger) {
        ListTag slots = new ListTag();
        ledger.slots().forEach(slot -> {
            CompoundTag value = new CompoundTag();
            value.putString("object", slot.key().objectId());
            value.putString("module", slot.key().moduleId());
            value.putString("slot", slot.key().slotId());
            value.putString("parcel", slot.parcelKind().name());
            value.putBoolean("conflicted", slot.conflicted());
            value.putString("diagnostic", slot.diagnostic());
            value.putString("resetPermit", slot.resetPermit());
            ListTag cells = new ListTag();
            slot.cells().forEach(cell -> {
                CompoundTag item = new CompoundTag();
                item.putLong("position", cell.position().asLong());
                item.put("baseline", NbtUtils.writeBlockState(cell.baselineState()));
                item.put("lastApplied", NbtUtils.writeBlockState(cell.lastAppliedState()));
                cells.add(item);
            });
            value.put("cells", cells);
            slots.add(value);
        });
        root.put("semanticSlots", slots);
    }

    static SemanticSlotLedger read(CompoundTag root, HolderLookup.Provider registries) {
        Map<String, SemanticSlotRecord> slots = new LinkedHashMap<>();
        for (Tag raw : root.getList("semanticSlots", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            SemanticSlotKey key = new SemanticSlotKey(value.getString("object"), value.getString("module"), value.getString("slot"));
            List<SemanticCellRecord> cells = new ArrayList<>();
            for (Tag rawCell : value.getList("cells", Tag.TAG_COMPOUND)) {
                CompoundTag cell = (CompoundTag) rawCell;
                var blocks = registries.lookupOrThrow(Registries.BLOCK);
                cells.add(new SemanticCellRecord(BlockPos.of(cell.getLong("position")),
                        NbtUtils.readBlockState(blocks, cell.getCompound("baseline")),
                        NbtUtils.readBlockState(blocks, cell.getCompound("lastApplied"))));
            }
            SemanticSlotRecord slot = new SemanticSlotRecord(key, ParcelKind.valueOf(value.getString("parcel")), cells,
                    value.getBoolean("conflicted"), value.getString("diagnostic"), value.getString("resetPermit"));
            if (slots.putIfAbsent(key.value(), slot) != null) throw new IllegalStateException("Duplicate semantic slot " + key.value());
        }
        return new SemanticSlotLedger(slots);
    }
}
