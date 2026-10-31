package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Exact identities/terms surrounding dated market observations, shared by review stages. */
final class GoodsParticipantPeers {
    record Declaration(GoodsTradeParty party, GoodsPolicyKind policy, SubjectId settlement,
            List<GoodsParticipant.Counterparty> known) { }
    static Map<SubjectId, Declaration> read(FrontierWorldState state, GoodsParticipant owner) {
        var registry = state.companies().goodsTrade().participants();
        var result = new TreeMap<SubjectId, Declaration>();
        var ids = new TreeSet<SubjectId>(); ids.add(owner.party().id()); owner.known().forEach(peer -> ids.add(peer.party().id()));
        for (var id : ids) {
            var participant = registry.participants().get(id);
            if (participant == null) continue; // Presence/absence is part of this exact declared-peer map.
            // A peer's changing stock is a dated market observation, never a
            // reservation. Peer prices are also dated forecast inputs. An actual
            // conditional offer retains its buyer's exact plan/input envelope;
            // trade admission independently reads current quotes and resources.
            result.put(id, new Declaration(participant.party(), participant.policy(), participant.settlementId(), participant.known()));
        }
        return Map.copyOf(result);
    }
    private GoodsParticipantPeers() { }
}
