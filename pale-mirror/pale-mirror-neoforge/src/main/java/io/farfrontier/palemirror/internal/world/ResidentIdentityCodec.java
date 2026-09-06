package io.farfrontier.palemirror.internal.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

final class ResidentIdentityCodec {
    private ResidentIdentityCodec() { }
    static void write(CompoundTag root, ResidentIdentityLedger ledger) {
        ListTag values = new ListTag();
        ledger.retiredIds().stream().sorted().forEach(value -> values.add(StringTag.valueOf(value)));
        root.put("retiredResidentIdentities", values);
    }
    static ResidentIdentityLedger read(CompoundTag root) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (Tag value : root.getList("retiredResidentIdentities", Tag.TAG_STRING)) values.add(value.getAsString());
        return new ResidentIdentityLedger(values);
    }
}
