package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Mining policy around a general geometry planner. All effects stay with the original extraction owner. */
public final class ExtractionAreaPlanning {
    private ExtractionAreaPlanning() { }
    public static Optional<ExtractionAreaExtended> proposal(FrontierWorldState state, SubjectId siteId) {
        return proposal(state, siteId, true);
    }
    /** Staffing asks whether known work exists; it cannot require the staff it has not assigned yet. */
    public static boolean hasKnownExtension(FrontierWorldState state, SubjectId siteId) {
        return extensionAvailability(state, siteId) == Availability.AVAILABLE;
    }
    public enum Availability { AVAILABLE, WAITING, NONE }
    /** A calculation/knowledge wait is not geological exhaustion or an admission grant. */
    public static Availability extensionAvailability(FrontierWorldState state, SubjectId siteId) {
        try { return proposal(state, siteId, false).isPresent() ? Availability.AVAILABLE : Availability.NONE; }
        catch (KnownPedestrianNavigation.RouteUnavailable pending) {
            return pending.status() == PedestrianRouteResult.Status.NO_PATH ? Availability.NONE : Availability.WAITING;
        }
    }
    private static Optional<ExtractionAreaExtended> proposal(FrontierWorldState state, SubjectId siteId, boolean requireStaff) {
        var deposit = Objects.requireNonNull(state.extractionSites().deposits().get(siteId), "declared excavation site");
        var rules = state.bootstrap().ruleset().extraction(); var layout = deposit.site().layout();
        if (rules.geology().isEmpty() || rules.developmentBatchCells() < 2 || layout.cells().size() > ExtractionLayout.MAX_CELLS - 2
                || requireStaff && SettlementWorkPolicy.permissions(state, deposit.site().settlementId()).workers(ResidentWorkKind.EXTRACTION).isEmpty()
                || ExtractionDevelopment.proposal(state, siteId).isPresent()) return Optional.empty();
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var access = knowledge.accessSelection(layout.storagePort());
        var needed = ExtractionStockDemand.requiredCells(state, deposit, Optional.empty());
        var priorities = ExtractionPriorities.read(state, deposit, Optional.empty());
        var bestExisting = deposit.available(Set.of()).stream().filter(cell -> needed.contains(cell.id()))
                .filter(cell -> access.reachable(cell.workstation())).map(cell -> priorities.require(cell.id()))
                .min(io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPriority::compareTo);
        var levels = new TreeSet<io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPriority>(priorities.products().values());
        // Existing work of equal or higher importance wins; do not expand merely
        // to duplicate it. Lower-priority stone cannot hide an urgent known fuel front.
        if (bestExisting.isPresent() && (levels.isEmpty() || bestExisting.orElseThrow().compareTo(levels.first()) <= 0))
            return Optional.empty();
        access.awaitUnresolved();
        var front = new ExtractionAvailableFront(state, deposit);
        if (front.stations().isEmpty()) return Optional.empty();
        int count = columnLimit(rules, layout);
        if (count < 1) return Optional.empty();
        var ground = ExtractionGroundKnowledge.forSite(state, deposit);
        List<AdjacentExcavationPlanner.Column<BlockExtraction.Block>> proposed = List.of();
        for (var priority : levels) {
            if (bestExisting.isPresent() && priority.compareTo(bestExisting.orElseThrow()) >= 0) break;
            // The generic geometry planner knows neither products nor urgency. The
            // extraction adapter admits the best executable demand band before quota.
            proposed = AdjacentExcavationPlanner.propose(front.stations(), ground,
                    access::reachable, floor -> !declarationHeld(state, siteId, floor.support()),
                    block -> rules.definitionFor(block).isPresent(),
                    block -> block.kind().equals("minecraft:air") || rules.definitionFor(block).isPresent(), count,
                    front.selection(), column -> front.executable(column) && column.clearance().stream()
                            .map(KnownBlockGeometry.Sample::block).flatMap(block -> rules.definitionFor(block).stream())
                            .flatMap(material -> material.coldOutput().stream()).map(output -> priorities.products().get(output.itemKind()))
                            .filter(Objects::nonNull).anyMatch(value -> value.compareTo(priority) == 0));
            if (!proposed.isEmpty()) break;
        }
        var columns = new ArrayList<AdjacentExcavationPlanner.Column<BlockExtraction.Block>>();
        int remaining = Math.min(rules.developmentBatchCells(), ExtractionLayout.MAX_CELLS - layout.cells().size());
        for (var column : proposed) {
            int cost = sourceCells(column);
            if (cost > remaining) continue;
            columns.add(column); remaining -= cost;
        }
        if (columns.isEmpty()) { access.awaitUnresolved(); return Optional.empty(); }
        return Optional.of(new ExtractionAreaExtended(siteId, deposit.development().revision(), columns,
                access.receipt(columns.stream().map(AdjacentExcavationPlanner.Column::station).collect(java.util.stream.Collectors.toSet()))));
    }
    private static int columnLimit(ExtractionRules rules, ExtractionLayout layout) {
        return Math.min(AdjacentExcavationPlanner.MAX_COLUMNS,
                Math.min(rules.developmentBatchCells() / 2, (ExtractionLayout.MAX_CELLS - layout.cells().size()) / 2));
    }
    private static int sourceCells(AdjacentExcavationPlanner.Column<BlockExtraction.Block> column) {
        return (int) column.clearance().stream().filter(sample -> !sample.block().kind().equals("minecraft:air")).count();
    }
    private static boolean declarationHeld(FrontierWorldState state, SubjectId siteId, BlockPosition position) {
        var region = new ExtractionRegion(siteId, Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
        var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
        return lease != null && lease.live() && lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                && lease.status() != PhysicalCustodyLeaseStatus.PREPARING
                || state.extractionSites().work().values().stream().anyMatch(job -> job.siteId().equals(siteId)
                    && job.pending().isPresent() && job.target().filter(target -> region.contains(
                        state.extractionSites().deposits().get(siteId).site().layout().require(target.key().cell()).source())).isPresent());
    }
    /** Planning view only; the event reducer also updates physical region obligations at its actual revision. */
    public static FrontierWorldState preview(FrontierWorldState state, SubjectId subject, ExtractionAreaExtended event) {
        var prior = state.extractionSites().deposits().get(subject);
        var rules = state.bootstrap().ruleset().extraction();
        if (!subject.equals(event.siteId()) || prior == null || prior.development().revision() != event.expectedRevision()
                || rules.geology().isEmpty() || rules.developmentBatchCells() < 2
                || SettlementWorkPolicy.permissions(state, prior.site().settlementId()).workers(ResidentWorkKind.EXTRACTION).isEmpty())
            throw new IllegalArgumentException("stale, foreign, protected or unavailable excavation plan");
        var layout = prior.site().layout(); var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        int added = event.columns().stream().mapToInt(ExtractionAreaPlanning::sourceCells).sum();
        if (added > rules.developmentBatchCells() || added > ExtractionLayout.MAX_CELLS - layout.cells().size())
            throw new IllegalArgumentException("selected excavation exceeds its actual source-cell budget");
        var front = new ExtractionAvailableFront(state, prior);
        AdjacentExcavationPlanner.requireSelection(front.stations(), event.columns(), ExtractionGroundKnowledge.forSite(state, prior),
                floor -> !declarationHeld(state, subject, floor.support()), block -> rules.definitionFor(block).isPresent(),
                block -> block.kind().equals("minecraft:air") || rules.definitionFor(block).isPresent(), columnLimit(rules, layout), front.selection());
        if (event.columns().stream().anyMatch(column -> !front.executable(column)))
            throw new IllegalArgumentException("selected excavation loses work reach, occupied support or retained access");
        knowledge.requireAccess(event.access(), layout.storagePort(), event.columns().stream()
                .map(AdjacentExcavationPlanner.Column::station).collect(java.util.stream.Collectors.toSet()));
        var next = prior.extend(event.expectedRevision(), extend(layout, rules, event));
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().extend(prior, next)));
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ExtractionAreaExtended event, long canonicalRevision) {
        var successor = preview(state, subject, event);
        var custody = state.replicaCustody();
        var regions = event.columns().stream().map(column -> new ExtractionRegion(subject,
                Math.floorDiv(column.floor().x(), 16), Math.floorDiv(column.floor().z(), 16))).distinct()
                .sorted(Comparator.comparingInt(ExtractionRegion::chunkX).thenComparingInt(ExtractionRegion::chunkZ)).toList();
        for (var region : regions) {
            var lease = custody.custodyByScope().get(region.scopeId());
            if (lease == null || !lease.live()) continue;
            var fingerprint = ExtractionSourceCustody.fingerprint(successor.extractionSites(), region);
            if (!lease.providerId().equals(ExtractionSourceCustody.PROVIDER))
                throw new IllegalArgumentException("excavation expansion crosses a foreign source custodian");
            if (lease.status() == PhysicalCustodyLeaseStatus.PREPARING) {
                custody = custody.supersedeProjection(region.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(),
                        lease.expectedReplicaRevision(), canonicalRevision, fingerprint, region.provenance());
            } else {
                custody = ExtractionSourceCustody.release(custody, lease);
                var replica = custody.replicas().get(region.objectId());
                custody = custody.emit(region.objectId(), replica.emittedCanonicalRevision(), replica.replicaRevision(),
                        canonicalRevision, fingerprint, region.provenance());
                replica = custody.replicas().get(region.objectId());
                custody = custody.prepareProjection(new PhysicalCustodyLease(region.scopeId(), region.objectId(), ExtractionSourceCustody.PROVIDER,
                        Math.addExact(lease.authorityEpoch(), 1), canonicalRevision, replica.replicaRevision(), PhysicalCustodyLeaseStatus.PREPARING, null));
            }
        }
        return successor.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
    }
    private static ExtractionLayout extend(ExtractionLayout prior, ExtractionRules rules, ExtractionAreaExtended event) {
        var cells = new ArrayList<>(prior.cells()); var fixed = new LinkedHashMap<>(prior.fixedBlocks());
        var ids = new LinkedHashMap<>(prior.infrastructureIds()); var surfaces = new ArrayList<>(prior.accessSurfaces());
        var natural = new LinkedHashMap<>(prior.terrainSupports());
        var terrainIds = new LinkedHashMap<>(prior.terrainIds());
        var sources = prior.cells().stream().collect(java.util.stream.Collectors.toMap(ExtractionLayout.Cell::source, ExtractionLayout.Cell::id));
        long cellId = prior.cells().stream().mapToLong(ExtractionLayout.Cell::id).max().orElseThrow();
        long terrainId = terrainIds.values().stream().mapToLong(Long::longValue).max().orElse(0);
        for (var column : event.columns()) {
            var floor = column.floor().support(); var prerequisites = new HashSet<Long>();
            for (int height : List.of(1, 2)) {
                var dependency = sources.get(column.station().support().offset(0, height, 0));
                if (dependency != null) prerequisites.add(dependency);
            }
            var clearance = column.clearance();
            for (int height = clearance.size(); height >= 1; height--) {
                if (clearance.get(height - 1).block().kind().equals("minecraft:air")) continue;
                var definition = rules.definitionFor(clearance.get(height - 1).block()).orElseThrow(
                        () -> new IllegalArgumentException("undeclared excavation preparation recipe"));
                long currentId = ++cellId;
                cells.add(new ExtractionLayout.Cell(currentId, floor.offset(0, height, 0), column.station(), definition, prerequisites));
                prerequisites = new HashSet<>(prerequisites); prerequisites.add(currentId);
            }
            if (!natural.containsKey(floor)) {
                natural.put(floor, column.support().block()); terrainIds.put(floor, ++terrainId);
            }
            if (!surfaces.contains(column.floor())) surfaces.add(column.floor());
        }
        return new ExtractionLayout(cells, fixed, surfaces, prior.entrance(), prior.container(), prior.storagePort(), ids, natural, terrainIds);
    }
}
