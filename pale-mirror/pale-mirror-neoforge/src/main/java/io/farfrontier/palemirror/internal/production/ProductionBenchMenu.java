package io.farfrontier.palemirror.internal.production;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Same ordinary inventory interaction surface for player inputs and observed NPC transfers. */
public final class ProductionBenchMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final Player player;
    private final ContainerData data;
    public ProductionBenchMenu(int id, Inventory player) {
        this(id, player, new SimpleContainer(ProductionBenchBlockEntity.SLOT_COUNT), new SimpleContainerData(4));
    }
    public ProductionBenchMenu(int id, Inventory player, Container inventory) {
        this(id, player, inventory, new SimpleContainerData(4));
    }
    private ProductionBenchMenu(int id, Inventory player, Container inventory, ContainerData data) {
        super(PaleMirrorProductionStations.BENCH_MENU.get(), id);
        checkContainerSize(inventory, ProductionBenchBlockEntity.SLOT_COUNT);
        this.inventory = inventory;
        this.player = player.player; this.data = data; addDataSlots(data);
        addSlot(new Slot(inventory, ProductionBenchBlockEntity.INPUT, 56, 17) {
            @Override public boolean mayPlace(ItemStack stack) { return accepts(ProductionBenchBlockEntity.INPUT, stack); }
        });
        addSlot(new Slot(inventory, ProductionBenchBlockEntity.OUTPUT, 116, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new Slot(inventory, ProductionBenchBlockEntity.FUEL, 56, 53) {
            @Override public boolean mayPlace(ItemStack stack) { return accepts(ProductionBenchBlockEntity.FUEL, stack); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(player, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 8 + col * 18, 142));
    }
    public Container inventory() { return inventory; }
    public long storedHeat() { return (data.get(0) & 0xffffL) | ((data.get(1) & 0xffffL) << 16); }
    public int progressPermille() { return data.get(2); }
    public int status() { return data.get(3); }
    private boolean accepts(int port, ItemStack stack) {
        return !(inventory instanceof ProductionBenchBlockEntity bench)
                ? port != ProductionBenchBlockEntity.OUTPUT : ProductionBenchInteraction.access().accepts(bench, port, stack);
    }
    @Override public void broadcastChanges() {
        if (inventory instanceof ProductionBenchBlockEntity bench && !player.level().isClientSide()) {
            ProductionBenchInteraction.access().attend(bench, player);
            var view = ProductionBenchInteraction.access().view(bench, player);
            data.set(0, (int) view.storedHeat() & 0xffff); data.set(1, (int)(view.storedHeat() >>> 16) & 0xffff);
            data.set(2, view.progressPermille()); data.set(3, view.status());
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        return button == 0 && inventory instanceof ProductionBenchBlockEntity bench && stillValid(player)
                && ProductionBenchInteraction.access().startOrResume(bench, player);
    }
    @Override public void removed(Player player) {
        if (inventory instanceof ProductionBenchBlockEntity bench && !player.level().isClientSide())
            ProductionBenchInteraction.access().pause(bench, player);
        super.removed(player);
    }
    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), before = stack.copy();
        if (index < ProductionBenchBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(stack, ProductionBenchBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean input = accepts(ProductionBenchBlockEntity.INPUT, stack), fuel = accepts(ProductionBenchBlockEntity.FUEL, stack);
            if (input == fuel) return ItemStack.EMPTY;
            int port = input ? ProductionBenchBlockEntity.INPUT : ProductionBenchBlockEntity.FUEL;
            if (!moveItemStackTo(stack, port, port + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
