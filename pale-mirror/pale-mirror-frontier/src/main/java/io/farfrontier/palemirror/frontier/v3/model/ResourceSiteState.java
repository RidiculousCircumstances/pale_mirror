package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded canonical register for every deterministic renewable site in one Frontier v3 world. */
public record ResourceSiteState(Map<SubjectId, ResourceSiteDeclaration> declarations,
                                Map<SubjectId, ResourceSiteLifecycle> sites,
                                Map<SubjectId, ResourceFieldCycle> cycles,
                                Map<CellMutationKey, ResourceFieldCellObserved> pendingWorldChanges,
                                Map<CellMutationKey, ResourceFieldForeignChangeHeld> pendingForeignChanges,
                                Map<SubjectId, CultivationReview> cultivation) {
    public static final int MAX_SITES = 1024;
    private static final ImmutableInputView<Boolean> GEOMETRY_VALIDATION = new ImmutableInputView<>();
    public ResourceSiteState(Map<SubjectId, ResourceSiteDeclaration> declarations, Map<SubjectId, ResourceSiteLifecycle> sites,
            Map<SubjectId, ResourceFieldCycle> cycles, Map<CellMutationKey, ResourceFieldCellObserved> pendingWorldChanges,
            Map<CellMutationKey, ResourceFieldForeignChangeHeld> pendingForeignChanges) {
        this(declarations, sites, cycles, pendingWorldChanges, pendingForeignChanges, Map.of());
    }
    public ResourceSiteState(Map<SubjectId, ResourceSiteDeclaration> declarations,
                             Map<SubjectId, ResourceSiteLifecycle> sites,
                             Map<SubjectId, ResourceFieldCycle> cycles) {
        this(declarations, sites, cycles, Map.of(), Map.of());
    }

    public ResourceSiteState(Map<SubjectId, ResourceSiteDeclaration> declarations,
                             Map<SubjectId, ResourceSiteLifecycle> sites,
                             Map<SubjectId, ResourceFieldCycle> cycles,
                             Map<CellMutationKey, ResourceFieldCellObserved> pendingWorldChanges) {
        this(declarations, sites, cycles, pendingWorldChanges, Map.of());
    }

    public ResourceSiteState {
        declarations = Map.copyOf(Objects.requireNonNull(declarations, "resource-site declarations"));
        cultivation = Map.copyOf(cultivation);
        if (cultivation.size() > MAX_SITES) throw new IllegalArgumentException("unbounded cultivation reviews");
        sites = Map.copyOf(Objects.requireNonNull(sites, "resource-site lifecycles"));
        cycles = Map.copyOf(Objects.requireNonNull(cycles, "resource-site cell cycles"));
        pendingWorldChanges = Map.copyOf(Objects.requireNonNull(pendingWorldChanges, "pending world field changes"));
        pendingForeignChanges = Map.copyOf(Objects.requireNonNull(pendingForeignChanges, "pending foreign field changes"));
        if (sites.size() > MAX_SITES || !sites.keySet().equals(cycles.keySet()) || !sites.keySet().equals(declarations.keySet()))
            throw new IllegalArgumentException("every resource-site lifecycle needs one exact cell cycle");
        if (declarations.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id())))
            throw new IllegalArgumentException("resource-site declaration key differs from its exact owner");
        var declaredSites = sites.keySet();
        if (pendingWorldChanges.keySet().stream().anyMatch(key -> !declaredSites.contains(key.owner())))
            throw new IllegalArgumentException("world field change has no declared site owner");
        if (pendingForeignChanges.keySet().stream().anyMatch(key -> !declaredSites.contains(key.owner()))
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
            for (ResourceSiteHarvestJob job : lifecycle.harvestJobs().values()) {
                if (job.progress().totalCropSlots() != cycle.layout().cells().size())
                    throw new IllegalArgumentException("field worker must retain its exact admitted layout size");
                if (job.navigationBlock().isPresent()
                        && job.navigationBlock().orElseThrow().layoutRevision() != cycle.layout().revision())
                    throw new IllegalArgumentException("farmer's blocked goal has a foreign field layout revision");
                if (!job.progress().complete() && !job.returningForBatch()) {
                    ResourceFieldLayout.CellId selected = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
                    if (!job.target().current(cycle)
                            || !job.target().cellId().equals(selected))
                        throw new IllegalArgumentException("field target has a stale generation or disagrees with its work pool");
                }
            }
        }
        for (var entry : pendingWorldChanges.entrySet()) {
            SubjectId siteId = entry.getKey().owner();
            ResourceFieldCellObserved observation = entry.getValue();
            ResourceFieldCycle cycle = cycles.get(siteId);
            if (!entry.getKey().equals(mutationKey(observation.siteId(), observation.cellId())) || observation.source() != ResourceFieldCellObserved.Source.WORLD
                    || observation.change() == ResourceFieldCellObserved.Change.UNCHANGED
                    || observation.epoch() != cycle.epoch() || observation.layoutRevision() != cycle.layout().revision()
                    || !ResourceFieldPhysicalSurface.Condition.of(cycle.cell(observation.cellId())).equals(observation.before())
                    && !ResourceFieldPhysicalSurface.Condition.of(cycle.cell(observation.cellId())).equals(observation.after())
                    || cycle.pendingPlayerBreaks().containsKey(observation.cellId()))
                throw new IllegalArgumentException("world field hold has a stale or competing canonical predecessor");
        }
        for (var entry : pendingForeignChanges.entrySet()) {
            SubjectId siteId = entry.getKey().owner();
            ResourceFieldForeignChangeHeld held = entry.getValue();
            ResourceFieldCycle cycle = cycles.get(siteId);
            if (!entry.getKey().equals(mutationKey(held.siteId(), held.cellId())) || held.epoch() != cycle.epoch()
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
        boolean accountedLoss = !before.accounted() && after.accounted() && !before.yielded()
                && !after.yielded()
                && (before.crop() == ResourceFieldCycle.Crop.GROWING
                    || before.crop() == ResourceFieldCycle.Crop.MATURE)
                && (after.crop() == ResourceFieldCycle.Crop.ABSENT
                    || after.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                    || after.crop() == ResourceFieldCycle.Crop.GROWING && after.growthStage() < before.growthStage());
        boolean observedPlant = before.soil() == ResourceFieldCycle.Soil.FARMLAND
                && after.soil() == ResourceFieldCycle.Soil.FARMLAND
                && (after.crop() == ResourceFieldCycle.Crop.GROWING || after.crop() == ResourceFieldCycle.Crop.MATURE);
        boolean ownedRestoration = before.soil().cultivable() && after.soil().cultivable()
                && before.soil() != after.soil() && after.crop() == ResourceFieldCycle.Crop.ABSENT;
        return (foreign || ownedLoss || observedPlant || ownedRestoration) && (before.accounted() == after.accounted() || accountedLoss)
                && before.yielded() == after.yielded();
    }

    public static ResourceSiteState initial(FrontierBootstrap bootstrap) {
        Map<SubjectId, ResourceSiteLifecycle> values = new LinkedHashMap<>();
        Map<SubjectId, ResourceFieldCycle> cells = new LinkedHashMap<>();
        FrontierResourceSitePlan.compile(bootstrap).forEach((siteId, site) -> {
            values.put(siteId, ResourceSiteLifecycle.unprepared(siteId));
            cells.put(siteId, ResourceFieldCycle.unsurveyed(siteId, site.layout(), 0));
        });
        var declarations = new LinkedHashMap<SubjectId, ResourceSiteDeclaration>();
        FrontierResourceSitePlan.compile(bootstrap).forEach((id, site) -> declarations.put(id, ResourceSiteDeclaration.of(site)));
        return new ResourceSiteState(declarations, values, cells);
    }

    void validate(FrontierBootstrap bootstrap) {
        if (!bootstrap.settlements().stream().map(Settlement::id).collect(java.util.stream.Collectors.toSet()).containsAll(cultivation.keySet()))
            throw new IllegalArgumentException("cultivation review has an unknown home");
        var inputs = new java.util.ArrayList<Object>(java.util.List.of(bootstrap, declarations));
        cycles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> inputs.add(entry.getValue().layout()));
        GEOMETRY_VALIDATION.get(inputs, () -> { validateGeometry(bootstrap); return Boolean.TRUE; });
    }

    private void validateGeometry(FrontierBootstrap bootstrap) {
        var authored = FrontierResourceSitePlan.compile(bootstrap);
        if (!declarations.keySet().containsAll(authored.keySet()))
            throw new IllegalArgumentException("resource-site state lost an initial declared field");
        var occupied = new java.util.HashSet<BlockPosition>();
        for (ResourceSiteDeclaration declaration : declarations.values()) {
            var initial = authored.get(declaration.id());
            if (declaration.land().kind() == FieldLandOrigin.Kind.AUTHORED
                    && (initial == null || !declaration.equals(ResourceSiteDeclaration.of(initial)))
                    || initial != null && !declaration.equals(ResourceSiteDeclaration.of(initial)))
                throw new IllegalArgumentException("field declaration forged its authored land authority");
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, declaration.settlementId());
            SettlementStructure facility = FrontierWorldStateSupport.structure(settlement, declaration.facilityId());
            if (facility.kind() != StructureKind.FARM || declaration.kind() != ResourceSiteKind.WHEAT_FIELD) {
                throw new IllegalArgumentException("resource-site lifecycle must bind an owning farm");
            }
            var layout = cycles.get(declaration.id()).layout();
            declaration.land().ground().keySet().forEach(layout::requireCell);
            for (BlockPosition position : layout.managedSlots())
                if (!bootstrap.bounds().contains(position) || !occupied.add(position))
                    throw new IllegalArgumentException("field geometry overlaps another field or leaves world bounds");
        }
    }

    public ResourceSiteLifecycle site(SubjectId id) {
        ResourceSiteLifecycle lifecycle = sites.get(Objects.requireNonNull(id, "resource-site id"));
        if (lifecycle == null) throw new IllegalArgumentException("unknown resource site: " + id.value());
        return lifecycle;
    }

    /** Runtime declaration establishes land ownership only; no prepared soil, plant or resource is granted. */
    public ResourceSiteState declare(ResourceSiteDeclaration declaration, ResourceFieldCycle ground) {
        Objects.requireNonNull(declaration); Objects.requireNonNull(ground);
        if (declarations.containsKey(declaration.id()) || !declaration.id().equals(ground.siteId())
                || declaration.land().kind() != FieldLandOrigin.Kind.SURVEYED
                || !declaration.land().ground().keySet().equals(ground.cellStates().keySet())
                || declaration.land().ground().entrySet().stream().anyMatch(entry -> ground.cell(entry.getKey()).soil() != entry.getValue())
                || ground.epoch() != 1 || ground.layout().cells().isEmpty() || !ground.pendingPlayerBreaks().isEmpty()
                || ground.cellStates().values().stream().anyMatch(cell -> !cell.soil().cultivable()
                        || cell.crop() != ResourceFieldCycle.Crop.ABSENT || cell.accounted() || cell.yielded()))
            throw new IllegalArgumentException("new field must declare exact, unused bare land");
        var nextDeclarations = new LinkedHashMap<>(declarations); nextDeclarations.put(declaration.id(), declaration);
        var nextSites = new LinkedHashMap<>(sites);
        nextSites.put(declaration.id(), new ResourceSiteLifecycle(declaration.id(), ResourceSitePhase.GROWING, 1, 0,
                java.util.Optional.empty(), java.util.Optional.empty(), Map.of(), Map.of(), 0));
        var nextCycles = new LinkedHashMap<>(cycles); nextCycles.put(declaration.id(), ground);
        return new ResourceSiteState(nextDeclarations, nextSites, nextCycles, pendingWorldChanges, pendingForeignChanges, cultivation);
    }

    public ResourceSiteState reviewed(SubjectId home, CultivationReview review) {
        var prior = cultivation.get(home);
        if (prior != null && prior.atTick() >= review.atTick()) throw new IllegalArgumentException("stale cultivation review");
        var next = new LinkedHashMap<>(cultivation); next.put(home, review);
        return new ResourceSiteState(declarations, sites, cycles, pendingWorldChanges, pendingForeignChanges, next);
    }

    /** Revoke future sowing, but never reinterpret an already prepared physical effect. */
    public ResourceSiteState planSowing(SubjectId id, java.util.Set<ResourceFieldLayout.CellId> selected) {
        var cycle = cycle(id); var next = new java.util.HashSet<>(selected);
        for (var job : site(id).harvestJobs().values()) {
            if (!job.progress().hasPendingCrop()) continue;
            var cell = job.target().cellId();
            var outcome = cycle.expectedWorkOutcome(cell);
            if (outcome == ResourceFieldCycle.WorkOutcome.PLANTED || outcome == ResourceFieldCycle.WorkOutcome.TILLED)
                next.add(cell);
        }
        var plan = cycle.sowing().revise(next);
        return plan.equals(cycle.sowing()) ? this : replace(site(id), cycle.withSowing(plan));
    }

    /** Geometry may grow between this field's batches, independently of other fields and retained plant ages. */
    public ResourceSiteState extend(SubjectId id, ResourceFieldLayout layout,
                                    Map<ResourceFieldLayout.CellId, ResourceFieldCycle.Soil> added) {
        var lifecycle = site(id);
        if (!lifecycle.harvestJobs().isEmpty() || lifecycle.preparationWork().isPresent() || hasPendingWorldChange(id)
                || lifecycle.harvestLineages().values().stream().anyMatch(ResourceSiteHarvestLineage::receiptPending))
            throw new IllegalArgumentException("field extension overlaps a live execution or unresolved physical receipt");
        var next = new LinkedHashMap<>(cycles); next.put(id, cycle(id).extended(layout, added));
        return new ResourceSiteState(declarations, sites, next, pendingWorldChanges, pendingForeignChanges, cultivation);
    }

    /** Family address producer: the shared protocol never discovers an owner from a payload. */
    public static CellMutationKey mutationKey(SubjectId site, ResourceFieldLayout.CellId cell) {
        return new CellMutationKey(CellMutationKey.OwnerFamily.RESOURCE_SITE, site, cell.value());
    }

    public java.util.Set<ResourceFieldLayout.CellId> growthProtectedCells(SubjectId id) {
        var protectedCells = new java.util.HashSet<>(site(id).harvestJobs().values().stream()
                .filter(job -> job.progress().hasPendingCrop())
                .map(job -> cycle(id).layout().cells().get(job.progress().pendingCropSlotIndex()).id()).toList());
        protectedCells.addAll(cycle(id).pendingPlayerBreaks().keySet());
        pendingWorldChanges.keySet().stream().filter(key -> key.owner().equals(id))
                .forEach(key -> protectedCells.add(new ResourceFieldLayout.CellId(key.cell())));
        pendingForeignChanges.keySet().stream().filter(key -> key.owner().equals(id))
                .forEach(key -> protectedCells.add(new ResourceFieldLayout.CellId(key.cell())));
        return java.util.Set.copyOf(protectedCells);
    }

    public ResourceFieldCycle cycle(SubjectId id) {
        ResourceFieldCycle cycle = cycles.get(Objects.requireNonNull(id, "resource-site id"));
        if (cycle == null) throw new IllegalArgumentException("unknown resource-site cell cycle: " + id.value());
        return cycle;
    }

    public ResourceFieldCellObserved pendingWorldChange(SubjectId id, ResourceFieldLayout.CellId cell) {
        return pendingWorldChanges.get(mutationKey(id, cell));
    }
    public ResourceFieldForeignChangeHeld pendingForeignChange(SubjectId id, ResourceFieldLayout.CellId cell) {
        return pendingForeignChanges.get(mutationKey(id, cell));
    }
    /** Aggregate scope-release/readiness query only, not a cell-work or biology lock. */
    public boolean hasPendingWorldChange(SubjectId id) {
        return pendingWorldChanges.keySet().stream().anyMatch(key -> key.owner().equals(id))
                || pendingForeignChanges.keySet().stream().anyMatch(key -> key.owner().equals(id));
    }
    /** A work owner fences only its selected cell, never its carried batch or neighbours. */
    public boolean harvestMutationPending(ResourceSiteHarvestJob job) {
        if (job.progress().complete() || job.returningForBatch()) return false;
        return hasPendingCellMutation(job.siteId(), cycle(job.siteId()).layout().cells()
                .get(job.progress().nextCropSlotIndex()).id());
    }
    public boolean hasPendingCellMutation(SubjectId id, ResourceFieldLayout.CellId cell) {
        return pendingWorldChange(id, cell) != null || pendingForeignChange(id, cell) != null
                || cycle(id).pendingPlayerBreaks().containsKey(cell);
    }

    public ResourceSiteState holdWorldChange(ResourceFieldCellObserved observation) {
        var key = mutationKey(observation.siteId(), observation.cellId());
        if (hasPendingCellMutation(observation.siteId(), observation.cellId()))
            throw new IllegalArgumentException("cell already has an unresolved mutation: " + key);
        return new ResourceSiteState(declarations, sites, cycles,
                CellMutationProtocol.reserve(pendingWorldChanges, key, observation), pendingForeignChanges, cultivation);
    }
    public ResourceSiteState acknowledgeWorldChange(ResourceFieldCellObserved observation) {
        return new ResourceSiteState(declarations, sites, cycles, CellMutationProtocol.release(pendingWorldChanges,
                mutationKey(observation.siteId(), observation.cellId()), observation), pendingForeignChanges, cultivation);
    }
    public ResourceSiteState holdForeignChange(ResourceFieldForeignChangeHeld held) {
        var key = mutationKey(held.siteId(), held.cellId());
        if (hasPendingCellMutation(held.siteId(), held.cellId()))
            throw new IllegalArgumentException("cell already has an unresolved mutation: " + key);
        return new ResourceSiteState(declarations, sites, cycles, pendingWorldChanges,
                CellMutationProtocol.reserve(pendingForeignChanges, key, held), cultivation);
    }
    public ResourceSiteState acknowledgeForeignChange(ResourceFieldForeignChangeHeld held) {
        return new ResourceSiteState(declarations, sites, cycles, pendingWorldChanges, CellMutationProtocol.release(
                pendingForeignChanges, mutationKey(held.siteId(), held.cellId()), held), cultivation);
    }

    /** Current registry declaration; bootstrap never supplies missing runtime identity. */
    public ResourceSite descriptor(FrontierBootstrap bootstrap, SubjectId id) {
        Objects.requireNonNull(bootstrap, "resource-site descriptor bootstrap");
        ResourceSiteDeclaration declaration = declarations.get(Objects.requireNonNull(id, "resource-site id"));
        if (declaration == null) throw new IllegalArgumentException("unknown resource site: " + id.value());
        ResourceFieldCycle current = cycle(id);
        return declaration.describe(current.layout());
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
        return new ResourceSiteState(declarations, next, cycles, pendingWorldChanges, pendingForeignChanges, cultivation);
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
        return new ResourceSiteState(declarations, nextSites, nextCycles, pendingWorldChanges, pendingForeignChanges, cultivation);
    }
}
