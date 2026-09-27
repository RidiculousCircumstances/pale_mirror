package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded canonical register for every deterministic renewable site in one Frontier v3 world. */
public record ResourceSiteState(Map<SubjectId, ResourceSiteLifecycle> sites,
                                Map<SubjectId, ResourceFieldCycle> cycles,
                                Map<SubjectId, ResourceFieldCellObserved> pendingWorldChanges,
                                Map<SubjectId, ResourceFieldForeignChangeHeld> pendingForeignChanges) {
    public ResourceSiteState(Map<SubjectId, ResourceSiteLifecycle> sites,
                             Map<SubjectId, ResourceFieldCycle> cycles) {
        this(sites, cycles, Map.of(), Map.of());
    }

    public ResourceSiteState(Map<SubjectId, ResourceSiteLifecycle> sites,
                             Map<SubjectId, ResourceFieldCycle> cycles,
                             Map<SubjectId, ResourceFieldCellObserved> pendingWorldChanges) {
        this(sites, cycles, pendingWorldChanges, Map.of());
    }

    public ResourceSiteState {
        sites = Map.copyOf(Objects.requireNonNull(sites, "resource-site lifecycles"));
        cycles = Map.copyOf(Objects.requireNonNull(cycles, "resource-site cell cycles"));
        pendingWorldChanges = Map.copyOf(Objects.requireNonNull(pendingWorldChanges, "pending world field changes"));
        pendingForeignChanges = Map.copyOf(Objects.requireNonNull(pendingForeignChanges, "pending foreign field changes"));
        if (!sites.keySet().equals(cycles.keySet()))
            throw new IllegalArgumentException("every resource-site lifecycle needs one exact cell cycle");
        if (!sites.keySet().containsAll(pendingWorldChanges.keySet()))
            throw new IllegalArgumentException("world field change has no declared site owner");
        if (!sites.keySet().containsAll(pendingForeignChanges.keySet())
                || pendingForeignChanges.keySet().stream().anyMatch(pendingWorldChanges::containsKey))
            throw new IllegalArgumentException("foreign field change has no unique declared site owner");
        for (Map.Entry<SubjectId, ResourceSiteLifecycle> entry : sites.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().siteId())) throw new IllegalArgumentException("resource-site map key must match lifecycle identity");
            ResourceSiteLifecycle lifecycle = entry.getValue();
            ResourceFieldCycle cycle = cycles.get(entry.getKey());
            if (!entry.getKey().equals(cycle.siteId()))
                throw new IllegalArgumentException("resource-site cell cycle declares another site owner");
            if (cycle.epoch() != lifecycle.growthEpoch())
                throw new IllegalArgumentException("resource-site cell cycle must share its lifecycle epoch");
            if (lifecycle.phase() == ResourceSitePhase.HARVESTING) {
                ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                        .map(ResourceSiteHarvestJob.class::cast).orElseThrow();
                if (job.progress().totalCropSlots() != cycle.layout().cells().size())
                    throw new IllegalArgumentException("field worker must retain its exact admitted layout size");
                if (job.navigationBlock().isPresent()
                        && job.navigationBlock().orElseThrow().layoutRevision() != cycle.layout().revision())
                    throw new IllegalArgumentException("farmer's blocked goal has a foreign field layout revision");
                if (cycle.accountedCount() != job.progress().completedCropSlots())
                    throw new IllegalArgumentException("field cell work and retained farmer cursor disagree");
                if (!job.progress().complete() && cycle.cell(cycle.layout().cells().get(
                        job.progress().nextCropSlotIndex()).id()).accounted()
                        || job.progress().lastCompletedCropSlotIndex() >= 0
                        && !cycle.cell(cycle.layout().cells().get(
                                job.progress().lastCompletedCropSlotIndex()).id()).accounted())
                    throw new IllegalArgumentException("field target selection disagrees with the cell work pool");
                // The job's delivered offset is authority for the next part identity.  It
                // cannot outrun observed yield or leave more than one physical hand stack
                // outstanding, including when completed cells had no crop to collect.
                job.carriedYieldQuantity(cycle.harvestedCount());
            } else if ((lifecycle.phase() == ResourceSitePhase.GROWING || lifecycle.phase() == ResourceSitePhase.READY)
                    && cycle.accountedCount() != 0) {
                throw new IllegalArgumentException("non-harvesting field retains unretired cell work");
            }
        }
        for (var entry : pendingWorldChanges.entrySet()) {
            SubjectId siteId = entry.getKey();
            ResourceFieldCellObserved observation = entry.getValue();
            ResourceFieldCycle cycle = cycles.get(siteId);
            if (!siteId.equals(observation.siteId()) || observation.source() != ResourceFieldCellObserved.Source.WORLD
                    || observation.change() == ResourceFieldCellObserved.Change.UNCHANGED
                    || observation.epoch() != cycle.epoch() || observation.layoutRevision() != cycle.layout().revision()
                    || !ResourceFieldPhysicalSurface.Condition.of(cycle.cell(observation.cellId())).equals(observation.before())
                    && !ResourceFieldPhysicalSurface.Condition.of(cycle.cell(observation.cellId())).equals(observation.after())
                    || cycle.pendingPlayerBreaks().containsKey(observation.cellId()))
                throw new IllegalArgumentException("world field hold has a stale or competing canonical predecessor");
        }
        for (var entry : pendingForeignChanges.entrySet()) {
            SubjectId siteId = entry.getKey();
            ResourceFieldForeignChangeHeld held = entry.getValue();
            ResourceFieldCycle cycle = cycles.get(siteId);
            if (!siteId.equals(held.siteId()) || held.epoch() != cycle.epoch()
                    || held.layoutRevision() != cycle.layout().revision()
                    || !held.before().equals(cycle.cell(held.cellId()))
                    && !foreignTransition(held.before(), cycle.cell(held.cellId()))
                    || cycle.pendingPlayerBreaks().containsKey(held.cellId()))
                throw new IllegalArgumentException("foreign field hold has a stale or competing canonical predecessor");
        }
    }

    private static boolean foreignTransition(ResourceFieldCycle.CellState before, ResourceFieldCycle.CellState after) {
        boolean foreign = before.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                || before.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                || after.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                || after.crop() == ResourceFieldCycle.Crop.OBSTRUCTED;
        boolean ownedLoss = before.soil() == ResourceFieldCycle.Soil.FARMLAND
                && (before.crop() == ResourceFieldCycle.Crop.GROWING || before.crop() == ResourceFieldCycle.Crop.MATURE)
                && after.soil() == ResourceFieldCycle.Soil.FARMLAND
                && after.crop() == ResourceFieldCycle.Crop.ABSENT
                || before.soil() == ResourceFieldCycle.Soil.FARMLAND
                && after.soil() == ResourceFieldCycle.Soil.DIRT
                && after.crop() == ResourceFieldCycle.Crop.ABSENT;
        return (foreign || ownedLoss) && before.accounted() == after.accounted()
                && before.yielded() == after.yielded();
    }

    public static ResourceSiteState initial(FrontierBootstrap bootstrap) {
        Map<SubjectId, ResourceSiteLifecycle> values = new LinkedHashMap<>();
        Map<SubjectId, ResourceFieldCycle> cells = new LinkedHashMap<>();
        FrontierResourceSitePlan.compile(bootstrap).forEach((siteId, site) -> {
            values.put(siteId, ResourceSiteLifecycle.unprepared(siteId));
            cells.put(siteId, ResourceFieldCycle.unsurveyed(siteId, site.layout(), 0));
        });
        return new ResourceSiteState(values, cells);
    }

    void validate(FrontierBootstrap bootstrap) {
        Map<SubjectId, ResourceSite> expected = FrontierResourceSitePlan.compile(bootstrap);
        if (!expected.keySet().equals(sites.keySet())) throw new IllegalArgumentException("resource-site state must retain every and only bootstrap resource site");
        for (ResourceSite site : expected.values()) {
            ResourceSiteLifecycle lifecycle = sites.get(site.id());
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, site.settlementId());
            SettlementStructure facility = FrontierWorldStateSupport.structure(settlement, site.facilityId());
            if (facility.kind() != StructureKind.FARM || site.kind() != ResourceSiteKind.WHEAT_FIELD) {
                throw new IllegalArgumentException("resource-site lifecycle must bind an owning farm");
            }
            if (!cycles.get(site.id()).layout().equals(site.layout()))
                throw new IllegalArgumentException("resource-site cell plan differs from its current bootstrap geometry");
        }
    }

    public ResourceSiteLifecycle site(SubjectId id) {
        ResourceSiteLifecycle lifecycle = sites.get(Objects.requireNonNull(id, "resource-site id"));
        if (lifecycle == null) throw new IllegalArgumentException("unknown resource site: " + id.value());
        return lifecycle;
    }

    public ResourceFieldCycle cycle(SubjectId id) {
        ResourceFieldCycle cycle = cycles.get(Objects.requireNonNull(id, "resource-site id"));
        if (cycle == null) throw new IllegalArgumentException("unknown resource-site cell cycle: " + id.value());
        return cycle;
    }

    public ResourceFieldCellObserved pendingWorldChange(SubjectId id) {
        return pendingWorldChanges.get(Objects.requireNonNull(id, "world field change site"));
    }

    public ResourceFieldForeignChangeHeld pendingForeignChange(SubjectId id) {
        return pendingForeignChanges.get(Objects.requireNonNull(id, "foreign field change site"));
    }

    public boolean hasPendingWorldChange(SubjectId id) {
        return pendingWorldChange(id) != null || pendingForeignChange(id) != null;
    }

    public ResourceSiteState holdWorldChange(ResourceFieldCellObserved observation) {
        Objects.requireNonNull(observation, "world field hold observation");
        if (hasPendingWorldChange(observation.siteId()))
            throw new IllegalArgumentException("field already has an unresolved world change");
        Map<SubjectId, ResourceFieldCellObserved> next = new LinkedHashMap<>(pendingWorldChanges);
        next.put(observation.siteId(), observation);
        return new ResourceSiteState(sites, cycles, next, pendingForeignChanges);
    }

    /** Only an observed, persisted physical claim may retire this canonical site hold. */
    public ResourceSiteState acknowledgeWorldChange(ResourceFieldCellObserved observation) {
        if (!observation.equals(pendingWorldChanges.get(observation.siteId())))
            throw new IllegalArgumentException("field world change acknowledgement lacks its exact held cause");
        Map<SubjectId, ResourceFieldCellObserved> nextChanges = new LinkedHashMap<>(pendingWorldChanges);
        nextChanges.remove(observation.siteId());
        return new ResourceSiteState(sites, cycles, nextChanges, pendingForeignChanges);
    }

    public ResourceSiteState holdForeignChange(ResourceFieldForeignChangeHeld held) {
        Objects.requireNonNull(held, "foreign field hold");
        if (hasPendingWorldChange(held.siteId()))
            throw new IllegalArgumentException("field already has an unresolved world change");
        var next = new LinkedHashMap<>(pendingForeignChanges);
        next.put(held.siteId(), held);
        return new ResourceSiteState(sites, cycles, pendingWorldChanges, next);
    }

    public ResourceSiteState acknowledgeForeignChange(ResourceFieldForeignChangeHeld held) {
        if (!held.equals(pendingForeignChange(held.siteId())))
            throw new IllegalArgumentException("foreign field acknowledgement lacks its exact held cause");
        var next = new LinkedHashMap<>(pendingForeignChanges);
        next.remove(held.siteId());
        return new ResourceSiteState(sites, cycles, pendingWorldChanges, next);
    }

    /** Stable bootstrap identity with the current canonical, versioned field geometry. */
    public ResourceSite descriptor(FrontierBootstrap bootstrap, SubjectId id) {
        Objects.requireNonNull(bootstrap, "resource-site descriptor bootstrap");
        ResourceSite initial = FrontierResourceSitePlan.compile(bootstrap).get(Objects.requireNonNull(id, "resource-site id"));
        if (initial == null) throw new IllegalArgumentException("unknown resource site: " + id.value());
        ResourceFieldCycle current = cycle(id);
        return new ResourceSite(initial.id(), initial.settlementId(), initial.facilityId(), initial.kind(), current.layout());
    }

    public Map<SubjectId, ResourceSite> descriptors(FrontierBootstrap bootstrap) {
        Map<SubjectId, ResourceSite> current = new LinkedHashMap<>();
        for (SubjectId id : sites.keySet()) current.put(id, descriptor(bootstrap, id));
        return Map.copyOf(current);
    }

    public ResourceSiteState replace(ResourceSiteLifecycle lifecycle) {
        Objects.requireNonNull(lifecycle, "resource-site lifecycle");
        if (!sites.containsKey(lifecycle.siteId())) throw new IllegalArgumentException("unknown resource site: " + lifecycle.siteId().value());
        Map<SubjectId, ResourceSiteLifecycle> next = new LinkedHashMap<>(sites); next.put(lifecycle.siteId(), lifecycle);
        return new ResourceSiteState(next, cycles, pendingWorldChanges, pendingForeignChanges);
    }

    public ResourceSiteState replace(ResourceSiteLifecycle lifecycle, ResourceFieldCycle cycle) {
        Objects.requireNonNull(lifecycle, "resource-site lifecycle");
        Objects.requireNonNull(cycle, "resource-site cell cycle");
        if (!sites.containsKey(lifecycle.siteId()) || !cycle.siteId().equals(lifecycle.siteId())
                || !cycle.layout().equals(cycles.get(lifecycle.siteId()).layout()))
            throw new IllegalArgumentException("resource-site cell replacement has a foreign owner or layout");
        Map<SubjectId, ResourceSiteLifecycle> nextSites = new LinkedHashMap<>(sites);
        nextSites.put(lifecycle.siteId(), lifecycle);
        Map<SubjectId, ResourceFieldCycle> nextCycles = new LinkedHashMap<>(cycles);
        nextCycles.put(lifecycle.siteId(), cycle);
        return new ResourceSiteState(nextSites, nextCycles, pendingWorldChanges, pendingForeignChanges);
    }
}
