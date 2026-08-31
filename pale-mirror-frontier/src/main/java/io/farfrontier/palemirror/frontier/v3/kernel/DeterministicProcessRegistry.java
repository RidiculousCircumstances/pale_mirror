package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Closed deterministic ownership registry for one world composition.
 *
 * <p>It deliberately has no reflection or service loading.  Startup rejects a duplicate,
 * an unowned codec, an event without a reducer owner or an emitted type outside its process
 * contract.  This turns payload/codec completeness into an engine-admission invariant instead
 * of a code-review convention.</p>
 */
public final class DeterministicProcessRegistry {
    private final Map<String, DeterministicProcessDescriptor> commandOwners;
    private final Map<String, DeterministicProcessDescriptor> scheduledOwners;
    private final Map<String, DeterministicProcessDescriptor> reducedEventOwners;
    private final Map<String, DeterministicProcessDescriptor> descriptors;

    public DeterministicProcessRegistry(List<DeterministicProcessDescriptor> descriptors, PayloadCodecs codecs) {
        Objects.requireNonNull(descriptors, "descriptors");
        Objects.requireNonNull(codecs, "codecs");
        Map<String, DeterministicProcessDescriptor> byId = new LinkedHashMap<>();
        Map<String, DeterministicProcessDescriptor> commands = new LinkedHashMap<>();
        Map<String, DeterministicProcessDescriptor> schedules = new LinkedHashMap<>();
        Map<String, DeterministicProcessDescriptor> events = new LinkedHashMap<>();
        Map<String, DeterministicProcessDescriptor> codecOwners = new LinkedHashMap<>();
        for (DeterministicProcessDescriptor descriptor : List.copyOf(descriptors)) {
            Objects.requireNonNull(descriptor, "process descriptor");
            putUnique(byId, descriptor.id(), descriptor, "process id");
            descriptor.commandPayloadTypes().forEach(type -> putUnique(commands, type, descriptor, "command payload"));
            descriptor.scheduledKinds().forEach(kind -> putUnique(schedules, kind, descriptor, "scheduled kind"));
            descriptor.reducedEventTypes().forEach(type -> putUnique(events, type, descriptor, "reduced event payload"));
            descriptor.codecTypes().forEach(type -> putUnique(codecOwners, type, descriptor, "payload codec"));
        }
        Set<String> actualCodecTypes = codecs.types();
        if (!codecOwners.keySet().equals(actualCodecTypes)) {
            java.util.Set<String> missing = new java.util.TreeSet<>(actualCodecTypes); missing.removeAll(codecOwners.keySet());
            java.util.Set<String> undeclared = new java.util.TreeSet<>(codecOwners.keySet()); undeclared.removeAll(actualCodecTypes);
            throw new IllegalArgumentException("process codec ownership differs from payload registry; missing=" + missing + " undeclared=" + undeclared);
        }
        for (DeterministicProcessDescriptor descriptor : byId.values()) {
            requireDeclaredCodecs(codecOwners, descriptor, descriptor.commandPayloadTypes(), "command payload");
            requireDeclaredCodecs(codecOwners, descriptor, descriptor.reducedEventTypes(), "reduced event payload");
            requireDeclaredCodecs(codecOwners, descriptor, descriptor.emittedPayloadTypes(), "emitted payload");
        }
        commandOwners = Map.copyOf(commands);
        scheduledOwners = Map.copyOf(schedules);
        reducedEventOwners = Map.copyOf(events);
        this.descriptors = Map.copyOf(byId);
    }

    public String requireCommandOwner(String payloadType) { return require(commandOwners, payloadType, "command payload").id(); }

    public String requireScheduledOwner(String scheduledKind) { return require(scheduledOwners, scheduledKind, "scheduled kind").id(); }

    public String requireReducedEventOwner(String payloadType) { return require(reducedEventOwners, payloadType, "reduced event payload").id(); }

    /** Fails before a transaction is committed if a process emitted an undeclared durable fact. */
    public List<ProposedEvent> validateEmissions(String processId, List<ProposedEvent> events) {
        DeterministicProcessDescriptor owner = descriptors.get(Objects.requireNonNull(processId, "processId"));
        if (owner == null) throw new IllegalArgumentException("unknown deterministic process: " + processId);
        for (ProposedEvent event : List.copyOf(events)) {
            String type = event.payload().type();
            if (!owner.emittedPayloadTypes().contains(type)) {
                throw new IllegalStateException("process " + processId + " emitted undeclared payload: " + type);
            }
        }
        return List.copyOf(events);
    }

    private static DeterministicProcessDescriptor require(Map<String, DeterministicProcessDescriptor> owners, String key, String role) {
        DeterministicProcessDescriptor descriptor = owners.get(Objects.requireNonNull(key, role));
        if (descriptor == null) throw new IllegalArgumentException("unregistered deterministic " + role + ": " + key);
        return descriptor;
    }

    private static void putUnique(Map<String, DeterministicProcessDescriptor> values, String key,
                                  DeterministicProcessDescriptor owner, String role) {
        DeterministicProcessDescriptor previous = values.putIfAbsent(key, owner);
        if (previous != null) {
            throw new IllegalArgumentException("duplicate deterministic " + role + " owner for " + key + ": "
                    + previous.id() + " and " + owner.id());
        }
    }

    private static void requireDeclaredCodecs(Map<String, DeterministicProcessDescriptor> codecOwners,
                                              DeterministicProcessDescriptor descriptor, Set<String> types, String role) {
        for (String type : types) {
            if (!codecOwners.containsKey(type)) {
                throw new IllegalArgumentException("process " + descriptor.id() + " declares " + role
                        + " without a stable codec: " + type);
            }
        }
    }
}
