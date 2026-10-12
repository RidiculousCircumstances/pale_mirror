package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Exact declared dispatch for an equipment owner. No family discovery or permissive default. */
public final class EquipmentServiceCapabilities {
    public record Key(PhysicalIntentLifecycleOwner owner, PhysicalIntentKind kind, PhysicalIntentRoleSchema schema) {
        public Key { Objects.requireNonNull(owner); Objects.requireNonNull(kind); Objects.requireNonNull(schema); }
    }
    public record Capability(Key key, PhysicalIntentSubjectRole ownerRole, PhysicalIntentSubjectRole actorRole,
                             FencedRecoveryAsset recoveryAsset, ServiceAccessDemand.Kind serviceKind,
                             BiConsumer<FrontierWorldState, PhysicalIntent> validate,
                             BiConsumer<FrontierWorldState, PhysicalIntent> validateCommitment,
                             BiFunction<FrontierWorldState, PhysicalIntent, Optional<SurfaceAnchor>> serviceStation) {
        public Capability {
            Objects.requireNonNull(key); Objects.requireNonNull(ownerRole); Objects.requireNonNull(actorRole); Objects.requireNonNull(recoveryAsset);
            Objects.requireNonNull(serviceKind, "declared equipment service kind");
            Objects.requireNonNull(validate); Objects.requireNonNull(validateCommitment); Objects.requireNonNull(serviceStation);
            if (key.kind() != PhysicalIntentKind.EQUIPMENT_ISSUE && key.kind() != PhysicalIntentKind.EQUIPMENT_RETURN
                    || key.schema().owner() != key.owner() || key.schema().kind() != key.kind()
                    || !key.schema().roles().contains(ownerRole) || !key.schema().roles().contains(actorRole)
                    || !key.schema().roles().contains(PhysicalIntentSubjectRole.RESOURCE_CONTAINER))
                throw new IllegalArgumentException("equipment capability lacks its nominal owner/actor/container roles");
        }
        public SubjectId ownerId(PhysicalIntent intent) { return intent.roles().require(ownerRole); }
        public SubjectId actorId(PhysicalIntent intent) { return intent.roles().require(actorRole); }
        public EquipmentServiceRequest serviceRequest(PhysicalIntent intent) {
            var request = intent.equipmentServiceRequest().orElseThrow(() -> new IllegalArgumentException("missing equipment service declaration"));
            validateRequest(intent.roles(), request);
            return request;
        }
        private void validateRequest(PhysicalIntentRoleBinding roles, EquipmentServiceRequest request) {
            var expected = new ServiceAccessDemand.Identity(serviceKind, roles.require(ownerRole), roles.require(actorRole),
                    roles.require(PhysicalIntentSubjectRole.RESOURCE_CONTAINER));
            if (!request.identity().equals(expected)) throw new IllegalArgumentException("equipment service declaration differs from its exact capability/roles");
        }
    }
    private final Map<Key, Capability> capabilities;
    public EquipmentServiceCapabilities(Set<Key> required, Collection<Capability> declarations) {
        var entries = new LinkedHashMap<Key, Capability>();
        for (var capability : declarations) {
            if (!required.contains(capability.key()) || entries.putIfAbsent(capability.key(), capability) != null)
                throw new IllegalArgumentException("foreign or duplicate equipment capability");
        }
        if (!entries.keySet().equals(required)) throw new IllegalArgumentException("missing equipment capability");
        capabilities = Map.copyOf(entries);
    }
    public Capability require(PhysicalIntent intent) {
        return require(intent.lifecycleOwner(), intent.kind(), intent.roles(), intent.equipmentServiceRequest().orElseThrow(
                () -> new IllegalArgumentException("missing equipment service declaration")));
    }
    public Capability require(PhysicalIntentLifecycleOwner owner, PhysicalIntentKind kind, PhysicalIntentRoleBinding roles, EquipmentServiceRequest request) {
        roles.validate(owner, kind);
        var declaration = capabilities.get(new Key(owner, kind, roles.schema()));
        if (declaration == null) throw new IllegalArgumentException("unregistered equipment owner/kind/schema");
        declaration.validateRequest(roles, Objects.requireNonNull(request, "complete equipment service request"));
        return declaration;
    }
}
