package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Producer of explicit initial lawful knowledge. The market cannot discover arbitrary world stocks. */
public final class GoodsParticipantDeclarations {
    private GoodsParticipantDeclarations() { }
    public static ShipmentEndpoint.Depot endpoint(Settlement settlement) {
        var depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .reduce((left, right) -> { throw new IllegalArgumentException("ambiguous settlement depot"); }).orElseThrow();
        return new ShipmentEndpoint.Depot(settlement.id(), depot.id(),
                FrontierWorldState.depotId(settlement.id()), SettlementDepotServicePort.forDepot(depot).loadingSurface());
    }
    public static GoodsParticipantState initial(FrontierBootstrap bootstrap) {
        var participants = GoodsParticipantState.empty();
        var settlements = bootstrap.settlements().stream().sorted(Comparator.comparing(Settlement::id)).toList();
        // The initial acquaintance chain follows stable settlement IDs; later knowledge
        // must be declared explicitly. This is initial knowledge, not live inventory discovery.
        for (int i = 0; i < settlements.size(); i++) {
            var settlement = settlements.get(i);
            var peers = new ArrayList<GoodsParticipant.Counterparty>();
            if (i > 0) peers.add(publicPeer(settlements.get(i - 1)));
            if (i + 1 < settlements.size()) peers.add(publicPeer(settlements.get(i + 1)));
            participants = participants.register(new GoodsParticipant(publicParty(settlement.id()),
                    GoodsPolicyKind.PUBLIC_SETTLEMENT, endpoint(settlement), peers, 0, "NOT_REVIEWED", 0));
        }
        return participants;
    }
    public static GoodsParticipantState registerCompany(FrontierBootstrap bootstrap, GoodsParticipantState state, Company company) {
        var home = FrontierWorldStateSupport.settlement(bootstrap, company.settlementId());
        var publicParticipant = state.participants().get(home.id());
        if (publicParticipant == null) throw new IllegalArgumentException("company has no declared home market");
        var ownParty = new GoodsTradeParty(company.id(), EconomicOwnerKind.COMPANY);
        var peers = new ArrayList<>(publicParticipant.known()); peers.add(publicPeer(home));
        var next = state.register(new GoodsParticipant(ownParty, GoodsPolicyKind.OWN_ACCOUNT_COMPANY,
                endpoint(home), peers, 0, "NOT_REVIEWED", company.registeredAtTick()));
        // Institutions may introduce their company, but do not grant it public inventory.
        var map = new HashMap<>(next.participants());
        for (var entry : map.entrySet()) {
            var participant = entry.getValue();
            if (participant.policy() != GoodsPolicyKind.PUBLIC_SETTLEMENT
                    || !participant.party().id().equals(home.id()) && participant.known().stream()
                        .noneMatch(peer -> peer.party().equals(publicParty(home.id())))) continue;
            var known = new ArrayList<>(participant.known()); known.add(new GoodsParticipant.Counterparty(ownParty, endpoint(home)));
            entry.setValue(new GoodsParticipant(participant.party(), participant.policy(), participant.endpoint(),
                    known, participant.reviewRevision(), participant.decision(), participant.reviewedAtTick()));
        }
        return new GoodsParticipantState(map);
    }
    private static GoodsParticipant.Counterparty publicPeer(Settlement settlement) {
        return new GoodsParticipant.Counterparty(publicParty(settlement.id()), endpoint(settlement));
    }
    public static GoodsTradeParty publicParty(SubjectId settlement) {
        return new GoodsTradeParty(settlement, EconomicOwnerKind.SETTLEMENT_TREASURY);
    }
}
