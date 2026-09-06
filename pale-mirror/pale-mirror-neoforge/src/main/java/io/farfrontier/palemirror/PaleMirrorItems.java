package io.farfrontier.palemirror;

import io.farfrontier.palemirror.internal.presentation.RegionalLedgerItem;
import io.farfrontier.palemirror.internal.presentation.RefugeeAnchorItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PaleMirrorItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PaleMirrorMod.MOD_ID);
    public static final DeferredItem<RegionalLedgerItem> REGIONAL_LEDGER = ITEMS.register("regional_ledger",
            () -> new RegionalLedgerItem(new net.minecraft.world.item.Item.Properties().stacksTo(1)));
    public static final DeferredItem<RefugeeAnchorItem> REFUGEE_ANCHOR = ITEMS.register("refugee_anchor",
            () -> new RefugeeAnchorItem(new net.minecraft.world.item.Item.Properties()));

    private PaleMirrorItems() { }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
