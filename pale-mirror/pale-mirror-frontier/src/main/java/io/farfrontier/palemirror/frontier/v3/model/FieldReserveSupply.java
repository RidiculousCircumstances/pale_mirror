package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Renewable-source owner estimates a complete crop cycle, not the near-complete current crop. */
final class FieldReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.FIELD; }
    private static final ImmutableKeyedInputView<SubjectId, Facts> VIEWS = new ImmutableKeyedInputView<>(256);
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        var inputs = FieldWorkForecast.permittedInputs(state, home);
        return VIEWS.get(state.bootstrap(), home, inputs.keys(), () -> project(state, home, inputs));
    }
    private static Facts project(FrontierWorldState state, SubjectId home, FieldWorkForecast.PlanningInputs inputs) {
        var needs = new TreeMap<String, Need>(); var timing = new TreeMap<String, Timing>();
        for (var policy : state.bootstrap().ruleset().fields().crops()) {
            var kind = policy.kind();
            if (policy.minimumStock() > 0) needs.put(kind.resourceKind(), new Need(Rate.ZERO, policy.minimumStock()));
            if (SupplyPlanningWindow.dailyWork(state.humanPopulation().schedule(home)) == 0) continue;
            var estimate = new FieldWorkForecast(state, home, inputs.workers(), kind, true,
                    FieldWorkForecast.storagePositions(state, home, kind));
            var lead = state.resourceSites().siteIdsIn(home).stream()
                    .filter(id -> state.resourceSite(id).kind() == kind).map(state.resourceSites()::site)
                    .filter(site -> site.phase() != ResourceSitePhase.DESTROYED && site.phase() != ResourceSitePhase.CONFLICT)
                    .flatMap(site -> {
                        var cycle = state.resourceSites().cycle(site.siteId());
                        return cycle.layout().cells().stream().filter(cell -> cycle.cell(cell.id()).soil().cultivable()
                                || cycle.cell(cell.id()).soil() == ResourceFieldCycle.Soil.UNKNOWN && site.growthEpoch() == 0);
                    })
                    .mapToLong(cell -> estimate.lead(new FieldSupplyCapacity.Cell(cell.crop(),
                            new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.DIRT, ResourceFieldCycle.Crop.ABSENT, 0, false, false), true, kind)))
                    .min();
            if (lead.isPresent()) timing.put(kind.resourceKind(), new Timing(lead.getAsLong(), TimingBasis.LOCAL_ESTIMATE));
        }
        return new Facts(needs, List.of(), timing);
    }
}
