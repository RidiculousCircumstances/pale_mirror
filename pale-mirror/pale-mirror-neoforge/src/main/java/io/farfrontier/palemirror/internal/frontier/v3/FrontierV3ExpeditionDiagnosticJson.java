package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Comparator;
import java.util.stream.Collectors;

/** Read-only join of existing mission, fleet, stock and budget owners. Never selects execution. */
final class FrontierV3ExpeditionDiagnosticJson {
    private FrontierV3ExpeditionDiagnosticJson() { }
    static String render(CheckpointImage checkpoint, FrontierWorldState state, GoodsParticipant participant) {
        var home = participant.party().id();
        var missions = state.shipments().missions().values().stream().filter(m -> m.sender().settlementId().equals(home))
                .sorted(Comparator.comparing(TransportMission::id)).toList();
        var assets = state.transportFleet().assets().values().stream().filter(a -> a.homeSettlementId().equals(home))
                .sorted(Comparator.comparing(a -> a.actorId())).toList();
        var resources = state.inventory().fungibleResources();
        String journeys = missions.stream().map(m -> {
            var group = state.unitGroups().groups().get(m.groupId());
            int planned = m.supplies().map(s -> s.foodTargets().values().stream().mapToInt(Integer::intValue).sum()).orElse(0);
            int loaded = m.supplies().map(s -> s.allocations().stream().filter(a -> a.loaded()).mapToInt(a -> a.quantity()).sum()).orElse(0);
            var readiness = io.farfrontier.palemirror.frontier.v3.process.UnitGroupProcess.navigationReadiness(state, group);
            return "{\"id\":" + string(m.id().value()) + ",\"group\":" + string(m.groupId().value())
                    + ",\"stage\":" + string(m.stage().name()) + ",\"revision\":" + m.revision()
                    + ",\"goalOrdinal\":" + group.goalOrdinal() + ",\"groupPhase\":" + string(group.phase().name())
                    + ",\"plannedPersonalFood\":" + planned + ",\"loadedFood\":" + loaded
                    + ",\"provisioned\":" + m.supplies().map(s -> s.complete()).orElse(false)
                    + ",\"refillPending\":" + m.replenishment().isPresent()
                    + ",\"navigationStatus\":" + string(readiness.status()) + ",\"navigationReason\":" + string(readiness.reason())
                    + ",\"memberView\":" + string(group.id().value())
                    + ",\"asset\":" + m.transportAssetId().map(id -> string(id.value())).orElse("null")
                    + ",\"budget\":" + m.financialBudgetId().map(id -> string(id.value())).orElse("null") + "}";
        }).collect(Collectors.joining(","));
        String fleet = assets.stream().map(a -> "{\"actor\":" + string(a.actorId().value())
                + ",\"container\":" + string(a.containerId().value())
                + ",\"life\":" + string(state.actorLocations().get(a.actorId()).condition().status().name())
                + ",\"mission\":" + a.missionId().map(id -> string(id.value())).orElse("null")
                + ",\"body\":" + FrontierV3DiagnosticJson.position(
                    io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.bodyAt(state, a.actorId(), checkpoint.instant().ticks()))
                + ",\"freeFood\":" + resources.accounts().values().stream()
                    .filter(c -> c.custody().equals(new ResourceCustody.Container(a.containerId())))
                    .mapToInt(c -> resources.unclaimedQuantity(c.id(), home, FoodCatalog.BREAD)).sum() + "}").collect(Collectors.joining(","));
        int accepted = state.companies().goodsTrade().contracts().values().stream()
                .filter(c -> c.seller().equals(participant.party())).mapToInt(GoodsTradeContract::acceptedQuantity).sum();
        long budgets = state.inventory().economics().budgets().values().stream().filter(b -> b.payerId().equals(home)).count();
        return "{\"activeMissions\":" + missions.size() + ",\"reservedAnimals\":" + assets.stream().filter(a -> a.missionId().isPresent()).count()
                + ",\"provisionedOutbound\":" + missions.stream().filter(m -> m.stage() == TransportMission.Stage.OUTBOUND
                    && m.supplies().map(s -> s.complete()).orElse(false)).count()
                + ",\"returningMissions\":" + missions.stream().filter(m -> m.stage() == TransportMission.Stage.RETURNING).count()
                + ",\"allocatedBudgets\":" + budgets + ",\"acceptedGoods\":" + accepted
                + ",\"missions\":[" + journeys + "],\"assets\":[" + fleet + "]}";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
}
