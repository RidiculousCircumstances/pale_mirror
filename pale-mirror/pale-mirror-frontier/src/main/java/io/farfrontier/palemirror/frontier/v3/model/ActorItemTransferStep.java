package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import java.util.List;
import java.util.Objects;

/** Generic durable preimage for one loaded inventory interaction; the task owns its lifecycle. */
public record ActorItemTransferStep(ActorHotObservation observation, List<MaterialSourceSelection.Slice> source,
                                    long destinationEpoch, int destinationSlot, int destinationBefore) {
    public ActorItemTransferStep(ActorHotObservation observation, List<MaterialSourceSelection.Slice> source, long destinationEpoch) {
        this(observation, source, destinationEpoch, -1, 0);
    }
    public ActorItemTransferStep {
        Objects.requireNonNull(observation); source = List.copyOf(source);
        if (source.isEmpty() || source.size() > 27 || destinationEpoch < 1
                || source.stream().map(MaterialSourceSelection.Slice::address).distinct().count() != source.size()
                || source.stream().map(MaterialSourceSelection.Slice::epoch).distinct().count() != 1
                || source.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum() > 64
                || destinationSlot < -1 || destinationSlot > 26 || destinationBefore < 0
                || destinationBefore + source.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum() > 64
                || destinationSlot == -1 && destinationBefore != 0)
            throw new IllegalArgumentException("item interaction needs one bounded exact physical preimage");
    }
    public long sourceEpoch() { return source.getFirst().epoch(); }
}
