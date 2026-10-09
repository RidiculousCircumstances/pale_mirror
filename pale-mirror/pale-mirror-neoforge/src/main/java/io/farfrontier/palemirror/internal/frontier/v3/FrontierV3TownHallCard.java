package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.internal.presentation.PlayerContextCard;
import java.math.BigDecimal;
import java.util.List;

/** Read-only treasury totals, using the one economic owner and exact hall declaration. */
final class FrontierV3TownHallCard {
    private FrontierV3TownHallCard() { }
    static PlayerContextCard from(FrontierWorldState state, SettlementStructure hall) {
        if (hall.kind() != StructureKind.HALL) throw new IllegalArgumentException("settlement card requires a town hall");
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), hall.settlementId());
        if (!home.structures().contains(hall)) throw new IllegalArgumentException("foreign town hall declaration");
        var ledger = state.inventory().economics();
        var account = ledger.require(home.id());
        FixedScalar available = ledger.availableToReserve(home.id());
        FixedScalar held = account.balance().plus(account.creditLimit()).minus(available);
        var residents = state.humanPopulation().residents().values().stream()
                .filter(person -> person.settlementId().equals(home.id()))
                .filter(person -> state.actorLocations().get(person.id()).condition().status() == ActorLifeStatus.ALIVE).toList();
        long hungry = residents.stream().filter(person -> state.humanPopulation().nutrition(person.id())
                .wantsFood(state.bootstrap().ruleset().residentLife())).count();
        long groups = state.shipments().missions().values().stream()
                .filter(mission -> mission.sender().settlementId().equals(home.id()))
                .filter(mission -> mission.stage() != TransportMission.Stage.COMPLETE).count();
        return new PlayerContextCard(home.displayName() + " · Town Hall", List.of(
                "Treasury: " + money(account.balance()),
                "Held: " + money(held) + " · Available: " + money(available),
                "Credit limit: " + money(account.creditLimit()),
                "Residents: " + residents.size() + " · Hungry: " + hungry,
                "Bread stock: " + SettlementFoodPolicy.breadStock(state, home.id()),
                "Expeditions: " + groups), 0xF4B942);
    }
    private static String money(FixedScalar value) {
        return BigDecimal.valueOf(value.raw(), 6).stripTrailingZeros().toPlainString();
    }
}
