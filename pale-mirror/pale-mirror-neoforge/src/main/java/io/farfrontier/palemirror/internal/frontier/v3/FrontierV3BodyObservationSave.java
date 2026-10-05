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
    private FrontierV3BodyObservationSave() { }

    public static void observe(Entity entity, CompoundTag saved) {
        if (!(entity instanceof Mob) || !(entity.level() instanceof ServerLevel)
                || !entity.getPersistentData().contains(FrontierV3ActorCarrierComposition.ACTOR_KEY, Tag.TAG_STRING)) return;
        var observation = FrontierV3BodyObservation.captureForDeparture(entity);
        saved.put(KEY, encode(observation, entity.getX(), entity.getY(), entity.getZ()));
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
