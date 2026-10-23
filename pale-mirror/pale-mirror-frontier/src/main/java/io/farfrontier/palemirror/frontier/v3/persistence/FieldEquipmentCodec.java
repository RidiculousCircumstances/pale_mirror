package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Optional;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.*;

final class FieldEquipmentCodec implements PayloadCodec {
    @Override public String type() { return "frontier.field_equipment_changed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        return encodeProduction(out -> {
            var value = (FieldEquipmentChanged) payload; writeSubject(out, value.siteId()); writeSubject(out, value.jobId());
            out.writeByte(value.expected().wireTag()); out.writeByte(value.action().wireTag()); out.writeBoolean(value.physical().isPresent());
            if (value.physical().isPresent()) WorkEquipmentStepCodec.write(out, value.physical().orElseThrow());
            out.writeBoolean(value.returnTarget().isPresent());
            if (value.returnTarget().isPresent()) ShipmentStateCodec.endpoint(out, value.returnTarget().orElseThrow());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return decodeProduction(bytes, in -> new FieldEquipmentChanged(readSubject(in).value(), readSubject(in).value(),
                WorkToolAssignment.Stage.decode(in.readUnsignedByte()), FieldEquipmentChanged.Action.decode(in.readUnsignedByte()),
                in.readBoolean() ? Optional.of(WorkEquipmentStepCodec.read(in)) : Optional.empty(),
                in.readBoolean() ? Optional.of(ShipmentStateCodec.endpoint(in)) : Optional.empty()));
    }
}
