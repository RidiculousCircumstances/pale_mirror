package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pre-effect member admission. The composed transaction owns the one captured outcome. */
record FrontierV3DepotClickWitness(SubjectId containerId, SubjectId accountId, SubjectId ownerId,
                                  long authorityEpoch, UUID playerId, UUID interactionId,
                                  List<FungiblePhysicalObservation.Stack> before, Optional<ReturnSource> returnSource) {
    record ReturnSource(SubjectId bindingId, SubjectId accountId, long epoch, int playerSlot,
                        String itemKind, int quantity) {
        ReturnSource {
            Objects.requireNonNull(bindingId); Objects.requireNonNull(accountId);
            if (epoch < 1 || playerSlot < 0 || playerSlot > 255 || quantity < 1 || quantity > 64
                    || itemKind == null) throw new IllegalArgumentException("invalid exact player return source");
        }
    }

    FrontierV3DepotClickWitness {
        Objects.requireNonNull(containerId, "click container");
        Objects.requireNonNull(accountId, "click account");
        Objects.requireNonNull(ownerId, "click owner");
        Objects.requireNonNull(playerId, "click player");
        Objects.requireNonNull(interactionId, "click id");
        before = List.copyOf(Objects.requireNonNull(before, "click predecessor"));
        Objects.requireNonNull(returnSource);
        if (authorityEpoch < 1 || before.size() > 54
                || !addressesMatch(containerId, before)) {
            throw new IllegalArgumentException("depot click witness must retain bounded exact container layout");
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("container", containerId.value()); tag.putString("account", accountId.value());
        tag.putString("owner", ownerId.value()); tag.putLong("epoch", authorityEpoch);
        tag.putUUID("player", playerId); tag.putUUID("interaction", interactionId);
        tag.put("before", stacks(before));
        returnSource.ifPresent(source -> {
            var value = new CompoundTag(); value.putString("binding", source.bindingId().value());
            value.putString("account", source.accountId().value()); value.putLong("epoch", source.epoch());
            value.putInt("slot", source.playerSlot()); value.putString("kind", source.itemKind()); value.putInt("quantity", source.quantity());
            tag.put("returnSource", value);
        });
        return tag;
    }

    static FrontierV3DepotClickWitness read(CompoundTag tag) {
        SubjectId container = new SubjectId(tag.getString("container"));
        return new FrontierV3DepotClickWitness(container, new SubjectId(tag.getString("account")),
                new SubjectId(tag.getString("owner")), tag.getLong("epoch"), tag.getUUID("player"),
                tag.getUUID("interaction"), readStacks(container, tag.getList("before", Tag.TAG_COMPOUND)),
                tag.contains("returnSource", Tag.TAG_COMPOUND)
                        ? Optional.of(readReturnSource(tag.getCompound("returnSource"))) : Optional.empty());
    }

    private static ReturnSource readReturnSource(CompoundTag tag) {
        return new ReturnSource(new SubjectId(tag.getString("binding")), new SubjectId(tag.getString("account")),
                tag.getLong("epoch"), tag.getInt("slot"), tag.getString("kind"), tag.getInt("quantity"));
    }

    private static ListTag stacks(List<FungiblePhysicalObservation.Stack> stacks) {
        ListTag values = new ListTag();
        for (var stack : stacks) {
            var slot = (PhysicalStackAddress.ContainerSlot) stack.address();
            CompoundTag tag = new CompoundTag(); tag.putInt("slot", slot.slot().slot());
            tag.putString("kind", stack.itemKind()); tag.putInt("quantity", stack.quantity());
            values.add(tag);
        }
        return values;
    }

    private static List<FungiblePhysicalObservation.Stack> readStacks(SubjectId container, ListTag tags) {
        java.util.ArrayList<FungiblePhysicalObservation.Stack> values = new java.util.ArrayList<>();
        for (int index = 0; index < tags.size(); index++) {
            CompoundTag tag = tags.getCompound(index);
            values.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(container, tag.getInt("slot"))),
                    tag.getString("kind"), tag.getInt("quantity")));
        }
        return List.copyOf(values);
    }

    private static boolean addressesMatch(SubjectId container, List<FungiblePhysicalObservation.Stack> stacks) {
        return stacks.stream().allMatch(stack -> stack.address() instanceof PhysicalStackAddress.ContainerSlot slot
                && slot.slot().containerId().equals(container))
                && stacks.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() == stacks.size();
    }
}
