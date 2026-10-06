package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/** Entity-save witness of the same observation, bound to vanilla's exact serialized pose. */
public final class FrontierV3BodyObservationSave {
    static final String KEY = "pm_v3_body_observation";
    // Vanilla deserializes entity chunks off the server thread; join consumes
    // this witness on the server thread. Values never retain their weak key.
    private static final java.util.Map<Entity, LoadedPose> LOADED_POSES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private FrontierV3BodyObservationSave() { }

    public static void observe(Entity entity, CompoundTag saved) {
        if (!(entity instanceof Mob) || !(entity.level() instanceof ServerLevel)
                || !entity.getPersistentData().contains(FrontierV3ActorCarrierComposition.ACTOR_KEY, Tag.TAG_STRING)) return;
        var observation = FrontierV3BodyObservation.captureForDeparture(entity);
        saved.put(KEY, encode(observation, entity.getX(), entity.getY(), entity.getZ()));
    }

    /** Retain the existing save witness only for the exact loaded physical pose. */
    public static void observeLoad(Entity entity, CompoundTag saved) {
        LOADED_POSES.remove(entity);
        if (!(entity instanceof Mob) || !(entity.level() instanceof ServerLevel)) return;
        read(saved, entity.getX(), entity.getY(), entity.getZ()).ifPresent(position ->
                LOADED_POSES.put(entity, new LoadedPose(entity.getX(), entity.getY(), entity.getZ(), position)));
    }

    static BodyPosition returnedPosition(Entity entity) {
        var saved = LOADED_POSES.get(entity);
        if (saved != null && saved.matches(entity.getX(), entity.getY(), entity.getZ())) return saved.position();
        // A same-object live return has no deserialization witness. Observe its
        // current pose; neither path certifies current support or work arrival.
        LOADED_POSES.remove(entity);
        return FrontierV3BodyObservation.capture(entity).position();
    }

    record LoadedPose(double x, double y, double z, BodyPosition position) {
        boolean matches(double currentX, double currentY, double currentZ) {
            return Double.compare(x, currentX) == 0 && Double.compare(y, currentY) == 0 && Double.compare(z, currentZ) == 0;
        }
    }

    static CompoundTag encode(FrontierV3BodyObservation.Observation observation, double x, double y, double z) {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putInt("kind", observation.support().isPresent() ? 1 : 2);
        tag.putInt("bodyX", observation.position().x());
        tag.putInt("bodyY", observation.position().y());
        tag.putInt("bodyZ", observation.position().z());
        tag.putDouble("poseX", x); tag.putDouble("poseY", y); tag.putDouble("poseZ", z);
        return tag;
    }

    static Optional<BodyPosition> read(CompoundTag saved, double x, double y, double z) {
        if (!saved.contains(KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        var tag = saved.getCompound(KEY);
        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1
                || !tag.contains("kind", Tag.TAG_INT) || (tag.getInt("kind") != 1 && tag.getInt("kind") != 2)
                || !tag.contains("bodyX", Tag.TAG_INT) || !tag.contains("bodyY", Tag.TAG_INT)
                || !tag.contains("bodyZ", Tag.TAG_INT) || !tag.contains("poseX", Tag.TAG_DOUBLE)
                || !tag.contains("poseY", Tag.TAG_DOUBLE) || !tag.contains("poseZ", Tag.TAG_DOUBLE)
                || Double.compare(tag.getDouble("poseX"), x) != 0
                || Double.compare(tag.getDouble("poseY"), y) != 0
                || Double.compare(tag.getDouble("poseZ"), z) != 0) return Optional.empty();
        return Optional.of(new BodyPosition(tag.getInt("bodyX"), tag.getInt("bodyY"), tag.getInt("bodyZ")));
    }
}
