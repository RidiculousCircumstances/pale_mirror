package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Renewable-source owner estimates a complete crop cycle, not the near-complete current crop. */
final class FieldReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.FIELD; }
    private static final ImmutableKeyedInputView<SubjectId, Facts> VIEWS = new ImmutableKeyedInputView<>(256);
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        return VIEWS.get(state.bootstrap(), home, List.of(state.resourceSites(), state.inventory().items(), state.inventory().containers(),
                state.inventory().surfaces(), state.replicaCustody(), state.actorLocations(),
                state.humanPopulation().residents(), state.humanPopulation().schedule(home),
                state.strategicPlans().decisionAuthorities(), WorkEquipmentClaimComposition.reserved(state)), () -> project(state, home));
    }
    private static Facts project(FrontierWorldState state, SubjectId home) {
        boolean usable = state.resourceSites().sites().values().stream()
                .filter(site -> state.resourceSite(site.siteId()).settlementId().equals(home))
                .filter(site -> site.phase() != ResourceSitePhase.DESTROYED && site.phase() != ResourceSitePhase.CONFLICT)
                .anyMatch(site -> {
                    var cycle = state.resourceSites().cycles().get(site.siteId());
                    return cycle.layout().cells().stream().anyMatch(cell -> {
                        var soil = cycle.cell(cell.id()).soil();
                        return soil.cultivable()
                                || soil == ResourceFieldCycle.Soil.UNKNOWN && site.growthEpoch() == 0;
                    });
                });
        if (!usable) return new Facts(Map.of(), List.of(), Map.of());
        var workers = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.AGRICULTURE).stream()
                .filter(id -> state.actorLocations().get(id).condition().status() == ActorLifeStatus.ALIVE)
                .map(id -> state.humanPopulation().resident(id)).toList();
        var estimate = new FieldWorkForecast(state, home, workers);
        long lead = state.resourceSites().sites().values().stream()
                .filter(site -> state.resourceSite(site.siteId()).settlementId().equals(home))
                .filter(site -> site.phase() != ResourceSitePhase.DESTROYED && site.phase() != ResourceSitePhase.CONFLICT)
                .flatMap(site -> state.resourceSites().cycle(site.siteId()).layout().cells().stream())
                .mapToLong(cell -> estimate.lead(new FieldSupplyCapacity.Cell(cell.crop(),
                        new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.DIRT, ResourceFieldCycle.Crop.ABSENT, 0, false, false), true)))
                .min().orElseThrow();
        return new Facts(Map.of(), List.of(), Map.of("minecraft:wheat", new Timing(lead, TimingBasis.LOCAL_ESTIMATE)));
    }
}
