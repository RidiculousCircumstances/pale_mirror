package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class GoodsParticipantDeclarationsTest {
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
