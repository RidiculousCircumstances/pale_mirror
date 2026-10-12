package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentServiceRequest;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.ServiceAccessDemand;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Same explicit request declaration in snapshots and WAL; no recovered identity completion. */
final class EquipmentServiceRequestCodec {
    private EquipmentServiceRequestCodec() { }
    static void write(DataOutputStream output, Optional<EquipmentServiceRequest> request) throws IOException {
        output.writeBoolean(request.isPresent());
        if (request.isEmpty()) return;
        var value = request.orElseThrow();
        output.writeByte(FrontierWireTags.tag(value.identity().kind()));
        FrontierWorldStateCodec.writeString(output, value.identity().ownerId().value());
        FrontierWorldStateCodec.writeString(output, value.identity().actorId().value());
        FrontierWorldStateCodec.writeString(output, value.identity().pointId().value());
        output.writeLong(value.requestedAtTick());
    }
    static Optional<EquipmentServiceRequest> read(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return Optional.empty();
        var identity = new ServiceAccessDemand.Identity(FrontierWireTags.require(ServiceAccessDemand.Kind.class, input.readUnsignedByte()),
                new SubjectId(FrontierWorldStateCodec.readString(input)), new SubjectId(FrontierWorldStateCodec.readString(input)),
                new SubjectId(FrontierWorldStateCodec.readString(input)));
        return Optional.of(new EquipmentServiceRequest(identity, input.readLong()));
    }
}
