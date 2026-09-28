package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import net.minecraft.world.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3DepotClickWitnessTest {
    private static final SubjectId DEPOT = new SubjectId("container:one-depot");
    private static final SubjectId ACCOUNT = new SubjectId("custody:container-one-depot");
    private static final SubjectId OWNER = new SubjectId("settlement:one");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000141");
    private static final UUID INTERACTION = UUID.fromString("00000000-0000-0000-0000-000000000142");

    @Test void durablePreAndPostLayoutsRoundTripIncludingAnEmptyChest() {
        var prepared = new FrontierV3DepotClickWitness(DEPOT, ACCOUNT, OWNER, 7, PLAYER, INTERACTION,
                List.of(stack(0, 64), stack(1, 8)), Optional.empty());
        assertEquals(prepared, FrontierV3DepotClickWitness.read(prepared.save()));
        var observed = prepared.observed(List.of());
        assertEquals(observed, FrontierV3DepotClickWitness.read(observed.save()));
        assertThrows(IllegalArgumentException.class, () -> observed.observed(List.of(stack(0, 1))));
    }

    @Test void partiallyClaimedStackPermitsOnlyTheUnclaimedPhysicalPortion() {
        SubjectId claim = new SubjectId("claim:one-bread");
        PhysicalStackBinding binding = new PhysicalStackBinding(new SubjectId("binding:bread"), ACCOUNT,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)),
                7L, "minecraft:bread", Map.of(new SubjectId("lot:bread"), 64), Map.of(claim, 33));
        assertFalse(FrontierV3DepotClickExecutor.exceedsUnclaimedPortion(binding, ClickType.THROW, 0, true));
        assertTrue(FrontierV3DepotClickExecutor.exceedsUnclaimedPortion(binding, ClickType.PICKUP, 1, true));
        assertTrue(FrontierV3DepotClickExecutor.exceedsUnclaimedPortion(binding, ClickType.PICKUP, 0, true));
        assertTrue(FrontierV3DepotClickExecutor.exceedsUnclaimedPortion(binding, ClickType.QUICK_MOVE, 0, true));
        assertFalse(FrontierV3DepotClickExecutor.exceedsUnclaimedPortion(binding, ClickType.PICKUP, 1, false));
    }

    private static FungiblePhysicalObservation.Stack stack(int slot, int count) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(DEPOT, slot)), "minecraft:bread", count);
    }
}
