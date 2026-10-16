package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Renewable-source owner estimates a complete crop cycle, not the near-complete current crop. */
final class FieldReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.FIELD; }
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        boolean usable = state.resourceSites().sites().values().stream()
                .filter(site -> state.resourceSite(site.siteId()).settlementId().equals(home))
                .filter(site -> site.phase() != ResourceSitePhase.DESTROYED && site.phase() != ResourceSitePhase.CONFLICT)
                .anyMatch(site -> {
                    var cycle = state.resourceSites().cycles().get(site.siteId());
                    return cycle.layout().cells().stream().anyMatch(cell -> {
                        var soil = cycle.cell(cell.id()).soil();
                        return soil == ResourceFieldCycle.Soil.FARMLAND || soil == ResourceFieldCycle.Soil.DIRT
                                || soil == ResourceFieldCycle.Soil.UNKNOWN && site.growthEpoch() == 0;
                    });
                });
        if (!usable) return new Facts(Map.of(), List.of(), Map.of());
        long growth = state.resourceSites().sites().values().stream()
                .filter(site -> state.resourceSite(site.siteId()).settlementId().equals(home))
                .mapToLong(site -> FieldAgronomy.fullGrowthTicks(state.bootstrap(), state.resourceSites().cycle(site.siteId()).layout()))
                .min().orElseThrow();
        long lead = Math.addExact(growth, state.humanPopulation().schedule(home).dayTicks());
        return new Facts(Map.of(), List.of(), Map.of("minecraft:wheat", new Timing(lead, TimingBasis.LOCAL_ESTIMATE)));
    }
}
