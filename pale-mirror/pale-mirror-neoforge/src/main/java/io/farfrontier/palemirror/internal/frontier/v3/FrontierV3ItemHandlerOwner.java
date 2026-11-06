package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.StorageBlockKind;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Optional native provider declares its own leaf inventory, never a neighbour's combined view. */
public interface FrontierV3ItemHandlerOwner {
    IItemHandlerModifiable frontierV3$inventory();
    StorageBlockKind frontierV3$storageKind();
}
