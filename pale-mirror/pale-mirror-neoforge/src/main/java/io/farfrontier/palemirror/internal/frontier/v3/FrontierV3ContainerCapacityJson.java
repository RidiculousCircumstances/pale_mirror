package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** Storage explanation stays separate from physical socket observations. */
final class FrontierV3ContainerCapacityJson {
    private FrontierV3ContainerCapacityJson() { }
    static String render(FrontierWorldState state, SubjectId subject) {
        var capacity = state.inventory().containerCapacity(subject, state.reservedContainerSlots(subject));
        String inbound = state.pendingContainerInbound(subject).entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> "\"" + FrontierV3DiagnosticJson.quote(entry.getKey()) + "\":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        return ",\"canonicalCapacity\":{\"exactSlots\":" + capacity.exactSlots()
                + ",\"boundFungibleSlots\":" + capacity.boundFungibleSlots()
                + ",\"packedFungibleSlots\":" + capacity.packedFungibleSlots()
                + ",\"reservedSlots\":" + capacity.reservedSlots()
                + ",\"freeCapacitySlots\":" + capacity.freeCapacitySlots()
                + ",\"reservationsValid\":" + capacity.reservationsValid()
                + ",\"pendingInbound\":" + inbound + "}";
    }
}
