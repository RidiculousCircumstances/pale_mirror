package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.Optional;

/** Shared exact-item identity and custody tags, independent of transport. */
final class FrontierV3ExactItemPresentation {
    static final String CONTAINER_ID_KEY = "pale_mirror_frontier_v3_container";
    static final String ITEM_ID_KEY = "pale_mirror_frontier_v3_item";
    static final String PENDING_INGRESS_KEY = "pale_mirror_frontier_v3_pending_ingress";
    static final String WORLD_CARRIER_ID_KEY = "pale_mirror_frontier_v3_world_carrier";
    private FrontierV3ExactItemPresentation() { }

    record StoreTarget(net.minecraft.core.BlockPos position, SubjectId containerId) { }
    static net.minecraft.world.level.block.entity.ChestBlockEntity activeChest(net.minecraft.server.level.ServerLevel level, StoreTarget target) {
        return FrontierV3ContainerSurfaceExecutor.activeChest(level, target.position(), target.containerId());
    }

    static ItemStack materializedStack(ExactItemStack item) {
        Item minecraftItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.itemKind()));
        if (minecraftItem == Items.AIR) throw new IllegalStateException("unknown Minecraft item for exact hand-off: " + item.itemKind());
        ItemStack stack = new ItemStack(minecraftItem, item.count());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(ITEM_ID_KEY, item.id().value()));
        return stack;
    }

    static boolean exactMatch(ItemStack actual, ExactItemStack expected) {
        if (actual.getCount() != expected.count() || !BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(expected.itemKind())) return false;
        CustomData data = actual.get(DataComponents.CUSTOM_DATA);
        return data != null && expected.id().value().equals(data.copyTag().getString(ITEM_ID_KEY));
    }

    static boolean exactOneUnitDecrement(ItemStack actual, ExactItemStack source) {
        if (source.count() == 1) return actual.isEmpty();
        return exactMatch(actual, new ExactItemStack(source.id(), source.economicOwnerId(), source.itemKind(), source.count() - 1, source.custody()));
    }

    static Optional<SubjectId> itemId(ItemStack actual) {
        CustomData data = actual.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        String value = data.copyTag().getString(ITEM_ID_KEY);
        if (value.isBlank()) return Optional.empty();
        try { return Optional.of(new SubjectId(value)); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    static void bindExactItemId(ItemStack stack, SubjectId itemId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(ITEM_ID_KEY, itemId.value()));
    }

    static void markPendingIngress(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(PENDING_INGRESS_KEY, true));
    }

    static boolean pendingIngress(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(PENDING_INGRESS_KEY);
    }

    static void clearPendingIngress(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(PENDING_INGRESS_KEY));
    }

    static void bindWorldCarrier(ItemStack stack, java.util.UUID carrierId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(WORLD_CARRIER_ID_KEY, carrierId));
    }

    static Optional<java.util.UUID> worldCarrierId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().hasUUID(WORLD_CARRIER_ID_KEY)) return Optional.empty();
        return Optional.of(data.copyTag().getUUID(WORLD_CARRIER_ID_KEY));
    }

    static boolean hasWorldCarrierDeclaration(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(WORLD_CARRIER_ID_KEY);
    }
}
