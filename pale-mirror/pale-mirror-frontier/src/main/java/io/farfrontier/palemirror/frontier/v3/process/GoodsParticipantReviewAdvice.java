package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Economic owner of disposable forecasts. Advice contains neither reservations,
 * actor assignments nor transactions. Exact trade execution starts from current state. */
final class GoodsParticipantReviewAdvice {
    private record Peer(GoodsTradeParty party, GoodsPolicyKind policy, SubjectId settlement,
            List<GoodsParticipant.Counterparty> known) { }
    static ScheduledAdvisory<FrontierWorldState> capture(FrontierWorldState state, ScheduledAction action, SimInstant at) {
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(action.subject()), "economic advice owner");
        var basis = peers(state, participant); var bootstrap = state.bootstrap();
        var peerInputs = new HashMap<SubjectId, Long>();
        basis.keySet().forEach(id -> peerInputs.put(id, state.companies().goodsTrade().participants().economicState(id).inputRevision()));
        long ownerInput = state.companies().goodsTrade().participants().economicState(action.subject()).inputRevision();
        var navigation = state.navigationGeometry(); var physical = state.physicalDeltas();
        return () -> {
            var prepared = SettlementEconomicPlanPreparation.review(state, action, at);
            return (current, executionAt) -> {
                if (current.bootstrap() != bootstrap
                        || !participant.equals(current.companies().goodsTrade().participants().participants().get(action.subject()))
                        || current.companies().goodsTrade().participants().economicState(action.subject()).inputRevision() != ownerInput
                        || current.navigationGeometry() != navigation || current.physicalDeltas() != physical
                        || !basis.equals(peers(current, participant)) || executionAt.compareTo(at) < 0
                        || prepared.publication().filter(plan -> !SettlementEconomicPublication.peerEnvelopesCurrent(
                                current.companies().goodsTrade().participants(), plan)
                            || plan.peerVersions().keySet().stream().anyMatch(id -> !Objects.equals(peerInputs.get(id),
                                current.companies().goodsTrade().participants().economicState(id).inputRevision()))).isPresent()
                        || executionAt.ticks() >= prepared.publication().map(SettlementEconomicPlan::reviewAtTick)
                                .orElse(Math.addExact(at.ticks(), bootstrap.ruleset().goodsTrade().reviewInterval())))
                    return Optional.empty();
                return Optional.of(() -> GoodsParticipantProcess.prepare(current, action, executionAt, prepared));
            };
        };
    }
    private static Map<SubjectId, Peer> peers(FrontierWorldState state, GoodsParticipant owner) {
        var registry = state.companies().goodsTrade().participants();
        var result = new TreeMap<SubjectId, Peer>();
        var ids = new TreeSet<SubjectId>(); ids.add(owner.party().id()); owner.known().forEach(peer -> ids.add(peer.party().id()));
        for (var id : ids) {
            var participant = registry.participants().get(id);
            if (participant == null) continue; // Presence/absence is part of this exact declared-peer map.
            // A peer's changing stock is a dated market observation, never a
            // reservation. Peer prices are also dated forecast inputs. An actual
            // conditional offer retains its buyer's exact plan/input envelope;
            // trade admission independently reads current quotes and resources.
            result.put(id, new Peer(participant.party(), participant.policy(), participant.settlementId(), participant.known()));
        }
        return Map.copyOf(result);
    }
    private GoodsParticipantReviewAdvice() { }
}
