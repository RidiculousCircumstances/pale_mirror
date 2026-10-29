package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Legal participant facts, independent of quote reviews and account balances.
 * Commercial validation retains its owner; the shared index only addresses reads. */
final class GoodsTradeParticipantValidation {
    private record Identity(GoodsTradeParty party, GoodsPolicyKind policy, SubjectId settlement,
                            List<GoodsParticipant.Counterparty> known) { }
    private record Context(FrontierBootstrap bootstrap, Map<SubjectId, Identity> participants,
                           Map<SubjectId, EconomicOwnerKind> accounts, Map<SubjectId, Company> companies) { }
    private static final ImmutableRegistryView<SubjectId, GoodsParticipant, Identity> IDENTITIES =
            new ImmutableRegistryView<>(GoodsParticipantState.MAX_PARTICIPANTS, Objects::equals);
    private static final ImmutableRegistryView<SubjectId, EconomicAccount, EconomicOwnerKind> KINDS =
            new ImmutableRegistryView<>(EconomicLedger.MAX_ACCOUNTS);
    private static final ImmutableReferenceIndex.Source<Context, SubjectId, Identity> PARTICIPANTS =
            new ImmutableReferenceIndex.Source<>(Context::participants);
    private static final ImmutableReferenceIndex.Source<Context, SubjectId, EconomicOwnerKind> ACCOUNTS =
            new ImmutableReferenceIndex.Source<>(Context::accounts);
    private static final ImmutableReferenceIndex.Source<Context, SubjectId, Company> COMPANIES =
            new ImmutableReferenceIndex.Source<>(Context::companies);
    private static final ImmutableReferenceIndex<Context, SubjectId, Identity> CHECKS = new ImmutableReferenceIndex<>(
            GoodsParticipantState.MAX_PARTICIPANTS, 2 * GoodsParticipant.MAX_KNOWN + 2,
            List.of(PARTICIPANTS, ACCOUNTS, COMPANIES), (context, participant) -> {
                var reads = new ArrayList<ImmutableReferenceIndex.Read<Context>>();
                reads.add(ACCOUNTS.at(participant.party().id()));
                if (participant.policy() == GoodsPolicyKind.OWN_ACCOUNT_COMPANY)
                    reads.add(COMPANIES.at(participant.party().id()));
                for (var peer : participant.known()) {
                    reads.add(ACCOUNTS.at(peer.party().id())); reads.add(PARTICIPANTS.at(peer.party().id()));
                }
                return List.copyOf(reads);
            }, (context, id, participant) -> validate(context, participant));
    private GoodsTradeParticipantValidation() { }
    static void validate(FrontierWorldState state) {
        var participants = IDENTITIES.project(state.bootstrap(), state.companies().goodsTrade().participants().participants(),
                (id, p) -> new Identity(p.party(), p.policy(), p.settlementId(), p.known()));
        var accounts = KINDS.project(state.bootstrap(), state.inventory().economics().accounts(), (id, account) -> account.ownerKind());
        CHECKS.validate(state.bootstrap(), participants, new Context(state.bootstrap(), participants, accounts, state.companies().companies()));
    }
    private static void validate(Context context, Identity participant) {
        participant.policy().validate(participant.party());
        if (context.accounts().get(participant.party().id()) != participant.party().kind())
            throw new IllegalArgumentException("participant lost its nominal economic owner");
        FrontierWorldStateSupport.settlement(context.bootstrap(), participant.settlement());
        if (participant.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT && !participant.party().id().equals(participant.settlement()))
            throw new IllegalArgumentException("public goods policy declared another settlement endpoint");
        if (participant.policy() == GoodsPolicyKind.OWN_ACCOUNT_COMPANY) {
            var company = context.companies().get(participant.party().id());
            if (company == null || !company.settlementId().equals(participant.settlement()))
                throw new IllegalArgumentException("company policy lost its exact legal home");
        }
        for (var peer : participant.known()) {
            if (context.accounts().get(peer.party().id()) != peer.party().kind())
                throw new IllegalArgumentException("known goods peer has forged nominal economic identity");
            var known = context.participants().get(peer.party().id());
            if (known == null || !known.party().equals(peer.party()) || !known.settlement().equals(peer.settlementId()))
                throw new IllegalArgumentException("known peer has a foreign legal home");
        }
    }
}
