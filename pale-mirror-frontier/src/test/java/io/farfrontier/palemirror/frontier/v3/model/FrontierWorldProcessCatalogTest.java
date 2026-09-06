package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import io.farfrontier.palemirror.frontier.v3.process.FrontierProcessSceneSdk;
import io.farfrontier.palemirror.frontier.v3.process.SettlementServiceWorkProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.kernel.KernelCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierWorldProcessCatalogTest {
    @Test
    void everyWorldPayloadCodecHasOneReducerOwnerBeforeRuntimeConfiguration() {
        DeterministicProcessRegistry registry = FrontierWorldRuntimeDefinition.processRegistry();
        for (String type : FrontierWorldProcessCatalog.allWorldPayloadTypes()) {
            assertFalse(registry.requireReducedEventOwner(type).isBlank());
        }
    }

    @Test
    void everyDeclaredScheduledKindHasExactlyOneCatalogPlanner() {
        assertEquals(FrontierWorldProcessCatalog.scheduledKinds(), FrontierWorldProcessCatalog.descriptors().stream()
                .flatMap(descriptor -> descriptor.scheduledKinds().stream()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @Test
    void pairedDriverCompositionInventoriesEveryCurrentDurationFamilyAndRegistersHarvestExactlyOnce() {
        assertEquals(java.util.EnumSet.allOf(FrontierDurationProcessDriverRegistry.Family.class),
                FrontierDurationProcessDriverRegistry.inventory());
        FrontierDurationProcessDriverRegistry.requireCurrentComposition(FrontierWorldProcessCatalog.descriptors(), FrontierWorldProcessCatalog.scheduledKinds(),
                java.util.Set.of(SceneCauseKind.values()), FrontierWorldRuntimeDefinition.payloadCodecs().types());
        assertEquals(List.of(
                        new FrontierDurationProcessDriverRegistry.ColdDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                                "frontier.resource_site.harvest.cold_progress"),
                        new FrontierDurationProcessDriverRegistry.HotDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                                SceneCauseKind.RESOURCE_SITE_HARVEST)),
                FrontierDurationProcessDriverRegistry.currentRegistrations());
    }

    @Test
    void everyInternalProcessSceneDescriptorVocabularyIsDerivedFromTheProductionCatalogAndCodecRegistry() {
        DeterministicProcessRegistry registry = FrontierWorldRuntimeDefinition.processRegistry();
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        Map<String, io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor> production = FrontierWorldProcessCatalog.descriptors().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor::id, value -> value));
        for (FrontierDurationProcessDriverRegistry.Family family : FrontierDurationProcessDriverRegistry.inventory()) {
            var owner = production.get(family.productionProcessId());
            assertTrue(owner != null, family + " production owner");
            FrontierProcessSceneSdk.DescriptorDefinition descriptor = family.definition(owner);
            assertEquals(owner.commandPayloadTypes(), descriptor.vocabulary().commands(), family + " command vocabulary");
            assertEquals(owner.reducedEventTypes(), descriptor.vocabulary().observations(), family + " observation vocabulary");
            assertEquals(owner.codecTypes(), descriptor.vocabulary().payloadCodecs(), family + " codec vocabulary");
            assertEquals(owner.emittedPayloadTypes(), descriptor.vocabulary().lifecycleTransitions(), family + " lifecycle vocabulary");
            descriptor.vocabulary().payloadCodecs().forEach(codec -> {
                assertTrue(codecs.types().contains(codec), family + " production payload codec registry");
                assertFalse(registry.requireReducedEventOwner(codec).isBlank(), family + " reducer owner");
            });
        }
    }

    @Test
    void pairedDriverCompositionRejectsAColdOnlyDurationProcess() {
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.compose(List.of(
                        new FrontierDurationProcessDriverRegistry.ColdDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                                "frontier.resource_site.harvest.cold_progress")),
                FrontierWorldProcessCatalog.scheduledKinds(), java.util.Set.of(SceneCauseKind.values())));
    }

    @Test
    void pairedDriverCompositionRejectsAHotOnlyDurationProcess() {
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.compose(List.of(
                        new FrontierDurationProcessDriverRegistry.HotDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                                SceneCauseKind.RESOURCE_SITE_HARVEST)),
                FrontierWorldProcessCatalog.scheduledKinds(), java.util.Set.of(SceneCauseKind.values())));
    }

    @Test
    void pairedDriverCompositionRejectsDuplicateAndMismatchedHarvestDrivers() {
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.compose(List.of(
                        new FrontierDurationProcessDriverRegistry.ColdDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                                "frontier.resource_site.harvest.cold_progress"),
                        new FrontierDurationProcessDriverRegistry.ColdDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                                "frontier.resource_site.harvest.cold_progress"),
                        new FrontierDurationProcessDriverRegistry.HotDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                                SceneCauseKind.RESOURCE_SITE_HARVEST)),
                FrontierWorldProcessCatalog.scheduledKinds(), java.util.Set.of(SceneCauseKind.values())));
        assertThrows(IllegalArgumentException.class, () -> FrontierDurationProcessDriverRegistry.compose(List.of(
                        new FrontierDurationProcessDriverRegistry.ColdDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                                "frontier.resource_site.harvest.cold_progress"),
                        new FrontierDurationProcessDriverRegistry.HotDriver(FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST,
                                SceneCauseKind.LOGISTICS)),
                FrontierWorldProcessCatalog.scheduledKinds(), java.util.Set.of(SceneCauseKind.values())));
    }

    @Test
    void fieldWorkPhysicalCommandsHaveTheResourceSiteOwnerBeforeAnExecutorCanSubmitThem() {
        DeterministicProcessRegistry registry = FrontierWorldRuntimeDefinition.processRegistry();
        for (String type : List.of(
                "frontier.resource_site_harvest_crop_prepared",
                "frontier.resource_site_harvest_progressed",
                "frontier.resource_site_harvest_hot_traversal_advanced",
                "frontier.resource_site_harvest_scene_lease_prepared",
                "frontier.resource_site_harvest_scene_lease_handoff")) {
            assertEquals("resource-sites", registry.requireCommandOwner(type), type);
        }
    }

    @Test
    void productionWorkPayloadsHaveTheCompleteEconomyContractBeforeAnExecutorCanSubmitThem() {
        DeterministicProcessRegistry registry = FrontierWorldRuntimeDefinition.processRegistry();
        var economy = FrontierWorldProcessCatalog.descriptors().stream()
                .filter(descriptor -> descriptor.id().equals("economy"))
                .findFirst().orElseThrow();
        for (String type : List.of(
                "frontier.production_work_progressed",
                "frontier.production_work_traversal_advanced",
                "frontier.production_work_traversal_blocked",
                "frontier.production_work_scene_lease_prepared",
                "frontier.production_work_scene_lease_handoff",
                "frontier.production_work_scene_preparation_aborted",
                "frontier.production_work_scene_finalized")) {
            // Finalization and a pre-HOT abort are canonical consequences of an already
            // observed worker/input transition. They are not executor commands: registering
            // either as one would create a second mutable authority for the same scene.
            if (!type.equals("frontier.production_work_scene_finalized")
                    && !type.equals("frontier.production_work_scene_preparation_aborted")) {
                assertEquals("economy", registry.requireCommandOwner(type), type + " command owner");
            }
            assertEquals("economy", registry.requireReducedEventOwner(type), type + " reducer owner");
            assertTrue(economy.codecTypes().contains(type), type + " codec");
            assertTrue(economy.emittedPayloadTypes().contains(type), type + " emitted payload");
        }
    }

    @Test
    void sharedSceneReleaseMayEmitTheTypedProductionFinalizationItPhysicallyConfirms() {
        var releaseExecutor = FrontierWorldProcessCatalog.descriptors().stream()
                .filter(descriptor -> descriptor.id().equals("logistics-scenes"))
                .findFirst().orElseThrow();
        assertTrue(releaseExecutor.emittedPayloadTypes().contains("frontier.production_work_scene_finalized"));
    }

    @Test
    void noDomainDescriptorMayFallBackToTheGlobalWorldPayloadSet() {
        java.util.Set<String> worldPayloads = FrontierWorldProcessCatalog.allWorldPayloadTypes();
        for (var descriptor : FrontierWorldProcessCatalog.descriptors()) {
            if (descriptor.id().equals("kernel-schedule")) continue;
            assertFalse(descriptor.emittedPayloadTypes().containsAll(worldPayloads), descriptor.id());
            assertTrue(worldPayloads.stream().anyMatch(type -> !descriptor.emittedPayloadTypes().contains(type)), descriptor.id());
        }
    }

    @Test
    void everyNonKernelDescriptorHasExactlyOneExecutableModule() {
        java.util.Set<String> declared = FrontierWorldProcessCatalog.descriptors().stream()
                .map(io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor::id)
                .filter(id -> !id.equals("kernel-schedule"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(declared, FrontierWorldProcessCatalog.executableModuleIds());
    }

    @Test
    void everyProcessDescriptorRoundTripsOneOwnedRepresentativePayload() {
        Map<String, FrontierPayload> representatives = Map.ofEntries(
                Map.entry("kernel-schedule", new ScheduleEffect.Cancelled(new ScheduleId("schedule:representative"))),
                Map.entry("physical-observation", new PhysicalIntentTransition(new PhysicalIntentId("intent:representative"),
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty())),
                Map.entry("ambient-actors", new AmbientActorObserved(new SubjectId("actor:representative"), new BodyPosition(1, 64, 1), FixedScalar.ONE)),
                Map.entry("logistics-scenes", new SceneLeaseTransition(new SceneLeaseId("scene:representative"), SceneLeaseStatus.HOT)),
                Map.entry("population", new ResidentMigrationBlocked(new SubjectId("resident:representative"), ResidentMigrationBlockReason.QUARANTINE)),
                Map.entry("economy", new MarketDemandExpired(new SubjectId("demand:representative"))),
                Map.entry("resource-sites", new ResourceSiteGrowthAdvanced(new SubjectId("site:representative"), 1L, 0)),
                Map.entry("hive", new InfectionChanged(new InfectionCell(1, 1), new FixedRatio(FixedScalar.ONE))),
                Map.entry("infrastructure", new RouteTopologyCutover(new SubjectId("route-construction:representative"))),
                Map.entry("settlement-service-work", serviceWorkRepresentative()),
                Map.entry("strategy", new StrategicTaskTransition(new SubjectId("task:representative"), StrategicTaskStatus.ACTIVE)));
        assertEquals(FrontierWorldProcessCatalog.descriptors().stream().map(
                io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()), representatives.keySet());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        List<io.farfrontier.palemirror.frontier.v3.api.FrontierEvent> walEvents = new java.util.ArrayList<>();
        TransactionId transactionId = new TransactionId("transaction:process-representatives");
        WorldId worldId = new WorldId("frontier:process-representatives");
        CommandId commandId = new CommandId("command:process-representatives");
        int ordinal = 0;
        for (var descriptor : FrontierWorldProcessCatalog.descriptors()) {
            FrontierPayload payload = representatives.get(descriptor.id());
            assertTrue(descriptor.codecTypes().contains(payload.type()), descriptor.id() + " does not own " + payload.type());
            assertEquals(payload, codecs.decode(payload.type(), codecs.encode(payload)), descriptor.id());
            walEvents.add(new io.farfrontier.palemirror.frontier.v3.api.FrontierEvent(1,
                    new EventId("event:process-representative-" + ordinal++), transactionId, worldId, new Revision(1L),
                    new SimInstant(1L), new SubjectId("owner:" + descriptor.id()), CauseChain.root(commandId), payload));
        }
        TransactionRecord wal = new TransactionRecord(transactionId, worldId, new Revision(1L), new SimInstant(1L),
                walEvents, Optional.empty());
        assertEquals(wal, KernelCodec.decodeTransaction(KernelCodec.encodeTransaction(wal, codecs), codecs));
    }

    private static SettlementServiceWorkStarted serviceWorkRepresentative() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-representative"), 301L);
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().value().equals("settlement:9")).findFirst().orElseThrow();
        SettlementStructure infirmary = settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY).findFirst().orElseThrow();
        InfectionCell cell = treatmentCell(bootstrap, infirmary);
        SubjectId depot = FrontierWorldState.depotId(settlement.id()), item = new SubjectId("item:service-representative-reagent");
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap).withInfection(cell, new FixedRatio(new FixedScalar(750_000L)));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:service-representative"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(cell), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:service-representative"), objective.id(), settlement.id(),
                StrategicTaskKind.DECONTAMINATE_INFECTION_CELL, Optional.of(cell), List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY,
                StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.PENDING);
        FrontierWorldState state = initial.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)).withInventory(initial.inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        return SettlementServiceWorkProcess.planDecontamination(state, SettlementServiceWorkProcess.scan(1, 1_000L)).stream()
                .map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance).map(SettlementServiceWorkStarted.class::cast).findFirst().orElseThrow();
    }

    private static InfectionCell treatmentCell(FrontierBootstrap bootstrap, SettlementStructure infirmary) {
        for (int radius = 4; radius <= 32; radius += 4) for (int dx = -radius; dx <= radius; dx += 4) for (int dz = -radius; dz <= radius; dz += 4) {
            if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
            InfectionCell candidate = InfectionCell.at(infirmary.anchor().offset(dx, 0, dz));
            try {
                if (!InfectionTreatmentWorksite.candidates(bootstrap, candidate).isEmpty()) return candidate;
            } catch (IllegalArgumentException ignored) { }
        }
        throw new IllegalStateException("service representative has no treatment cell");
    }
}
