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
            var output = boundary.exactAfter().map(FrontierV3ExactItemPresentation::materializedStack).orElseGet(() ->
                    new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.product().itemKind())), operation.outputQuantity()));
            var effect = new FrontierV3StationRecipeEffect(physical.inventory(), station, recipe, boundary.before(), boundary.after(), output,
                    boundary.exactBefore());
            var participants = List.of(physical.persistence().get());
            var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.STATION_RECIPE, machine.stationId(),
                    new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_PREPARED, boundary, List.of()), participants);
            var applied = FrontierV3MinecraftOperation.applyDeferred(level, declaration, participants, effect::before, FrontierV3NativeEffect.observed(() -> { effect.apply(); return true; }), effect::after);
            if (!applied.complete()) continue;
            var observed = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(physical.inventory(), state, record.id(),
                    boundary.exactAfter().isPresent() ? Set.of(station.outputSlot()) : Set.of());
            observed = observed.stream().filter(value -> {
                int slot = ((PhysicalStackAddress.ContainerSlot) value.address()).slot().slot();
                return station.inputSlots().contains(slot) || slot == station.outputSlot();
            }).toList();
            var observedExact = boundary.exactAfter().filter(item -> FrontierV3ExactItemPresentation.exactMatch(
                    physical.inventory().getItem(station.outputSlot()), item));
            FrontierV3OperationConfirmation.submitRequired(level, runtime, applied.ticket(),
                    new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_COMPLETED, boundary, observed, observedExact));
            return;
        }
    }
    private FrontierV3ProductionStationRecipeExecutor() { }
}
