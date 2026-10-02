package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Comparator;
import java.util.Optional;

/** Profile owner proposes operations; it never commits goals or takes a resident body. */
final class SettlementFieldPlanner implements SettlementOperationPlanner {
    @Override public String id() { return "frontier:field"; }
    @Override public Assessment assess(FrontierWorldState state, Settlement settlement) {
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        Optional<ResourceSite> recoverableField = state.resourceSiteDescriptors().values().stream()
                .filter(site -> site.settlementId().equals(settlement.id()))
                .filter(site -> state.resourceSites().site(site.id()).phase() == ResourceSitePhase.READY)
                .filter(site -> state.structureConditions().get(site.facilityId()) == StructureCondition.INTACT)
                .min(Comparator.comparing(ResourceSite::id));
        if (recoverableField.isPresent() && state.firstFreeContainerSlot(depot).isPresent()
                && FrontierWorldStateSupport.availableFieldResident(state, settlement.id(), ResidentProfession.AGRICULTURAL_WORKER).isPresent())
            return Assessment.offer(settlement.id(), new StrategicOperationProposal(
                StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, Optional.empty(),
                Optional.of(recoverableField.orElseThrow().id()), FixedScalar.SCALE), Priority.BACKGROUND);
        return Assessment.empty();
    }
}
