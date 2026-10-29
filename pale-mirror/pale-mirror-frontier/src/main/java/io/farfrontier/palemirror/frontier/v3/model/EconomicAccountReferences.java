package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** The economic account declares its legal kind; this checks only the matching
 * declared identity's existence, never discovers or assigns a legal owner. */
final class EconomicAccountReferences {
    private record Context(Map<SubjectId, Company> companies, Map<SubjectId, ResidentProfile> residents) { }
    private static final ImmutableReferenceIndex.Source<Context, SubjectId, Company> COMPANIES =
            new ImmutableReferenceIndex.Source<>(Context::companies, (before, after) -> (before == null) == (after == null));
    private static final ImmutableReferenceIndex.Source<Context, SubjectId, ResidentProfile> RESIDENTS =
            new ImmutableReferenceIndex.Source<>(Context::residents, (before, after) -> (before == null) == (after == null));
    private static final ImmutableReferenceIndex<Context, SubjectId, EconomicAccount> CHECKS = new ImmutableReferenceIndex<>(
            EconomicLedger.MAX_ACCOUNTS, 1, List.of(COMPANIES, RESIDENTS),
            (context, account) -> switch (account.ownerKind()) {
                case COMPANY -> List.of(COMPANIES.at(account.ownerId()));
                case RESIDENT -> List.of(RESIDENTS.at(account.ownerId()));
                case SETTLEMENT_TREASURY, HIVE_COLLECTIVE, PUBLIC_INFRASTRUCTURE -> List.of();
            }, (context, id, account) -> {
                if (account.ownerKind() == EconomicOwnerKind.COMPANY && !context.companies().containsKey(account.ownerId()))
                    throw new IllegalArgumentException("company account must have one registered legal company");
                if (account.ownerKind() == EconomicOwnerKind.RESIDENT && !context.residents().containsKey(account.ownerId()))
                    throw new IllegalArgumentException("resident resource-title registration must name a known person");
            });
    private EconomicAccountReferences() { }
    static void validate(FrontierBootstrap bootstrap, Map<SubjectId, EconomicAccount> accounts,
                         Map<SubjectId, Company> companies, Map<SubjectId, ResidentProfile> residents) {
        try (var ignored = ImmutableValidationScope.detail("owned.economic-account-references")) {
            CHECKS.validate(bootstrap, accounts, new Context(companies, residents));
        }
    }
}
