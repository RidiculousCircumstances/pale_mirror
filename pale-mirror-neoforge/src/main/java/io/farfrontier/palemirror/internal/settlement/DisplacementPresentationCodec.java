package io.farfrontier.palemirror.internal.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.economy.SettlementDepotState;
import io.farfrontier.palemirror.internal.world.MutableCell;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class DisplacementPresentationCodec {
    private DisplacementPresentationCodec() { }

    public static void write(CompoundTag root, Map<String, RefugeeCampRecord> camps) {
        ListTag values = new ListTag();
        camps.values().forEach(camp -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("group", camp.populationGroupId());
            tag.putString("community", camp.communityId().value());
            tag.putString("site", camp.siteId().value());
            tag.putString("dimension", camp.dimensionId());
            tag.putLong("anchor", camp.anchor().asLong());
            tag.putString("state", camp.state().name());
            tag.putString("diagnostic", camp.diagnostic());
            ListTag actors = new ListTag();
            camp.representativeIds().forEach(value -> {
                CompoundTag actor = new CompoundTag();
                actor.putUUID("id", value);
                actors.add(actor);
            });
            tag.put("actors", actors);
            ListTag cells = new ListTag();
            camp.cells().forEach(cell -> {
                CompoundTag value = new CompoundTag();
                value.putLong("pos", cell.position().asLong());
                value.putString("baseline", cell.baselineBlock());
                value.putString("lastApplied", cell.lastAppliedBlock());
                value.putBoolean("conflicted", cell.conflicted());
                cells.add(value);
            });
            tag.put("cells", cells);
            values.add(tag);
        });
        root.put("refugeeCamps", values);
    }

    public static Map<String, RefugeeCampRecord> read(CompoundTag root) {
        Map<String, RefugeeCampRecord> values = new LinkedHashMap<>();
        for (Tag element : root.getList("refugeeCamps", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) element;
            java.util.List<MutableCell> cells = new ArrayList<>();
            for (Tag cellElement : tag.getList("cells", Tag.TAG_COMPOUND)) {
                CompoundTag cell = (CompoundTag) cellElement;
                cells.add(new MutableCell(BlockPos.of(cell.getLong("pos")), cell.getString("baseline"),
                        cell.getString("lastApplied"), cell.getBoolean("conflicted")));
            }
            java.util.List<UUID> actors = new ArrayList<>();
            for (Tag actorElement : tag.getList("actors", Tag.TAG_COMPOUND)) actors.add(((CompoundTag) actorElement).getUUID("id"));
            RefugeeCampRecord camp = new RefugeeCampRecord(tag.getString("group"),
                    new WorldObjectId(tag.getString("community")), new WorldObjectId(tag.getString("site")),
                    tag.getString("dimension"), BlockPos.of(tag.getLong("anchor")), cells, actors,
                    SettlementDepotState.valueOf(tag.getString("state")), tag.getString("diagnostic"));
            values.put(camp.populationGroupId(), camp);
        }
        return values;
    }
}
