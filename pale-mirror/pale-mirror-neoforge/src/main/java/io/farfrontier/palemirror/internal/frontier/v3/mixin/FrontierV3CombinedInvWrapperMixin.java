package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ItemHandlerParts;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.spongepowered.asm.mixin.*;
import java.util.List;

@Mixin(value=CombinedInvWrapper.class,remap=false)
abstract class FrontierV3CombinedInvWrapperMixin implements FrontierV3ItemHandlerParts {
    @Shadow @Final protected IItemHandlerModifiable[] itemHandler;
    @Override public List<IItemHandlerModifiable> frontierV3$parts() { return List.of(itemHandler); }
}
