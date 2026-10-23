package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Current planted crop and admitted sowing area, not hypothetical market expansion. */
final class FieldEquipmentDemand implements EquipmentDemandPort {
    @Override public Key key() { return Key.FIELD; }
    private static final ImmutableKeyedInputView<SubjectId, List<EquipmentWorkDemand>> VIEWS = new ImmutableKeyedInputView<>(256);
    @Override public List<EquipmentWorkDemand> read(FrontierWorldState state, SubjectId home) {
        return VIEWS.get(state.bootstrap(), home, List.of(state.resourceSites().sites(), state.resourceSites().cycles()), () -> project(state, home));
    }
    private static List<EquipmentWorkDemand> project(FrontierWorldState state, SubjectId home) {
        long wear = 0; long cells = 0;
        for (var site : state.resourceSites().sites().values()) {
            if (!state.resourceSite(site.siteId()).settlementId().equals(home)
                    || site.phase() == ResourceSitePhase.DESTROYED || site.phase() == ResourceSitePhase.CONFLICT) continue;
            var cycle = state.resourceSites().cycle(site.siteId());
            for (var cell : cycle.layout().cells()) {
                var condition = cycle.cell(cell.id());
                if (!condition.soil().cultivable() && condition.soil() != ResourceFieldCycle.Soil.UNKNOWN) continue;
                if (condition.crop() == ResourceFieldCycle.Crop.MATURE || condition.crop() == ResourceFieldCycle.Crop.GROWING
                        || cycle.sowing().cells().contains(cell.id())) {
                    cells++; wear = Math.addExact(wear, FieldHarvestExtraction.DEFINITION.toolRequirement().wear());
                    if (condition.soil().untilled() || condition.soil() == ResourceFieldCycle.Soil.UNKNOWN) wear++;
                }
            }
        }
        int concurrency = Math.toIntExact(Math.min(cells, state.bootstrap().ruleset().labour().entries()
                .get(ResidentWorkKind.AGRICULTURE).targetWorkers()));
        return concurrency == 0 ? List.of() : List.of(new EquipmentWorkDemand("field", List.of(
                FieldHarvestExtraction.DEFINITION.toolRequirement(), new ToolRequirement(ToolAbilities.TILL, 0, 1)), concurrency,
                Math.max(concurrency, wear), state.bootstrap().ruleset().workCatalog()
                        .require(FieldHarvestExtraction.ITEM, WorkOperation.HARVEST).workUnits()));
    }
}
