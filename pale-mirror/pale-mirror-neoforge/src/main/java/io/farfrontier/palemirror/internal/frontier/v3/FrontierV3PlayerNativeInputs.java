package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;
import java.util.function.Supplier;

/** Transient ordinary-player input continuations, never restart replay or a domain action queue.
 * Existing geometry/physical owners prepare the exact writes; Minecraft alone applies the action.
 * A newer input, changed item/body/world/target or disconnect cancels an unattempted continuation. */
public final class FrontierV3PlayerNativeInputs {
    private static final Map<ServerPlayer, Pending<?>> PENDING = new LinkedHashMap<>();
    private static final ArrayDeque<Pending<?>> CANCELLED = new ArrayDeque<>();
    private static final int MAX_PENDING=32;
    private FrontierV3PlayerNativeInputs() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<?,?> runtime) {
        for (var retired:List.copyOf(CANCELLED)) if(retired.level==level) { CANCELLED.remove(retired);retired.finish(); }
        for(var input:List.copyOf(PENDING.values()).stream().filter(p -> p.level==level && p.runtime==runtime).limit(8).toList()) input.attempt();
    }
    static void forget(net.minecraft.server.MinecraftServer server) {
        PENDING.entrySet().removeIf(e -> e.getValue().level.getServer()==server);
        CANCELLED.removeIf(p -> p.level.getServer()==server);
    }
    private static void cancel(ServerPlayer player) {
        var previous=PENDING.remove(player);
        if(previous!=null) CANCELLED.add(previous);
    }

    public static boolean destroy(ServerPlayer player, ServerLevel level, BlockPos position, Supplier<Boolean> action) {
        var replacement = level.getFluidState(position).createLegacyBlock();
        return execute(player, level, InteractionHand.MAIN_HAND,
                List.of(new FrontierV3ManagedBlockWrites.PlannedWrite(position,replacement)), action, false);
    }
    private static void place(BlockPlaceContext context, BlockState placement, Runnable action, Runnable rejected) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)
                || placement == null) { action.run(); return; }
        var writes = new ArrayList<FrontierV3ManagedBlockWrites.PlannedWrite>(); var position = context.getClickedPos();
        writes.add(new FrontierV3ManagedBlockWrites.PlannedWrite(position,placement));
        if (placement.getBlock() instanceof DoorBlock || placement.getBlock() instanceof DoublePlantBlock)
            writes.add(new FrontierV3ManagedBlockWrites.PlannedWrite(position.above(),placement.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER)));
        if (placement.getBlock() instanceof BedBlock)
            writes.add(new FrontierV3ManagedBlockWrites.PlannedWrite(position.relative(placement.getValue(BlockStateProperties.HORIZONTAL_FACING)),
                    placement.setValue(BlockStateProperties.BED_PART,BedPart.HEAD)));
        execute(player,level,context.getHand(),writes,() -> {action.run();return true;},false,rejected);
    }
    static void usePacket(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit,
            Runnable action, Runnable rejected) {
        cancel(player);
        var position=hit.getBlockPos(); var block=level.getBlockState(position);
        if (!(block.getBlock() instanceof DoorBlock || block.getBlock() instanceof FenceGateBlock || block.getBlock() instanceof TrapDoorBlock)) {
            // Resume the WHOLE packet handler, including Minecraft's prediction ack,
            // NeoForge events/rollback, vanilla item use and authoritative updates.
            if (player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem item
                    && (player.isSecondaryUseActive() || block.getMenuProvider(level,position)==null)) {
                var context=item.updatePlacementContext(new BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(player,hand,hit)));
                if (context!=null && context.canPlace()) { place(context,
                        ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3BlockItemAccessor)item).frontierV3$placementState(context),action,rejected); return; }
            }
            action.run(); return;
        }
        if (block.getBlock() instanceof DoorBlock && !player.isSecondaryUseActive()) {
            boolean open=!block.getValue(DoorBlock.OPEN);
            var states=FrontierV3PassageBlocks.linkedDoorStates(level,position,open);
            if (!states.isEmpty()) {
                var writes=states.entrySet().stream().map(e -> new FrontierV3ManagedBlockWrites.PlannedWrite(e.getKey(),e.getValue())).toList();
                execute(player,level,hand,writes,() -> {
                    action.run();
                    // Native use/events still decide whether the click succeeded. A matching
                    // paired leaf follows that confirmed operation under the same geometry fence.
                    if (level.getBlockState(position).equals(states.get(position)))
                        FrontierV3PassageBlocks.completeLinkedDoors(level,player,states);
                    return true;
                },false,rejected); return;
            }
        }
        var writes=new ArrayList<FrontierV3ManagedBlockWrites.PlannedWrite>();
        writes.add(new FrontierV3ManagedBlockWrites.PlannedWrite(position,
                FrontierV3PassageBlocks.playerTransition(block,player.getDirection())));
        if (block.getBlock() instanceof DoorBlock) {
            var other=position.relative(block.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER
                    ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.DOWN);
            var state=level.getBlockState(other);
            if (state.is(block.getBlock())) writes.add(new FrontierV3ManagedBlockWrites.PlannedWrite(other,state.setValue(BlockStateProperties.OPEN,!block.getValue(BlockStateProperties.OPEN))));
        }
        execute(player,level,hand,writes,() -> {action.run();return true;},false,rejected);
    }
    private static <T> T execute(ServerPlayer player,ServerLevel level,InteractionHand hand,
            List<FrontierV3ManagedBlockWrites.PlannedWrite> writes,Supplier<T> action,T waiting) {
        return execute(player,level,hand,writes,action,waiting,() -> { });
    }
    private static <T> T execute(ServerPlayer player,ServerLevel level,InteractionHand hand,
            List<FrontierV3ManagedBlockWrites.PlannedWrite> writes,Supplier<T> action,T waiting,Runnable rejected) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return action.get();
        var runtime=FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime==null || runtime.status().kind()!=FrontierV3RuntimeStatus.Kind.ACTIVE) return action.get();
        cancel(player);
        if (!FrontierV3ManagedBlockWrites.defersPlayerInput(level,runtime,writes)) return action.get();
        if (PENDING.size()+CANCELLED.size()>=MAX_PENDING) { rejected.run();return waiting; }
        var input=new Pending<>(player,level,hand,writes,action,waiting,rejected,runtime);
        PENDING.put(player,input);
        return waiting; // Ordinary persistence turn owns preparation; never force the WAL from the packet thread.
    }
    private static final class Pending<T> {
        final ServerPlayer player; final ServerLevel level; final InteractionHand hand;
        final List<FrontierV3ManagedBlockWrites.PlannedWrite> writes;
        final Map<BlockPos,BlockState> predecessors=new HashMap<>(); final ItemStack item,itemReference;
        final Supplier<T> action; final T waiting; final Runnable rejected; final FrontierV3ServerRuntime<io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState,?> runtime;
        final long expires; final net.minecraft.world.level.GameType gameMode; boolean finished;
        Pending(ServerPlayer player,ServerLevel level,InteractionHand hand,List<FrontierV3ManagedBlockWrites.PlannedWrite> writes,
                Supplier<T> action,T waiting,Runnable rejected,FrontierV3ServerRuntime<io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState,?> runtime) {
            this.player=player;this.level=level;this.hand=hand;this.writes=List.copyOf(writes);this.action=action;this.waiting=waiting;this.rejected=rejected;this.runtime=runtime;
            itemReference=player.getItemInHand(hand);item=itemReference.copy();expires=level.getGameTime()+200; gameMode=player.gameMode.getGameModeForPlayer();
            for (var write:writes) predecessors.put(write.position(),level.getBlockState(write.position()));
        }
        T attempt() {
            if (finished || PENDING.get(player)!=this) return waiting;
            if (!valid()) { finish();return waiting; }
            if (!FrontierV3ManagedBlockWrites.preparePlannedWrites(level,runtime,writes,this::attempt)) return waiting;
            // Preparation may synchronously deliver a durable continuation. It must
            // not let this outer stack execute an already-consumed input again.
            if (finished || PENDING.get(player)!=this) return waiting;
            if (!valid()) { finish();return waiting; }
            // Remove BEFORE entering Minecraft: nested native interactions are distinct inputs,
            // never permission to invoke this action twice.
            finished=true;PENDING.remove(player,this);
            try { return action.get(); }
            finally {
                FrontierV3ManagedBlockWrites.finishPlannedWrites(level,runtime,writes);
                for (var write:writes) player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level,write.position()));
                player.containerMenu.broadcastChanges();
            }
        }
        boolean valid() {
            return runtime.status().kind()==FrontierV3RuntimeStatus.Kind.ACTIVE && !player.isRemoved() && player.isAlive()
                    && player.serverLevel()==level && !player.isSpectator() && level.getGameTime()<=expires
                    && player.gameMode.getGameModeForPlayer()==gameMode && player.getItemInHand(hand)==itemReference && ItemStack.matches(item,player.getItemInHand(hand))
                    && predecessors.entrySet().stream().allMatch(e -> FrontierV3LoadedWorld.ready(level,e.getKey())
                        && level.getBlockState(e.getKey()).equals(e.getValue()) && level.mayInteract(player,e.getKey())
                        && player.canInteractWithBlock(e.getKey(),1.0));
        }
        void finish() {
            if(finished)return;
            finished=true;PENDING.remove(player,this);
            try { FrontierV3ManagedBlockWrites.finishPlannedWrites(level,runtime,writes); }
            finally {
                rejected.run();
                if (!player.isRemoved() && player.serverLevel()==level && player.connection.isAcceptingMessages())
                    for (var write:writes) player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level,write.position()));
            }
        }
    }
}
