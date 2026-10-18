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
            List<FieldDevelopmentCell> additions, CultivationRules rules) {
        if (additions.size() > rules.maximumNewCells()) throw new IllegalArgumentException("cultivation exceeds its development turn");
        var geometry = KnownPedestrianRouteKnowledge.forSettlement(state, home, List.of()).geometry();
        var remaining = new LinkedHashMap<SurfaceAnchor, FieldDevelopmentCell>();
        additions.forEach(cell -> remaining.put(cell.support(), cell));
        var declared = new ArrayList<SubjectId>(); boolean deferred = false;
        int count = (int) sites.declarations().values().stream().filter(d -> d.settlementId().equals(home)).count();
        while (!remaining.isEmpty()) {
            var component = new ArrayList<FieldDevelopmentCell>();
            var queue = new ArrayDeque<FieldDevelopmentCell>(); queue.add(remaining.remove(remaining.keySet().iterator().next()));
            while (!queue.isEmpty()) {
                var cell = queue.removeFirst(); component.add(cell);
                var neighbours = remaining.values().stream().filter(next -> geometry.canTraverse(cell.support(), next.support())).toList();
                for (var neighbour : neighbours) { remaining.remove(neighbour.support()); queue.addLast(neighbour); }
            }
            ResourceSite attach = null;
            for (var field : fields) {
                var lifecycle = sites.site(field.id());
                if (!lifecycle.harvestJobs().isEmpty() || lifecycle.preparationWork().isPresent() || sites.hasPendingWorldChange(field.id())
                        || !sites.cycle(field.id()).pendingPlayerBreaks().isEmpty()
                        || lifecycle.harvestLineages().values().stream().anyMatch(ResourceSiteHarvestLineage::receiptPending)) continue;
                var layout = sites.cycle(field.id()).layout();
                if (component.stream().anyMatch(cell -> layout.cells().stream().anyMatch(prior -> geometry.canTraverse(prior.workstation(), cell.support())))) {
                    attach = field; break;
                }
            }
            if (attach == null && count >= rules.maximumFields()) { deferred = true; continue; }
            SubjectId siteId;
            ResourceFieldLayout old = attach == null ? null : sites.cycle(attach.id()).layout();
            long nextId = old == null ? 1 : old.nextCellId();
            var addedCells = new ArrayList<ResourceFieldLayout.Cell>();
            var soil = new LinkedHashMap<ResourceFieldLayout.CellId, ResourceFieldCycle.Soil>();
            for (var cell : component) {
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
                declared.add(siteId); count++;
            }
            var selected = new HashSet<>(sites.cycle(siteId).sowing().cells()); selected.addAll(soil.keySet());
            sites = sites.planSowing(siteId, selected);
        }
        return new Result(sites, declared, deferred);
    }
    private FieldLandAdmission() { }
}
