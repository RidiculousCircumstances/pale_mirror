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
    private int sent, sequence, observations;
    private final String observedProperty;
    private final java.util.List<String> expected;
    private final int observeTicks;
    private final java.util.List<String> values=new java.util.ArrayList<>();
    private long lastTick = Long.MIN_VALUE;
    FrontierV3PilotItemActions(JsonObject action) {
        item = ResourceLocation.parse(action.get("item").getAsString()); uses = action.get("uses").getAsInt();
        observedProperty=action.has("observeProperty")?action.get("observeProperty").getAsString():null;
        observeTicks=action.has("observeTicks")?action.get("observeTicks").getAsInt():0;
        expected=action.has("expectedValues")?action.getAsJsonArray("expectedValues").asList().stream().map(v -> v.getAsString()).toList():java.util.List.of();
    }
    boolean use(Minecraft minecraft, BlockPos target) {
        if(sent>=uses && observedProperty!=null) {
            sample(minecraft,target);observations++;
            boolean acknowledged=!((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3PredictionStateAccessor)
                    ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ClientPredictionAccessor)minecraft.level).frontierV3$predictionHandler()).frontierV3$knownServerStates().containsKey(target.asLong());
            if(!acknowledged || observations<observeTicks)return false;
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_PILOT_BLOCK_USE target={} sequence={} acknowledged={} property={} values={}",
                    target,sequence,true,observedProperty,values);
            if(!values.equals(expected))throw new IllegalStateException("ordinary use has unexpected predicted/confirmed transitions: "+values+" expected="+expected);
            return true;
        }
        long tick = minecraft.level.getGameTime();
        if (lastTick != Long.MIN_VALUE && tick - lastTick < 4) return false;
        select(minecraft, item);
        if(observedProperty!=null)sample(minecraft,target);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(target).add(0, 0.5D, 0), Direction.UP, target, false));
        lastTick = tick;sequence=((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ClientPredictionAccessor)minecraft.level).frontierV3$predictionHandler().currentSequence();
        if(observedProperty!=null)sample(minecraft,target);
        return ++sent >= uses && observedProperty==null;
    }
    private void sample(Minecraft minecraft,BlockPos target) {
        var state=minecraft.level.getBlockState(target);
        var property=state.getBlock().getStateDefinition().getProperty(observedProperty);
        if(property==null)throw new IllegalArgumentException("observed block lacks property "+observedProperty);
        String value=propertyValue(state,property);
        if(values.isEmpty()||!values.getLast().equals(value))values.add(value);
        if(values.size()>16)throw new IllegalStateException("ordinary block use oscillates: "+values);
    }
    private static <T extends Comparable<T>> String propertyValue(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property){return property.getName(state.getValue(property));}

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
            if (stack.isEmpty() && item.equals(ResourceLocation.withDefaultNamespace("air"))
                    || !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                minecraft.player.getInventory().selected = index; return;
            }
        }
        throw new IllegalStateException("ordinary player hotbar lacks item " + item);
    }
}
