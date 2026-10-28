package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class GoodsParticipantDeclarationsTest {
    @Test void indexedKnowledgeRetainsNominalKindHomeAndIndependentPredecessors() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:peer-knowledge"), 41L));
        var participant = state.companies().goodsTrade().participants().participants().values().iterator().next();
        var address = participant.known().getFirst();
        var peer = state.companies().goodsTrade().participants().participants().get(address.party().id());
        assertTrue(participant.knows(peer.party()));
        assertTrue(GoodsBuyerDemand.mutuallyKnown(participant, peer));
        assertFalse(participant.knows(peer.party(), participant.settlementId()));
        assertFalse(participant.knows(new GoodsTradeParty(peer.party().id(), EconomicOwnerKind.COMPANY)));
        var unknown = new GoodsParticipant(participant.party(), participant.policy(), participant.settlementId(),
                java.util.List.of(), participant.reviewRevision(), participant.decision(), participant.reviewedAtTick(), participant.prices());
        assertFalse(unknown.knows(peer.party()));
        assertFalse(GoodsBuyerDemand.mutuallyKnown(peer, unknown));
        assertTrue(GoodsBuyerDemand.mutuallyKnown(participant, peer), "a knowledge branch cannot change its predecessor");
    }
    @Test void economicProjectionIsReusedOnlyForTheExactImmutableState() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:market-view"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r13")));
        var home = state.bootstrap().settlements().getFirst().id();
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var before = GoodsParticipantView.observe(state, participant);
        assertSame(before, GoodsParticipantView.observe(state, participant));
        var paid = state.withInventory(state.inventory().withEconomics(state.inventory().economics().transfer(
                home, participant.known().getFirst().party().id(), io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(1))));
        var after = GoodsParticipantView.observe(paid, participant);
        assertEquals(before.availableMoney().minus(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(1)), after.availableMoney());
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(paid));
        assertEquals(after, GoodsParticipantView.observe(recovered, participant));
        assertEquals(before, GoodsParticipantView.observe(state, participant));
    }
    @Test void allPublicSettlementsKnowEachOthersExactMarketAddressesFromBootstrap() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:public-markets"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r13")));
        var participants = GoodsParticipantDeclarations.initial(state.bootstrap());
        assertEquals(12, participants.participants().size());
        for (var settlement : state.bootstrap().settlements()) {
            var participant = participants.participants().get(settlement.id());
            assertEquals(state.bootstrap().settlements().stream().filter(other -> !other.id().equals(settlement.id()))
                    .map(other -> new GoodsParticipant.Counterparty(GoodsParticipantDeclarations.publicParty(other.id()),
                            other.id())).collect(Collectors.toSet()),
                    java.util.Set.copyOf(participant.known()));
            assertEquals(11, participant.known().size());
        }
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
}
