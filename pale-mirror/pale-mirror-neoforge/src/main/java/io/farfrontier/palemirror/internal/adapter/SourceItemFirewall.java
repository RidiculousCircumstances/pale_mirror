package io.farfrontier.palemirror.internal.adapter;

import java.util.Optional;

import net.minecraft.world.item.ItemStack;

/** Source-neutral event boundary for item policies owned by isolated adapters. */
public final class SourceItemFirewall {
    private SourceItemFirewall() { }

    public static Optional<BlockedSourceItem> classify(ItemStack stack) {
        return AdapterRegistry.sourceAdapters().stream()
                .map(adapter -> adapter.classifyExcludedItem(stack))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public static boolean blocks(ItemStack stack) {
        return classify(stack).isPresent();
    }
}
