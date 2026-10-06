package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Resource/title declaration is independent of the facility's operational settlement. */
public record ProductionRights(Mode mode, GoodsTradeParty resourceOwner, SubjectId destinationContainerId) {
    public enum Mode {
        BUYER_OWNED_SERVICE(1), COMPANY_OWN_ACCOUNT(2);
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
        if (mode == Mode.COMPANY_OWN_ACCOUNT && resourceOwner.kind() != EconomicOwnerKind.COMPANY
                || mode == Mode.BUYER_OWNED_SERVICE && resourceOwner.kind() != EconomicOwnerKind.SETTLEMENT_TREASURY)
            throw new IllegalArgumentException("production mode has a forged nominal owner");
    }
    public static ProductionRights publicService(SubjectId settlement) {
        return new ProductionRights(Mode.BUYER_OWNED_SERVICE, GoodsParticipantDeclarations.publicParty(settlement), FrontierWorldState.depotId(settlement));
    }
    public void validate(FrontierWorldState state, ProductionJob job) {
        validate(state.inventory(), state.companies(), job);
    }
    public void validate(ExactInventory inventory, CompanyRegistry companies, ProductionJob job) {
        resourceOwner.validate(inventory.economics());
        if (!destinationContainerId.equals(FrontierWorldState.depotId(job.settlementId())))
            throw new IllegalArgumentException("current production capability requires its declared home depot");
        if (mode == Mode.BUYER_OWNED_SERVICE && !resourceOwner.id().equals(job.settlementId()))
            throw new IllegalArgumentException("public manufacturing declared a foreign buyer");
        if (mode == Mode.COMPANY_OWN_ACCOUNT) {
            var company = companies.companies().get(resourceOwner.id());
            if (company == null || !company.settlementId().equals(job.settlementId()))
                throw new IllegalArgumentException("own-account manufacturing lost its exact company home");
        }
    }
}
