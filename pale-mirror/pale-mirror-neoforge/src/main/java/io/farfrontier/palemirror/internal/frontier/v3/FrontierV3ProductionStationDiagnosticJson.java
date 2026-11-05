package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ProductionStationProgressObligation;

/** Existing process endpoint, actual installed instance and stock; never admits or advances work. */
final class FrontierV3ProductionStationDiagnosticJson {
    static String render(FrontierScheduleView checkpoint, FrontierWorldState state, ProductionStations.Machine machine) {
        var record = ProductionStationEnergy.record(state, machine.stationId());
        var spec = record.productionStation().orElseThrow();
        var obligation = ProductionStationProgressObligation.describe(state, machine);
        var facility = io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateSupport.settlement(state.bootstrap(), record.ownerId()).structures().stream()
                .filter(value -> value.id().equals(spec.facilityId())).findFirst().orElseThrow();
        var approach = io.farfrontier.palemirror.frontier.v3.model.SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach();
        boolean fuelReady = state.bootstrap().ruleset().production().recipes().stream()
                .filter(recipe -> recipe.capability() == spec.capability())
                .anyMatch(recipe -> !recipe.requiresCombustion() || machine.burnRemaining(checkpoint.instant().ticks()) > 0);
        String operation = machine.operation().map(value -> {
            var clock = value.progress();
            return "{\"id\":" + string(value.id().value().value()) + ",\"order\":" + string(value.orderId().value())
                    + ",\"recipe\":" + string(value.recipeId().value()) + ",\"mode\":"
                    + string(state.bootstrap().ruleset().production().require(value.recipeId()).processingMode().name())
                    + ",\"initiator\":" + string(switch (value.initiator()) {
                        case ProductionOperation.OrderInitiator ignored -> "ORDER";
                        case ProductionOperation.PlayerInitiator ignored -> "PLAYER";
                        case ProductionOperation.SettlementInitiator ignored -> "SETTLEMENT";
                    })
                    + ",\"playerAttendanceIntent\":" + (value.initiator() instanceof ProductionOperation.PlayerInitiator player
                            ? string(player.attendanceIntent().name()) : "null")
                    + ",\"inputQuantity\":" + value.inputQuantity() + ",\"outputQuantity\":" + value.outputQuantity()
                    + ",\"transformed\":" + value.transformed()
                    + ",\"completedMilliWork\":" + clock.map(WorkProgress::completedMilliWork).orElse(0L)
                    + ",\"elapsedCompletedMilliWork\":" + clock.map(valueClock -> valueClock.completedAt(checkpoint.instant().ticks())).orElse(0L)
                    + ",\"requiredMilliWork\":" + clock.map(WorkProgress::requiredMilliWork).orElse(0L)
                    + ",\"ratePermille\":" + clock.map(WorkProgress::ratePermille).orElse(0) + "}";
        }).orElse("null");
        String stocks = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container holder && holder.containerId().equals(record.id()))
                .flatMap(account -> account.lotQuantities().entrySet().stream().map(entry -> "{\"lot\":" + string(entry.getKey().value())
                        + ",\"section\":" + string(((ResourceCustody.Container) account.custody()).section().value())
                        + ",\"itemKind\":" + string(state.inventory().fungibleResources().lots().get(entry.getKey()).itemKind())
                        + ",\"quantity\":" + entry.getValue() + "}"))
                .collect(java.util.stream.Collectors.joining(","));
        return FrontierV3DiagnosticJson.base("process", machine.stationId().value(), checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.production-station\",\"identity\":{\"station\":"
                + string(machine.stationId().value()) + ",\"container\":" + string(record.id().value())
                + ",\"facility\":" + string(spec.facilityId().value()) + "},\"position\":"
                + FrontierV3DiagnosticJson.position(state.inventory().surfaces().get(record.id()).position(state))
                + ",\"exteriorApproach\":" + FrontierV3DiagnosticJson.position(approach.support().offset(0, 1, 0))
                + ",\"result\":{\"remainingBurnTicks\":" + machine.burnRemaining(checkpoint.instant().ticks())
                + ",\"fuelReady\":" + fuelReady
                + ",\"revision\":" + machine.revision() + ",\"surface\":" + string(state.inventory().surfaces().get(record.id()).status().name())
                + ",\"pendingFuel\":" + machine.pendingFuel().isPresent() + ",\"pendingRecipe\":" + machine.pendingRecipe().isPresent()
                + ",\"operation\":" + operation + ",\"stocks\":[" + stocks + "]},\"progressObligation\":{\"schema\":1"
                + ",\"rule\":\"frontier.production-station.progress.v1\",\"subject\":" + string(machine.stationId().value())
                + ",\"worker\":" + string(machine.stationId().value()) + ",\"generation\":1,\"revision\":" + machine.revision()
                + ",\"fingerprint\":" + string(obligation.fingerprint()) + ",\"budgetTicks\":" + obligation.budgetTicks()
                + ",\"disposition\":" + string(obligation.disposition().name()) + ",\"next\":" + string(obligation.next().name()) + "}}";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
    private FrontierV3ProductionStationDiagnosticJson() { }
}
