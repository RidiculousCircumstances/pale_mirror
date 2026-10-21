package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Loaded machine fuel effects. The shared operation boundary owns native save evidence. */
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
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(burn.itemKind()));
            int before = burn.source().getFirst().before();
            if (item == Items.AIR) throw new IllegalStateException("station fuel item is unavailable: " + burn);
            var participants = List.of(physical.persistence().get());
            var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.STATION_FUEL, machine.stationId(),
                    new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_PREPARED, burn, List.of()), participants);
            var effect = FrontierV3MinecraftOperation.apply(level, declaration, participants,
                    () -> plain(physical.inventory().getItem(slot), new ItemStack(item, before)),
                    () -> { physical.inventory().setItem(slot, before == 1 ? ItemStack.EMPTY : new ItemStack(item, before - 1));
                        physical.inventory().setChanged(); return true; },
                    () -> before == 1 ? physical.inventory().getItem(slot).isEmpty()
                            : plain(physical.inventory().getItem(slot), new ItemStack(item, before - 1)));
            if (!effect.complete()) continue;
            var remaining = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(physical.inventory(), state, record.id());
            boolean accepted = FrontierV3OperationConfirmation.submit(level, runtime, effect.ticket(),
                    new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_CONSUMED, burn, remaining));
            if (!accepted) throw new IllegalStateException("station fuel receipt rejected after physical effect: " + burn);
            return;
        }
    }
    private static boolean plain(ItemStack actual, ItemStack expected) {
        return actual.getCount() == expected.getCount() && ItemStack.isSameItemSameComponents(actual, expected);
    }
    private FrontierV3ProductionStationFuelExecutor() { }
}
