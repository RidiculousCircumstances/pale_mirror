package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Ordinary client item packets only. No world mutation, simulated result or server authority. */
final class FrontierV3PilotItemActions {
    record Placement(boolean complete, boolean attempted) { }
    private final ResourceLocation item;
    private final int uses;
    private int sent;
    private long lastTick = Long.MIN_VALUE;
    FrontierV3PilotItemActions(JsonObject action) {
        item = ResourceLocation.parse(action.get("item").getAsString()); uses = action.get("uses").getAsInt();
    }
    boolean use(Minecraft minecraft, BlockPos target) {
        long tick = minecraft.level.getGameTime();
        if (lastTick != Long.MIN_VALUE && tick - lastTick < 4) return false;
        select(minecraft, item);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(target).add(0, 0.5D, 0), Direction.UP, target, false));
        lastTick = tick;
        return ++sent >= uses;
    }
    static Placement place(Minecraft minecraft, BlockPos target, ResourceLocation item, boolean attempted) {
        var expected = BuiltInRegistries.BLOCK.getOptional(item).orElseThrow(() -> new IllegalArgumentException("placement item is not a block " + item));
        if (minecraft.level.getBlockState(target).is(expected)) return new Placement(true, attempted);
        var support = target.below();
        if (minecraft.level.getBlockState(support).isAir()) throw new IllegalStateException("ordinary placement has no support at " + support);
        if (!attempted) {
            select(minecraft, item);
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5D, 0), Direction.UP, support, false));
        }
        return new Placement(false, true);
    }
    private static void select(Minecraft minecraft, ResourceLocation item) {
        for (int index = 0; index < 9; index++) {
            var stack = minecraft.player.getInventory().getItem(index);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                minecraft.player.getInventory().selected = index; return;
            }
        }
        throw new IllegalStateException("ordinary player hotbar lacks item " + item);
    }
}
