package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Resource/title declaration is independent of the facility's operational settlement. */
public record ProductionRights(Mode mode, GoodsTradeParty resourceOwner, SubjectId destinationContainerId,
                               WorkEmployer employer) {
    public enum Mode {
        BUYER_OWNED_SERVICE(1), COMPANY_OWN_ACCOUNT(2), PUBLIC_PRODUCTION(3);
        private final int tag;
        Mode(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Mode fromWireTag(int tag) {
            for (var mode : values()) if (mode.tag == tag) return mode;
            throw new IllegalArgumentException("unknown production ownership mode");
        }
    }
    public ProductionRights {
        Objects.requireNonNull(mode); Objects.requireNonNull(resourceOwner); Objects.requireNonNull(destinationContainerId);
        Objects.requireNonNull(employer);
        if (mode == Mode.COMPANY_OWN_ACCOUNT && resourceOwner.kind() != EconomicOwnerKind.COMPANY
                || mode == Mode.BUYER_OWNED_SERVICE && resourceOwner.kind() != EconomicOwnerKind.SETTLEMENT_TREASURY)
            throw new IllegalArgumentException("production mode has a forged nominal owner");
        if (mode == Mode.COMPANY_OWN_ACCOUNT && (employer.kind() != EconomicOwnerKind.COMPANY
                || !employer.id().equals(resourceOwner.id())))
            throw new IllegalArgumentException("own-account manufacturing must declare its exact company employer");
        if (mode == Mode.PUBLIC_PRODUCTION && (resourceOwner.kind() != EconomicOwnerKind.SETTLEMENT_TREASURY
                || employer.kind() != EconomicOwnerKind.SETTLEMENT_TREASURY
                || !resourceOwner.id().equals(employer.id())))
            throw new IllegalArgumentException("public production requires its exact settlement authority and resource title");
        if (mode == Mode.BUYER_OWNED_SERVICE && employer.kind() != EconomicOwnerKind.COMPANY)
            throw new IllegalArgumentException("commercial service requires an explicitly declared company");
    }
    public static ProductionRights publicService(SubjectId settlement) {
        return new ProductionRights(Mode.PUBLIC_PRODUCTION, GoodsParticipantDeclarations.publicParty(settlement),
                FrontierWorldState.depotId(settlement), WorkEmployer.settlement(settlement));
    }
    public void validate(FrontierWorldState state, ProductionJob job) {
        validate(state.inventory(), state.companies(), job);
    }
    public void validate(ExactInventory inventory, CompanyRegistry companies, ProductionJob job) {
        if (mode == Mode.PUBLIC_PRODUCTION) {
            resourceOwner.validateTitle(inventory.economics());
            employer.validateIdentity(inventory.economics(), companies.companies());
        } else {
            resourceOwner.validate(inventory.economics());
            employer.validate(inventory.economics(), companies.companies());
        }
        if (!employer.settlementId().equals(job.settlementId()))
            throw new IllegalArgumentException("production declared a foreign employer home");
        if (!destinationContainerId.equals(FrontierWorldState.depotId(job.settlementId())))
            throw new IllegalArgumentException("current production capability requires its declared home depot");
        if ((mode == Mode.BUYER_OWNED_SERVICE || mode == Mode.PUBLIC_PRODUCTION) && !resourceOwner.id().equals(job.settlementId()))
            throw new IllegalArgumentException("public manufacturing declared a foreign buyer");
        if (mode == Mode.COMPANY_OWN_ACCOUNT) {
            var company = companies.companies().get(resourceOwner.id());
            if (company == null || !company.settlementId().equals(job.settlementId()))
                throw new IllegalArgumentException("own-account manufacturing lost its exact company home");
        }
    }
}
