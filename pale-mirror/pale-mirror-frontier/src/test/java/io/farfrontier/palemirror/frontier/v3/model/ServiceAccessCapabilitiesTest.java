package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ServiceAccessCapabilitiesTest {
    private static final SubjectId POINT = new SubjectId("container:1-depot");
    private static final ServiceAccessDemand.Identity IDENTITY = new ServiceAccessDemand.Identity(
            ServiceAccessDemand.Kind.PRODUCTION, new SubjectId("job:test-production"), new SubjectId("resident:1-1"), POINT);

    @Test void closedRegistryRejectsMissingAndDuplicateOwners() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceAccessCapabilities(List.of(new ResidentMealServiceAccess())));
        assertThrows(IllegalArgumentException.class, () -> new ServiceAccessCapabilities(List.of(
                new ResidentMealServiceAccess(), new ProductionServiceAccess(), new HarvestServiceAccess(), new ProductionServiceAccess())));
    }

    @Test void wrongFamilyPointPriorityAndDuplicateDemandFailBeforeArbitration() {
        var valid = new ServiceAccessDemand(IDENTITY, ServiceAccessDemand.Priority.WORK,
                ServiceAccessDemand.Presence.OCCUPIED, 0, true);
        assertEquals(List.of(valid), registry(List.of(valid)).evaluate(null, POINT));
        var wrongFamily = new ServiceAccessDemand(new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.FIELD_HARVEST,
                IDENTITY.ownerId(), IDENTITY.actorId(), POINT), valid.priority(), valid.presence(), 0, true);
        var wrongPoint = new ServiceAccessDemand(new ServiceAccessDemand.Identity(IDENTITY.kind(), IDENTITY.ownerId(),
                IDENTITY.actorId(), new SubjectId("container:2-depot")), valid.priority(), valid.presence(), 0, true);
        var wrongPriority = new ServiceAccessDemand(IDENTITY, ServiceAccessDemand.Priority.SELF_CARE, valid.presence(), 0, true);
        for (var invalid : List.of(wrongFamily, wrongPoint, wrongPriority))
            assertThrows(IllegalArgumentException.class, () -> registry(List.of(invalid)).evaluate(null, POINT));
        assertThrows(IllegalArgumentException.class, () -> registry(List.of(valid, valid)).evaluate(null, POINT));
    }

    @Test void incompleteNominalIdentityCannotBeConstructed() {
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(null, IDENTITY.ownerId(), IDENTITY.actorId(), POINT));
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(IDENTITY.kind(), null, IDENTITY.actorId(), POINT));
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(IDENTITY.kind(), IDENTITY.ownerId(), null, POINT));
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(IDENTITY.kind(), IDENTITY.ownerId(), IDENTITY.actorId(), null));
    }

    private static ServiceAccessCapabilities registry(List<ServiceAccessDemand> demands) {
        return new ServiceAccessCapabilities(List.of(provider(ServiceAccessDemand.Kind.MEAL, List.of()),
                provider(ServiceAccessDemand.Kind.FIELD_HARVEST, List.of()), provider(ServiceAccessDemand.Kind.PRODUCTION, demands)));
    }

    private static ServiceAccessCapability provider(ServiceAccessDemand.Kind kind, List<ServiceAccessDemand> demands) {
        return new ServiceAccessCapability() {
            @Override public ServiceAccessDemand.Kind kind() { return kind; }
            @Override public ServiceAccessDemand.Priority priority() {
                return kind == ServiceAccessDemand.Kind.MEAL ? ServiceAccessDemand.Priority.SELF_CARE : ServiceAccessDemand.Priority.WORK;
            }
            @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) { return demands; }
        };
    }
}
