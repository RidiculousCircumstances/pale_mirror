package io.farfrontier.palemirror.frontier.v3.kernel;

import java.util.Objects;
import java.util.Set;

/**
 * One closed owner in a deterministic world composition.
 *
 * <p>The descriptor is deliberately data, not a plugin SPI.  A process owns the command kinds
 * it admits, scheduled kinds it plans, event kinds it reduces and wire codecs it publishes.
 * Composition validates the complete finite set before an engine can start.</p>
 */
public record DeterministicProcessDescriptor(
        String id,
        Set<String> commandPayloadTypes,
        Set<String> scheduledKinds,
        Set<String> reducedEventTypes,
        Set<String> emittedPayloadTypes,
        Set<String> codecTypes
) {
    public DeterministicProcessDescriptor {
        id = requireIdentifier(id, "process id");
        commandPayloadTypes = normalized(commandPayloadTypes, "command payload type");
        scheduledKinds = normalized(scheduledKinds, "scheduled kind");
        reducedEventTypes = normalized(reducedEventTypes, "reduced event type");
        emittedPayloadTypes = normalized(emittedPayloadTypes, "emitted payload type");
        codecTypes = normalized(codecTypes, "codec type");
    }

    private static Set<String> normalized(Set<String> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return Set.copyOf(values.stream().map(value -> requireIdentifier(value, role)).toList());
    }

    private static String requireIdentifier(String value, String role) {
        String normalized = Objects.requireNonNull(value, role).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(role + " must not be blank");
        return normalized;
    }
}
