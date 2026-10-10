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
        return proposal(state, siteId, false).isPresent();
    }
    private static Optional<ExtractionAreaExtended> proposal(FrontierWorldState state, SubjectId siteId, boolean requireStaff) {
        var deposit = Objects.requireNonNull(state.extractionSites().deposits().get(siteId), "declared excavation site");
        var rules = state.bootstrap().ruleset().extraction(); var layout = deposit.site().layout();
        if (rules.geology().isEmpty() || rules.developmentBatchCells() < 2 || layout.cells().size() > ExtractionLayout.MAX_CELLS - 2
                || requireStaff && SettlementWorkPolicy.permissions(state, deposit.site().settlementId()).workers(ResidentWorkKind.EXTRACTION).isEmpty()
                || ExtractionDevelopment.proposal(state, siteId).isPresent()) return Optional.empty();
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        java.util.function.Predicate<SurfaceAnchor> reachable = surface -> {
            try {
                knowledge.path(layout.storagePort(), new MovementOrder(siteId, siteId, 0, deposit.development().revision(),
                        List.of(surface), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION));
                return true;
            } catch (KnownPedestrianNavigation.RouteUnavailable blocked) { return false; }
        };
        if (deposit.available(Set.of()).stream().anyMatch(cell -> reachable.test(cell.workstation()))) return Optional.empty();
        int count = Math.min(rules.developmentBatchCells() / 2, (ExtractionLayout.MAX_CELLS - layout.cells().size()) / 2);
        var columns = AdjacentExcavationPlanner.propose(layout.accessSurfaces(), ExtractionGroundKnowledge.forState(state),
                reachable, floor -> !declarationHeld(state, siteId, floor.support()),
                rules.source().before()::equals, rules.source().before()::equals, count);
        return columns.isEmpty() ? Optional.empty() : Optional.of(new ExtractionAreaExtended(siteId, deposit.development().revision(), columns));
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
        if (!subject.equals(event.siteId()) || !proposal(state, subject).equals(Optional.of(event)))
            throw new IllegalArgumentException("stale, foreign, protected or unavailable excavation plan");
        var prior = state.extractionSites().deposits().get(subject);
        var next = prior.extend(event.expectedRevision(), extend(prior.site().layout(), state.bootstrap().ruleset().extraction().source(), event));
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
    private static ExtractionLayout extend(ExtractionLayout prior, BlockExtraction.Definition definition, ExtractionAreaExtended event) {
        var cells = new ArrayList<>(prior.cells()); var fixed = new LinkedHashMap<>(prior.fixedBlocks());
        var ids = new LinkedHashMap<>(prior.infrastructureIds()); var surfaces = new ArrayList<>(prior.accessSurfaces());
        var sources = prior.cells().stream().collect(java.util.stream.Collectors.toMap(ExtractionLayout.Cell::source, ExtractionLayout.Cell::id));
        long cellId = prior.cells().stream().mapToLong(ExtractionLayout.Cell::id).max().orElseThrow();
        long infrastructureId = ids.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        for (var column : event.columns()) {
            var floor = column.floor().support(); var prerequisites = new HashSet<Long>();
            for (int height : List.of(1, 2)) {
                var dependency = sources.get(column.station().support().offset(0, height, 0));
                if (dependency != null) prerequisites.add(dependency);
            }
            long upperId = ++cellId;
            cells.add(new ExtractionLayout.Cell(upperId, floor.offset(0, 2, 0), column.station(), definition, prerequisites));
            var lowerParents = new HashSet<>(prerequisites); lowerParents.add(upperId);
            cells.add(new ExtractionLayout.Cell(++cellId, floor.offset(0, 1, 0), column.station(), definition, lowerParents));
            fixed.put(floor, column.support().block()); ids.put(floor, ++infrastructureId); surfaces.add(column.floor());
        }
        return new ExtractionLayout(cells, fixed, surfaces, prior.entrance(), prior.container(), prior.storagePort(), ids);
    }
}
