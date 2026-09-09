package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only pilot access to exact currently admitted vanilla tickets. */
@Mixin(DistanceManager.class)
public interface FrontierV3PilotDistanceManagerAccessor {
    @Accessor("tickets")
    Long2ObjectMap<SortedArraySet<Ticket<?>>> paleMirror$tickets();
}
