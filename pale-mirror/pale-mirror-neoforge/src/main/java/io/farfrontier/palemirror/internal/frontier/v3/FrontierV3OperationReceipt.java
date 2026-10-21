package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Storage-independent physical protocol. It neither selects work nor changes resource custody.
 * A producer must validate its current durable domain intent before opening this envelope. */
record FrontierV3OperationReceipt(Declaration declaration, UUID ticket, long sequence, Set<Participant> applied,
        Set<Participant> saved, boolean ownerClosed) {
    enum Medium { TERRAIN, CHUNK_BLOCKS, ENTITIES, ENTITY_COLUMN, PLAYER }
    /** Closed, explicit producer keys. They name authority; the coordinator never
     * infers a protocol from an owner ID or dispatches business logic on these keys. */
    enum Protocol {
        MEAL_TAKE("resident-meal-take-v1"), MEAL_CONSUME("resident-meal-consume-v1"),
        STATION_FUEL("station-fuel-v1"), STATION_RECIPE("station-recipe-v1"), BAKERY("bakery-effect-v1"),
        EXTRACTION_BLOCK("extraction-block-v1"), EXTRACTION_EQUIPMENT("extraction-equipment-v1"),
        EXTRACTION_CARGO("extraction-cargo-v1"), EXPEDITION_SUPPLY("expedition-supply-v1"),
        EXPEDITION_REPLENISHMENT("expedition-replenishment-v1"), FIELD_DELIVERY("field-delivery-v1"), FIELD_WORK("field-work-v1"),
        SHIPMENT_TRANSFER("shipment-transfer-v1"), FIELD_PROJECTION("field-projection-v1"),
        PLAYER_GROUND_PICKUP("player-ground-pickup-v1"), PLAYER_CONTAINER_CLICK("player-container-click-v1"),
        GROUND_DESTRUCTION("ground-destruction-v1"), GROUND_MERGE("ground-merge-v1");
        private final String wire;
        Protocol(String wire) { this.wire = wire; }
        String wire() { return wire; }
        private static final Map<String, Protocol> WIRES;
        static {
            var values = new HashMap<String, Protocol>();
            for (var value : values()) if (values.put(value.wire, value) != null)
                throw new IllegalStateException("duplicate native operation protocol wire key");
            WIRES = Map.copyOf(values);
        }
        static Protocol fromWire(String wire) {
            var value = WIRES.get(wire);
            if (value == null) throw new IllegalArgumentException("unknown native operation protocol: " + wire);
            return value;
        }
    }
    record Participant(Medium medium, String identity, long incarnation) {
        Participant {
            Objects.requireNonNull(medium); text(identity);
            if (incarnation < 0) throw new IllegalArgumentException("negative native participant incarnation");
        }
    }
    record Declaration(Protocol protocol, SubjectId owner, String operation, String fingerprint,
            Set<Participant> participants) {
        Declaration {
            Objects.requireNonNull(protocol); Objects.requireNonNull(owner); text(operation); text(fingerprint);
            participants = Set.copyOf(participants);
            if (participants.isEmpty() || participants.size() > 64)
                throw new IllegalArgumentException("physical operation needs a bounded exact participant set");
        }
    }
    /** This token must be serialized WITH the participant's changed physical state.
     * Creating it, observing it in memory or submitting a write does not establish durability. */
    record NativeReceipt(UUID ticket, long sequence, Participant participant, String fingerprint) {
        NativeReceipt {
            Objects.requireNonNull(ticket); Objects.requireNonNull(participant); text(fingerprint);
            if (sequence < 1) throw new IllegalArgumentException("invalid native operation sequence");
        }
    }
    FrontierV3OperationReceipt {
        Objects.requireNonNull(declaration); Objects.requireNonNull(ticket);
        if (sequence < 1) throw new IllegalArgumentException("invalid physical operation sequence");
        applied = Set.copyOf(applied); saved = Set.copyOf(saved);
        if (!declaration.participants().containsAll(applied) || !applied.containsAll(saved)
                || ownerClosed && !applied.equals(declaration.participants()))
            throw new IllegalArgumentException("invalid physical operation acknowledgement order");
    }
    static FrontierV3OperationReceipt prepare(Declaration declaration, long sequence) {
        return new FrontierV3OperationReceipt(declaration, UUID.randomUUID(), sequence, Set.of(), Set.of(), false);
    }
    NativeReceipt nativeReceipt(Participant participant) {
        requireParticipant(participant);
        return new NativeReceipt(ticket, sequence, participant, declaration.fingerprint());
    }
    FrontierV3OperationReceipt applied(NativeReceipt receipt) {
        requireReceipt(receipt); var next = new HashSet<>(applied); next.add(receipt.participant());
        return new FrontierV3OperationReceipt(declaration, ticket, sequence, next, saved, ownerClosed);
    }
    /** Invoked only by an actual successful write+sync boundary or a validated disk read.
     * A native receipt can outlive a crash before the physical journal's applied update. */
    FrontierV3OperationReceipt saved(NativeReceipt receipt) {
        requireReceipt(receipt); var nextApplied = new HashSet<>(applied); nextApplied.add(receipt.participant());
        var nextSaved = new HashSet<>(saved); nextSaved.add(receipt.participant());
        return new FrontierV3OperationReceipt(declaration, ticket, sequence, nextApplied, nextSaved, ownerClosed);
    }
    /** The domain producer supplies this only after its canonical consequence and successor
     * boundary are durable and no live incoming operation still needs this envelope. */
    FrontierV3OperationReceipt closeOwner() {
        if (!applied.equals(declaration.participants()))
            throw new IllegalStateException("cannot close an incompletely observed physical operation");
        return new FrontierV3OperationReceipt(declaration, ticket, sequence, applied, saved, true);
    }
    boolean retirable() { return ownerClosed && saved.equals(declaration.participants()); }
    private void requireParticipant(Participant participant) {
        if (!declaration.participants().contains(participant)) throw new IllegalArgumentException("foreign native participant");
    }
    private void requireReceipt(NativeReceipt receipt) {
        requireParticipant(receipt.participant());
        if (!ticket.equals(receipt.ticket()) || sequence != receipt.sequence() || !declaration.fingerprint().equals(receipt.fingerprint()))
            throw new IllegalArgumentException("native receipt belongs to another physical operation");
    }
    private static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 4096)
            throw new IllegalArgumentException("invalid physical operation identity");
    }
}
