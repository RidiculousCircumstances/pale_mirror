package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Finite deposit owner, distinct from renewable crop biology and the inventory ledger. */
public record ExtractionSiteState(Map<SubjectId, ExtractionDeposit> deposits, Map<SubjectId, ExtractionWork> work, long nextWorkOrdinal,
        io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockExclusions geologicalExclusions) {
    public static final int MAX_WORK = 64;
    public ExtractionSiteState(Map<SubjectId, ExtractionDeposit> deposits) { this(deposits, Map.of(), 1); }
    public ExtractionSiteState(Map<SubjectId, ExtractionDeposit> deposits, Map<SubjectId, ExtractionWork> work, long nextWorkOrdinal) {
        this(deposits, work, nextWorkOrdinal, io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockExclusions.empty());
    }
    public ExtractionSiteState {
        Objects.requireNonNull(geologicalExclusions);
        deposits = Map.copyOf(deposits); work = Map.copyOf(work);
        if (deposits.size() > 64 || deposits.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().site().id())))
            throw new IllegalArgumentException("invalid extraction site register");
        if (nextWorkOrdinal < 1 || work.size() > MAX_WORK || work.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id())))
            throw new IllegalArgumentException("invalid bounded extraction work register");
        var sourceCells = new HashSet<io.farfrontier.palemirror.frontier.v3.model.BlockPosition>();
        for (var deposit : deposits.values()) for (var cell : deposit.site().layout().cells())
            if (!sourceCells.add(cell.source())) throw new IllegalArgumentException("competing extraction source ownership");
        var actors = new HashSet<SubjectId>(); var tools = new HashSet<SubjectId>();
        var targets = new HashSet<io.farfrontier.palemirror.frontier.v3.model.CellMutationKey>();
        for (var job : work.values()) {
            var deposit = deposits.get(job.siteId());
            if (deposit == null || !job.toolReturnSlot().containerId().equals(deposit.site().containerId()))
                throw new IllegalArgumentException("extraction work lost its declared site/tool-return relationship");
            if (job.terminal()) continue;
            if (!actors.add(job.execution().actorId()) || !tools.add(job.toolId())
                    || job.target().filter(target -> !targets.add(target.key())).isPresent())
                throw new IllegalArgumentException("competing extraction worker, tool or source reservation");
            job.target().ifPresent(target -> {
                var cell = deposit.cells().get(target.key().cell());
                if (cell == null || cell.revision() != target.revision() || cell.disposition() != ExtractionDeposit.Disposition.PRESENT
                        || !deposit.development().openedCells().contains(target.key().cell()))
                    throw new IllegalArgumentException("extraction work has a stale source generation");
                if (job.pending().orElse(null) instanceof ExtractionPhysicalStep.BlockWork step
                        && (!step.extraction().target().equals(deposit.site().layout().require(target.key().cell()).source())
                            || !step.extraction().definition().equals(deposit.site().layout().require(target.key().cell()).definition())))
                    throw new IllegalArgumentException("extraction effect has a foreign block target or rule");
            });
        }
    }
    public static ExtractionSiteState empty() { return new ExtractionSiteState(Map.of()); }
    public void validate(io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap bootstrap,
                         io.farfrontier.palemirror.frontier.v3.model.ExactInventory inventory) {
        for (var position : geologicalExclusions.positions())
            if (bootstrap.ruleset().extraction().geology().flatMap(value -> value.at(bootstrap.bounds(), position)).isEmpty())
                throw new IllegalArgumentException("geological exclusion has no declared baseline");
        var homes = bootstrap.settlements().stream().map(io.farfrontier.palemirror.frontier.v3.model.Settlement::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (var deposit : deposits.values()) {
            var site = deposit.site(); var container = inventory.containers().get(site.containerId());
            var surface = inventory.surfaces().get(site.containerId());
            if (!homes.contains(site.settlementId()) || container == null || !container.ownerId().equals(site.settlementId())
                    || surface == null || !surface.fixed() || !surface.position().equals(site.layout().container()))
                throw new IllegalArgumentException("extraction site lost declared land/storage ownership: " + site.id());
            ExtractionLandValidation.require(site.layout(), bootstrap.bounds());
        }
        if (!bootstrap.ruleset().extraction().enabled() && !deposits.isEmpty())
            throw new IllegalArgumentException("extraction sites require declared content rules");
    }
    public ExtractionSiteState replace(ExtractionDeposit deposit) {
        if (!deposits.containsKey(deposit.site().id()) || !deposits.get(deposit.site().id()).site().equals(deposit.site()))
            throw new IllegalArgumentException("depletion update cannot change site declaration");
        var next = new LinkedHashMap<>(deposits); next.put(deposit.site().id(), deposit);
        return new ExtractionSiteState(next, work, nextWorkOrdinal, geologicalExclusions);
    }
    /** The declaration owner alone admits an append-only plan; depletion replacement cannot do so. */
    public ExtractionSiteState extend(ExtractionDeposit expected, ExtractionDeposit successor) {
        if (!Objects.equals(deposits.get(expected.site().id()), expected)
                || !successor.site().id().equals(expected.site().id())
                || !successor.site().settlementId().equals(expected.site().settlementId())
                || !successor.site().containerId().equals(expected.site().containerId())
                || !successor.cells().entrySet().containsAll(expected.cells().entrySet())
                || !successor.geometry().equals(expected.geometry()))
            throw new IllegalArgumentException("stale or destructive declaration extension");
        successor.site().layout().requireExtensionOf(expected.site().layout());
        var next = new LinkedHashMap<>(deposits); next.put(successor.site().id(), successor);
        return new ExtractionSiteState(next, work, nextWorkOrdinal, geologicalExclusions);
    }
    public Set<Long> reservedCells(SubjectId site) {
        return work.values().stream().filter(job -> job.siteId().equals(site) && !job.terminal())
                .flatMap(job -> job.target().stream()).map(target -> target.key().cell()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public ExtractionSiteState admit(ExtractionWork job, long expectedOrdinal) {
        if (nextWorkOrdinal != expectedOrdinal || work.containsKey(job.id()) || job.phase() != ExtractionWork.Phase.TAKE_TOOL
                || job.revision() != 1 || job.pending().isPresent() || job.labour().isPresent())
            throw new IllegalArgumentException("extraction admission is stale or not a fresh declared job");
        var next = new LinkedHashMap<>(work); next.put(job.id(), job);
        return new ExtractionSiteState(deposits, next, Math.addExact(nextWorkOrdinal, 1), geologicalExclusions);
    }
    public ExtractionSiteState replaceWork(ExtractionWork expected, ExtractionWork next) {
        requireReplacement(expected, next);
        var jobs = new LinkedHashMap<>(work); jobs.put(next.id(), next);
        return new ExtractionSiteState(deposits, jobs, nextWorkOrdinal, geologicalExclusions);
    }
    private void requireReplacement(ExtractionWork expected, ExtractionWork next) {
        if (!Objects.equals(work.get(expected.id()), expected) || !expected.id().equals(next.id())
                || !expected.siteId().equals(next.siteId()) || !expected.toolId().equals(next.toolId())
                || !expected.execution().actorId().equals(next.execution().actorId())
                || !expected.toolReturnSlot().equals(next.toolReturnSlot()) || !expected.outputKind().equals(next.outputKind())
                || !sameResourcePart(expected, next))
            throw new IllegalArgumentException("extraction continuation has a stale or changed retained relationship");
    }
    private static boolean sameResourcePart(ExtractionWork expected, ExtractionWork next) {
        if (expected.phase() == ExtractionWork.Phase.STORE && expected.delivered().equals(next)) return true;
        return expected.batch() == next.batch() && expected.carriedAccountId().equals(next.carriedAccountId())
                && expected.outputLotId().equals(next.outputLotId());
    }
    /** Depletion and the next source declaration settle atomically; no stale intermediate target is published. */
    public ExtractionSiteState settle(ExtractionWork expected, ExtractionWork next, ExtractionDeposit deposit) {
        requireReplacement(expected, next);
        if (!Objects.equals(work.get(expected.id()), expected) || !expected.siteId().equals(deposit.site().id())
                || !deposits.get(deposit.site().id()).site().equals(deposit.site()))
            throw new IllegalArgumentException("extraction settlement lost its current worker/site");
        var sites = new LinkedHashMap<>(deposits); sites.put(deposit.site().id(), deposit);
        var jobs = new LinkedHashMap<>(work); jobs.put(expected.id(), next);
        return new ExtractionSiteState(sites, jobs, nextWorkOrdinal, geologicalExclusions);
    }
    public ExtractionSiteState retire(SubjectId id, long revision) {
        var job = work.get(id);
        if (job == null || !job.terminal() || job.revision() != revision || job.pending().isPresent())
            throw new IllegalArgumentException("extraction retirement lost its exact terminal obligation");
        var next = new LinkedHashMap<>(work); next.remove(id);
        return new ExtractionSiteState(deposits, next, nextWorkOrdinal, geologicalExclusions);
    }
    public ExtractionSiteState invalidateGeology(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
        var next = geologicalExclusions.invalidate(position);
        return next == geologicalExclusions ? this : new ExtractionSiteState(deposits, work, nextWorkOrdinal, next);
    }
}
