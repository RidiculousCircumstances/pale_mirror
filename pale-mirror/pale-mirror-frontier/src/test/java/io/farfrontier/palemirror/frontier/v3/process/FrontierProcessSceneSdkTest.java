package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production conformance proofs for the internal Process/Scene SDK.
 *
 * <p>Descriptors contain only typed fixture setup plus observations. Every lifecycle mutation
 * enters a real {@link FrontierCanonicalStateAccess}: the runtime command planner, kernel
 * schedule queue, registered reducer, WAL transaction boundary and state/payload codecs are the
 * same production composition root used by a server. A descriptor never owns a duplicate
 * counter, event reducer, or schedule queue.</p>
 */
class FrontierProcessSceneSdkTest {
    @Test
    void referenceHarvestUsesTheGenericSdkOverItsProductionAggregateReducerSchedulerAndCodecs() {
        FrontierProcessSceneSdk.Descriptor<HarvestContext> descriptor = new HarvestDescriptor();
        FrontierProcessSceneSdk.requireDistinctFamilies(List.of(descriptor));
        assertEquals(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                FrontierDurationProcessDriverRegistry.requireFamily(descriptor.family()));
        FrontierProcessSceneSdk.verify(descriptor);
    }

    @Test
    void existingExactTransitConformsThroughTheSameSdkWithoutBecomingAnEnforcedFamily() {
        FrontierProcessSceneSdk.Descriptor<TransitContext> descriptor = new TransitDescriptor();
        FrontierProcessSceneSdk.requireDistinctFamilies(List.of(new HarvestDescriptor(), descriptor));
        assertEquals(FrontierDurationProcessDriverRegistry.Family.POPULATION_MIGRATION,
                FrontierDurationProcessDriverRegistry.requireFamily(descriptor.family()));
        assertEquals(FrontierDurationProcessDriverRegistry.ContractState.DEFERRED,
                FrontierDurationProcessDriverRegistry.Family.POPULATION_MIGRATION.contractState(),
                "F0.V conformance is not permission to close transit's later foundation slice");
        FrontierProcessSceneSdk.verify(descriptor);
    }

    @Test
    void closedDriverCompositionStillRejectsMissingOrDuplicateHarvestDrivers() {
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.compose(List.of(
                new FrontierDurationProcessDriverRegistry.ColdDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                        ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)), Set.of(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND),
                Set.of(SceneCauseKind.RESOURCE_SITE_HARVEST)));
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.compose(List.of(
                new FrontierDurationProcessDriverRegistry.ColdDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                        ResourceSiteHarvestProcess.COLD_PROGRESS_KIND),
                new FrontierDurationProcessDriverRegistry.ColdDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                        ResourceSiteHarvestProcess.COLD_PROGRESS_KIND),
                new FrontierDurationProcessDriverRegistry.HotDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                        SceneCauseKind.RESOURCE_SITE_HARVEST)), Set.of(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND),
                Set.of(SceneCauseKind.RESOURCE_SITE_HARVEST)));
    }

    @Test
    void closedSdkInventoryRejectsAMissingPhysicalSceneProviderBeforeAdmission() {
        java.util.EnumSet<SceneCauseKind> providers = java.util.EnumSet.allOf(SceneCauseKind.class);
        assertTrue(providers.remove(SceneCauseKind.RESOURCE_SITE_HARVEST));
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requirePhysicalSceneProviders(providers));
        FrontierDurationProcessDriverRegistry.requirePhysicalSceneProviders(java.util.EnumSet.allOf(SceneCauseKind.class));
    }

    @Test
    void registeredSceneAdmissionAndTickRejectEveryLiveHarvestBudgetOverflow() {
        SceneLease one = budgetLease(1);
        FrontierDurationProcessDriverRegistry.SceneWorkUsage base = FrontierDurationProcessDriverRegistry.SceneWorkUsage.forLease(one, 0, 0);
        FrontierSceneLeaseAdmissionGuard.require(new ResourceSiteHarvestSceneLeasePrepared(one));
        assertThrows(IllegalArgumentException.class, () -> FrontierSceneLeaseAdmissionGuard.require(
                new ResourceSiteHarvestSceneLeasePrepared(budgetLease(17))),
                "the generic command/replay guard must use the registered harvest descriptor");
        FrontierDurationProcessDriverRegistry.requireSceneAdmission(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST, one, base);
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireSceneAdmission(
                FrontierDurationProcessDriverRegistry.Family.PRODUCTION_WORK, one, base),
                "a lease may not borrow a different family's descriptor limits");
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireSceneAdmission(
                FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST, budgetLease(17),
                FrontierDurationProcessDriverRegistry.SceneWorkUsage.forLease(budgetLease(17), 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireSceneAdmission(
                FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST, one,
                new FrontierDurationProcessDriverRegistry.SceneWorkUsage(1, 33, 0, base.localChunks(), base.navigationNodes(), 0,
                        base.retainedRecords(), base.retainedBytes(), 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireSceneTick(
                FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST, one, base.withObservations(33)));
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireSceneAdmission(
                FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST, one,
                new FrontierDurationProcessDriverRegistry.SceneWorkUsage(1, 0, 0, base.localChunks(), base.navigationNodes(), 0,
                        1_025, base.retainedBytes(), 1, 1)));
        assertEquals(1, FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST.definition().limits().maxActors(),
                "the reference process must retain an explicit per-family actor ceiling");
        assertNotEquals(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST.definition().limits(),
                FrontierDurationProcessDriverRegistry.Family.ROUTE_OPERATION.definition().limits(),
                "a broad execution archetype must not silently supply every family's limits");
    }

    private static SceneLease budgetLease(int count) {
        List<SceneMember> members = new ArrayList<>(); java.util.Map<SubjectId, BodyPosition> positions = new java.util.LinkedHashMap<>();
        WorldId world = new WorldId("frontier:f0v-sdk-budget");
        for (int index = 0; index < count; index++) {
            SubjectId actor = new SubjectId("resident:budget-" + index);
            members.add(new SceneMember(actor, SceneLease.deterministicEntityId(world, actor)));
            positions.put(actor, new BodyPosition(index * 16, 65, 0));
        }
        return SceneLease.forCause(new SceneLeaseId("lease:f0v-sdk-budget-" + count), world,
                new ResourceSiteHarvestSceneCause(new SubjectId("job:site-harvest-f0v-sdk-budget")), new BlockPosition(0, 64, 0), new SimInstant(1L), 1L,
                SceneLeaseStatus.PREPARED, members, positions, Set.of(), Optional.empty());
    }

    @Test
    void closedSdkInventoryRejectsDescriptorArchetypeVersionVocabularyAndBoundDrift() {
        List<io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor> production = FrontierWorldProcessCatalog.descriptors();
        List<FrontierProcessSceneSdk.DescriptorDefinition> declarations = FrontierDurationProcessDriverRegistry.inventoryDefinitions(production);
        Set<String> installedCodecs = FrontierWorldRuntimeDefinition.payloadCodecs().types();
        FrontierDurationProcessDriverRegistry.requireDescriptorComposition(declarations, production, FrontierWorldProcessCatalog.scheduledKinds(), Set.of(SceneCauseKind.values()), installedCodecs);
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireDescriptorComposition(
                declarations.subList(1, declarations.size()), production, FrontierWorldProcessCatalog.scheduledKinds(),
                Set.of(SceneCauseKind.values()), installedCodecs));
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireDescriptorComposition(List.of(
                declarations.getFirst(), declarations.getFirst()), production, FrontierWorldProcessCatalog.scheduledKinds(), Set.of(SceneCauseKind.values()), installedCodecs));
        FrontierProcessSceneSdk.DescriptorDefinition harvest = declarations.stream().filter(value -> value.family().equals(
                FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST.sdkFamilyKey())).findFirst().orElseThrow();
        FrontierProcessSceneSdk.DescriptorDefinition wrongArchetype = new FrontierProcessSceneSdk.DescriptorDefinition(harvest.family(),
                harvest.schemaVersion(), FrontierProcessSceneSdk.ExecutionArchetype.ATOMIC_INTENT, harvest.canonicalOwner(), harvest.vocabulary(),
                FrontierProcessSceneSdk.ExecutionBinding.intent("frontier.test.intent", "frontier.test.postcondition"), harvest.limits());
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireDescriptorComposition(
                replace(declarations, harvest, wrongArchetype), production, FrontierWorldProcessCatalog.scheduledKinds(),
                Set.of(SceneCauseKind.values()), installedCodecs));
        FrontierProcessSceneSdk.DescriptorDefinition unknownVersion = new FrontierProcessSceneSdk.DescriptorDefinition(harvest.family(),
                harvest.schemaVersion() + 1, harvest.archetype(), harvest.canonicalOwner(), harvest.vocabulary(), harvest.binding(), harvest.limits());
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireDescriptorComposition(
                replace(declarations, harvest, unknownVersion), production, FrontierWorldProcessCatalog.scheduledKinds(),
                Set.of(SceneCauseKind.values()), installedCodecs));
        assertThrows(IllegalArgumentException.class, () -> new FrontierProcessSceneSdk.Vocabulary(Set.of("frontier.test.command"),
                Set.of("frontier.test.observation"), Set.of(), Set.of("frontier.test.lifecycle")));
        assertThrows(IllegalArgumentException.class, () -> new FrontierProcessSceneSdk.Limits(0, 1, 1, 1, 1, 1, 1, 1, 1, 1));
    }

    @Test
    void closedSdkInventoryRejectsADescriptorVocabularyWhoseProductionCodecWasOmitted() {
        List<io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor> production = FrontierWorldProcessCatalog.descriptors();
        List<FrontierProcessSceneSdk.DescriptorDefinition> declarations = FrontierDurationProcessDriverRegistry.inventoryDefinitions(production);
        String requiredHarvestCodec = declarations.stream().filter(value -> value.family().equals(
                        FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST.sdkFamilyKey())).findFirst().orElseThrow()
                .vocabulary().payloadCodecs().iterator().next();
        Set<String> withoutHarvestCodec = new java.util.HashSet<>(FrontierWorldRuntimeDefinition.payloadCodecs().types());
        assertTrue(withoutHarvestCodec.remove(requiredHarvestCodec));
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.requireDescriptorComposition(
                declarations, production, FrontierWorldProcessCatalog.scheduledKinds(), Set.of(SceneCauseKind.values()), withoutHarvestCodec));
    }

    @Test
    void snapshotRecoveryFencesAnIncompatibleRetainedProcessSceneInventory() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:f0v-sdk-fence"), 201L));
        byte[] encoded = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state);
        byte[] fingerprint = FrontierDurationProcessDriverRegistry.inventoryFingerprint().getBytes(StandardCharsets.UTF_8);
        int offset = indexOf(encoded, fingerprint);
        assertTrue(offset >= 0, "current snapshot must retain its complete SDK inventory fingerprint");
        encoded[offset] = encoded[offset] == '0' ? (byte) '1' : (byte) '0';
        assertThrows(IllegalArgumentException.class, () -> new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(encoded));
    }

    @Test
    void genericSdkRejectsAnAdapterThatLetsANegativeAttemptAlterTheSemanticCheckpoint() {
        assertThrows(AssertionError.class, () -> FrontierProcessSceneSdk.verify(new BrokenNegativeProbe(BrokenNegativeProbe.Breakage.CURSOR)));
        assertThrows(AssertionError.class, () -> FrontierProcessSceneSdk.verify(new BrokenNegativeProbe(BrokenNegativeProbe.Breakage.AUTHORITY_EPOCH)));
    }

    private static final class HarvestDescriptor implements FrontierProcessSceneSdk.Descriptor<HarvestContext> {
        @Override public FrontierProcessSceneSdk.DescriptorDefinition definition() { return FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST.definition(); }
        @Override public HarvestContext initial() {
            SubjectId site = new SubjectId("site:1-wheat-field");
            FrontierWorldState prepared = admitHarvestTask(matureField(FrontierWorldState.initial(FrontierBootstrapper.create(
                    new WorldId("frontier:f0v-sdk-harvest"), 125L)), site), site, 22_000L);
            StrategicTask task = prepared.strategicPlans().tasks().values().stream()
                    .filter(value -> value.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
            EngineContext engine = advance(launch(prepared, List.of(ResourceSiteHarvestProcess.start(task, 22_100L))), new SimInstant(22_100L), 8);
            ResourceSiteHarvestJob job = FrontierProcessSceneSdkTest.harvest(engine, site);
            engine = submit(engine, new PhysicalIntentTransition(job.intentId(), PhysicalIntentStatus.RUNNING, Optional.empty()));
            assertEquals(PhysicalIntentStatus.RUNNING, state(engine).physicalIntents().get(job.intentId()).status());
            return new HarvestContext(engine, site);
        }
        @Override public HarvestContext coldAdvance(HarvestContext context) {
            ScheduledAction action = scheduled(context.engine(), ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, harvest(context).id());
            return context.with(advance(fork(context.engine()), action.dueAt(), 8));
        }
        @Override public HarvestContext acquireHot(HarvestContext context) {
            EngineContext engine = fork(context.engine()); ResourceSiteHarvestJob job = FrontierProcessSceneSdkTest.harvest(engine, context.site());
            BodyPosition body = job.traversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
            BlockPosition crop = FrontierResourceSitePlan.compile(state(engine).bootstrap()).get(context.site()).cropSlots().get(job.progress().nextCropSlotIndex());
            SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:f0v-sdk-harvest-" + revision(engine).value()), state(engine).bootstrap().worldId(),
                    new ResourceSiteHarvestSceneCause(job.id()), crop, instant(engine), revision(engine).value(), SceneLeaseStatus.PREPARED,
                    List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state(engine).bootstrap().worldId(), job.workerId()))),
                    java.util.Map.of(job.workerId(), body), Set.of(job.workerId()), Optional.empty());
            engine = submitBound(engine, new ResourceSiteHarvestSceneLeasePrepared(lease),
                    scheduled(engine, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, job.id()));
            return context.with(submit(engine, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
        }
        @Override public HarvestContext hotCheckpoint(HarvestContext context) {
            EngineContext engine = fork(context.engine()); ResourceSiteHarvestJob job = FrontierProcessSceneSdkTest.harvest(engine, context.site());
            SceneLease lease = activeHarvestLease(state(engine), job);
            return context.with(submitBound(engine, new ResourceSiteHarvestHotTraversalAdvanced(job.id(), lease.id(), job.workerId(),
                    job.nextTraversalSurface().standingBody(), job.traversalCursor() + 1),
                    scheduled(engine, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, job.id())));
        }
        @Override public HarvestContext releaseToCold(HarvestContext context) {
            EngineContext engine = fork(context.engine()); ResourceSiteHarvestJob job = FrontierProcessSceneSdkTest.harvest(engine, context.site()); SceneLease lease = activeHarvestLease(state(engine), job);
            engine = submit(engine, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            ActorLocation actor = state(engine).actorLocations().get(job.workerId());
            return context.with(submitBound(engine, new SceneLeaseReleased(lease.id(), List.of(new SceneMemberPosition(job.workerId(), actor.body(), actor.condition().health()))),
                    scheduled(engine, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, job.id())));
        }
        @Override public HarvestContext snapshotWalRecovery(HarvestContext context) { assertRecovery(context.engine()); return context; }
        @Override public FrontierProcessSceneSdk.SemanticCheckpoint checkpoint(HarvestContext context) {
            ResourceSiteHarvestJob job = harvest(context); SceneLease lease = activeOrClosedHarvestLease(state(context.engine()), job); boolean hot = lease != null && lease.status() == SceneLeaseStatus.HOT;
            return new FrontierProcessSceneSdk.SemanticCheckpoint(family(), job.id().value(), job.workerId().value(), job.traversalCursor(), lease == null ? 0L : lease.revision(), hot,
                    hot ? Set.of(job.workerId().value()) : Set.of(), schedules(context.engine()), job.intentId().value() + ":" + job.outputItemId().value(),
                    job.id() + ":" + job.traversalCursor() + ":" + state(context.engine()).actorLocations().get(job.workerId()).body());
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<HarvestContext> rejectStaleObservation(HarvestContext context) {
            ResourceSiteHarvestJob job = harvest(context); SceneLease lease = activeHarvestLease(state(context.engine()), job);
            assertRejectedBound(context.engine(), new ResourceSiteHarvestHotTraversalAdvanced(job.id(), lease.id(), job.workerId(), state(context.engine()).actorLocations().get(job.workerId()).body(), job.traversalCursor()),
                    scheduled(context.engine(), ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, job.id()));
            return rejected(context, "stale harvest checkpoint");
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<HarvestContext> rejectSecondCursor(HarvestContext context) {
            ResourceSiteHarvestJob job = harvest(context); SceneLease lease = activeHarvestLease(state(context.engine()), job); int skipped = job.traversalCursor() + 2;
            if (skipped >= job.traversal().linearCorridorSurfaces().size()) skipped = 0;
            assertRejectedBound(context.engine(), new ResourceSiteHarvestHotTraversalAdvanced(job.id(), lease.id(), job.workerId(), job.nextTraversalSurface().standingBody(), skipped),
                    scheduled(context.engine(), ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, job.id()));
            return rejected(context, "skipped harvest checkpoint");
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<HarvestContext> rejectDuplicateSchedule(HarvestContext context) {
            assertDuplicateSchedule(scheduled(context.engine(), ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, harvest(context).id())); return rejected(context, "duplicate harvest continuation");
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<HarvestContext> rejectUnregisteredPayload(HarvestContext context) {
            assertRejected(context.engine(), new UnregisteredF0vPayload());
            return rejected(context, "unregistered harvest payload");
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<HarvestContext> rejectMissingCodec(HarvestContext context) { assertMissingCodec(); return rejected(context, "missing harvest codec"); }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<HarvestContext> rejectConcurrentAuthority(HarvestContext context) {
            ResourceSiteHarvestJob job = harvest(context); SceneLease current = activeHarvestLease(state(context.engine()), job);
            SceneLease duplicate = SceneLease.forCause(new SceneLeaseId("lease:f0v-sdk-harvest-duplicate"), state(context.engine()).bootstrap().worldId(), new ResourceSiteHarvestSceneCause(job.id()),
                    current.handoffPosition(), instant(context.engine()), revision(context.engine()).value(), SceneLeaseStatus.PREPARED, current.members(), current.memberPositions(), current.ambientHandoffActorIds(), Optional.empty());
            assertRejectedBound(context.engine(), new ResourceSiteHarvestSceneLeasePrepared(duplicate),
                    scheduled(context.engine(), ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, job.id())); return rejected(context, "concurrent harvest lease");
        }
        @Override public FrontierProcessSceneSdk.ProcessOwnedIntervention<HarvestContext> interventionOwnedByProcess(HarvestContext context) {
            EngineContext engine = fork(context.engine()); ResourceSiteHarvestJob job = FrontierProcessSceneSdkTest.harvest(engine, context.site()); SceneLease lease = activeHarvestLease(state(engine), job);
            engine = submit(engine, new ActorDied(lease.id(), job.workerId(), state(engine).actorLocations().get(job.workerId()).body(), "test:f0v"));
            assertEquals(ActorLifeStatus.DEAD, state(engine).actorLocations().get(job.workerId()).condition().status());
            return new FrontierProcessSceneSdk.ProcessOwnedIntervention<>(context.with(engine), "harvest farmer death entered its owning scene process");
        }
        private ResourceSiteHarvestJob harvest(HarvestContext context) { return FrontierProcessSceneSdkTest.harvest(context.engine(), context.site()); }
    }

    private static final class TransitDescriptor implements FrontierProcessSceneSdk.Descriptor<TransitContext> {
        @Override public FrontierProcessSceneSdk.DescriptorDefinition definition() { return FrontierDurationProcessDriverRegistry.Family.POPULATION_MIGRATION.definition(); }
        @Override public TransitContext initial() {
            FrontierWorldState prepared = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:f0v-sdk-transit"), 91L));
            Settlement source = prepared.bootstrap().settlements().getFirst(); SubjectId housing = source.structures().stream().filter(value -> value.kind() == StructureKind.HOUSING).findFirst().orElseThrow().id();
            prepared = prepared.withStructureCondition(housing, StructureCondition.DESTROYED);
            EngineContext engine = advance(launch(prepared, List.of(PopulationMigrationProcess.review(1, 100L))), new SimInstant(100L), 16);
            SubjectId resident = state(engine).humanPopulation().migrations().keySet().stream().min(Comparator.naturalOrder()).orElseThrow();
            return new TransitContext(engine, resident);
        }
        @Override public TransitContext coldAdvance(TransitContext context) {
            ScheduledAction action = scheduled(context.engine(), "frontier.population.migration.progress", context.resident());
            return context.with(advance(fork(context.engine()), action.dueAt(), 16));
        }
        @Override public TransitContext acquireHot(TransitContext context) {
            EngineContext engine = fork(context.engine()); AmbientActorLease lease = AmbientActorProcess.nextLease(state(engine), context.resident(), instant(engine));
            engine = submit(engine, new AmbientLeasePrepared(lease)); return context.with(submit(engine, new AmbientLeaseTransition(context.resident(), AmbientLeaseStatus.HOT)));
        }
        @Override public TransitContext hotCheckpoint(TransitContext context) {
            EngineContext engine = fork(context.engine()); ResidentMigrationJourney journey = migration(engine, context.resident());
            return context.with(submit(engine, new ResidentTransitAdvanced(context.resident(), journey.nextRouteIndex())));
        }
        @Override public TransitContext releaseToCold(TransitContext context) {
            EngineContext engine = submit(fork(context.engine()), new AmbientLeaseTransition(context.resident(), AmbientLeaseStatus.DRAINING));
            ActorLocation actor = state(engine).actorLocations().get(context.resident());
            return context.with(submit(engine, new AmbientLeaseReleased(context.resident(), actor.body(), actor.condition().health())));
        }
        @Override public TransitContext snapshotWalRecovery(TransitContext context) { assertRecovery(context.engine()); return context; }
        @Override public FrontierProcessSceneSdk.SemanticCheckpoint checkpoint(TransitContext context) {
            ResidentMigrationJourney journey = migration(context.engine(), context.resident());
            AmbientActorLease lease = state(context.engine()).ambientLeases().get(context.resident());
            boolean hot = lease != null && lease.status() == AmbientLeaseStatus.HOT;
            if (journey == null) {
                ActorLocation actor = state(context.engine()).actorLocations().get(context.resident());
                return new FrontierProcessSceneSdk.SemanticCheckpoint(family(), context.resident().value(), context.resident().value(), 0,
                        lease == null ? 0L : lease.revision(), false, Set.of(), schedules(context.engine()), "migration-terminated",
                        context.resident() + ":terminated:" + actor.condition().status());
            }
            return new FrontierProcessSceneSdk.SemanticCheckpoint(family(), context.resident().value(), context.resident().value(), journey.routeIndex(), lease == null ? 0L : lease.revision(), hot,
                    hot ? Set.of(context.resident().value()) : Set.of(), schedules(context.engine()), journey.destinationSettlementId().value() + ":" + journey.destinationHouseholdId().value(),
                    journey.residentId() + ":" + journey.routeIndex() + ":" + state(context.engine()).actorLocations().get(context.resident()).body());
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<TransitContext> rejectStaleObservation(TransitContext context) {
            ResidentMigrationJourney journey = migration(context.engine(), context.resident());
            assertRejected(context.engine(), new ResidentTransitAdvanced(context.resident(), journey.routeIndex()));
            return rejected(context, "stale transit observation");
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<TransitContext> rejectSecondCursor(TransitContext context) {
            ResidentMigrationJourney journey = migration(context.engine(), context.resident());
            assertRejected(context.engine(), new ResidentTransitAdvanced(context.resident(), journey.nextRouteIndex() + 1));
            return rejected(context, "skipped transit cursor");
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<TransitContext> rejectDuplicateSchedule(TransitContext context) {
            assertDuplicateSchedule(scheduled(context.engine(), "frontier.population.migration.progress", context.resident()));
            return rejected(context, "duplicate transit continuation");
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<TransitContext> rejectUnregisteredPayload(TransitContext context) {
            assertRejected(context.engine(), new UnregisteredF0vPayload());
            return rejected(context, "unregistered transit payload");
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<TransitContext> rejectMissingCodec(TransitContext context) { assertMissingCodec(); return rejected(context, "missing transit codec"); }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<TransitContext> rejectConcurrentAuthority(TransitContext context) {
            assertRejected(context.engine(), new AmbientLeasePrepared(
                    AmbientActorProcess.nextLease(state(context.engine()), context.resident(), instant(context.engine()))));
            return rejected(context, "concurrent ambient lease");
        }
        @Override public FrontierProcessSceneSdk.ProcessOwnedIntervention<TransitContext> interventionOwnedByProcess(TransitContext context) {
            EngineContext engine = submit(fork(context.engine()), new AmbientActorDied(context.resident(), state(context.engine()).actorLocations().get(context.resident()).body(), "test:f0v"));
            assertNull(state(engine).humanPopulation().migration(context.resident()));
            return new FrontierProcessSceneSdk.ProcessOwnedIntervention<>(context.with(engine), "transit death entered its owning ambient process");
        }
    }

    /** Deliberately false adapter: generic checkpoint comparison owns its assertions. */
    private static final class BrokenNegativeProbe implements FrontierProcessSceneSdk.Descriptor<ProbeState> {
        enum Breakage { CURSOR, AUTHORITY_EPOCH }
        private static final FrontierProcessSceneSdk.FamilyKey FAMILY = new FrontierProcessSceneSdk.FamilyKey("f0v.negative-probe");
        private final Breakage breakage;
        private BrokenNegativeProbe(Breakage breakage) { this.breakage = breakage; }
        @Override public FrontierProcessSceneSdk.DescriptorDefinition definition() { return new FrontierProcessSceneSdk.DescriptorDefinition(FAMILY, 1, FrontierProcessSceneSdk.ExecutionArchetype.DURATION_WORK,
                "probe-owner", new FrontierProcessSceneSdk.Vocabulary(Set.of("probe.command"), Set.of("probe.observation"), Set.of("probe.codec"), Set.of("probe.lifecycle")),
                FrontierProcessSceneSdk.ExecutionBinding.paired("probe.cold", "probe.hot"), new FrontierProcessSceneSdk.Limits(1, 1, 1, 1, 1, 1, 1, 1, 1, 1)); }
        @Override public ProbeState initial() { return new ProbeState(0, false, 0); }
        @Override public ProbeState coldAdvance(ProbeState state) { return new ProbeState(state.cursor() + 1, false, state.authorityEpoch()); }
        @Override public ProbeState acquireHot(ProbeState state) { return new ProbeState(state.cursor(), true, state.authorityEpoch() + 1); }
        @Override public ProbeState hotCheckpoint(ProbeState state) { return new ProbeState(state.cursor() + 1, true, state.authorityEpoch()); }
        @Override public ProbeState releaseToCold(ProbeState state) { return new ProbeState(state.cursor(), false, state.authorityEpoch()); }
        @Override public ProbeState snapshotWalRecovery(ProbeState state) { return state; }
        @Override public FrontierProcessSceneSdk.SemanticCheckpoint checkpoint(ProbeState state) {
            return new FrontierProcessSceneSdk.SemanticCheckpoint(FAMILY, "probe-owner", "probe-actor", state.cursor(),
                    state.authorityEpoch(), state.hot(), state.hot() ? Set.of("probe-actor") : Set.of(),
                    Set.of("probe-schedule"), "probe-custody", "probe:" + state.cursor());
        }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<ProbeState> rejectStaleObservation(ProbeState state) { return switch (breakage) {
            case CURSOR -> rejected(new ProbeState(state.cursor() + 1, state.hot(), state.authorityEpoch()), "incorrectly accepted stale probe");
            case AUTHORITY_EPOCH -> rejected(new ProbeState(state.cursor(), state.hot(), state.authorityEpoch() + 1), "incorrectly accepted stale authority epoch"); }; }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<ProbeState> rejectSecondCursor(ProbeState state) { return rejected(state, "probe second cursor"); }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<ProbeState> rejectDuplicateSchedule(ProbeState state) { return rejected(state, "probe duplicate schedule"); }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<ProbeState> rejectUnregisteredPayload(ProbeState state) { return rejected(state, "probe payload"); }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<ProbeState> rejectMissingCodec(ProbeState state) { return rejected(state, "probe codec"); }
        @Override public FrontierProcessSceneSdk.RejectedAttempt<ProbeState> rejectConcurrentAuthority(ProbeState state) { return rejected(state, "probe authority"); }
        @Override public FrontierProcessSceneSdk.ProcessOwnedIntervention<ProbeState> interventionOwnedByProcess(ProbeState state) { return new FrontierProcessSceneSdk.ProcessOwnedIntervention<>(state, "probe intervention"); }
    }

    private record HarvestContext(EngineContext engine, SubjectId site) { HarvestContext with(EngineContext replacement) { return new HarvestContext(replacement, site); } }
    private record TransitContext(EngineContext engine, SubjectId resident) { TransitContext with(EngineContext replacement) { return new TransitContext(replacement, resident); } }
    private record ProbeState(int cursor, boolean hot, long authorityEpoch) { }
    /** Immutable branch metadata around a real kernel engine and its actual WAL tail. */
    private record EngineContext(FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration,
                                 FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection> engine,
                                 RecordingCommitter committer, CheckpointImage recoveryBase, long nextCommand) {
        EngineContext afterCommand() { return new EngineContext(configuration, engine, committer, recoveryBase, Math.addExact(nextCommand, 1L)); }
    }
    private static final class RecordingCommitter implements TransactionCommitter {
        private final List<TransactionRecord> transactions = new ArrayList<>();
        @Override public void commit(TransactionRecord transaction, Durability durability) { transactions.add(transaction); }
        List<TransactionRecord> transactions() { return List.copyOf(transactions); }
    }

    private static EngineContext launch(FrontierWorldState state, List<ScheduledAction> schedules) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed());
        RecordingCommitter committer = new RecordingCommitter();
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(state.bootstrap().worldId(), state, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), schedules, committer, base.stateValidator(), base.executionMetrics());
        FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection> engine = FrontierEngines.createCanonicalStateAccess(configuration);
        return new EngineContext(configuration, engine, committer, engine.checkpoint(), 1L);
    }
    /** Forks an encoded canonical snapshot into an isolated engine branch; it never copies lifecycle state by hand. */
    private static EngineContext fork(EngineContext prior) {
        CheckpointImage base = prior.engine().checkpoint(); RecordingCommitter committer = new RecordingCommitter();
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = prior.configuration().withTransactionCommitter(committer);
        FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection> engine = FrontierEngines.recoverCanonicalStateAccess(configuration,
                new RecoveryImage(configuration.worldId(), Optional.of(new SnapshotRecord(base, base.revision().value())), List.of()));
        // Command receipts are part of the recovered snapshot.  Keep the monotonic test nonce
        // across a branch so an otherwise-valid physical observation is never rejected merely
        // because the test harness reused a retained command ID.
        return new EngineContext(configuration, engine, committer, base, prior.nextCommand());
    }
    private static EngineContext advance(EngineContext context, SimInstant target, int maxActions) {
        AdvanceResult result = context.engine().advanceTo(target, new WorkBudget(maxActions, 256));
        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), () -> result.status().failureDetail().orElse("engine quarantined"));
        assertTransactionCodecs(context); return context;
    }
    private static EngineContext submit(EngineContext context, FrontierPayload payload) {
        FrontierCanonicalState<FrontierWorldState> canonical = context.engine().canonicalState(); CommandId id = new CommandId("command:f0v-sdk-" + context.nextCommand());
        CommandResult result = context.engine().submit(new FrontierCommand(1, id, canonical.worldId(), canonical.revision(), canonical.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
        assertInstanceOf(CommandResult.Accepted.class, result, () -> "production command rejected: " + payload.type() + " / "
                + (result instanceof CommandResult.Rejected rejected ? rejected.rejection().detail() : result));
        assertTransactionCodecs(context); return context.afterCommand();
    }
    /** Uses the exact engine action observed at this canonical revision; the SDK owns no shadow queue. */
    private static EngineContext submitBound(EngineContext context, FrontierPayload payload, ScheduledAction action) {
        FrontierCanonicalState<FrontierWorldState> canonical = context.engine().canonicalState(); CommandId id = new CommandId("command:f0v-sdk-" + context.nextCommand());
        CommandResult result = context.engine().submit(new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, canonical.worldId(), canonical.revision(), canonical.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload,
                Optional.of(new EngineScheduleBinding(canonical.revision(), action))));
        assertInstanceOf(CommandResult.Accepted.class, result, () -> "production bound command rejected: " + payload.type() + " / "
                + (result instanceof CommandResult.Rejected rejected ? rejected.rejection().detail() : result));
        assertTransactionCodecs(context); return context.afterCommand();
    }
    private static void assertRejected(EngineContext context, FrontierPayload payload) {
        FrontierCanonicalState<FrontierWorldState> before = context.engine().canonicalState(); CommandId id = new CommandId("command:f0v-sdk-rejected-" + context.nextCommand());
        CommandResult result = context.engine().submit(new FrontierCommand(1, id, before.worldId(), before.revision(), before.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
        assertInstanceOf(CommandResult.Rejected.class, result, () -> "production command unexpectedly accepted: " + payload.type());
        assertEquals(before, context.engine().canonicalState(), "rejected observation may not install a canonical mutation");
    }
    private static void assertRejectedBound(EngineContext context, FrontierPayload payload, ScheduledAction action) {
        FrontierCanonicalState<FrontierWorldState> before = context.engine().canonicalState(); CommandId id = new CommandId("command:f0v-sdk-rejected-" + context.nextCommand());
        CommandResult result = context.engine().submit(new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, before.worldId(), before.revision(), before.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload,
                Optional.of(new EngineScheduleBinding(before.revision(), action))));
        assertInstanceOf(CommandResult.Rejected.class, result, () -> "production bound command unexpectedly accepted: " + payload.type());
        assertEquals(before, context.engine().canonicalState(), "rejected bound observation may not install a canonical mutation");
    }
    private static void assertRecovery(EngineContext context) {
        CheckpointImage current = context.engine().checkpoint();
        FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection> fromSnapshot = FrontierEngines.recoverCanonicalStateAccess(context.configuration(),
                new RecoveryImage(context.configuration().worldId(), Optional.of(new SnapshotRecord(current, current.revision().value())), List.of()));
        FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection> fromWal = FrontierEngines.recoverCanonicalStateAccess(context.configuration(),
                new RecoveryImage(context.configuration().worldId(), Optional.of(new SnapshotRecord(context.recoveryBase(), context.recoveryBase().revision().value())), context.committer().transactions()));
        assertEquals(context.engine().canonicalState(), fromSnapshot.canonicalState(), "production snapshot recovery drifted");
        assertEquals(context.engine().canonicalState(), fromWal.canonicalState(), "production WAL recovery drifted");
        assertEquals(current.schedules(), fromWal.checkpoint().schedules(), "WAL recovery must restore the kernel-owned schedule queue"); assertTransactionCodecs(context);
    }
    private static void assertTransactionCodecs(EngineContext context) {
        for (TransactionRecord transaction : context.committer().transactions()) for (FrontierEvent event : transaction.events()) {
            if (event.payload() instanceof ScheduleEffect) continue;
            assertEquals(event.payload(), FrontierWorldRuntimeDefinition.payloadCodecs().decode(event.payload().type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(event.payload())), "actual WAL payload lacks a stable production codec");
        }
    }
    private static FrontierWorldState state(EngineContext context) { return context.engine().canonicalState().state(); }
    private static SimInstant instant(EngineContext context) { return context.engine().canonicalState().instant(); }
    private static Revision revision(EngineContext context) { return context.engine().canonicalState().revision(); }
    private static ResourceSiteHarvestJob harvest(EngineContext context, SubjectId site) { return (ResourceSiteHarvestJob) state(context).resourceSites().site(site).activeWork().orElseThrow(); }
    private static ResidentMigrationJourney migration(EngineContext context, SubjectId resident) { return state(context).humanPopulation().migration(resident); }
    private static ScheduledAction scheduled(EngineContext context, String kind, SubjectId subject) {
        return context.engine().checkpoint().schedules().stream()
                .filter(value -> value.kind().equals(kind) && value.subject().equals(subject)).findFirst().orElseThrow();
    }
    private static Set<String> schedules(EngineContext context) { return context.engine().checkpoint().schedules().stream().map(value -> value.id().value()).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    private static SceneLease activeHarvestLease(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.HOT)
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .filter(lease -> FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()))
                .findFirst().orElseThrow();
    }
    private static SceneLease activeOrClosedHarvestLease(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .filter(lease -> FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()))
                .max(Comparator.comparing(SceneLease::id)).orElse(null);
    }
    private static FrontierWorldState matureField(FrontierWorldState state, SubjectId site) {
        List<ProposedEvent> preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, payload(preparation, ResourceSitePreparationStarted.class)); state = ResourceSiteProcess.reducePrepared(state, site, payload(preparation, ResourceSitePrepared.class));
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle current = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site,
                    new ResourceSiteGrowthAdvanced(site, current.growthEpoch(), current.growthStage()));
        }
        return state;
    }
    private static FrontierWorldState admitHarvestTask(FrontierWorldState state, SubjectId site, long dueAt) {
        List<ProposedEvent> planned = StrategicObjectiveProcess.planResourceHarvestOpportunity(state, StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), dueAt));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                payload(planned, StrategicObjectiveSelected.class));
        return StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                payload(planned, StrategicTaskPlanned.class));
    }
    private static <T> T payload(List<ProposedEvent> events, Class<T> type) { return events.stream().map(ProposedEvent::payload).filter(type::isInstance).map(type::cast).findFirst().orElseThrow(); }
    private static void assertDuplicateSchedule(ScheduledAction schedule) { ScheduledActionQueue queue = new ScheduledActionQueue(); queue.schedule(schedule); assertThrows(IllegalArgumentException.class, () -> queue.schedule(schedule)); }
    private static void assertMissingCodec() { assertThrows(IllegalArgumentException.class, () -> FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.f0v.missing", new byte[0])); }
    private static <S> FrontierProcessSceneSdk.RejectedAttempt<S> rejected(S retained, String reason) { return new FrontierProcessSceneSdk.RejectedAttempt<>(retained, reason); }
    private record UnregisteredF0vPayload() implements FrontierPayload { @Override public String type() { return "frontier.f0v.sdk.unregistered"; } }
    private static List<FrontierProcessSceneSdk.DescriptorDefinition> replace(
            List<FrontierProcessSceneSdk.DescriptorDefinition> declarations,
            FrontierProcessSceneSdk.DescriptorDefinition oldValue,
            FrontierProcessSceneSdk.DescriptorDefinition newValue) {
        return declarations.stream().map(value -> value.equals(oldValue) ? newValue : value).toList();
    }
    private static int indexOf(byte[] source, byte[] target) {
        for (int index = 0; index <= source.length - target.length; index++) {
            int candidate = 0;
            while (candidate < target.length && source[index + candidate] == target[candidate]) candidate++;
            if (candidate == target.length) return index;
        }
        return -1;
    }
}
