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
    private int recipeChoice;
    public ProductionBenchMenu(int id, Inventory player) {
        this(id, player, new SimpleContainer(ProductionBenchBlockEntity.SLOT_COUNT), new SimpleContainerData(6), PaleMirrorProductionStations.Layout.STANDARD);
    }
    public static ProductionBenchMenu forgeClient(int id, Inventory player) {
        return new ProductionBenchMenu(id, player, new SimpleContainer(PaleMirrorProductionStations.Layout.MAINTENANCE.slots()), new SimpleContainerData(6), PaleMirrorProductionStations.Layout.MAINTENANCE);
    }
    public static ProductionBenchMenu woodworkingClient(int id, Inventory player) {
        return new ProductionBenchMenu(id, player, new SimpleContainer(PaleMirrorProductionStations.Layout.WOODWORKING.slots()), new SimpleContainerData(6), PaleMirrorProductionStations.Layout.WOODWORKING);
    }
    public ProductionBenchMenu(int id, Inventory player, Container inventory) {
        this(id, player, inventory, new SimpleContainerData(6), inventory instanceof ProductionBenchBlockEntity bench
                ? bench.layout() : PaleMirrorProductionStations.Layout.STANDARD);
    }
    private ProductionBenchMenu(int id, Inventory player, Container inventory, ContainerData data, PaleMirrorProductionStations.Layout layout) {
        super(layout.menu(), id);
        checkContainerSize(inventory, layout.slots());
        this.inventory = inventory;
        this.player = player.player; this.data = data; addDataSlots(data);
        for (int input = 0; input < ProductionBenchBlockEntity.INPUT_COUNT; input++) {
            final int port = input;
            addSlot(new Slot(inventory, port, 26 + input % 3 * 18, 18 + input / 3 * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return accepts(port, stack); }
            });
        }
        addSlot(new Slot(inventory, ProductionBenchBlockEntity.OUTPUT, 120, 36) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        if (layout.slots() > ProductionBenchBlockEntity.FUEL)
            addSlot(new Slot(inventory, ProductionBenchBlockEntity.FUEL, 26, 80) {
                @Override public boolean mayPlace(ItemStack stack) { return accepts(ProductionBenchBlockEntity.FUEL, stack); }
            });
        if (layout == PaleMirrorProductionStations.Layout.MAINTENANCE)
            addSlot(new Slot(inventory, ProductionBenchBlockEntity.MAINTENANCE_TARGET, 62, 80) {
                @Override public boolean mayPlace(ItemStack stack) { return accepts(ProductionBenchBlockEntity.MAINTENANCE_TARGET, stack); }
            });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(player, 9 + row * 9 + col, 8 + col * 18, 119 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 8 + col * 18, 177));
    }
    public Container inventory() { return inventory; }
    public boolean hasMaintenancePort() { return inventory.getContainerSize() > ProductionBenchBlockEntity.MAINTENANCE_TARGET; }
    public int recipeChoice() { return recipeChoice; }
    public int productRegistryId() { return data.get(4); }
    public boolean repairRecipe() { return data.get(5) != 0; }
    public long remainingBurnTicks() { return (data.get(0) & 0xffffL) | ((data.get(1) & 0xffffL) << 16); }
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
            data.set(0, (int) view.remainingBurnTicks() & 0xffff); data.set(1, (int)(view.remainingBurnTicks() >>> 16) & 0xffff);
            data.set(2, view.progressPermille()); data.set(3, view.status());
            data.set(4, view.productRegistryId()); data.set(5, view.repair() ? 1 : 0);
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (button == 1 && stillValid(player) && inventory instanceof ProductionBenchBlockEntity) {
            recipeChoice = Math.addExact(recipeChoice, 1); broadcastChanges(); return true;
        }
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
        if (index < inventory.getContainerSize()) {
            if (!moveItemStackTo(stack, inventory.getContainerSize(), slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Explicit player-UI routing: fill the input inventory first, then ancillary ports.
            // The witnessed destination, not the item kind, determines canonical compartment custody.
            boolean moved = moveItemStackTo(stack, 0, ProductionBenchBlockEntity.INPUT_COUNT, false);
            if (!moved) for (int port = ProductionBenchBlockEntity.FUEL; port < inventory.getContainerSize(); port++) {
                if (accepts(port, stack) && moveItemStackTo(stack, port, port + 1, false)) { moved = true; break; }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
