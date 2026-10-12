package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentServiceCapabilitiesTest {
    private static final SubjectId PROJECT = new SubjectId("project:nominal");
    private static final SubjectId ACTOR = new SubjectId("actor:nominal");
    private static final SubjectId ITEM = new SubjectId("item:nominal");
    private static final SubjectId STORE = new SubjectId("warehouse:nominal");

    @Test void everyDeclaredEquipmentTupleDispatchesWithoutDiscoveringAFamilyFromItsId() {
        for (var roles : List.of(PhysicalIntentRoleBinding.engineeringEquipmentIssue(PROJECT, ACTOR, ITEM, STORE),
                PhysicalIntentRoleBinding.engineeringEquipmentReturn(PROJECT, ACTOR, ITEM, STORE),
                PhysicalIntentRoleBinding.assaultEquipmentIssue(PROJECT, ACTOR, ITEM, STORE),
                PhysicalIntentRoleBinding.assaultEquipmentReturn(PROJECT, ACTOR, ITEM, STORE))) {
            var intent = intent(roles);
            var capability = EquipmentServiceBoundary.capability(intent);
            assertEquals(PROJECT, capability.ownerId(intent));
            assertEquals(ACTOR, capability.actorId(intent));
            assertEquals(STORE, EquipmentPhysicalAuthority.container(intent));
            var prepared = new PhysicalIntentPrepared(intent);
            var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(prepared, codecs.decode(prepared.type(), codecs.encode(prepared)));
        }
    }

    @Test void missingOrDuplicateRegistrationCannotGainAuthority() {
        var roles = PhysicalIntentRoleBinding.engineeringEquipmentIssue(PROJECT, ACTOR, ITEM, STORE);
        var capability = EquipmentServiceBoundary.capability(intent(roles));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentServiceCapabilities(Set.of(capability.key()), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentServiceCapabilities(Set.of(capability.key()), List.of(capability, capability)));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentServiceCapabilities(Set.of(), List.of(capability)));
    }

    @Test void aMissingOrForgedNominalDimensionFailsBeforeOwnerCode() {
        var roles = PhysicalIntentRoleBinding.engineeringEquipmentIssue(PROJECT, ACTOR, ITEM, STORE);
        for (var removed : roles.namedRoles().keySet()) {
            var missing = new EnumMap<PhysicalIntentSubjectRole, SubjectId>(PhysicalIntentSubjectRole.class);
            missing.putAll(roles.namedRoles()); missing.remove(removed);
            assertThrows(IllegalArgumentException.class, () -> PhysicalIntentRoleBinding.decode(roles.schema(), missing));
        }
        assertThrows(NullPointerException.class, () -> new EquipmentServiceCapabilities.Key(null, roles.kind(), roles.schema()));
        assertThrows(NullPointerException.class, () -> new EquipmentServiceCapabilities.Key(roles.schema().owner(), null, roles.schema()));
        assertThrows(NullPointerException.class, () -> new EquipmentServiceCapabilities.Key(roles.schema().owner(), roles.kind(), null));
        var valid = EquipmentServiceBoundary.capability(intent(roles));
        for (var key : List.of(new EquipmentServiceCapabilities.Key(PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT, roles.kind(), roles.schema()),
                new EquipmentServiceCapabilities.Key(roles.schema().owner(), PhysicalIntentKind.EQUIPMENT_RETURN, roles.schema()),
                new EquipmentServiceCapabilities.Key(roles.schema().owner(), roles.kind(), PhysicalIntentRoleSchema.ASSAULT_EQUIPMENT_ISSUE)))
            assertThrows(IllegalArgumentException.class, () -> new EquipmentServiceCapabilities.Capability(key, valid.ownerRole(), valid.actorRole(), valid.recoveryAsset(), valid.serviceKind(),
                    (state, ignored) -> fail("invalid registration executed owner code"),
                    (state, ignored) -> fail("invalid registration executed commitment code"),
                    (state, ignored) -> { fail("invalid registration executed station code"); return Optional.empty(); }));
    }

    @Test void returnCannotDeclareADifferentContainerFromItsTypedSlot() {
        var roles = PhysicalIntentRoleBinding.assaultEquipmentReturn(PROJECT, ACTOR, ITEM, STORE);
        var valid = intent(roles);
        var forged = new PhysicalIntent(valid.id(), valid.kind(), valid.status(), valid.causeSubjectId(), valid.roles(),
                valid.origin(), valid.radiusBlocks(), valid.postcondition(), new PhysicalContainerSlot(new SubjectId("warehouse:other"), 0),
                valid.equipmentServiceRequest().orElseThrow(), valid.lifecycleOwner());
        assertThrows(IllegalArgumentException.class, () -> EquipmentServiceBoundary.targetSlot(forged));
    }

    @Test void missingOrForgedServiceDimensionsFailBeforeFamilyCallbacks() {
        var valid = intent(PhysicalIntentRoleBinding.engineeringEquipmentIssue(PROJECT, ACTOR, ITEM, STORE));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalIntent(valid.id(), valid.kind(), valid.status(), valid.causeSubjectId(),
                valid.roles(), valid.origin(), 0, valid.postcondition(), valid.lifecycleOwner()), "old ownerless service constructor is not an equipment admission");
        var capability = EquipmentServiceBoundary.capability(valid);
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        var guarded = new EquipmentServiceCapabilities.Capability(capability.key(), capability.ownerRole(), capability.actorRole(),
                capability.recoveryAsset(), capability.serviceKind(), (state, ignored) -> called.set(true),
                (state, ignored) -> called.set(true), (state, ignored) -> { called.set(true); return Optional.empty(); });
        var registry = new EquipmentServiceCapabilities(Set.of(capability.key()), List.of(guarded));
        var other = new SubjectId("subject:foreign");
        for (var identity : List.of(
                new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT, PROJECT, ACTOR, STORE),
                new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.ENGINEERING_EQUIPMENT, other, ACTOR, STORE),
                new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.ENGINEERING_EQUIPMENT, PROJECT, other, STORE),
                new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.ENGINEERING_EQUIPMENT, PROJECT, ACTOR, other))) {
            var request = new EquipmentServiceRequest(identity, 73L);
            assertThrows(IllegalArgumentException.class, () -> new PhysicalIntent(valid.id(), valid.kind(), valid.status(), valid.causeSubjectId(),
                    valid.roles(), valid.origin(), 0, valid.postcondition(), request, valid.lifecycleOwner()));
            assertThrows(IllegalArgumentException.class, () -> registry.require(valid.lifecycleOwner(), valid.kind(), valid.roles(), request).validate().accept(null, valid));
            assertFalse(called.get(), "forged nominal declaration reached family behavior");
        }
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(null, PROJECT, ACTOR, STORE));
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(capability.serviceKind(), null, ACTOR, STORE));
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(capability.serviceKind(), PROJECT, null, STORE));
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(capability.serviceKind(), PROJECT, ACTOR, null));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentServiceRequest(valid.equipmentServiceRequest().orElseThrow().identity(), -1L));
    }

    private static PhysicalIntent intent(PhysicalIntentRoleBinding roles) {
        var origin = new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO);
        var id = new PhysicalIntentId("intent:equipment-declaration");
        var serviceKind = switch (roles.schema()) {
            case ENGINEERING_EQUIPMENT_ISSUE, ENGINEERING_EQUIPMENT_RETURN -> ServiceAccessDemand.Kind.ENGINEERING_EQUIPMENT;
            case ASSAULT_EQUIPMENT_ISSUE, ASSAULT_EQUIPMENT_RETURN -> ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT;
            default -> throw new IllegalArgumentException("fixture requires an explicit equipment tuple");
        };
        var request = new EquipmentServiceRequest(new ServiceAccessDemand.Identity(serviceKind, PROJECT, ACTOR, STORE), 73L);
        if (roles.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
            return new PhysicalIntent(id, roles.kind(), PhysicalIntentStatus.PREPARED, new SubjectId("settlement:nominal"), roles,
                    origin, 0, PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED, new PhysicalContainerSlot(STORE, 0), request, roles.schema().owner());
        return new PhysicalIntent(id, roles.kind(), PhysicalIntentStatus.PREPARED, new SubjectId("settlement:nominal"), roles,
                origin, 0, PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED, request, roles.schema().owner());
    }
}
