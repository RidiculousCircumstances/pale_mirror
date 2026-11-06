package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ItemHandlerParts;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.*;
import java.util.List;

@Pseudo
@Mixin(targets="com.minecolonies.api.inventory.api.CombinedItemHandler", remap=false)
abstract class FrontierV3CombinedItemHandlerMixin implements FrontierV3ItemHandlerParts {
    @Shadow @Final private IItemHandlerModifiable[] handlers;
    @Override public List<IItemHandlerModifiable> frontierV3$parts() { return List.of(handlers); }
}
