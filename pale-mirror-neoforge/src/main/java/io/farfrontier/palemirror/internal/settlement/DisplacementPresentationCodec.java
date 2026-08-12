package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.domain.WorldObjectId;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Fresh-schema v36 codec; old representative actors and specialized job state deliberately do not exist. */
public final class DisplacementPresentationCodec {
    private DisplacementPresentationCodec() { }

    public static void write(CompoundTag root, Map<String, RefugeeCampRecord> camps,
                             RefugeeAnchorPermitLedger permits) {
        ListTag values = new ListTag();
        camps.values().forEach(camp -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("group", camp.populationGroupId()); tag.putString("community", camp.communityId().value());
            tag.putString("site", camp.siteId().value()); tag.putString("dimension", camp.dimensionId());
            tag.putLong("anchor", camp.anchor().asLong());
            tag.putString("slotObject", camp.semanticSlot().objectId());
            tag.putString("slotModule", camp.semanticSlot().moduleId());
            tag.putString("slotId", camp.semanticSlot().slotId()); values.add(tag);
        });
        root.put("refugeeCamps", values);
        ListTag permitValues = new ListTag();
        permits.permits().forEach(permit -> {
            CompoundTag value = new CompoundTag(); value.putString("id", permit.id());
            value.putUUID("player", permit.playerId()); value.putString("community", permit.communityId().value());
            value.putString("audience", permit.audience().value()); value.putLong("expires", permit.expiresAtStep());
            value.putBoolean("consumed", permit.consumed()); permitValues.add(value);
        });
        root.put("refugeeAnchorPermits", permitValues);
    }

    public static Map<String, RefugeeCampRecord> read(CompoundTag root) {
        Map<String, RefugeeCampRecord> values = new LinkedHashMap<>();
        for (Tag element : root.getList("refugeeCamps", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) element;
            RefugeeCampRecord camp = new RefugeeCampRecord(tag.getString("group"),
                    new WorldObjectId(tag.getString("community")), new WorldObjectId(tag.getString("site")),
                    tag.getString("dimension"), BlockPos.of(tag.getLong("anchor")),
                    new SemanticSlotKey(tag.getString("slotObject"), tag.getString("slotModule"), tag.getString("slotId")));
            values.put(camp.populationGroupId(), camp);
        }
        return values;
    }

    public static RefugeeAnchorPermitLedger readPermits(CompoundTag root) {
        Map<String, RefugeeAnchorPermit> values = new LinkedHashMap<>();
        for (Tag element : root.getList("refugeeAnchorPermits", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) element;
            RefugeeAnchorPermit permit = new RefugeeAnchorPermit(tag.getString("id"), tag.getUUID("player"),
                    new WorldObjectId(tag.getString("community")),
                    new io.farfrontier.palemirror.domain.StoryAudienceId(tag.getString("audience")),
                    tag.getLong("expires"), tag.getBoolean("consumed")); values.put(permit.id(), permit);
        }
        return new RefugeeAnchorPermitLedger(values);
    }
}
