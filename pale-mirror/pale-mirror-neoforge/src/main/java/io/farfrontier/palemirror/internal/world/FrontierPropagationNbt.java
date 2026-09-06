package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.FrontierAdaptation;
import io.farfrontier.palemirror.frontier.FrontierDamageKind;
import io.farfrontier.palemirror.frontier.FrontierLatentColony;
import io.farfrontier.palemirror.frontier.FrontierPoint;
import io.farfrontier.palemirror.frontier.FrontierPropagationRun;
import io.farfrontier.palemirror.frontier.FrontierStateHydration;
import io.farfrontier.palemirror.frontier.FrontierWorldState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** NBT edge adapter for physical propagation, deposited colonies, and response memory. */
final class FrontierPropagationNbt {
    record Read(List<FrontierStateHydration.PropagationRunState> runs, List<FrontierStateHydration.LatentColonyState> colonies,
                FrontierStateHydration.AdaptationState adaptations) { }
    private FrontierPropagationNbt() { }

    static void write(CompoundTag tag, FrontierWorldState state) {
        ListTag runs = new ListTag();
        state.propagationRuns().forEach(value -> {
            CompoundTag run = new CompoundTag();
            run.putString("id", value.id()); run.putString("hive", value.hiveId()); run.putString("source", value.sourceOrganId());
            run.putString("bioform", value.bioformId()); point(run, "origin", value.origin()); point(run, "target", value.target());
            point(run, "position", value.position()); run.putString("state", value.state().name()); run.putLong("startedDay", value.startedDay());
            run.putInt("transitDays", value.transitDays()); run.putInt("transitProgress", value.transitProgress());
            if (value.latentColonyId() != null) run.putString("colony", value.latentColonyId());
            run.putLong("finishedDay", value.finishedDay()); run.putLong("revision", value.revision()); runs.add(run);
        });
        tag.put("propagationRuns", runs); tag.putString("propagationRunDigest", digest(runs, FrontierPropagationNbt::runLine));
        ListTag colonies = new ListTag();
        state.latentColonies().forEach(value -> {
            CompoundTag colony = new CompoundTag();
            colony.putString("id", value.id()); colony.putString("hive", value.hiveId()); colony.putString("source", value.sourceOrganId());
            point(colony, "position", value.position()); colony.putLong("depositedDay", value.depositedDay()); colony.putLong("propagules", value.propagules());
            colony.putLong("strength", value.strength()); colony.putBoolean("cleared", value.cleared()); colony.putLong("revision", value.revision()); colonies.add(colony);
        });
        tag.put("latentColonies", colonies); tag.putString("latentColonyDigest", digest(colonies, FrontierPropagationNbt::colonyLine));
        CompoundTag adaptations = new CompoundTag();
        state.adaptations().forEach((kind, level) -> adaptations.putInt(kind.name(), level));
        CompoundTag memory = new CompoundTag();
        state.adaptationMemory().forEach((kind, value) -> memory.putLong(kind.name(), value));
        adaptations.put("damageMemory", memory); tag.put("adaptations", adaptations);
        tag.putString("adaptationDigest", adaptationDigest(state.adaptations(), state.adaptationMemory()));
    }

    static Read read(CompoundTag tag) {
        List<FrontierStateHydration.PropagationRunState> runs = new ArrayList<>();
        ListTag runTags = tag.getList("propagationRuns", Tag.TAG_COMPOUND);
        for (Tag value : runTags) {
            CompoundTag run = (CompoundTag) value;
            try {
                runs.add(new FrontierStateHydration.PropagationRunState(run.getString("id"), run.getString("hive"), run.getString("source"),
                        run.getString("bioform"), point(run, "origin"), point(run, "target"), point(run, "position"),
                        FrontierPropagationRun.State.valueOf(run.getString("state")), run.getLong("startedDay"), run.getInt("transitDays"),
                        run.getInt("transitProgress"), run.contains("colony", Tag.TAG_STRING) ? run.getString("colony") : null,
                        run.getLong("finishedDay"), run.getLong("revision")));
            } catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid Frontier propagation", invalid); }
        }
        if (!tag.getString("propagationRunDigest").equals(digest(runTags, FrontierPropagationNbt::runLine))) {
            throw new IllegalStateException("incomplete or corrupt Frontier propagation runs");
        }
        List<FrontierStateHydration.LatentColonyState> colonies = new ArrayList<>();
        ListTag colonyTags = tag.getList("latentColonies", Tag.TAG_COMPOUND);
        for (Tag value : colonyTags) {
            CompoundTag colony = (CompoundTag) value;
            colonies.add(new FrontierStateHydration.LatentColonyState(colony.getString("id"), colony.getString("hive"),
                    colony.getString("source"), point(colony, "position"), colony.getLong("depositedDay"), colony.getLong("propagules"),
                    colony.getLong("strength"), colony.getBoolean("cleared"), colony.getLong("revision")));
        }
        if (!tag.getString("latentColonyDigest").equals(digest(colonyTags, FrontierPropagationNbt::colonyLine))) {
            throw new IllegalStateException("incomplete or corrupt Frontier latent colonies");
        }
        EnumMap<FrontierAdaptation, Integer> levels = new EnumMap<>(FrontierAdaptation.class);
        EnumMap<FrontierDamageKind, Long> memory = new EnumMap<>(FrontierDamageKind.class);
        CompoundTag adaptations = tag.getCompound("adaptations");
        for (FrontierAdaptation kind : FrontierAdaptation.values()) if (adaptations.contains(kind.name(), Tag.TAG_INT)) levels.put(kind, adaptations.getInt(kind.name()));
        CompoundTag memoryTag = adaptations.getCompound("damageMemory");
        for (FrontierDamageKind kind : FrontierDamageKind.values()) if (memoryTag.contains(kind.name(), Tag.TAG_LONG)) memory.put(kind, memoryTag.getLong(kind.name()));
        if (!tag.getString("adaptationDigest").equals(adaptationDigest(levels, memory))) {
            throw new IllegalStateException("incomplete or corrupt Frontier adaptations");
        }
        return new Read(List.copyOf(runs), List.copyOf(colonies), new FrontierStateHydration.AdaptationState(levels, memory));
    }

    private static void point(CompoundTag tag, String key, FrontierPoint point) { tag.putInt(key + "X", point.x()); tag.putInt(key + "Z", point.z()); }
    private static FrontierPoint point(CompoundTag tag, String key) { return new FrontierPoint(tag.getInt(key + "X"), tag.getInt(key + "Z")); }
    private static String runLine(CompoundTag value) { return value.getString("id") + ":" + value.getString("hive") + ":" + value.getString("source")
            + ":" + value.getString("bioform") + ":" + value.getInt("originX") + ":" + value.getInt("originZ") + ":"
            + value.getInt("targetX") + ":" + value.getInt("targetZ") + ":" + value.getInt("positionX") + ":" + value.getInt("positionZ")
            + ":" + value.getString("state") + ":" + value.getLong("startedDay") + ":" + value.getInt("transitDays") + ":"
            + value.getInt("transitProgress") + ":" + value.getString("colony") + ":" + value.getLong("finishedDay") + ":" + value.getLong("revision"); }
    private static String colonyLine(CompoundTag value) { return value.getString("id") + ":" + value.getString("hive") + ":" + value.getString("source")
            + ":" + value.getInt("positionX") + ":" + value.getInt("positionZ") + ":" + value.getLong("depositedDay") + ":"
            + value.getLong("propagules") + ":" + value.getLong("strength") + ":" + value.getBoolean("cleared") + ":" + value.getLong("revision"); }
    private static String adaptationDigest(Map<FrontierAdaptation, Integer> levels, Map<FrontierDamageKind, Long> memory) {
        StringBuilder line = new StringBuilder();
        for (FrontierAdaptation kind : FrontierAdaptation.values()) line.append(kind.name()).append(':').append(levels.getOrDefault(kind, 0)).append(';');
        line.append('|'); for (FrontierDamageKind kind : FrontierDamageKind.values()) line.append(kind.name()).append(':').append(memory.getOrDefault(kind, 0L)).append(';');
        return digest(line.toString());
    }
    private static String digest(ListTag values, java.util.function.Function<CompoundTag, String> line) {
        List<String> ordered = new ArrayList<>(); for (Tag value : values) ordered.add(line.apply((CompoundTag) value));
        ordered.sort(String::compareTo);
        return digest(String.join("\n", ordered));
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }
}
