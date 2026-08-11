package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Exact persistence for PM-owned vanilla minecart corridor jobs. */
final class VanillaMinecartRouteCodec {
    private VanillaMinecartRouteCodec() { }

    static void write(CompoundTag root, Map<String, VanillaMinecartRouteRecord> records) {
        ListTag values = new ListTag();
        records.values().forEach(record -> {
            CompoundTag value = new CompoundTag();
            value.putString("region", record.regionId());
            value.putString("dimension", record.dimensionId());
            value.putString("route", record.routeId());
            value.putLong("start", record.start().asLong());
            value.putLong("target", record.target().asLong());
            value.putString("status", record.status().name());
            value.putString("diagnostic", record.diagnostic());
            value.putInt("verificationCursor", record.verificationCursor());
            value.putString("cartLease", record.cartLeaseId());
            value.putBoolean("cartLeaseDispatched", record.cartLeaseDispatched());
            if (record.representativeCartId() != null) value.putUUID("cart", record.representativeCartId());
            if (record.representativeCargoId() != null) value.putUUID("cargoDisplay", record.representativeCargoId());
            value.putDouble("cartProgress", record.cartProgress());
            value.putBoolean("cartForward", record.cartForward());
            ListTag cells = new ListTag();
            record.cells().values().forEach(cell -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("position", cell.position().asLong());
                entry.putString("baseline", cell.baselineState());
                entry.putString("lastApplied", cell.lastAppliedState());
                entry.putBoolean("conflicted", cell.conflicted());
                cells.add(entry);
            });
            value.put("cells", cells);
            ListTag completed = new ListTag();
            record.completedSegments().stream().sorted().forEach(index -> completed.add(net.minecraft.nbt.IntTag.valueOf(index)));
            value.put("completed", completed);
            long[] damaged = record.damagedCriticalCells().stream().mapToLong(Long::longValue).toArray();
            value.putLongArray("damagedCriticalCells", damaged);
            values.add(value);
        });
        root.put("vanillaMinecartRoutes", values);
    }

    static Map<String, VanillaMinecartRouteRecord> read(CompoundTag root) {
        Map<String, VanillaMinecartRouteRecord> result = new LinkedHashMap<>();
        for (Tag raw : root.getList("vanillaMinecartRoutes", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            Map<Long, VanillaMinecartMutableCell> cells = new LinkedHashMap<>();
            for (Tag cellRaw : value.getList("cells", Tag.TAG_COMPOUND)) {
                CompoundTag cell = (CompoundTag) cellRaw;
                BlockPos position = BlockPos.of(cell.getLong("position"));
                cells.put(position.asLong(), new VanillaMinecartMutableCell(position, cell.getString("baseline"),
                        cell.getString("lastApplied"), cell.getBoolean("conflicted")));
            }
            Set<Integer> completed = new LinkedHashSet<>();
            for (Tag index : value.getList("completed", Tag.TAG_INT)) completed.add(((net.minecraft.nbt.IntTag) index).getAsInt());
            UUID cart = value.hasUUID("cart") ? value.getUUID("cart") : null;
            UUID cargo = value.hasUUID("cargoDisplay") ? value.getUUID("cargoDisplay") : null;
            Set<Long> damaged = new LinkedHashSet<>();
            for (long position : value.getLongArray("damagedCriticalCells")) damaged.add(position);
            VanillaMinecartRouteRecord record = new VanillaMinecartRouteRecord(value.getString("region"),
                    value.getString("dimension"), value.getString("route"), BlockPos.of(value.getLong("start")),
                    BlockPos.of(value.getLong("target")), VanillaMinecartRouteStatus.valueOf(value.getString("status")),
                    value.getString("diagnostic"), cells, completed, damaged, value.getInt("verificationCursor"),
                    value.getString("cartLease"), value.getBoolean("cartLeaseDispatched"), cart, cargo,
                    value.contains("cartProgress", Tag.TAG_DOUBLE) ? value.getDouble("cartProgress") : 0D,
                    !value.contains("cartForward", Tag.TAG_BYTE) || value.getBoolean("cartForward"));
            result.put(record.regionId(), record);
        }
        return result;
    }
}
