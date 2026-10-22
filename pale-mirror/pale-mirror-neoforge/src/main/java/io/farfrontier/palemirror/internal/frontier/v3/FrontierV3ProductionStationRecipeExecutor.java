package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Machine effects run without an NPC body; attendance is a separate canonical clock owner. */
final class FrontierV3ProductionStationRecipeExecutor {
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null); if (state == null) return;
        for (var machine : state.productionStations().machines().values().stream().sorted(Comparator.comparing(ProductionStations.Machine::stationId)).toList()) {
            var record = ProductionStationEnergy.record(state, machine.stationId()); var station = record.productionStation().orElseThrow();
            var physical = FrontierV3PhysicalContainer.loaded(level, state, record.id()).orElse(null); if (physical == null) continue;
            if (machine.pendingRecipe().isEmpty()) {
                var boundary = ProductionStationTransformation.next(state, machine.stationId()).filter(value -> value.authorityEpoch() > 0);
                if (boundary.isEmpty()) continue;
                FrontierV3CommandSubmission.submit(runtime, "station-recipe-prepare", machine.stationId().value(),
                        new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_PREPARED, boundary.orElseThrow(), List.of()));
                return;
            }
            var boundary = machine.pendingRecipe().orElseThrow(); var operation = machine.operation().orElseThrow();
            var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
            var inputAddress = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(record.id(), station.inputSlot()));
            var source = boundary.before().stream().filter(value -> value.address().equals(inputAddress)).findFirst().orElseThrow();
            var input = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(source.itemKind())), source.quantity());
            var output = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.product().itemKind())), operation.outputQuantity());
            var effect = new FrontierV3StationRecipeEffect(physical.inventory(), station, recipe, input, operation.inputQuantity(), output);
            var participants = List.of(physical.persistence().get());
            var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.STATION_RECIPE, machine.stationId(),
                    new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_PREPARED, boundary, List.of()), participants);
            var applied = FrontierV3MinecraftOperation.apply(level, declaration, participants, effect::before,
                    () -> { effect.apply(); return true; }, effect::after);
            if (!applied.complete()) continue;
            var observed = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(physical.inventory(), state, record.id());
            FrontierV3OperationConfirmation.submitRequired(level, runtime, applied.ticket(),
                    new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_COMPLETED, boundary, observed));
            return;
        }
    }
    private FrontierV3ProductionStationRecipeExecutor() { }
}
