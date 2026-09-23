package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Final cargo observation. Evidence only: it neither grants COLD custody nor authorizes spawning. */
record FrontierV3CargoDeparture(SceneLeaseId leaseId, SubjectId cargoId, UUID entityId,
                                long sceneRevision, long authorityEpoch, BodyPosition body,
                                List<CompoundTag> inventory) {
    static final int SLOTS = 27;

    FrontierV3CargoDeparture {
        Objects.requireNonNull(leaseId); Objects.requireNonNull(cargoId); Objects.requireNonNull(entityId);
        Objects.requireNonNull(body); Objects.requireNonNull(inventory);
        if (sceneRevision < 0 || authorityEpoch < 1 || inventory.size() != SLOTS) {
            throw new IllegalArgumentException("invalid exact cargo departure");
        }
        inventory = inventory.stream().map(CompoundTag::copy).toList();
    }

    @Override public List<CompoundTag> inventory() {
        return inventory.stream().map(CompoundTag::copy).toList();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("lease", leaseId.value()); tag.putString("cargo", cargoId.value());
        tag.putUUID("entity", entityId); tag.putLong("revision", sceneRevision); tag.putLong("epoch", authorityEpoch);
        tag.putInt("x", body.x()); tag.putInt("y", body.y()); tag.putInt("z", body.z());
        var slots = new ListTag(); inventory.forEach(slot -> slots.add(slot.copy()));
        tag.put("inventory", slots);
        return tag;
    }

    static FrontierV3CargoDeparture load(CompoundTag tag) {
        if (!tag.contains("lease", Tag.TAG_STRING) || !tag.contains("cargo", Tag.TAG_STRING) || !tag.hasUUID("entity")
                || !tag.contains("revision", Tag.TAG_LONG) || !tag.contains("epoch", Tag.TAG_LONG)
                || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT) || !tag.contains("z", Tag.TAG_INT)
                || !tag.contains("inventory", Tag.TAG_LIST)) throw new IllegalStateException("incomplete cargo departure");
        var slots = tag.getList("inventory", Tag.TAG_COMPOUND);
        if (slots.size() != SLOTS) throw new IllegalStateException("invalid cargo departure inventory extent");
        return new FrontierV3CargoDeparture(new SceneLeaseId(tag.getString("lease")), new SubjectId(tag.getString("cargo")),
                tag.getUUID("entity"), tag.getLong("revision"), tag.getLong("epoch"),
                new BodyPosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                slots.stream().map(CompoundTag.class::cast).toList());
    }
}
