package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceBoundaryPlanningIsolationTest {
    @Test void anotherWorkerCannotEvictTheCurrentWorkersDeclarationOrColumnImage() throws Exception {
        var state = initial("frontier:boundary-owner");
        var other = initial("frontier:boundary-worker");
        var declared = ServiceBoundaryComposition.declarations(state);
        var columns = ServiceBoundaryComposition.nearbyDeclarationIndex(state);
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                assertFalse(ServiceBoundaryComposition.declarations(other).isEmpty());
                assertFalse(ServiceBoundaryComposition.nearbyDeclarationIndex(other).isEmpty());
            }).get(10, TimeUnit.SECONDS);
        }
        assertSame(declared, ServiceBoundaryComposition.declarations(state),
                "an unchanged lane must not rebuild all service declarations after a foreign worker read");
        assertSame(columns, ServiceBoundaryComposition.nearbyDeclarationIndex(state),
                "an unchanged lane must retain its expanded service-column index");
    }

    @Test void capturedClearanceDoesNotBorrowChangedDeclarationsOrGrantForeignAccess() {
        var initial = initial("frontier:boundary-branches");
        var home = initial.bootstrap().settlements().getFirst();
        var actor = home.residents().getFirst().id();
        var id = new SubjectId("container:boundary-branch");
        var position = new BlockPosition(0, 64, 0);
        var contact = SurfaceAnchor.at(1, 63, 1);
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, contact, Optional.empty(), contact);
        var record = ContainerRecord.storage(id, home.id(), 27, StorageBlockKind.CHEST,
                new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE));
        var declared = new ResourceStorageDeclared(record, position).apply(initial, home.id());
        ImmutablePlanningScope.within(() -> {
            assertFalse(ServiceBoundaryComposition.declarations(initial).containsKey(id));
            var clearance = ServiceClearanceRoutes.from(initial, actor, contact);
            assertFalse(clearance.temporary(contact));
            var after = ServiceBoundaryComposition.declarations(declared);
            assertTrue(after.containsKey(id));
            assertTrue(ServiceClearanceRoutes.from(declared, actor, contact).temporary(contact),
                    "new storage interaction geometry must be visible to a new plan");
            assertFalse(clearance.temporary(contact), "the earlier plan retains only its captured immutable declarations");
            assertFalse(ServiceBoundaryComposition.declarations(initial).containsKey(id));
            assertTrue(ServiceBoundaryComposition.declarations(declared).containsKey(id));
            return true;
        });
        assertFalse(ServiceBoundaryComposition.declarations(initial).containsKey(id));
        assertThrows(IllegalArgumentException.class, () -> ServiceBoundaryComposition.require(initial,
                ServiceBoundaryComposition.declaration(declared, id).identity()),
                "a cached foreign declaration must never become access authority");
    }

    private static FrontierWorldState initial(String id) {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(id), 421L));
    }
}
