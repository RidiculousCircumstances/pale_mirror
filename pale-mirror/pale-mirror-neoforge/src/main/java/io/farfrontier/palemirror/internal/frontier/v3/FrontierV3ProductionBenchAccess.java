package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.internal.production.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import io.farfrontier.palemirror.internal.presentation.PaleMirrorPlayerPresentation;
import io.farfrontier.palemirror.internal.presentation.PlayerContextCard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Physical current-menu evidence -> shared station commands. No direct stock or block mutation. */
public final class FrontierV3ProductionBenchAccess implements ProductionBenchAccess {
    private record Current(FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime,
                           FrontierWorldState state, ContainerRecord record, ProductionStations.Machine machine) { }
    private static Optional<Current> current(ProductionBenchBlockEntity bench) {
        if (!(bench.getLevel() instanceof ServerLevel level)) return Optional.empty();
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null) return Optional.empty();
        var state = runtime.decodedState().orElse(null); if (state == null) return Optional.empty();
        var declared = bench.getPersistentData().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY);
        if (declared.isBlank()) return Optional.empty();
        var record = state.inventory().containers().get(new SubjectId(declared));
        if (record == null || record.productionStation().isEmpty()) return Optional.empty();
        var physical = FrontierV3PhysicalContainer.loaded(level, state, record.id());
        if (physical.isEmpty() || physical.orElseThrow().inventory() != bench) return Optional.empty();
        return Optional.of(new Current(runtime, state, record, state.productionStations().require(record.productionStation().orElseThrow().id())));
    }
    @Override public View view(ProductionBenchBlockEntity bench, Player player) {
        var found = current(bench); if (found.isEmpty()) return new View(0, 0, 0);
        var value = found.orElseThrow(); var operation = value.machine().operation();
        long tick = value.runtime().canonicalState().orElseThrow().instant().ticks();
        int progress = operation.flatMap(ProductionOperation::progress).map(clock ->
                (int)(clock.completedAt(Math.max(tick, clock.evaluatedAtTick())) * 1_000L / clock.requiredMilliWork())).orElse(0);
        int status;
        boolean materialsReady = FungibleResourceCustodySupport.accountAtContainer(value.state(), value.record().id())
                .map(account -> account.lotQuantities().isEmpty() || FungibleResourceCustodySupport.hasCurrentPhysicalPortion(
                        value.state(), account.id(), account.lotQuantities())).orElse(true);
        var lease = value.state().replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(value.record().id()));
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                || !ReferenceContainerCustody.hasOperationalCustody(value.state(), value.record().id())
                || ReferenceContainerCustody.blocksCanonicalUse(value.state(), value.record().id())
                || ContainerPhysicalAuthorityComposition.pending(value.state(), value.record().id()) || !materialsReady) status = 6;
        else if (operation.filter(ProductionOperation::transformed).isPresent()) status = 3;
        else if (operation.isPresent()) status = processingStatus(operation.orElseThrow(), tick);
        else if (!bench.getItem(ProductionBenchBlockEntity.OUTPUT).isEmpty()) status = 3;
        else if (bench.getItem(ProductionBenchBlockEntity.INPUT).isEmpty()) status = 5;
        else {
            var recipe = recipe(value, bench.getItem(ProductionBenchBlockEntity.INPUT));
            status = recipe.isEmpty() || bench.getItem(ProductionBenchBlockEntity.INPUT).getCount() < recipe.orElseThrow().ingredient().quantity()
                    ? 5 : value.machine().storedHeat() < recipe.orElseThrow().heatPerBatch() ? 4 : 1;
        }
        return new View(value.machine().storedHeat(), progress, status);
    }
    /** Presentation reads the bounded processing clock, not mere operation existence. */
    static int processingStatus(ProductionOperation operation, long tick) {
        if (operation.transformed()) return 3;
        return operation.progress().map(clock -> {
            if (clock.completedAt(Math.max(tick, clock.evaluatedAtTick())) == clock.requiredMilliWork()) return 8;
            return clock.running() && tick < clock.activeUntilTick() ? 2 : 7;
        }).orElse(7);
    }
    private static Optional<ProductionRecipe> recipe(Current value, ItemStack input) {
        var kind = BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
        var station = value.record().productionStation().orElseThrow();
        return value.state().bootstrap().ruleset().production().recipes().stream().filter(recipe -> recipe.capability() == station.capability()
                && recipe.ingredient().accepts(kind)).sorted(Comparator.comparing(recipe -> recipe.id().value())).findFirst();
    }
    @Override public boolean accepts(ProductionBenchBlockEntity bench, int port, ItemStack stack) {
        if (bench.getLevel() != null && bench.getLevel().isClientSide()) return port != ProductionBenchBlockEntity.OUTPUT;
        var found = current(bench); if (found.isEmpty()) return false;
        var value = found.orElseThrow(); var kind = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return switch (port) {
            case ProductionBenchBlockEntity.INPUT -> recipe(value, stack).isPresent();
            case ProductionBenchBlockEntity.FUEL -> value.state().bootstrap().ruleset().production().fuels().stream()
                    .anyMatch(fuel -> fuel.itemKind().equals(kind));
            default -> false;
        };
    }
    @Override public boolean startOrResume(ProductionBenchBlockEntity bench, Player player) {
        if (!(player.containerMenu instanceof ProductionBenchMenu menu) || menu.inventory() != bench || !bench.stillValid(player)) return false;
        var found = current(bench);
        if (found.isEmpty()) { rejected(player, "This bench is not registered at a production place."); return false; }
        var value = found.orElseThrow();
        var recipe = value.machine().operation().map(operation -> value.state().bootstrap().ruleset().production().require(operation.recipeId()))
                .or(() -> recipe(value, bench.getItem(ProductionBenchBlockEntity.INPUT)));
        if (recipe.isEmpty()) { rejected(player, "No compatible recipe input."); return false; }
        var result = submit(value, player, recipe.orElseThrow().id(), value.machine().operation().isEmpty()
                ? ProductionStationOperatorChanged.Stage.START : ProductionStationOperatorChanged.Stage.RESUME);
        if (!(result instanceof CommandResult.Accepted)) rejected(player, "Could not start: check ingredients, fuel and current operator.");
        return result instanceof CommandResult.Accepted;
    }
    private static void rejected(Player player, String reason) {
        if (player instanceof ServerPlayer serverPlayer) {
            PaleMirrorPlayerPresentation.actionRejected(serverPlayer, "production-bench:rejected");
            PaleMirrorPlayerPresentation.inspect(serverPlayer, "production-bench:result",
                    new PlayerContextCard("Production bench", List.of(reason), 0xCF8A48));
        }
    }
    @Override public void attend(ProductionBenchBlockEntity bench, Player player) {
        if (!(player.containerMenu instanceof ProductionBenchMenu menu)
                || menu.inventory() != bench || !bench.stillValid(player)) return;
        current(bench).ifPresent(value -> {
            var operation = owned(value, player);
            if (operation.isEmpty()) return;
            // Consent survives a temporary physical/lease wait, but never an explicit pause.
            if (!attendanceRequested(operation.orElseThrow(), player.getUUID())) return;
            long tick = value.runtime().canonicalState().orElseThrow().instant().ticks();
            if (operation.orElseThrow().progress().filter(clock -> clock.complete() || tick < clock.evaluatedAtTick() + 20).isPresent()) return;
            submit(value, player, operation.orElseThrow().recipeId(), ProductionStationOperatorChanged.Stage.ATTEND);
        });
    }
    @Override public void pause(ProductionBenchBlockEntity bench, Player player) {
        current(bench).ifPresent(value -> owned(value, player).filter(operation -> attendanceRequested(operation, player.getUUID()))
                .ifPresent(operation -> submit(value, player, operation.recipeId(), ProductionStationOperatorChanged.Stage.PAUSE)));
    }
    private static Optional<ProductionOperation> owned(Current value, Player player) {
        return value.machine().operation().filter(operation -> operation.initiator() instanceof ProductionOperation.PlayerInitiator initiator
                && initiator.playerId().equals(player.getUUID())
                && !operation.transformed() && value.machine().pendingRecipe().isEmpty()
                && value.state().bootstrap().ruleset().production().require(operation.recipeId()).processingMode() == ProductionRecipe.ProcessingMode.ATTENDED);
    }
    static boolean attendanceRequested(ProductionOperation operation, java.util.UUID playerId) {
        return operation.initiator() instanceof ProductionOperation.PlayerInitiator player
                && player.playerId().equals(playerId)
                && player.attendanceIntent() == ProductionOperation.PlayerAttendanceIntent.RUN_REQUESTED;
    }
    private static CommandResult submit(Current value, Player player, ProductionRecipe.Id recipe, ProductionStationOperatorChanged.Stage stage) {
        long tick = value.runtime().canonicalState().orElseThrow().instant().ticks();
        return FrontierV3CommandSubmission.submitResult(value.runtime(), "station-player-" + stage.name().toLowerCase(java.util.Locale.ROOT),
                value.machine().stationId().value(), new ProductionStationOperatorChanged(value.machine().stationId(), value.machine().revision(),
                        player.getUUID(), recipe, stage, tick));
    }
}
