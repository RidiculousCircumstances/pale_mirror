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
        this(descriptors, codecs, Map.of());
    }

    /**
     * Builds the closed process registry and, when supplied, proves the persistence-side codec
     * wiring is owned by exactly the descriptor that declares it.
     */
    public DeterministicProcessRegistry(List<DeterministicProcessDescriptor> descriptors, PayloadCodecs codecs,
                                        Map<String, Set<String>> codecTypesByProcess) {
        Objects.requireNonNull(descriptors, "descriptors");
        Objects.requireNonNull(codecs, "codecs");
        Objects.requireNonNull(codecTypesByProcess, "codecTypesByProcess");
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
        requirePersistenceOwnership(byId, codecTypesByProcess, actualCodecTypes);
        for (DeterministicProcessDescriptor descriptor : byId.values()) {
            requireDeclaredCodecs(codecOwners, descriptor, descriptor.commandPayloadTypes(), "command payload");
            requireDeclaredCodecs(codecOwners, descriptor, descriptor.reducedEventTypes(), "reduced event payload");
            requireDeclaredCodecs(codecOwners, descriptor, descriptor.emittedPayloadTypes(), "emitted payload");
            requireReducedOwners(events, descriptor, descriptor.emittedPayloadTypes());
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

    private static void requirePersistenceOwnership(Map<String, DeterministicProcessDescriptor> descriptors,
                                                    Map<String, Set<String>> codecTypesByProcess,
                                                    Set<String> actualCodecTypes) {
        if (codecTypesByProcess.isEmpty()) return;
        if (!descriptors.keySet().equals(codecTypesByProcess.keySet())) {
            Set<String> missing = new java.util.TreeSet<>(descriptors.keySet());
            missing.removeAll(codecTypesByProcess.keySet());
            Set<String> undeclared = new java.util.TreeSet<>(codecTypesByProcess.keySet());
            undeclared.removeAll(descriptors.keySet());
            throw new IllegalArgumentException("persistence codec owners differ from process descriptors; missing="
                    + missing + " undeclared=" + undeclared);
        }
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : codecTypesByProcess.entrySet()) {
            Set<String> installed = Set.copyOf(entry.getValue());
            DeterministicProcessDescriptor descriptor = descriptors.get(entry.getKey());
            if (!descriptor.codecTypes().equals(installed)) {
                throw new IllegalArgumentException("persistence codec ownership differs for process " + entry.getKey()
                        + "; descriptor=" + descriptor.codecTypes() + " persistence=" + installed);
            }
            for (String type : installed) {
                if (!seen.add(type)) throw new IllegalArgumentException("duplicate persistence codec owner for " + type);
            }
        }
        if (!seen.equals(actualCodecTypes)) {
            throw new IllegalArgumentException("persistence codec ownership does not cover installed payload codecs");
        }
    }

    private static void requireReducedOwners(Map<String, DeterministicProcessDescriptor> eventOwners,
                                             DeterministicProcessDescriptor descriptor, Set<String> types) {
        for (String type : types) {
            // Kernel schedule effects are consumed by the kernel queue before the world reducer;
            // all Frontier world facts must still name one world reducer owner below.
            if (type.startsWith("kernel.")) continue;
            if (!eventOwners.containsKey(type)) {
                throw new IllegalArgumentException("process " + descriptor.id()
                        + " emits payload without a deterministic reducer owner: " + type);
            }
        }
    }
}
