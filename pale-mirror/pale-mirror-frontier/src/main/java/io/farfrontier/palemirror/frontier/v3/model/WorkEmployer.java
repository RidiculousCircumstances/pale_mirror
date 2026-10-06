package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;

/** Explicit operational authority and legal home, not a wage payer or activity controller. */
public record WorkEmployer(SubjectId id, EconomicOwnerKind kind, SubjectId settlementId) {
    public WorkEmployer {
        Objects.requireNonNull(id); Objects.requireNonNull(kind); Objects.requireNonNull(settlementId);
        if (kind != EconomicOwnerKind.SETTLEMENT_TREASURY && kind != EconomicOwnerKind.COMPANY)
            throw new IllegalArgumentException("work employer must be a declared settlement or company");
        if (kind == EconomicOwnerKind.SETTLEMENT_TREASURY && !id.equals(settlementId))
            throw new IllegalArgumentException("settlement employer must declare its own home");
    }
    public static WorkEmployer settlement(SubjectId id) {
        return new WorkEmployer(id, EconomicOwnerKind.SETTLEMENT_TREASURY, id);
    }
    public static WorkEmployer company(Company company) {
        return new WorkEmployer(company.id(), EconomicOwnerKind.COMPANY, company.settlementId());
    }
    public void validate(EconomicLedger economics, Map<SubjectId, Company> companies) {
        validateIdentity(economics, companies);
        var account = economics.require(id);
        if (account.status() != EconomicAccountStatus.ACTIVE)
            throw new IllegalArgumentException("work employer lost its declared active legal account");
    }
    public void validateIdentity(EconomicLedger economics, Map<SubjectId, Company> companies) {
        if (economics.require(id).ownerKind() != kind)
            throw new IllegalArgumentException("work authority lost its declared legal identity");
        if (kind == EconomicOwnerKind.COMPANY) {
            var company = companies.get(id);
            if (company == null || company.status() != CompanyStatus.ACTIVE || !company.settlementId().equals(settlementId))
                throw new IllegalArgumentException("work employer lost its exact registered company home");
        }
    }
}
