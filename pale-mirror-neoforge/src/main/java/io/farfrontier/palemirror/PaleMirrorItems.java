package io.farfrontier.palemirror;

import io.farfrontier.palemirror.internal.presentation.RegionalLedgerItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PaleMirrorItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PaleMirrorMod.MOD_ID);
    public static final DeferredItem<RegionalLedgerItem> REGIONAL_LEDGER = ITEMS.register("regional_ledger",
            () -> new RegionalLedgerItem(new net.minecraft.world.item.Item.Properties().stacksTo(1)));

    private PaleMirrorItems() { }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
