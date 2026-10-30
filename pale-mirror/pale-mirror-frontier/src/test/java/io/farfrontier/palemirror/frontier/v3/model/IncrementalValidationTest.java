package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalValidationTest {
    @Test void fieldPhysicalClosureRechecksChangedIntentReceiptAndLifecycleAfterSuccessfulEvidence() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:field-physical-reads"), 125L));
        var site = state.resourceSites().declarations().keySet().iterator().next();
        var id = ResourceSitePhysicalIntentStateSupport.intentId(site);
        var preparation = new ResourceSitePreparationJob(new SubjectId("job:site-prepare-" + site.value().substring("site:".length())), site, id);
        var preparing = state.resourceSites().site(site).preparing(preparation);
        var cycle = state.resourceSites().cycle(site);
        var sites = state.resourceSites().replace(preparing.prepared(), ResourceFieldCycle.seeded(site, cycle.layout(), 1));
        var prepared = new PhysicalIntent(id, PhysicalIntentKind.RESOURCE_SITE_PREPARATION, PhysicalIntentStatus.PREPARED,
                site, PhysicalIntentRoleBinding.sitePreparation(site, preparation.id()),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.whole(64), FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION);
        var receipt = new ResourceSitePreparationObservation(new PhysicalObservationId("observation:field-reference-prepared"), id, site, 1, 1);
        var confirmed = prepared.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id()));
        Map<PhysicalIntentId, PhysicalIntent> intents = ImmutableLookupMap.copyOf(Map.of(id, confirmed));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = ImmutableLookupMap.copyOf(Map.of(receipt.id(), receipt));
        ResourceSitePhysicalIntentStateSupport.validateState(sites, intents, observations);
        assertThrows(IllegalArgumentException.class, () -> ResourceSitePhysicalIntentStateSupport.validateState(sites, intents, Map.of()),
                "receipt retirement must invalidate an unchanged lifecycle's successful evidence");
        var foreignReceipt = new ResourceSitePreparationObservation(receipt.id(), id, new SubjectId("site:foreign"), 1, 1);
        assertThrows(IllegalArgumentException.class, () -> ResourceSitePhysicalIntentStateSupport.validateState(sites, intents,
                ImmutableLookupMap.withEntry(observations, receipt.id(), foreignReceipt)), "same-address foreign receipt cannot inherit its predecessor's proof");
        assertThrows(IllegalArgumentException.class, () -> ResourceSitePhysicalIntentStateSupport.validateState(sites,
                ImmutableLookupMap.withEntry(intents, id, prepared), observations), "changed intent status cannot reuse a confirmed-field proof");
        var unprepared = state.resourceSites().replace(preparing);
        assertThrows(IllegalArgumentException.class, () -> ResourceSitePhysicalIntentStateSupport.validateState(unprepared, intents, observations),
                "a lifecycle-only change must recheck the physical boundary");
        assertDoesNotThrow(() -> ResourceSitePhysicalIntentStateSupport.validateState(sites, intents, observations));
        assertDoesNotThrow(() -> ImmutableInvariantCheck.full(() -> {
            ResourceSitePhysicalIntentStateSupport.validateState(sites, intents, observations); return null;
        }));
        var recovered = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        assertDoesNotThrow(() -> ResourceSitePhysicalIntentStateSupport.validateState(recovered.resourceSites(), Map.of(), Map.of()));
    }
    @Test void harvestAccountCustodyChangeCannotReuseAnUnchangedFieldsSuccessfulProof() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var before = fixture.state(); var job = fixture.job();
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, before));
        var resources = before.inventory().fungibleResources();
        var account = java.util.Objects.requireNonNull(resources.accounts().get(job.depotAccountId()));
        var foreign = new CustodyAccount(account.id(), new ResourceCustody.Actor(job.workerId()),
                account.lotQuantities(), account.claimQuantities());
        var changed = new FungibleResourceLedger(resources.lots(), resources.claims(),
                ImmutableLookupMap.withEntry(resources.accounts(), foreign.id(), foreign), resources.bindings());
        var preview = FrontierWorldState.duringUnpublishedTransition(() -> before.withInventory(
                before.inventory().withFungibleResources(changed)));
        var failure = assertThrows(IllegalArgumentException.class,
                () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, preview));
        assertEquals("active harvest depot account has foreign custody", failure.getMessage());
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateInitial(preview));
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, before));
    }
    @Test void populationPolicyRechecksChangedNeedHealthAndIndependentProfileBranches() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:policy-row-deltas"), 125L));
        var bootstrap = state.bootstrap(); var before = state.humanPopulation(); var rules = bootstrap.ruleset().residentLife();
        var person = before.residents().values().iterator().next();
        SettlementPolicyValidation.validate(bootstrap, before);
        var changedProfile = before.changeMetabolism(person.id(), person.characteristics(),
                person.characteristics().withBaseMetabolism(500), 1, rules);
        assertDoesNotThrow(() -> SettlementPolicyValidation.validate(bootstrap, changedProfile));
        assertDoesNotThrow(() -> SettlementPolicyValidation.validate(bootstrap, before), "independent branches restore their exact row basis");
        var malformedNeed = new ResidentNutrition(ResidentNutritionStatus.HUNGRY, rules.satietyCapacityUnits(), 0, 0);
        var changedNeed = new HumanPopulation(before.households(), before.residents(), before.birthJobs(), before.health(),
                before.quarantines(), before.migrations(), before.provisions(),
                ImmutableLookupMap.withEntry(before.nutrition(), person.id(), malformedNeed), before.medicalOperations(),
                before.schedules(), before.meals(), before.mealResourceObligations());
        var changedHealth = before.withStarvation(person.id(), new ResidentStarvation(1, rules.starvation().gainTicksPerUnit(), 0));
        for (var invalid : List.of(changedNeed, changedHealth)) {
            assertDoesNotThrow(() -> SettlementPolicyValidation.validate(bootstrap, before));
            assertThrows(IllegalArgumentException.class, () -> SettlementPolicyValidation.validate(bootstrap, invalid));
            assertThrows(IllegalArgumentException.class, () -> SettlementPolicyValidation.validate(bootstrap, invalid),
                    "a failed partial row audit cannot install its source basis");
            assertThrows(IllegalArgumentException.class, () -> ImmutableInvariantCheck.full(() -> {
                SettlementPolicyValidation.validate(bootstrap, invalid); return null;
            }));
        }
        assertDoesNotThrow(() -> ImmutableInvariantCheck.full(() -> {
            SettlementPolicyValidation.validate(bootstrap, changedProfile); return null;
        }));
    }
    @Test void structureMembershipCannotReuseSuccessfulEvidenceAfterEitherInsertionOrRemoval() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:structure-membership"), 125L));
        var first = state.structureConditions().keySet().iterator().next();
        for (var changed : List.of(ImmutableLookupMap.withoutEntry(state.structureConditions(), first),
                ImmutableLookupMap.withEntry(state.structureConditions(), new SubjectId("structure:foreign"), StructureCondition.INTACT))) {
            var preview = FrontierWorldState.duringUnpublishedTransition(() -> state.withChanges(
                    FrontierWorldStateUpdate.begin().structureConditions(changed)));
            assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(state, preview));
            assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateInitial(preview));
        }
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(state, state));
    }
    @Test void successfulEvidenceIsIdentityBoundAndFullAuditNeverUsesIt() {
        var check = new ImmutableInvariantCheck<Void>(); var calls = new AtomicInteger();
        var first = new Object(); var second = new Object();
        check.validate(calls::incrementAndGet, first, null);
        check.validate(calls::incrementAndGet, first, null);
        assertEquals(1, calls.get());
        assertThrows(IllegalArgumentException.class, () -> check.validate(() -> { throw new IllegalArgumentException("bad"); }, second, null));
        check.validate(calls::incrementAndGet, second, null);
        assertEquals(2, calls.get(), "failed checks cannot bless a changed input");
        ImmutableInvariantCheck.full(() -> { check.validate(calls::incrementAndGet, second, null); return null; });
        assertEquals(3, calls.get());
    }

    @Test void exactActorReplacementRetainsDeclaredKindsButCannotHideInvalidPositionKindOrRetirement() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:actor-map-change"), 421L));
        var id = state.bootstrap().settlements().getFirst().residents().getFirst().id(); var actor = state.actorLocations().get(id);
        var presence = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(id,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, id, 1);
        var idle = io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState.empty().begin(presence, 0);
        var alive = ImmutableLookupMap.copyOf(Map.of(id, actor));
        PresenceActivityCapability.validateReferences(alive, idle);
        assertThrows(IllegalArgumentException.class, () -> PresenceActivityCapability.validateReferences(
                ImmutableLookupMap.withEntry(alive, id, actor.deadAt(actor.body())), idle),
                "unchanged presence authority must still reject a changed dead actor");
        assertDoesNotThrow(() -> PresenceActivityCapability.validateReferences(alive, idle));
        var kinds = ActorIdentityDeclarations.declaredKinds(state.bootstrap(), state.actorLocations());
        var moved = ImmutableLookupMap.withEntry(state.actorLocations(), id, actor.withBody(actor.body().offset(1, 0, 0)));
        assertSame(kinds, ActorIdentityDeclarations.declaredKinds(state.bootstrap(), moved));
        var outside = actor.withBody(BodyPosition.above(SurfaceAnchor.at(state.bootstrap().bounds().maxXExclusive(), 64, 0)));
        var candidates = List.of(ImmutableLookupMap.withEntry(state.actorLocations(), id, outside),
                ImmutableLookupMap.withEntry(state.actorLocations(), id, new ActorLocation(actor.body(), actor.condition(), ActorKind.BIOFORM, actor.navigationProfile())),
                ImmutableLookupMap.withoutEntry(state.actorLocations(), id));
        for (var actors : candidates) {
            var preview = FrontierWorldState.duringUnpublishedTransition(() -> state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)));
            assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(state, preview));
        }
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateInitial(state));
    }

    @Test void fieldReferenceProjectionAndExactJobDirectoryFollowRetirement() {
        var fixture = ResourceSiteHarvestProcessTest.awaitingEquipment(125L);
        var sites = fixture.state().resourceSites(); var job = fixture.job();
        var before = ResourceSitePhysicalIntentStateSupport.referenceView(sites);
        assertTrue(before.subjects().contains(job.id()));
        assertEquals(job, sites.harvestJob(job.id()).orElseThrow());
        var refreshed = sites.replace(sites.site(job.siteId()).withHarvestEquipment(job, job.equipment().transferred()));
        assertNotEquals(sites, refreshed, "fixture advances the equipment phase without changing field references");
        assertSame(before.subjects(), ResourceSitePhysicalIntentStateSupport.referenceView(refreshed).subjects(),
                "an unchanged declaration does not rebuild every field's reference membership");
        var after = sites.replace(sites.site(job.siteId()).destroyed());
        assertFalse(ResourceSitePhysicalIntentStateSupport.referenceView(after).subjects().contains(job.id()));
        assertTrue(after.harvestJob(job.id()).isEmpty());
        assertEquals(job, sites.harvestJob(job.id()).orElseThrow(), "a later image cannot change an earlier immutable job directory");
        assertTrue(ResourceSitePhysicalIntentStateSupport.referenceView(sites).subjects().contains(job.id()));
        var full = ImmutableInvariantCheck.full(() -> ResourceSitePhysicalIntentStateSupport.referenceView(sites));
        assertEquals(before, full, "full audit reconstructs the same complete owner reference set");
    }

    @Test void deferredPreviewStillFailsAtPublicationAndRecoveryWhenAnExecutionRelationshipIsMissing() {
        var before = ResourceSiteHarvestProcessTest.awaitingEquipment(125L).state();
        var actors = new java.util.LinkedHashMap<>(before.actorLocations());
        actors.remove(before.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                .findFirst().orElseThrow().workerId());
        var preview = FrontierWorldState.duringUnpublishedTransition(() -> before.withChanges(
                FrontierWorldStateUpdate.begin().actorLocations(actors)));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, preview));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateInitial(preview));
        // Warm successful evidence must invalidate when either endpoint changes independently.
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, before));
        var job = before.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                .findFirst().orElseThrow();
        var withdrawn = FrontierWorldState.duringUnpublishedTransition(() -> before.withChanges(
                FrontierWorldStateUpdate.begin().resourceSites(before.resourceSites().replace(before.resourceSites().site(job.siteId()).destroyed()))));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, withdrawn));
        var current = before.actorExecutions().current(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST)
                .get(job.workerId());
        var vacant = before.actorExecutions().finish(current);
        var foreign = vacant.next(job.workerId(), current.activityKind(), new SubjectId("job:site-harvest-absent"));
        var replaced = FrontierWorldState.duringUnpublishedTransition(() -> before.withChanges(
                FrontierWorldStateUpdate.begin().actorExecutions(vacant.begin(foreign, current.generation()))));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, replaced));
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateInitial(before));
    }

    @Test void retiredResourceRejectsUnchangedIncomingScheduleButAtomicCancellationIsValid() {
        var before = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:incremental-reference"), 421L));
        var owner = before.bootstrap().settlements().getFirst().id();
        var lot = new SubjectId("lot:bootstrap-1-wheat");
        var action = new ScheduledAction(new ScheduleId("schedule:lot-observation"), new SimInstant(1), 1, lot, "test.reference", 1);
        FrontierReferenceClosure.validateScheduledSubjects(before, List.of(action));
        var terrainChanged = before.recordPhysicalDelta(new PhysicalDelta(new BlockPosition(0, 64, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, java.util.Optional.empty(), java.util.Optional.empty(), "ordinary-observation"));
        var addressed = new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleReadView() {
            @Override public List<ScheduledAction> snapshot() { throw new AssertionError("ordinary validation copied the entire queue"); }
            @Override public List<ScheduledAction> forSubjects(java.util.Set<SubjectId> subjects) {
                return subjects.contains(lot) ? List.of(action) : List.of();
            }
            @Override public java.util.Optional<ScheduledAction> action(ScheduleId id) { return java.util.Optional.of(action).filter(value -> value.id().equals(id)); }
        };
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransaction(before, terrainChanged, List.of(),
                addressed, addressed, List.of()));
        var ledger = before.inventory().fungibleResources();
        var amount = ledger.lots().get(lot).quantity();
        var changed = ledger.transformCold(ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(owner)),
                Map.of(lot, amount), Map.of(), new ResourceLot(new SubjectId("lot:replacement-bread"), owner,
                        ResidentMeal.BREAD_KIND, 1, "test", List.of()));
        var after = before.withInventory(before.inventory().withFungibleResources(changed));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(before, after,
                addressed, List.of()), "retirement cannot evade validation by leaving the schedule unchanged");
        assertDoesNotThrow(() -> FrontierReferenceClosure.validateTransition(before, after, io.farfrontier.palemirror.frontier.v3.kernel.ScheduleReadView.of(List.of()), List.of()));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(after, after,
                addressed, List.of(action)), "new references must also exist");
    }
}
