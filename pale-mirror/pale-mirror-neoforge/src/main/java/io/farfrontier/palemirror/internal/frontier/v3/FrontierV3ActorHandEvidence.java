package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.CustomData;
import java.util.Optional;
import java.util.Set;

/** Shared physical hand image. Capturing identity is not a resource transfer or a custody grant. */
final class FrontierV3ActorHandEvidence {
    private FrontierV3ActorHandEvidence() { }
    static boolean capturable(ItemStack actual) {
        if (actual.isEmpty()) return true;
        var expected = new ItemStack(actual.getItem(), actual.getCount());
        var id = FrontierV3ExactItemPresentation.itemId(actual);
        id.ifPresent(value -> CustomData.update(DataComponents.CUSTOM_DATA, expected,
                tag -> tag.putString(FrontierV3ExactItemPresentation.ITEM_ID_KEY, value.value())));
        return ItemStack.isSameItemSameComponents(actual, expected);
    }
    static Optional<FrontierV3ActorBodyDeparture.HandStack> observed(ItemStack actual) {
        if (!capturable(actual)) throw new IllegalArgumentException("unsupported actor hand components");
        return actual.isEmpty() ? Optional.empty() : Optional.of(new FrontierV3ActorBodyDeparture.HandStack(
                BuiltInRegistries.ITEM.getKey(actual.getItem()).toString(), actual.getCount(), FrontierV3ExactItemPresentation.itemId(actual)));
    }
    /** Vanilla serialized stack read, without registries or loading the entity/chunk into the world. */
    static Optional<FrontierV3ActorBodyDeparture.HandStack> saved(CompoundTag raw) {
        if (raw.isEmpty()) return Optional.empty();
        if (!raw.contains("id", Tag.TAG_STRING) || !raw.contains("count", Tag.TAG_INT))
            throw new IllegalArgumentException("incomplete serialized actor hand");
        Optional<SubjectId> id = Optional.empty();
        if (raw.contains("components")) {
            if (!raw.contains("components", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("invalid hand components");
            var components = raw.getCompound("components");
            if (!components.getAllKeys().equals(Set.of("minecraft:custom_data"))
                    || !components.contains("minecraft:custom_data", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("unsupported serialized actor hand components");
            var data = components.getCompound("minecraft:custom_data");
            if (!data.getAllKeys().equals(Set.of(FrontierV3ExactItemPresentation.ITEM_ID_KEY))
                    || !data.contains(FrontierV3ExactItemPresentation.ITEM_ID_KEY, Tag.TAG_STRING))
                throw new IllegalArgumentException("unsupported exact hand declaration");
            id = Optional.of(new SubjectId(data.getString(FrontierV3ExactItemPresentation.ITEM_ID_KEY)));
        }
        return Optional.of(new FrontierV3ActorBodyDeparture.HandStack(raw.getString("id"), raw.getInt("count"), id));
    }
}
