package io.farfrontier.palemirror.internal.frontier.v3;

import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.List;

/** Ordered native composition, declared by its producer; slot contents cannot identify owners. */
public interface FrontierV3ItemHandlerParts {
    List<IItemHandlerModifiable> frontierV3$parts();
}
