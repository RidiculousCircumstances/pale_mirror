package io.farfrontier.palemirror.internal.production;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Actual machine slots, persisted by Minecraft; not a second canonical stock ledger. */
public final class ProductionBenchBlockEntity extends BaseContainerBlockEntity {
    public static final int INPUT = 0, OUTPUT = 1, FUEL = 2, SLOT_COUNT = 3;
    public static final int MAINTENANCE_TARGET = 3;
    private final PaleMirrorProductionStations.Layout layout;
    private NonNullList<ItemStack> items;
    public ProductionBenchBlockEntity(BlockPos position, BlockState state) {
        super(PaleMirrorProductionStations.BENCH_ENTITY.get(), position, state);
        layout = PaleMirrorProductionStations.layout(state.getBlock());
        items = NonNullList.withSize(layout.slots(), ItemStack.EMPTY);
    }
    public PaleMirrorProductionStations.Layout layout() { return layout; }
    @Override public int getContainerSize() { return layout.slots(); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> next) {
        if (next.size() != getContainerSize()) throw new IllegalArgumentException("station inventory has wrong slot count");
        items = next;
    }
    @Override protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new ProductionBenchMenu(id, player, this);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
