package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.world.Container;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** Repairs only missing provenance after an already committed, exactly validated cart handoff. */
final class FrontierV3CargoCarrierProvenance {
    private FrontierV3CargoCarrierProvenance() { }

    static boolean restore(Map<SubjectId, ExactItemStack> canonicalItems, UUID carrier, Container container) {
        var custody = new InventoryCustody.WorldCarrier(carrier);
        var seen = new HashSet<SubjectId>();
        var missing = new ArrayList<Integer>();
        // Validate the entire physical inventory before writing any metadata. Slot ordering
        // need not match the old shipment, and missing items must never be recreated.
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            var stack = container.getItem(slot);
            var id = FrontierV3CargoHandoffExecutor.itemId(stack).orElse(null);
            var item = id == null ? null : canonicalItems.get(id);
            if (item == null || !item.custody().equals(custody)) continue;
            if (!seen.add(id) || !FrontierV3CargoHandoffExecutor.exactMatch(stack, item)) return false;
            var declared = FrontierV3CargoHandoffExecutor.worldCarrierId(stack);
            if (declared.isPresent() && !declared.get().equals(carrier)) return false;
            if (declared.isEmpty()) {
                if (FrontierV3CargoHandoffExecutor.hasWorldCarrierDeclaration(stack)) return false;
                missing.add(slot);
            }
        }
        for (int slot : missing) {
            var stack = container.getItem(slot);
            FrontierV3CargoHandoffExecutor.bindWorldCarrier(stack, carrier);
            container.setItem(slot, stack);
        }
        if (!missing.isEmpty()) container.setChanged();
        return true;
    }
}
