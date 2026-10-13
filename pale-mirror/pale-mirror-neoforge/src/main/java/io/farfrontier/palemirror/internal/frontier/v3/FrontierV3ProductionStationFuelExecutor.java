package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Loaded machine fuel effects. Before/after inspection makes retained prepares non-replayable. */
final class FrontierV3ProductionStationFuelExecutor {
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (var machine : state.productionStations().machines().values().stream()
                .sorted(Comparator.comparing(ProductionStations.Machine::stationId)).toList()) {
            var record = ProductionStationEnergy.record(state, machine.stationId());
            var physical = FrontierV3PhysicalContainer.loaded(level, state, record.id()).orElse(null);
            if (physical == null) continue;
            if (machine.pendingFuel().isEmpty()) {
                var proposed = ProductionStationEnergy.next(state, machine.stationId()).filter(burn -> burn.authorityEpoch() > 0);
                if (proposed.isEmpty()) continue;
                FrontierV3CommandSubmission.submit(runtime, "station-fuel-prepare", machine.stationId().value(),
                        new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_PREPARED, proposed.orElseThrow(), List.of()));
                return; // The accepted prepare is durably forced before the next physical invocation.
            }
            var burn = machine.pendingFuel().orElseThrow(); var slot = record.productionStation().orElseThrow().fuelSlot();
            var actual = physical.inventory().getItem(slot); var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(burn.itemKind()));
            int before = burn.source().getFirst().before();
            if (item == Items.AIR) throw new IllegalStateException("station fuel item is unavailable: " + burn);
            boolean isBefore = plain(actual, new ItemStack(item, before));
            boolean isAfter = before == 1 ? actual.isEmpty() : plain(actual, new ItemStack(item, before - 1));
            if (!isBefore && !isAfter) throw new IllegalStateException("station fuel has neither prepared preimage nor postimage: "
                    + burn + " actual=" + actual);
            if (isBefore) {
                physical.inventory().setItem(slot, actual.copyWithCount(before - 1)); physical.inventory().setChanged();
            }
            var remaining = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(physical.inventory(), state, record.id());
            var result = FrontierV3CommandSubmission.submitResult(runtime, "station-fuel-consumed", machine.stationId().value(),
                    new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_CONSUMED, burn, remaining));
            if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("station fuel receipt rejected after physical effect: " + burn + " result=" + result);
            if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(runtime, record.id(), physical))
                throw new IllegalStateException("station fuel consumption lacks next replica boundary: " + burn);
            return;
        }
    }
    private static boolean plain(ItemStack actual, ItemStack expected) {
        return actual.getCount() == expected.getCount() && ItemStack.isSameItemSameComponents(actual, expected);
    }
    private FrontierV3ProductionStationFuelExecutor() { }
}
