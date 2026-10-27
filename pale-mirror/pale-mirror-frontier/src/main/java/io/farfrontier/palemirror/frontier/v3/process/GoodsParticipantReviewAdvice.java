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
        var peerInputs = new HashMap<SubjectId, SettlementEconomicPlanningState>();
        basis.keySet().forEach(id -> peerInputs.put(id, state.companies().goodsTrade().participants().economicState(id)));
        var ownerInput = state.companies().goodsTrade().participants().economicState(action.subject());
        var navigation = state.navigationGeometry(); var physical = state.physicalDeltas();
        return () -> {
            var prepared = SettlementEconomicPlanPreparation.review(state, action, at);
            return new ScheduledAdvisory.Completion<FrontierWorldState>() {
                private String rejection = "NONE";
                @Override public String rejectionReason() { return rejection; }
                private Optional<ScheduledPreparation> reject(String reason) { rejection = reason; return Optional.empty(); }
                @Override public Optional<ScheduledPreparation> revalidate(FrontierWorldState current, SimInstant executionAt) {
                    if (current.bootstrap() != bootstrap) return reject("BOOTSTRAP");
                    if (!participant.equals(current.companies().goodsTrade().participants().participants().get(action.subject())))
                        return reject("OWNER_TERMS");
                    var currentInput = current.companies().goodsTrade().participants().economicState(action.subject());
                    long inputShift = Math.subtractExact(currentInput.inputRevision(), ownerInput.inputRevision());
                    if (inputShift != 0 && !currentInput.onlyMarketChangesSince(ownerInput)) return reject("OWNER_INPUT");
                    if (current.navigationGeometry() != navigation || current.physicalDeltas() != physical)
                        return reject("GEOMETRY");
                    if (!basis.equals(peers(current, participant))) return reject("PEER_IDENTITY");
                    if (executionAt.compareTo(at) < 0) return reject("EXECUTION_TIME");
                    if (prepared.publication().filter(plan -> !SettlementEconomicPublication.peerEnvelopesCurrent(
                            current.companies().goodsTrade().participants(), plan)).isPresent()) return reject("BUYER_PLAN");
                    if (prepared.publication().filter(plan -> plan.peerVersions().keySet().stream().anyMatch(id -> {
                        var captured = peerInputs.get(id);
                        var latest = current.companies().goodsTrade().participants().economicState(id);
                        return captured == null || latest.inputRevision() != captured.inputRevision()
                                && !latest.onlyMarketChangesSince(captured);
                    })).isPresent()) return reject("BUYER_INPUT");
                    if (executionAt.ticks() >= prepared.publication().map(SettlementEconomicPlan::reviewAtTick)
                            .orElse(Math.addExact(at.ticks(), bootstrap.ruleset().goodsTrade().reviewInterval())))
                        return reject("EXPIRED");
                    rejection = "NONE";
                    var refreshed = inputShift == 0 ? prepared : new SettlementEconomicPlanPreparation.Review(prepared.atTick(),
                            prepared.prices(), prepared.publication().map(plan -> plan.withInputRevision(Math.addExact(plan.inputRevision(), inputShift))));
                    return Optional.of(() -> GoodsParticipantProcess.prepare(current, action, executionAt, refreshed));
                }
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
