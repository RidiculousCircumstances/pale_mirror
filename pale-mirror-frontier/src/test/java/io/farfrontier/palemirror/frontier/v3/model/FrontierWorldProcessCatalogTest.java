package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
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
    void fieldWorkPhysicalCommandsHaveTheResourceSiteOwnerBeforeAnExecutorCanSubmitThem() {
        DeterministicProcessRegistry registry = FrontierWorldRuntimeDefinition.processRegistry();
        for (String type : List.of(
                "frontier.resource_site_harvest_crop_prepared",
                "frontier.resource_site_harvest_progressed",
                "frontier.resource_site_harvest_traversal_advanced",
                "frontier.resource_site_harvest_scene_lease_prepared",
                "frontier.resource_site_harvest_scene_lease_handoff")) {
            assertEquals("resource-sites", registry.requireCommandOwner(type), type);
        }
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
        Map<String, FrontierPayload> representatives = Map.of(
                "kernel-schedule", new ScheduleEffect.Cancelled(new ScheduleId("schedule:representative")),
                "physical-observation", new PhysicalIntentTransition(new PhysicalIntentId("intent:representative"),
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty()),
                "ambient-actors", new AmbientActorObserved(new SubjectId("actor:representative"), new BodyPosition(1, 64, 1), FixedScalar.ONE),
                "logistics-scenes", new SceneLeaseTransition(new SceneLeaseId("scene:representative"), SceneLeaseStatus.HOT),
                "population", new ResidentMigrationBlocked(new SubjectId("resident:representative"), ResidentMigrationBlockReason.QUARANTINE),
                "economy", new MarketDemandExpired(new SubjectId("demand:representative")),
                "resource-sites", new ResourceSiteGrowthAdvanced(new SubjectId("site:representative"), 1L, 0),
                "hive", new InfectionChanged(new InfectionCell(1, 1), new FixedRatio(FixedScalar.ONE)),
                "infrastructure", new RouteTopologyCutover(new SubjectId("route-construction:representative")),
                "strategy", new StrategicTaskTransition(new SubjectId("task:representative"), StrategicTaskStatus.ACTIVE));
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
}
