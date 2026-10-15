package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Native mutation entry points; lifecycle owns runtime lookup, registered owners own their policy. */
public final class FrontierV3BlockMutationBridge {
    private FrontierV3BlockMutationBridge() { }

    /** Native destroyBlock emits drops before setBlock; admit the geometry fence before those effects. */
    public static boolean prepareDestruction(ServerLevel level, BlockPos position, BlockState replacement) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return true;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        return runtime == null || FrontierV3NavigationObservation.blockWrite(level, runtime, position, replacement, false);
    }

    /** Routes prepare/confirmed phases; each declared owner classifies its own external change. */
    public static boolean observe(ServerLevel level, BlockPos position, BlockState replacement, boolean committed) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return true;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        return runtime == null || FrontierV3ManagedBlockWrites.observe(level, runtime, position, replacement, committed);
    }
}
