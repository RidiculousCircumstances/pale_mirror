package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import net.minecraft.nbt.CompoundTag;
import java.util.Objects;

/** Exact before-removal authority. It never grants identity to a replacement block. */
record FrontierV3ContainerRemovalWitness(SubjectId container, SubjectId owner, BlockPosition position,
                                        long custodyEpoch, long recoveryEpoch) {
    FrontierV3ContainerRemovalWitness {
        Objects.requireNonNull(container); Objects.requireNonNull(owner); Objects.requireNonNull(position);
        if (custodyEpoch < 1 || recoveryEpoch < 1) throw new IllegalArgumentException("removal lacks exact authority epochs");
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putString("container", container.value()); tag.putString("owner", owner.value());
        tag.putInt("x", position.x()); tag.putInt("y", position.y()); tag.putInt("z", position.z());
        tag.putLong("custodyEpoch", custodyEpoch); tag.putLong("recoveryEpoch", recoveryEpoch); return tag;
    }
    static FrontierV3ContainerRemovalWitness read(CompoundTag tag) {
        return new FrontierV3ContainerRemovalWitness(new SubjectId(tag.getString("container")), new SubjectId(tag.getString("owner")),
                new BlockPosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), tag.getLong("custodyEpoch"), tag.getLong("recoveryEpoch"));
    }
}
