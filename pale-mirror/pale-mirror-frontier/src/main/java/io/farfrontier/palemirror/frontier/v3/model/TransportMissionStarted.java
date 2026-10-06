package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import java.util.List;
import java.util.Objects;

/** One atomic admission declares both transport obligations and the reusable group roster. */
public record TransportMissionStarted(SubjectId senderId, EconomicOwnerKind senderKind, TransportMission mission, UnitGroup group, List<Shipment> shipments) implements FrontierPayload {
    public TransportMissionStarted { Objects.requireNonNull(senderId); Objects.requireNonNull(senderKind); Objects.requireNonNull(mission); Objects.requireNonNull(group); shipments = List.copyOf(shipments); }
    @Override public String type() { return "frontier.transport_mission_started"; }
}
