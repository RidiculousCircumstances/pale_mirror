package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import org.junit.jupiter.api.Test;

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
    void noDomainDescriptorMayFallBackToTheGlobalWorldPayloadSet() {
        java.util.Set<String> worldPayloads = FrontierWorldProcessCatalog.allWorldPayloadTypes();
        for (var descriptor : FrontierWorldProcessCatalog.descriptors()) {
            if (descriptor.id().equals("kernel-schedule")) continue;
            assertFalse(descriptor.emittedPayloadTypes().containsAll(worldPayloads), descriptor.id());
            assertTrue(worldPayloads.stream().anyMatch(type -> !descriptor.emittedPayloadTypes().contains(type)), descriptor.id());
        }
    }
}
