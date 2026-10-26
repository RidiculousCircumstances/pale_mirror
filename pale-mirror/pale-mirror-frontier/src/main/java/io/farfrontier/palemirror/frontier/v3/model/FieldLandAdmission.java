package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.CultivationRules;
import java.util.*;

/** Sole land-admission boundary: extends idle exact geometry or declares another site; never prepares soil. */
final class FieldLandAdmission {
    record Result(ResourceSiteState sites, List<SubjectId> declared, boolean deferred) {
        Result { declared = List.copyOf(declared); }
    }
    static Result admit(FrontierWorldState state, ResourceSiteState sites, SubjectId home, List<ResourceSite> fields,
            FieldDevelopmentLand.Survey survey, CultivationRules rules) {
        survey.require(state, home, fields);
        var additions = survey.cells();
        if (additions.size() > rules.maximumNewCells()) throw new IllegalArgumentException("cultivation exceeds its development turn");
        var inputSites = sites;
        int area = sites.declarations().values().stream().filter(d -> d.settlementId().equals(home))
                .mapToInt(d -> inputSites.cycle(d.id()).layout().cells().size()).sum();
        if (area + additions.size() > rules.maximumCells()) throw new IllegalArgumentException("cultivation exceeds its area bound");
        if (additions.isEmpty()) return new Result(sites, List.of(), false);
        var declared = new ArrayList<SubjectId>();
        int count = (int) sites.declarations().values().stream().filter(d -> d.settlementId().equals(home)).count();
        ResourceSite attach = null;
        for (var field : fields) {
            var lifecycle = sites.site(field.id());
            if (!lifecycle.harvestJobs().isEmpty() || lifecycle.preparationWork().isPresent() || sites.hasPendingWorldChange(field.id())
                    || !sites.cycle(field.id()).pendingPlayerBreaks().isEmpty()
                    || lifecycle.harvestLineages().values().stream().anyMatch(ResourceSiteHarvestLineage::receiptPending)) continue;
            attach = field; break;
        }
        // A work area may contain gaps in cultivable soil. The exact survey already
        // proves access through the common walking graph, including those gaps.
        if (attach == null && count >= rules.maximumFields()) return new Result(sites, List.of(), true);
        SubjectId siteId;
        ResourceFieldLayout old = attach == null ? null : sites.cycle(attach.id()).layout();
        long nextId = old == null ? 1 : old.nextCellId();
        var addedCells = new ArrayList<ResourceFieldLayout.Cell>();
        var soil = new LinkedHashMap<ResourceFieldLayout.CellId, ResourceFieldCycle.Soil>();
        for (var cell : additions) {
            var id = new ResourceFieldLayout.CellId(nextId++);
            addedCells.add(new ResourceFieldLayout.Cell(id, cell.crop(), cell.support(), cell.support())); soil.put(id, cell.condition());
        }
        if (attach != null) {
            siteId = attach.id(); sites = sites.extend(siteId, old.extend(addedCells, nextId, soil), soil);
        } else {
            var facility = fields.getFirst().facilityId();
            int sequence = 1;
            do { siteId = new SubjectId("site:cultivation-" + home.value().replace(':', '-') + "-" + sequence++); }
            while (sites.declarations().containsKey(siteId));
            var layout = new ResourceFieldLayout(1, nextId, addedCells, List.of());
            var cells = new LinkedHashMap<ResourceFieldLayout.CellId, ResourceFieldCycle.CellState>();
            soil.forEach((id, kind) -> cells.put(id, new ResourceFieldCycle.CellState(kind, ResourceFieldCycle.Crop.ABSENT, 0, false, false)));
            var cycle = ResourceFieldCycle.restore(siteId, layout, 1, cells);
            sites = sites.declare(new ResourceSiteDeclaration(siteId, home, facility, ResourceSiteKind.WHEAT_FIELD,
                    new FieldLandOrigin(FieldLandOrigin.Kind.SURVEYED, soil)), cycle);
            declared.add(siteId);
        }
        var selected = new HashSet<>(sites.cycle(siteId).sowing().cells()); selected.addAll(soil.keySet());
        sites = sites.planSowing(siteId, selected);
        return new Result(sites, declared, false);
    }
    private FieldLandAdmission() { }
}
