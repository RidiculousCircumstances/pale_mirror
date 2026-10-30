package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteStateTest {
    @Test void declaredSettlementDirectoryKeepsForksAndCurrentMembershipSeparate() {
        var before = initial().resourceSites(); var declaration = before.declarations().values().iterator().next();
        var home = declaration.settlementId();
        var foreign = before.declarations().values().stream().map(ResourceSiteDeclaration::settlementId)
                .filter(id -> !id.equals(home)).findFirst().orElseThrow();
        var original = before.siteIdsIn(home); var other = before.siteIdsIn(foreign);
        assertTrue(original.contains(declaration.id()));
        var changed = new ResourceSiteDeclaration(declaration.id(), foreign, declaration.facilityId(), declaration.kind(), declaration.land());
        var fork = new ResourceSiteState(ImmutableLookupMap.withEntry(before.declarations(), declaration.id(), changed),
                before.sites(), before.cycles());
        assertEquals(original.stream().filter(id -> !id.equals(declaration.id())).toList(), fork.siteIdsIn(home));
        assertTrue(fork.siteIdsIn(foreign).contains(declaration.id()));
        assertEquals(original, before.siteIdsIn(home)); assertEquals(other, before.siteIdsIn(foreign));
        assertTrue(before.siteIdsIn(new SubjectId("settlement:absent")).isEmpty());
        assertTrue(fork.siteIdsIn(foreign).contains(declaration.id()), "querying a predecessor cannot poison the independent declaration branch");
        var codec = new FrontierWorldStateCodec(); var state = initial();
        assertEquals(state.resourceSites().siteIdsIn(home), codec.decode(codec.encode(state)).resourceSites().siteIdsIn(home));
    }

    @Test void executionReferencesRejectJobRemovalWhileItsActorAuthorityIsUnchanged() {
        var fixture = FrontierResourceSiteHarvestFixture.create(new WorldId("frontier:harvest-reference-removal"), 125L);
        var state = fixture.state(); var sites = state.resourceSites(); var lifecycle = sites.site(fixture.siteId());
        HarvestActivityCapability.validateReferences(sites, state.actorExecutions());
        var work = lifecycle.harvestJobs().keySet().iterator().next();
        assertEquals(Optional.of(fixture.siteId()), sites.workSiteId(work));
        assertTrue(sites.workSiteId(new SubjectId("work:absent")).isEmpty());
        var phase = lifecycle.growthStage() == ResourceSiteLifecycle.MATURE_STAGE ? ResourceSitePhase.READY : ResourceSitePhase.GROWING;
        var absent = new ResourceSiteLifecycle(lifecycle.siteId(), phase, lifecycle.growthEpoch(), lifecycle.growthStage(),
                lifecycle.preparationWork(), lifecycle.conflictDisposition(), Map.of(), lifecycle.harvestLineages(), lifecycle.harvestSequence());
        var changed = sites.replace(absent);
        assertTrue(changed.workSiteId(work).isEmpty());
        assertEquals(Optional.of(fixture.siteId()), sites.workSiteId(work), "an abandoned branch cannot lend or erase another site's work");
        assertThrows(IllegalArgumentException.class, () -> HarvestActivityCapability.validateReferences(changed, state.actorExecutions()));
        HarvestActivityCapability.validateReferences(sites, state.actorExecutions());
    }

    @Test void warmedRegisterStillRejectsChangedCyclesDeclarationsAndMissingMembership() {
        var fields = initial().resourceSites();
        var id = fields.sites().keySet().iterator().next();
        var cycle = fields.cycle(id);
        var forged = ImmutableLookupMap.withEntry(fields.cycles(), id,
                ResourceFieldCycle.unsurveyed(id, cycle.layout(), cycle.epoch() + 1));
        assertThrows(IllegalArgumentException.class, () -> new ResourceSiteState(fields.declarations(), fields.sites(), forged));
        assertThrows(IllegalArgumentException.class, () -> new ResourceSiteState(fields.declarations(),
                ImmutableLookupMap.withoutEntry(fields.sites(), id), fields.cycles()));
        var foreign = fields.declarations().values().stream().filter(value -> !value.id().equals(id)).findFirst().orElseThrow();
        var declarations = ImmutableLookupMap.withEntry(fields.declarations(), id, foreign);
        assertThrows(IllegalArgumentException.class, () -> new ResourceSiteState(declarations, fields.sites(), fields.cycles()));
        var restored = new ResourceSiteState(fields.declarations(), fields.sites(), fields.cycles());
        assertEquals(fields, restored, "failed partial validation must not poison the valid predecessor");
        assertEquals(fields, ImmutableInvariantCheck.full(() -> new ResourceSiteState(fields.declarations(), fields.sites(), fields.cycles())));
    }

    @Test void changedFieldCannotOverlapAnUnchangedFieldAfterWarmValidation() {
        var state = initial(); var fields = state.resourceSites();
        fields.validate(state.bootstrap());
        var ids = fields.cycles().keySet().stream().limit(2).toList();
        var id = ids.getFirst(); var cycle = fields.cycle(id);
        var foreign = fields.cycle(ids.get(1)).layout();
        var overlap = new ResourceSiteState(fields.declarations(), fields.sites(), ImmutableLookupMap.withEntry(
                fields.cycles(), id, ResourceFieldCycle.unsurveyed(id, foreign, cycle.epoch())));
        assertThrows(IllegalArgumentException.class, () -> overlap.validate(state.bootstrap()));
        fields.validate(state.bootstrap());
        assertThrows(IllegalArgumentException.class, () -> ImmutableInvariantCheck.full(() -> {
            overlap.validate(state.bootstrap()); return null;
        }));
        fields.validate(state.bootstrap());
    }
    @Test void warmedLayoutValidationRejectsAnOutOfBoundsForkAndKeepsItsPredecessorValid() {
        var state = initial(); var sites = state.resourceSites(); var id = sites.declarations().keySet().iterator().next();
        sites.validate(state.bootstrap()); var before = sites.descriptors(state.bootstrap()); var cycle = sites.cycle(id);
        int offset = state.bootstrap().bounds().width() * 2;
        var cells = cycle.layout().cells().stream().map(cell -> new ResourceFieldLayout.Cell(cell.id(),
                cell.crop().offset(offset, 0, 0), new SurfaceAnchor(cell.soil().support().offset(offset, 0, 0)),
                new SurfaceAnchor(cell.workstation().support().offset(offset, 0, 0)))).toList();
        // Hydration may supply a forged layout; ordinary revision already rejects moved cell IDs.
        var layout = new ResourceFieldLayout(cycle.layout().revision() + 1, cycle.layout().nextCellId(), cells, cycle.layout().irrigationSlots());
        var cycles = ImmutableLookupMap.withEntry(sites.cycles(), id, ResourceFieldCycle.unsurveyed(id, layout, cycle.epoch()));
        var changed = new ResourceSiteState(sites.declarations(), sites.sites(), cycles);
        assertThrows(IllegalArgumentException.class, () -> changed.validate(state.bootstrap()));
        assertThrows(IllegalArgumentException.class, () -> ImmutableInvariantCheck.full(() -> { changed.validate(state.bootstrap()); return null; }));
        sites.validate(state.bootstrap());
        assertEquals(before, sites.descriptors(state.bootstrap()));
        assertEquals(layout, changed.descriptors(state.bootstrap()).get(id).layout(), "a read view cannot conceal the changed declaration even when validation rejects it");
    }
    @Test
    void currentDescriptorTakesIdentityFromRegistryAndGeometryFromCanonicalCycle() {
        FrontierWorldState baseline = initial();
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        ResourceSite bootstrapSite = FrontierResourceSitePlan.compile(baseline.bootstrap()).get(siteId);
        var predecessor = baseline.resourceSites().descriptors(baseline.bootstrap());
        ResourceFieldCycle initial = baseline.resourceSites().cycle(siteId);
        ResourceFieldLayout revised = initial.layout().revise(2, initial.layout().nextCellId(),
                initial.layout().cells(), initial.layout().irrigationSlots());
        ResourceFieldCycle current = initial.revised(revised, java.util.Set.of());
        Map<SubjectId, ResourceFieldCycle> cycles = new java.util.LinkedHashMap<>(baseline.resourceSites().cycles());
        cycles.put(siteId, current);
        ResourceSiteState sites = new ResourceSiteState(baseline.resourceSites().declarations(), baseline.resourceSites().sites(), cycles);
        ResourceSite descriptor = sites.descriptor(baseline.bootstrap(), siteId);
        assertEquals(bootstrapSite.settlementId(), descriptor.settlementId());
        assertEquals(bootstrapSite.facilityId(), descriptor.facilityId());
        assertEquals(revised, descriptor.layout());
        assertEquals(revised, sites.descriptors(baseline.bootstrap()).get(siteId).layout());
        assertEquals(bootstrapSite.layout(), predecessor.get(siteId).layout(), "old read remains frozen after layout revision");
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(baseline));
        assertEquals(predecessor, recovered.resourceSites().descriptors(recovered.bootstrap()));
        assertThrows(UnsupportedOperationException.class, () -> predecessor.clear());
        assertEquals(1, bootstrapSite.layout().revision(), "bootstrap remains an immutable initial plan");
        assertThrows(IllegalArgumentException.class, () -> sites.descriptor(baseline.bootstrap(), new SubjectId("site:missing")));
    }

    @Test
    void fieldLifecycleKeepsOneExactWorkIdentityAndRecoversWithoutChangingIt() {
        FrontierWorldState baseline = initial();
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        ResourceSitePreparationJob preparation = new ResourceSitePreparationJob(new SubjectId("job:site-prepare-1-wheat-field"), siteId,
                new PhysicalIntentId("intent:site-prepare-1-wheat-field"));
        ResourceSiteLifecycle preparing = baseline.resourceSites().site(siteId).preparing(preparation);
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(
                baseline.withResourceSites(baseline.resourceSites().replace(preparing))));

        assertEquals(preparing, recovered.resourceSites().site(siteId));
        ResourceSiteLifecycle growing = preparing.prepared();
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) growing = growing.advanceGrowth();
        assertEquals(ResourceSitePhase.READY, growing.phase());
        SubjectId jobId = new SubjectId("job:site-harvest-1-wheat-field-1");
        ResourceSite site = FrontierResourceSitePlan.compile(baseline.bootstrap()).get(siteId);
        ResourceSiteHarvestJob harvest = new ResourceSiteHarvestJob(new SubjectId("job:site-harvest-1-wheat-field-1"), new SubjectId("task:field-harvest-1"), siteId,
                new SubjectId("resident:1-1"), new SubjectId("custody:field-actor-site-harvest-1-wheat-field-1"),
                ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(new SubjectId("settlement:1"))),
                new SubjectId("item:site-harvest-1-wheat-field-1"),
                new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(new SubjectId("settlement:1")), 1),
                new PhysicalIntentId("intent:site-harvest-1-wheat-field-1"), completeProgress(site.layout().cells().size()),
                ResourceFieldCycle.seeded(siteId, site.layout(), 1).target(site.layout().cells().getFirst().id()),
                new WorkToolAssignment(new SubjectId("item:field-hoe"), ResourceStorageAccess.at(baseline,
                        FrontierWorldState.depotId(new SubjectId("settlement:1"))).endpoint(), WorkToolAssignment.Stage.RETURNED));
        ResourceFieldCycle unworked = ResourceFieldCycle.seeded(siteId, site.layout(), 1);
        ResourceSiteLifecycle ready = growing;
        assertThrows(IllegalArgumentException.class, () -> new ResourceSiteState(Map.of(siteId, ResourceSiteDeclaration.of(site)), Map.of(siteId, ready),
                Map.of(siteId, ResourceFieldCycle.seeded(new SubjectId("site:foreign"), site.layout(), 1))),
                "the map key and matching geometry cannot adopt another site's cycle");
        ResourceSiteLifecycle falselyComplete = growing.harvesting(harvest);
        assertEquals(harvest, new ResourceSiteState(Map.of(siteId, ResourceSiteDeclaration.of(site)), Map.of(siteId, falselyComplete), Map.of(siteId, unworked))
                .site(siteId).harvestJob(harvest.id()).orElseThrow(),
                "execution accounting is independent of collective plant flags");
        SurfaceAnchor depotStation = SurfaceAnchor.at(1, 64, 1);
        ResourceSiteHarvestGoal depotGoal = new ResourceSiteHarvestGoal(harvest.id(), siteId, harvest.workerId(),
                site.layout().revision(), harvest.storageRevision(), site.layout().cells().size(), ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE,
                Optional.empty(), List.of(depotStation), TraversalCapability.PEDESTRIAN,
                ResourceSiteHarvestGoal.ArrivalContract.ANY_DEPOT_SERVICE_STATION);
        ResourceSiteLifecycle harvested = growing.harvesting(harvest).harvestedAt(depotGoal, depotStation.standingBody());
        assertEquals(ResourceSitePhase.READY, harvested.phase());
        assertEquals(1L, harvested.growthEpoch()); assertEquals(7, harvested.growthStage());
        assertTrue(harvested.harvestJobs().isEmpty());
    }

    private static ResourceSiteHarvestProgress completeProgress(int slots) {
        ResourceSiteHarvestProgress progress = ResourceSiteHarvestProgress.notStarted(slots);
        for (int index = 0; index < slots; index++) {
            progress = progress.prepareNextCrop().confirmPreparedCrop();
        }
        return progress;
    }

    @Test
    void registerRejectsMissingSitesAndIllegalLifecycleTransitions() {
        FrontierWorldState baseline = initial(); SubjectId siteId = new SubjectId("site:1-wheat-field");
        assertThrows(IllegalArgumentException.class, () -> baseline.withResourceSites(new ResourceSiteState(Map.of(), Map.of(), Map.of())));
        assertThrows(IllegalArgumentException.class, () -> new ResourceSiteLifecycle(siteId, ResourceSitePhase.READY, 0L,
                ResourceSiteLifecycle.MATURE_STAGE, java.util.Optional.empty()));
        assertThrows(IllegalStateException.class, () -> baseline.resourceSites().site(siteId).advanceGrowth());
        assertTrue(baseline.resourceSites().sites().values().stream().allMatch(site -> site.phase() == ResourceSitePhase.UNPREPARED));
    }

    @Test
    void destroyedFarmRetiresOnlyItsOwnResourceSite() {
        FrontierWorldState baseline = initial();
        FrontierWorldState destroyed = baseline.withStructureCondition(new SubjectId("structure:1-farm"), StructureCondition.DESTROYED);
        assertEquals(ResourceSitePhase.DESTROYED, destroyed.resourceSites().site(new SubjectId("site:1-wheat-field")).phase());
        assertEquals(ResourceSitePhase.UNPREPARED, destroyed.resourceSites().site(new SubjectId("site:2-wheat-field")).phase());
        assertEquals(destroyed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(destroyed)));
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-state"), 1234L));
    }
}
