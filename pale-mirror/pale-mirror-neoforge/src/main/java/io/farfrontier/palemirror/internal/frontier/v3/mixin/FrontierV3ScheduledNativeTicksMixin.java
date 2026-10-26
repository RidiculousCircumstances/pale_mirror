package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ScheduledNativeTicks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.*;
import org.spongepowered.asm.mixin.*;

/** Retains vanilla tick identity, priority, deadline, deduplication and chunk-save ownership. */
@Mixin(LevelTicks.class)
abstract class FrontierV3ScheduledNativeTicksMixin<T> {
    @Shadow @Final private Queue<ScheduledTick<T>> toRunThisTick;
    @Shadow @Final private Set<ScheduledTick<?>> toRunThisTickSet;
    @Shadow @Final private List<ScheduledTick<T>> alreadyRunThisTick;
    @Shadow @Final private Long2ObjectMap<LevelChunkTicks<T>> allContainers;
    @Shadow public abstract void schedule(ScheduledTick<T> tick);

    @WrapMethod(method = "runCollectedTicks")
    private void frontierV3$boundedNativeTicks(BiConsumer<BlockPos, T> action, Operation<Void> original) {
        if (!FrontierV3ScheduledNativeTicks.active()) { original.call(action); return; }
        while (!toRunThisTick.isEmpty() && FrontierV3ScheduledNativeTicks.available()) {
            var tick = toRunThisTick.remove();
            if (!toRunThisTickSet.isEmpty()) toRunThisTickSet.remove(tick);
            alreadyRunThisTick.add(tick);
            FrontierV3ScheduledNativeTicks.execute(() -> action.accept(tick.pos(), tick.type()));
        }
        FrontierV3ScheduledNativeTicks.restore(toRunThisTick, allContainers, this::schedule);
    }
}
