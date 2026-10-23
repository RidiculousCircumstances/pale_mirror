package io.farfrontier.palemirror.internal.production;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Menu-facing port. UI never owns a ledger, recipe execution or a worker scheduler. */
public interface ProductionBenchAccess {
    record View(long storedHeat, int progressPermille, int status, int productRegistryId, boolean repair) {
        public View(long storedHeat, int progressPermille, int status) { this(storedHeat, progressPermille, status, 0, false); }
    }
    View view(ProductionBenchBlockEntity bench, Player player);
    boolean accepts(ProductionBenchBlockEntity bench, int port, ItemStack stack);
    boolean startOrResume(ProductionBenchBlockEntity bench, Player player);
    void attend(ProductionBenchBlockEntity bench, Player player);
    void pause(ProductionBenchBlockEntity bench, Player player);
}
