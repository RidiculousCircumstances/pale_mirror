package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FungibleBindingReadIndexTest {
    private static final SubjectId FIRST = new SubjectId("account:first"), SECOND = new SubjectId("account:second");
    private static PhysicalStackBinding binding(int slot, SubjectId account, long epoch) {
        return new PhysicalStackBinding(new SubjectId("binding:row-" + slot), account,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(new SubjectId("container:test"), slot)),
                epoch, "minecraft:wheat", Map.of(new SubjectId("lot:test"), 1), Map.of());
    }
    @Test void readsPreserveOrderEpochAndBranchHistoryWithoutTrustingMutableIngress() {
        var rows = new LinkedHashMap<SubjectId, PhysicalStackBinding>();
        for (int i = 0; i < 300; i++) { var row = binding(i, i < 2 ? FIRST : SECOND, 1); rows.put(row.id(), row); }
        var original = ImmutableLookupMap.copyOf(rows);
        var before = FungibleBindingReadIndex.forAccount(original, FIRST);
        assertEquals(List.of(rows.get(new SubjectId("binding:row-0")), rows.get(new SubjectId("binding:row-1"))), before);
        var reassigned = binding(0, SECOND, 9);
        var changed = ImmutableLookupMap.withEntry(original, reassigned.id(), reassigned);
        assertEquals(List.of(before.get(1)), FungibleBindingReadIndex.forAccount(changed, FIRST));
        assertSame(reassigned, FungibleBindingReadIndex.forAccount(changed, SECOND).getFirst());
        assertEquals(before, FungibleBindingReadIndex.forAccount(original, FIRST), "old immutable branch still has its own accounts/epochs");
        var removed = ImmutableLookupMap.withoutEntry(original, before.get(0).id());
        assertEquals(List.of(before.get(1)), FungibleBindingReadIndex.forAccount(removed, FIRST));
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        assertEquals(2, FungibleBindingReadIndex.forAccount(rows, FIRST).size());
        rows.clear(); assertTrue(FungibleBindingReadIndex.forAccount(rows, FIRST).isEmpty(), "mutable ingress cannot become trusted identity evidence");
    }
}
