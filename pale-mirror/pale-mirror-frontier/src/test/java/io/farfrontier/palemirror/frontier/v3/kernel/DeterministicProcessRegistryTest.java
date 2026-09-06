package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicProcessRegistryTest {
    @Test
    void duplicateScheduledOwnerFailsBeforeAnyWorldCanStart() {
        DeterministicProcessDescriptor first = descriptor("first", Set.of(), Set.of("test.schedule"), Set.of("test.first"), Set.of("test.first"), Set.of("test.first"));
        DeterministicProcessDescriptor second = descriptor("second", Set.of(), Set.of("test.schedule"), Set.of("test.second"), Set.of("test.second"), Set.of("test.second"));
        assertThrows(IllegalArgumentException.class, () -> new DeterministicProcessRegistry(List.of(first, second), codecs("test.first", "test.second")));
    }

    @Test
    void omittedCodecFailsBeforeAnyPlannerCanEmitThePayload() {
        DeterministicProcessDescriptor descriptor = descriptor("owner", Set.of(), Set.of(), Set.of("test.missing"), Set.of("test.missing"), Set.of("test.missing"));
        assertThrows(IllegalArgumentException.class, () -> new DeterministicProcessRegistry(List.of(descriptor), codecs("test.other")));
    }

    @Test
    void undeclaredEmissionFailsBeforeTransactionCommit() {
        DeterministicProcessDescriptor owner = descriptor("owner", Set.of("test.first"), Set.of(), Set.of("test.first"), Set.of("test.first"), Set.of("test.first"));
        DeterministicProcessDescriptor other = descriptor("other", Set.of(), Set.of(), Set.of("test.second"), Set.of("test.second"), Set.of("test.second"));
        DeterministicProcessRegistry registry = new DeterministicProcessRegistry(List.of(owner, other), codecs("test.first", "test.second"));
        assertEquals("owner", registry.requireCommandOwner("test.first"));
        assertThrows(IllegalStateException.class, () -> registry.validateEmissions("owner",
                List.of(new ProposedEvent(new SubjectId("subject:test"), new TestPayload("test.second")))));
    }

    @Test
    void emittedPayloadWithoutReducerOwnerFailsBeforeRuntimeConstruction() {
        DeterministicProcessDescriptor owner = descriptor("owner", Set.of(), Set.of(), Set.of("test.first"),
                Set.of("test.first", "test.emitted"), Set.of("test.first", "test.emitted"));
        assertThrows(IllegalArgumentException.class, () -> new DeterministicProcessRegistry(List.of(owner),
                codecs("test.first", "test.emitted")));
    }

    @Test
    void persistenceCodecOwnerDisagreementFailsBeforeRuntimeConstruction() {
        DeterministicProcessDescriptor owner = descriptor("owner", Set.of(), Set.of(), Set.of("test.first"),
                Set.of("test.first"), Set.of("test.first"));
        assertThrows(IllegalArgumentException.class, () -> new DeterministicProcessRegistry(List.of(owner),
                codecs("test.first"), java.util.Map.of("owner", Set.of("test.other"))));
    }

    private static DeterministicProcessDescriptor descriptor(String id, Set<String> commands, Set<String> schedules,
                                                              Set<String> events, Set<String> emissions, Set<String> codecs) {
        return new DeterministicProcessDescriptor(id, commands, schedules, events, emissions, codecs);
    }

    private static PayloadCodecs codecs(String... types) {
        return new PayloadCodecs(java.util.Arrays.stream(types).map(TestCodec::new).map(codec -> (PayloadCodec) codec).toList());
    }

    private record TestPayload(String type) implements FrontierPayload { }

    private record TestCodec(String type) implements PayloadCodec {
        @Override public byte[] encode(FrontierPayload payload) { return payload.type().getBytes(StandardCharsets.UTF_8); }
        @Override public FrontierPayload decode(byte[] encoded) { return new TestPayload(new String(encoded, StandardCharsets.UTF_8)); }
    }
}
