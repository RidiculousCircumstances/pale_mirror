package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.FrontierHarvesterRun;
import io.farfrontier.palemirror.frontier.FrontierPoint;
import io.farfrontier.palemirror.frontier.FrontierStateHydration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** NBT edge adapter for the complete digest-checked harvester operation record. */
final class FrontierHarvesterRunNbt {
    private FrontierHarvesterRunNbt() { }

    static void write(CompoundTag tag, Iterable<FrontierHarvesterRun> runs) {
        ListTag values = new ListTag();
        runs.forEach(value -> {
            CompoundTag run = new CompoundTag();
            run.putString("id", value.id()); run.putString("hive", value.hiveId()); run.putString("source", value.sourceOrganId());
            run.putString("bioform", value.bioformId()); run.putInt("originX", value.origin().x()); run.putInt("originZ", value.origin().z());
            run.putInt("forageX", value.foragePosition().x()); run.putInt("forageZ", value.foragePosition().z());
            run.putInt("x", value.position().x()); run.putInt("z", value.position().z());
            if (value.receiverOrganId() != null) run.putString("receiver", value.receiverOrganId());
            run.putString("state", value.state().name()); run.putLong("startedDay", value.startedDay());
            run.putInt("outboundDays", value.outboundDays()); run.putInt("transitProgress", value.transitProgress());
            run.putInt("returnDays", value.returnDays()); run.putLong("cargo", value.cargo());
            run.putLong("geneticCargo", value.geneticCargo()); run.putLong("finishedDay", value.finishedDay());
            run.putLong("revision", value.revision()); values.add(run);
        });
        tag.put("harvesterRuns", values);
        tag.putString("harvesterRunDigest", FrontierStateCodec.harvesterRunDigest(runs));
    }

    static List<FrontierStateHydration.HarvesterRunState> read(CompoundTag tag) {
        List<FrontierStateHydration.HarvesterRunState> runs = new ArrayList<>();
        for (Tag value : tag.getList("harvesterRuns", Tag.TAG_COMPOUND)) {
            CompoundTag run = (CompoundTag) value;
            try {
                runs.add(new FrontierStateHydration.HarvesterRunState(run.getString("id"), run.getString("hive"),
                        run.getString("source"), run.getString("bioform"), new FrontierPoint(run.getInt("originX"), run.getInt("originZ")),
                        new FrontierPoint(run.getInt("forageX"), run.getInt("forageZ")), new FrontierPoint(run.getInt("x"), run.getInt("z")),
                        run.contains("receiver", Tag.TAG_STRING) ? run.getString("receiver") : null,
                        FrontierHarvesterRun.State.valueOf(run.getString("state")), run.getLong("startedDay"),
                        run.getInt("outboundDays"), run.getInt("transitProgress"), run.getInt("returnDays"), run.getLong("cargo"),
                        run.getLong("geneticCargo"), run.getLong("finishedDay"), run.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid Frontier harvester run", invalid);
            }
        }
        if (!tag.getString("harvesterRunDigest").equals(FrontierStateCodec.harvesterRunStateDigest(runs))) {
            throw new IllegalStateException("incomplete or corrupt Frontier harvester runs");
        }
        return List.copyOf(runs);
    }
}
